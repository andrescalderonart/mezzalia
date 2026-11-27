import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { crearPedido, llamarDesdeMesa, obtenerCarta } from '../api/publico.js'
import { guardarUltimaRonda, leerUltimaRonda, resumenRonda, rondaComoCarrito } from './ronda.js'
import { claveDe, nombresQuitados, parsearClave, textoSin } from './carrito.js'
import VistaIngredientes from './VistaIngredientes.jsx'
import PedidoDeMesa from './PedidoDeMesa.jsx'
import { IconoCampana, IconoCapas, IconoCheck, IconoMas, IconoMenos, IconoMesa, IconoRecibo, IconoRonda } from './iconos.jsx'

const formatoPrecio = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
})

function generarClaveIdempotencia() {
  // crypto.randomUUID existe en todo navegador moderno servido sobre HTTPS
  // (o localhost) -- el fallback de abajo es solo para no romper en un
  // contexto raro (http:// plano en LAN local, por ejemplo) donde la API
  // Crypto puede no estar disponible.
  if (window.crypto?.randomUUID) return window.crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export default function CartaPage() {
  const { token } = useParams()
  const [estado, setEstado] = useState('cargando') // 'cargando' | 'error' | 'listo'
  const [error, setError] = useState('')
  const [carta, setCarta] = useState(null)
  const [categoriaActiva, setCategoriaActiva] = useState(null)

  // carrito[itemId] = cantidad. Vive solo en memoria del navegador -- si el
  // comensal recarga la pagina, el carrito se pierde (mismo criterio que
  // sesiones[] en MesasPage.jsx del panel: no hay backend de "borrador de
  // pedido", el pedido solo existe una vez que se confirma y se envia).
  const [carrito, setCarrito] = useState({})
  // 2026-09-11: un solo plato expandido a la vez (acordeón, no todos a la
  // vez) -- una carta con 30 platos con todas las fotos abiertas de una
  // seria un scroll interminable y iria en contra del punto de la lista
  // tradicional (compacta, fácil de escanear con la vista).
  const [expandidoId, setExpandidoId] = useState(null)
  const [modalAbierto, setModalAbierto] = useState(false)
  const [notas, setNotas] = useState('')
  const [comensalNombre, setComensalNombre] = useState('')
  const [comensalTelefono, setComensalTelefono] = useState('')
  const [comensalConsentimiento, setComensalConsentimiento] = useState(false)
  const [envio, setEnvio] = useState({ estado: 'idle', error: '', pedido: null }) // idle|enviando|exito|error

  // Enfoque en bares (2026-11-26): lo ultimo que pidio ESTE celular en esta
  // mesa, para "otra ronda" con un toque (ver ronda.js).
  const [ultimaRonda, setUltimaRonda] = useState(() => leerUltimaRonda(token))
  // Estado de cada boton de llamado: idle | enviando | avisado.
  const [llamados, setLlamados] = useState({ MESERO: 'idle', CUENTA: 'idle' })
  // Mensaje corto arriba de la pantalla ("Avisamos al mesero").
  const [aviso, setAviso] = useState(null) // { texto, tipo: 'ok' | 'error' }
  const avisoTimerRef = useRef(null)
  // Plato abierto en la vista "desarmada" de ingredientes (o null).
  const [platoIngredientes, setPlatoIngredientes] = useState(null)
  // "Pedido de la mesa" (2026-11-27): apodo opcional de quien pide desde ESTE
  // celular (se recuerda por QR para las siguientes rondas) y la vista con
  // lo que pidió toda la mesa.
  const [apodo, setApodo] = useState(() => {
    try {
      return localStorage.getItem(`mezzalia.apodo.${token}`) || ''
    } catch {
      return ''
    }
  })
  const [verMesa, setVerMesa] = useState(false)

  // Misma clave de idempotencia (RF-04) mientras el comensal no haya
  // enviado el pedido con exito -- si el POST falla por red y reintenta
  // manualmente, el backend lo trata como el MISMO intento, no como un
  // pedido nuevo. Se regenera solo despues de un envio exitoso (ver
  // onEnviarPedido), para la proxima ronda de pedido de la misma mesa.
  const claveIdempotenciaRef = useRef(generarClaveIdempotencia())

  const seccionesRef = useRef({})
  const navRef = useRef(null)

  useEffect(() => {
    let cancelado = false
    setEstado('cargando')
    obtenerCarta(token)
      .then((datos) => {
        if (cancelado) return
        setCarta(datos)
        setCategoriaActiva(datos.categorias[0]?.id ?? null)
        setEstado('listo')
      })
      .catch((e) => {
        if (cancelado) return
        setError(e.message)
        setEstado('error')
      })
    return () => {
      cancelado = true
    }
  }, [token])

  useEffect(() => {
    document.title = carta?.nombreRestaurante ? `${carta.nombreRestaurante} · Carta` : 'Carta digital'
  }, [carta])

  // Resalta en la fila de categorías cuál sección está a la vista mientras
  // el comensal hace scroll -- sin esto, la fila de chips queda "muda" y no
  // ayuda a ubicarse en una carta larga con muchas categorías.
  useEffect(() => {
    if (estado !== 'listo') return
    const secciones = Object.values(seccionesRef.current).filter(Boolean)
    if (secciones.length === 0) return

    const observer = new IntersectionObserver(
      (entradas) => {
        const visible = entradas
          .filter((e) => e.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0]
        if (visible) {
          setCategoriaActiva(Number(visible.target.dataset.categoriaId))
        }
      },
      { rootMargin: '-35% 0px -55% 0px' },
    )
    secciones.forEach((el) => observer.observe(el))
    return () => observer.disconnect()
  }, [estado, carta])

  function irACategoria(categoriaId) {
    setCategoriaActiva(categoriaId)
    seccionesRef.current[categoriaId]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  // Sin color propio, Mezzalia usa tinta (botones negros, estilo app).
  // --color-sobre-acento se calcula: con un color de marca claro (amarillo,
  // menta…) el texto blanco no se leería, así que pasa a tinta (WCAG 1.4.3).
  const variablesMarca = useMemo(() => {
    const acento = carta?.branding?.colorPrimario || '#18181b'
    return {
      '--color-acento': acento,
      '--color-acento-suave': carta?.branding?.colorSecundario || '#f4f4f2',
      '--color-sobre-acento': textoSobre(acento),
    }
  }, [carta])

  // Mapa id -> item de toda la carta, para poder resolver nombre/precio de
  // lo que hay en `carrito` (que solo guarda cantidades por id) sin recorrer
  // categorias cada vez que se pinta el resumen del pedido.
  const itemsPorId = useMemo(() => {
    const mapa = {}
    carta?.categorias?.forEach((categoria) => {
      categoria.items.forEach((item) => {
        mapa[item.id] = item
      })
    })
    return mapa
  }, [carta])

  // Cada clave del carrito es una linea: el plato tal cual ("12") o con
  // ingredientes quitados ("12~3.7", ver carrito.js).
  const itemsCarrito = useMemo(
    () =>
      Object.entries(carrito)
        .map(([clave, cantidad]) => {
          const { itemId, sinIds } = parsearClave(clave)
          const item = itemsPorId[itemId]
          return { clave, item, cantidad, sinIds, sinTexto: textoSin(nombresQuitados(item, sinIds)) }
        })
        .filter((entrada) => entrada.item), // por si un item cambiara/desapareciera de la carta a mitad de sesion
    [carrito, itemsPorId],
  )
  const totalCarrito = itemsCarrito.reduce((acc, { item, cantidad }) => acc + item.precio * cantidad, 0)
  const cantidadTotalCarrito = itemsCarrito.reduce((acc, { cantidad }) => acc + cantidad, 0)

  // `clave` = linea del carrito: el id del plato tal cual, o "id~sin" (carrito.js).
  function agregarUnidad(clave) {
    setCarrito((prev) => ({ ...prev, [clave]: (prev[clave] || 0) + 1 }))
  }

  function quitarUnidad(clave) {
    setCarrito((prev) => {
      const actual = prev[clave] || 0
      if (actual <= 1) {
        const { [clave]: _omitido, ...resto } = prev
        return resto
      }
      return { ...prev, [clave]: actual - 1 }
    })
  }

  async function onEnviarPedido() {
    if (itemsCarrito.length === 0) return
    setEnvio({ estado: 'enviando', error: '', pedido: null })
    try {
      const payload = {
        claveIdempotencia: claveIdempotenciaRef.current,
        items: itemsCarrito.map(({ item, cantidad, sinIds }) => ({
          itemMenuId: item.id,
          cantidad,
          ...(sinIds.length > 0 ? { sinIngredientes: sinIds } : {}),
        })),
        notas: notas.trim() || undefined,
        apodo: apodo.trim() || undefined,
        // Sin consentimiento explicito, no se manda absolutamente ningun
        // dato del comensal -- ni siquiera un objeto vacio (ver
        // PedidoService.crearComensalSiAplica en el backend, que hace la
        // misma verificacion del lado del servidor: esto no depende de que
        // el frontend "se porte bien").
        comensal: comensalConsentimiento
          ? {
              nombre: comensalNombre.trim() || undefined,
              telefono: comensalTelefono.trim() || undefined,
              consentimiento: true,
            }
          : null,
      }
      const pedido = await crearPedido(token, payload)
      setEnvio({ estado: 'exito', error: '', pedido })
      guardarUltimaRonda(token, payload.items)
      try {
        localStorage.setItem(`mezzalia.apodo.${token}`, apodo.trim())
      } catch {
        // sin almacenamiento: se vuelve a preguntar la próxima vez
      }
      setUltimaRonda(payload.items)
      setCarrito({})
      claveIdempotenciaRef.current = generarClaveIdempotencia()
    } catch (e) {
      setEnvio({ estado: 'error', error: e.message, pedido: null })
    }
  }

  function mostrarAviso(texto, tipo = 'ok') {
    clearTimeout(avisoTimerRef.current)
    setAviso({ texto, tipo })
    avisoTimerRef.current = setTimeout(() => setAviso(null), 3500)
  }

  useEffect(() => () => clearTimeout(avisoTimerRef.current), [])

  /**
   * "Otra ronda": carga la ultima ronda en el carrito y abre el resumen para
   * que el comensal confirme (no se envia sola: un toque accidental no debe
   * pedir 6 cervezas). Lo agotado se descarta y se avisa.
   */
  function onOtraRonda() {
    const { carrito: nuevo, descartados } = rondaComoCarrito(ultimaRonda, itemsPorId)
    if (Object.keys(nuevo).length === 0) {
      mostrarAviso('Lo de tu última ronda ya no está disponible.', 'error')
      return
    }
    setCarrito(nuevo)
    if (descartados.length > 0) {
      mostrarAviso(`Agotado, no lo incluimos: ${descartados.join(', ')}`, 'error')
    }
    setModalAbierto(true)
  }

  async function onLlamar(tipo) {
    if (llamados[tipo] === 'enviando') return
    setLlamados((prev) => ({ ...prev, [tipo]: 'enviando' }))
    try {
      await llamarDesdeMesa(token, tipo)
      setLlamados((prev) => ({ ...prev, [tipo]: 'avisado' }))
      mostrarAviso(tipo === 'CUENTA' ? 'Listo, ya te traen la cuenta.' : 'Listo, avisamos al mesero.')
      // Despues de un rato se puede volver a llamar (el backend no duplica
      // mientras el aviso anterior siga sin atender).
      setTimeout(() => setLlamados((prev) => ({ ...prev, [tipo]: 'idle' })), 60000)
    } catch (e) {
      setLlamados((prev) => ({ ...prev, [tipo]: 'idle' }))
      mostrarAviso(e.message, 'error')
    }
  }

  /** Desde la vista de ingredientes: suma una unidad de la linea con lo quitado. */
  function onAgregarDesdeIngredientes(sinIds) {
    const item = platoIngredientes
    const clave = claveDe(item.id, sinIds)
    setCarrito((prev) => ({ ...prev, [clave]: (prev[clave] || 0) + 1 }))
    setPlatoIngredientes(null)
    const sin = textoSin(nombresQuitados(item, sinIds))
    mostrarAviso(`Agregado: ${item.nombre}${sin ? ` ${sin}` : ''}`)
  }

  function onCerrarModal() {
    if (envio.estado === 'enviando') return
    setModalAbierto(false)
    if (envio.estado === 'exito') {
      // Deja el formulario limpio para la proxima ronda de pedido de esta
      // misma mesa -- una sesion de mesa dura horas (RF-08) y puede incluir
      // varios pedidos (entrada, luego postre, etc.).
      setEnvio({ estado: 'idle', error: '', pedido: null })
      setNotas('')
    }
  }

  if (estado === 'cargando') {
    return (
      <div className="min-h-dvh flex items-center justify-center px-6">
        <div className="flex flex-col items-center gap-3 text-carbon-900/50">
          <div className="w-8 h-8 rounded-full border-2 border-carbon-900/20 border-t-carbon-900/60 animate-spin" />
          <p className="text-sm">Cargando la carta…</p>
        </div>
      </div>
    )
  }

  if (estado === 'error') {
    return (
      <div className="min-h-dvh flex items-center justify-center px-6 text-center">
        <div className="max-w-sm">
          <h1 className="font-display font-semibold text-xl text-carbon-950 mb-2">No pudimos abrir la carta</h1>
          <p className="text-sm text-carbon-900/60">{error}</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-dvh" style={variablesMarca}>
      {/* Encabezado + fila de categorías, ambos sticky: en una carta larga
          el comensal necesita poder saltar de sección sin scrollear a mano
          desde el principio. pt-[env(safe-area-inset-top)] para no quedar
          debajo del notch/Dynamic Island de iPhone. */}
      <header className="px-5 pt-5 pb-4 max-w-3xl mx-auto" style={{ paddingTop: 'max(1.25rem, env(safe-area-inset-top))' }}>
        <div className="flex items-center gap-3">
          {carta.branding?.logoUrl && (
            <img src={carta.branding.logoUrl} alt="" width={44} height={44} className="w-11 h-11 rounded-xl object-cover shrink-0" />
          )}
          <h1 className="min-w-0 text-[26px] leading-tight font-semibold text-carbon-950 line-clamp-2">{carta.nombreRestaurante}</h1>
        </div>

        {/* Enfoque en bares: pedirle cosas al personal sin levantar la mano,
            y ver lo que ya pidió toda la mesa. */}
        <div className="mt-4 grid grid-cols-3 gap-2">
          <BotonLlamado tipo="MESERO" estado={llamados.MESERO} onLlamar={onLlamar} />
          <BotonLlamado tipo="CUENTA" estado={llamados.CUENTA} onLlamar={onLlamar} />
          <button type="button" onClick={() => setVerMesa(true)} className="boton-servicio">
            <IconoMesa className="w-5 h-5" />
            Pedido de la mesa
          </button>
        </div>
      </header>

      {/* Solo la fila de categorías es sticky: el encabezado se va al
          hacer scroll y deja la pantalla a la comida. */}
      <div
        className="sticky top-0 z-20 bg-white/85 backdrop-blur-xl border-b border-carbon-950/[0.06]"
        style={{ paddingTop: 'env(safe-area-inset-top)' }}
      >
        <nav ref={navRef} className="flex gap-1 overflow-x-auto px-3 max-w-3xl mx-auto [scrollbar-width:none]">
          {carta.categorias.map((cat) => {
            const activa = categoriaActiva === cat.id
            return (
              <button
                key={cat.id}
                onClick={() => irACategoria(cat.id)}
                aria-current={activa ? 'true' : undefined}
                className={`relative shrink-0 whitespace-nowrap px-3 py-3.5 text-sm font-medium transition-colors ${
                  activa ? 'text-carbon-950' : 'text-carbon-600 hover:text-carbon-950'
                }`}
              >
                {cat.nombre}
                <span
                  className={`absolute left-3 right-3 bottom-0 h-0.5 rounded-full transition-opacity ${activa ? 'opacity-100' : 'opacity-0'}`}
                  style={{ backgroundColor: 'var(--color-acento)' }}
                  aria-hidden="true"
                />
              </button>
            )
          })}
        </nav>
      </div>

      <main
        className={`px-5 pt-5 max-w-3xl mx-auto ${
          cantidadTotalCarrito > 0 || ultimaRonda.length > 0 ? 'pb-32' : 'pb-[max(1.5rem,env(safe-area-inset-bottom))]'
        }`}
      >
        {carta.categorias.length === 0 && (
          <p className="text-center text-sm text-carbon-900/40 py-16">
            Esta carta todavía no tiene platos publicados.
          </p>
        )}

        {/* lg:columns-2: carta tradicional impresa a dos columnas en
            pantallas anchas (tablet apoyada en la mesa, o alguien mirando
            desde el celular en horizontal) -- en el celular en vertical
            (el caso real más común, comensal con el teléfono escaneando el
            QR) columns-2 no aplica y queda una sola columna apilada, que es
            lo que de verdad hace falta ahí. break-inside-avoid evita que
            una categoría se corte a la mitad entre una columna y la
            siguiente. */}
        <div className="space-y-10 lg:space-y-0 lg:columns-2 lg:gap-x-12">
          {carta.categorias.map((cat) => (
            <section
              key={cat.id}
              data-categoria-id={cat.id}
              ref={(el) => {
                seccionesRef.current[cat.id] = el
              }}
              className="scroll-mt-16 break-inside-avoid mb-8 lg:mb-12"
            >
              <h2 className="text-xl font-semibold text-carbon-950 mb-1">{cat.nombre}</h2>

              {cat.items.length === 0 ? (
                <p className="text-sm text-carbon-900/40">Sin platos en esta categoría todavía.</p>
              ) : (
                <div className="divide-y divide-carbon-950/[0.06]">
                  {cat.items.map((item) => (
                    <ItemFila
                      key={item.id}
                      item={item}
                      expandido={expandidoId === item.id}
                      onAlternarExpandido={() =>
                        setExpandidoId((prev) => (prev === item.id ? null : item.id))
                      }
                      cantidadEnCarrito={carrito[item.id] || 0}
                      cantidadTotalDelPlato={cantidadDelPlato(carrito, item.id)}
                      onAgregar={() => agregarUnidad(item.id)}
                      onQuitar={() => quitarUnidad(item.id)}
                      onVerIngredientes={() => setPlatoIngredientes(item)}
                    />
                  ))}
                </div>
              )}
            </section>
          ))}
        </div>

        <p className="text-center text-xs text-carbon-600/70 pt-6">
          Carta digital por Mezzalia
        </p>
      </main>

      {/* Barra de carrito: solo aparece con items adentro. Fixed (no
          sticky) para que quede siempre visible sobre el contenido, con
          padding de safe-area para no quedar debajo de la barra de gestos
          de iPhone/Android. */}
      {cantidadTotalCarrito > 0 && (
        <div className="fixed inset-x-0 bottom-0 z-30 px-3 animate-fadeInUp" style={{ paddingBottom: 'max(0.75rem, env(safe-area-inset-bottom))' }}>
          <button
            type="button"
            onClick={() => setModalAbierto(true)}
            aria-label={`Ver pedido: ${cantidadTotalCarrito} ${cantidadTotalCarrito === 1 ? 'plato' : 'platos'}, total ${formatoPrecio.format(totalCarrito)}`}
            aria-haspopup="dialog"
            className="w-full max-w-md mx-auto flex items-center gap-3 rounded-2xl h-14 pl-2 pr-5 shadow-flotante active:scale-[0.99] transition-transform"
            style={{ backgroundColor: 'var(--color-acento)', color: 'var(--color-sobre-acento)' }}
          >
            <span className="w-10 h-10 rounded-xl bg-white/15 flex items-center justify-center text-sm font-semibold tabular-nums">
              {cantidadTotalCarrito}
            </span>
            <span className="flex-1 text-left text-[15px] font-semibold">Ver pedido</span>
            <span className="text-[15px] font-semibold tabular-nums">{formatoPrecio.format(totalCarrito)}</span>
          </button>
        </div>
      )}

      {/* "Otra ronda": solo cuando el carrito esta vacio (si no, manda la
          barra del carrito) y este celular ya pidio algo en esta mesa. */}
      {cantidadTotalCarrito === 0 && ultimaRonda.length > 0 && !modalAbierto && (
        <div className="fixed inset-x-0 bottom-0 z-30 px-3 animate-fadeInUp" style={{ paddingBottom: 'max(0.75rem, env(safe-area-inset-bottom))' }}>
          <button
            type="button"
            onClick={onOtraRonda}
            aria-label={`Pedir otra ronda: ${resumenRonda(ultimaRonda, itemsPorId)}`}
            className="w-full max-w-md mx-auto flex items-center gap-3 rounded-2xl h-14 pl-2 pr-4 shadow-flotante active:scale-[0.99] transition-transform"
            style={{ backgroundColor: 'var(--color-acento)', color: 'var(--color-sobre-acento)' }}
          >
            <span className="w-10 h-10 rounded-xl bg-white/15 flex items-center justify-center">
              <IconoRonda className="w-5 h-5" />
            </span>
            <span className="min-w-0 flex-1 text-left">
              <span className="block text-[15px] font-semibold leading-tight">Otra ronda</span>
              <span className="block text-xs opacity-75 truncate">{resumenRonda(ultimaRonda, itemsPorId)}</span>
            </span>
          </button>
        </div>
      )}

      {/* El centrado va en el contenedor y la animacion en el hijo: si
          estuvieran en el mismo elemento, el transform de fadeInUp pisaria
          el -translate-x de centrado y el aviso saldria corrido. */}
      {aviso && (
        <div
          className="fixed inset-x-0 z-50 flex justify-center px-4 pointer-events-none"
          style={{ top: 'calc(0.75rem + env(safe-area-inset-top))' }}
        >
          <div
            role="status"
            className={`max-w-md px-4 py-2.5 rounded-full text-sm font-medium text-center shadow-lg animate-fadeInUp ${
              aviso.tipo === 'error' ? 'bg-sin text-white' : 'bg-carbon-950 text-white'
            }`}
          >
            {aviso.texto}
          </div>
        </div>
      )}

      {verMesa && (
        <PedidoDeMesa
          token={token}
          miApodo={apodo.trim()}
          sinEnviar={cantidadTotalCarrito}
          onVerMiPedido={() => {
            setVerMesa(false)
            setModalAbierto(true)
          }}
          onCerrar={() => setVerMesa(false)}
        />
      )}

      {platoIngredientes && (
        <VistaIngredientes
          item={platoIngredientes}
          onCerrar={() => setPlatoIngredientes(null)}
          onAgregar={onAgregarDesdeIngredientes}
        />
      )}

      <ModalCheckout
        abierto={modalAbierto}
        onCerrar={onCerrarModal}
        itemsCarrito={itemsCarrito}
        total={totalCarrito}
        notas={notas}
        setNotas={setNotas}
        comensalNombre={comensalNombre}
        setComensalNombre={setComensalNombre}
        comensalTelefono={comensalTelefono}
        setComensalTelefono={setComensalTelefono}
        comensalConsentimiento={comensalConsentimiento}
        setComensalConsentimiento={setComensalConsentimiento}
        envio={envio}
        onEnviar={onEnviarPedido}
        apodo={apodo}
        setApodo={setApodo}
        onAgregarLinea={agregarUnidad}
        onQuitarLinea={quitarUnidad}
      />
    </div>
  )
}

const TEXTO_LLAMADO = {
  MESERO: { idle: 'Llamar al mesero', enviando: 'Avisando…', avisado: 'Mesero avisado', Icono: IconoCampana },
  CUENTA: { idle: 'Pedir la cuenta', enviando: 'Avisando…', avisado: 'Cuenta pedida', Icono: IconoRecibo },
}

function BotonLlamado({ tipo, estado, onLlamar }) {
  const t = TEXTO_LLAMADO[tipo]
  const avisado = estado === 'avisado'
  const Icono = avisado ? IconoCheck : t.Icono
  return (
    <button
      type="button"
      onClick={() => onLlamar(tipo)}
      disabled={estado === 'enviando'}
      className={`boton-servicio disabled:opacity-60 ${avisado ? '!bg-emerald-50 !text-emerald-700' : ''}`}
    >
      <Icono className="w-5 h-5" />
      {t[estado]}
    </button>
  )
}

/** Unidades de un plato en el carrito, sumando sus variantes ("sin cebolla"). */
function cantidadDelPlato(carrito, itemId) {
  return Object.entries(carrito).reduce(
    (acc, [clave, cantidad]) => (parsearClave(clave).itemId === itemId ? acc + cantidad : acc),
    0,
  )
}

/** Tinta o blanco, lo que más contraste dé sobre `hex` (luminancia WCAG). */
function textoSobre(hex) {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex || '')
  if (!m) return '#ffffff'
  const canal = (i) => {
    const c = parseInt(m[1].slice(i, i + 2), 16) / 255
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  }
  const l = 0.2126 * canal(0) + 0.7152 * canal(2) + 0.0722 * canal(4)
  return (l + 0.05) / 0.05 > 1.05 / (l + 0.05) ? '#18181b' : '#ffffff'
}

/**
 * 2026-09-11: fila de carta tradicional (nombre ... línea punteada ... precio,
 * descripción de una línea debajo) en vez de la tarjeta con foto de antes --
 * pedido explícito de Andrés, imitando una carta impresa de restaurante. La
 * foto ya NO se ve por defecto: aparece solo al expandir (RF de "imagen
 * interactiva por plato"), con FotoZoomable (pellizco para acercar).
 *
 * Todo el <button> de la fila es el disparador de expandir/colapsar -- el
 * stepper de agregar/quitar vive DENTRO del bloque expandido, nunca en la
 * fila colapsada, para que tocar la fila nunca agregue al carrito por
 * accidente (a diferencia de la tarjeta anterior, donde "+ Agregar" y el
 * resto de la tarjeta eran áreas de toque separadas pero visualmente
 * confundibles en una lista compacta).
 */
function ItemFila({ item, expandido, onAlternarExpandido, cantidadEnCarrito, cantidadTotalDelPlato, onAgregar, onQuitar, onVerIngredientes }) {
  const personalizable = item.ingredientes?.some((i) => i.removible)
  return (
    <div className={!item.disponible ? 'opacity-50' : ''}>
      <div className="flex gap-4 py-4">
        <button onClick={onAlternarExpandido} aria-expanded={expandido} className="flex-1 min-w-0 text-left">
          <span className="flex items-center gap-2">
            <span className="text-[15px] font-semibold text-carbon-950 break-words">{item.nombre}</span>
            {cantidadTotalDelPlato > 0 && (
              <span
                className="shrink-0 min-w-[20px] h-5 px-1.5 rounded-full text-[11px] font-semibold flex items-center justify-center tabular-nums"
                style={{ backgroundColor: 'var(--color-acento)', color: 'var(--color-sobre-acento)' }}
                aria-label={`${cantidadTotalDelPlato} en tu pedido`}
              >
                {cantidadTotalDelPlato}
              </span>
            )}
          </span>
          {item.descripcion && (
            <span className={`block text-[13px] leading-snug text-carbon-600 mt-1 ${expandido ? '' : 'line-clamp-2'}`}>
              {item.descripcion}
            </span>
          )}
          <span className="mt-2 flex items-center gap-2">
            {item.disponible ? (
              <span className="text-sm font-semibold text-carbon-950 tabular-nums">{formatoPrecio.format(item.precio)}</span>
            ) : (
              // RF-02: lo agotado se muestra distinto, nunca desaparece.
              <span className="text-[13px] font-medium text-carbon-600">Agotado</span>
            )}
            {personalizable && (
              <span className="inline-flex items-center gap-1 rounded-full bg-papel-200 px-2 py-0.5 text-[11px] font-medium text-carbon-700">
                <IconoCapas className="w-3 h-3" />
                Personalizable
              </span>
            )}
          </span>
        </button>

        {/* Abierto, la foto grande ya está abajo: la miniatura sobraría. */}
        {item.imagenUrl && !expandido && (
          <div className="relative shrink-0 self-start">
            <button onClick={onAlternarExpandido} tabIndex={-1} aria-hidden="true" className="block">
              <img
                src={item.imagenUrl}
                alt=""
                width={96}
                height={96}
                loading="lazy"
                decoding="async"
                className="w-24 h-24 rounded-2xl object-cover bg-papel-200"
              />
            </button>
            {item.disponible && (
              <button
                type="button"
                onClick={onAgregar}
                aria-label={`Agregar ${item.nombre}`}
                className="absolute bottom-1.5 right-1.5 w-9 h-9 rounded-full bg-white text-carbon-950 shadow-flotante flex items-center justify-center active:scale-95 transition-transform"
              >
                <IconoMas className="w-[18px] h-[18px]" />
              </button>
            )}
          </div>
        )}
      </div>

      {/* key={item.id}: remonta DetalleExpandido en cada apertura para que
          `vista` (foto vs 3D) y el zoom arranquen limpios. */}
      {expandido && (
        <DetalleExpandido
          key={item.id}
          item={item}
          cantidadEnCarrito={cantidadEnCarrito}
          onAgregar={onAgregar}
          onQuitar={onQuitar}
          onVerIngredientes={onVerIngredientes}
        />
      )}
    </div>
  )
}

/**
 * 2026-09-11: contenido del plato expandido -- foto (o modelo 3D/AR si el
 * item tiene ambas URLs) + stepper de cantidad. Separado de ItemFila para
 * que el estado `vista` (foto vs 3D) sea nuevo cada vez que se expande un
 * plato, en vez de "recordar" la última pestaña elegida en otro plato.
 *
 * Por qué el toggle exige las DOS urls (modeloGlbUrl y modeloUsdzUrl) y no
 * alcanza con una: Safari en iOS no soporta WebXR AR y solo abre AR Quick
 * Look vía un archivo .usdz; Android usa Scene Viewer vía .glb -- son dos
 * formatos sin conversión automática entre sí, así que un modelo sin
 * ambos no puede ofrecer AR real en los dos sistemas operativos (ver
 * MenuService.actualizarModelo3d, que ya rechaza guardar uno solo).
 */
function DetalleExpandido({ item, cantidadEnCarrito, onAgregar, onQuitar, onVerIngredientes }) {
  const [vista, setVista] = useState('foto') // 'foto' | 'modelo3d'

  // Al abrir el plato, precarga en segundo plano las fotos de sus capas: si
  // el cliente toca "Ver ingredientes", la animación arranca con todo listo
  // en vez de ir apareciendo capa por capa en datos móviles. Solo de ESTE
  // plato, para no gastar datos en platos que nadie abre.
  useEffect(() => {
    for (const ing of item.ingredientes || []) {
      if (ing.imagenUrl) {
        const img = new Image()
        img.decoding = 'async'
        img.src = ing.imagenUrl
      }
    }
  }, [item])
  const tieneModelo3d = Boolean(item.modeloGlbUrl && item.modeloUsdzUrl)

  function verModelo3d() {
    // <model-viewer> (y el archivo .glb/.usdz que carga) pesan varios MB --
    // mucho más que la foto -- así que se importa bajo demanda, solo si el
    // comensal de verdad toca "Ver en 3D". Cargarlo siempre que hay un
    // modelo disponible violaría el mismo NFR de "carta en <2s en wifi de
    // restaurante" que ya obligó al diseño de CartaPublicaDto en el backend.
    import('@google/model-viewer').then(() => setVista('modelo3d'))
  }

  return (
    <div className="pb-5 animate-fadeInUp">
      {tieneModelo3d && (
        <div className="flex gap-2 mb-2.5">
          <button
            onClick={() => setVista('foto')}
            className={`text-xs font-medium px-3 h-8 rounded-full ${vista === 'foto' ? 'bg-carbon-950 text-white' : 'bg-papel-200 text-carbon-700'}`}
          >
            Foto
          </button>
          <button
            onClick={verModelo3d}
            className={`text-xs font-medium px-3 h-8 rounded-full ${vista === 'modelo3d' ? 'bg-carbon-950 text-white' : 'bg-papel-200 text-carbon-700'}`}
          >
            Ver en 3D y AR
          </button>
        </div>
      )}

      {vista === 'modelo3d' && tieneModelo3d ? (
        // ar-modes explícito (aunque coincide con el default de
        // model-viewer) a propósito, para que quede documentado acá mismo
        // cuál es el camino real por sistema operativo: quick-look es
        // AR Quick Look de iOS (usa ios-src/.usdz), scene-viewer es
        // Android (usa src/.glb), webxr es el fallback in-browser en
        // Android cuando Scene Viewer no está disponible. En Safari
        // desktop/iOS sin soporte de AR, el visor cae solo al render
        // WebGL normal (sin botón de AR).
        <model-viewer
          src={item.modeloGlbUrl}
          ios-src={item.modeloUsdzUrl}
          poster={item.imagenUrl || undefined}
          alt={item.nombre}
          ar="true"
          ar-modes="webxr scene-viewer quick-look"
          camera-controls="true"
          auto-rotate="true"
          shadow-intensity="1"
          style={{
            width: '100%',
            aspectRatio: '4 / 3',
            borderRadius: '1rem',
            backgroundColor: 'rgba(13,12,11,0.04)',
          }}
        />
      ) : item.imagenUrl ? (
        <FotoZoomable src={item.imagenUrl} alt={item.nombre} apagada={!item.disponible} />
      ) : (
        <div className="w-full aspect-[4/3] rounded-2xl bg-papel-200 flex items-center justify-center text-carbon-900/20 text-xs">
          Sin foto
        </div>
      )}

      <div className="mt-3 flex gap-2">
        {item.ingredientes?.length > 0 && (
          <button
            type="button"
            onClick={onVerIngredientes}
            className="flex-1 flex items-center justify-center gap-2 rounded-xl h-12 text-sm font-semibold bg-papel-200 text-carbon-950 hover:bg-papel-300 transition-colors"
          >
            <IconoCapas className="w-4 h-4" />
            {item.ingredientes.some((i) => i.removible) ? 'Personalizar' : 'Ver ingredientes'}
          </button>
        )}

        {item.disponible &&
          (cantidadEnCarrito > 0 ? (
            <div className="flex items-center gap-1 rounded-xl bg-papel-200 p-1 h-12">
              <button
                onClick={onQuitar}
                aria-label={`Quitar una unidad de ${item.nombre}`}
                className="w-10 h-10 rounded-lg bg-white text-carbon-950 flex items-center justify-center"
              >
                <IconoMenos />
              </button>
              <span className="w-8 text-center text-[15px] font-semibold tabular-nums">{cantidadEnCarrito}</span>
              <button
                onClick={onAgregar}
                aria-label={`Agregar una unidad de ${item.nombre}`}
                className="w-10 h-10 rounded-lg flex items-center justify-center"
                style={{ backgroundColor: 'var(--color-acento)', color: 'var(--color-sobre-acento)' }}
              >
                <IconoMas />
              </button>
            </div>
          ) : (
            <button
              onClick={onAgregar}
              className={`${item.ingredientes?.length > 0 ? '' : 'flex-1'} flex items-center justify-center gap-1.5 rounded-xl h-12 px-5 text-sm font-semibold`}
              style={{ backgroundColor: 'var(--color-acento)', color: 'var(--color-sobre-acento)' }}
            >
              <IconoMas className="w-4 h-4" />
              Agregar
            </button>
          ))}
      </div>
    </div>
  )
}

/**
 * Foto grande con zoom táctil (pellizco con dos dedos) + arrastre cuando
 * está acercada, y doble-tap/doble-click como atajo para alternar entre 1x
 * y ~2.2x -- sin librería externa: son ~40 líneas de touch events crudos,
 * no vale la pena la dependencia (y el peso extra) para esto.
 *
 * Deliberadamente con estado propio (no vive en CartaPage): cada foto tiene
 * su propio zoom/paneo independiente, y al colapsar la fila el componente
 * se desmonta entero -- la próxima vez que se expanda este mismo plato
 * arranca limpio en 1x, no "recuerda" el acercamiento anterior.
 */
function FotoZoomable({ src, alt, apagada }) {
  const [transform, setTransform] = useState({ escala: 1, x: 0, y: 0 })
  const gestoRef = useRef({ modo: null, inicioDistancia: 0, inicioEscala: 1, ultimoX: 0, ultimoY: 0 })
  const ultimoTapRef = useRef(0)

  function distanciaEntreDedos(touches) {
    const [a, b] = touches
    return Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY)
  }

  function alternarZoom() {
    setTransform((prev) => (prev.escala > 1 ? { escala: 1, x: 0, y: 0 } : { escala: 2.2, x: 0, y: 0 }))
  }

  function onTouchStart(e) {
    if (e.touches.length === 2) {
      gestoRef.current = {
        modo: 'pellizco',
        inicioDistancia: distanciaEntreDedos(e.touches),
        inicioEscala: transform.escala,
      }
      return
    }
    if (e.touches.length === 1) {
      const ahora = Date.now()
      if (ahora - ultimoTapRef.current < 300) {
        alternarZoom()
        ultimoTapRef.current = 0
        return
      }
      ultimoTapRef.current = ahora
      if (transform.escala > 1) {
        gestoRef.current = { modo: 'arrastre', ultimoX: e.touches[0].clientX, ultimoY: e.touches[0].clientY }
      }
    }
  }

  function onTouchMove(e) {
    const gesto = gestoRef.current
    if (gesto.modo === 'pellizco' && e.touches.length === 2) {
      e.preventDefault()
      const factor = distanciaEntreDedos(e.touches) / gesto.inicioDistancia
      const nuevaEscala = Math.min(4, Math.max(1, gesto.inicioEscala * factor))
      setTransform((prev) => ({
        escala: nuevaEscala,
        x: nuevaEscala === 1 ? 0 : prev.x,
        y: nuevaEscala === 1 ? 0 : prev.y,
      }))
    } else if (gesto.modo === 'arrastre' && e.touches.length === 1) {
      e.preventDefault()
      const dx = e.touches[0].clientX - gesto.ultimoX
      const dy = e.touches[0].clientY - gesto.ultimoY
      gestoRef.current = { ...gesto, ultimoX: e.touches[0].clientX, ultimoY: e.touches[0].clientY }
      setTransform((prev) => ({ ...prev, x: prev.x + dx, y: prev.y + dy }))
    }
  }

  function onTouchEnd(e) {
    if (e.touches.length === 0) gestoRef.current = { modo: null }
  }

  return (
    <div
      className="relative w-full aspect-[4/3] overflow-hidden rounded-2xl bg-papel-200 touch-none select-none"
      onTouchStart={onTouchStart}
      onTouchMove={onTouchMove}
      onTouchEnd={onTouchEnd}
      onDoubleClick={alternarZoom}
    >
      <img
        src={src}
        alt={alt}
        draggable={false}
        className={`w-full h-full object-cover ${apagada ? 'grayscale-[35%]' : ''}`}
        style={{
          transform: `translate(${transform.x}px, ${transform.y}px) scale(${transform.escala})`,
          transition: gestoRef.current.modo ? 'none' : 'transform 150ms ease-out',
        }}
      />
      {transform.escala === 1 && (
        <span className="absolute bottom-2 right-2 text-[10px] bg-carbon-950/60 text-white px-2 py-1 rounded-full pointer-events-none">
          Pellizca o toca dos veces para acercar
        </span>
      )}
    </div>
  )
}

function ModalCheckout({
  abierto,
  onCerrar,
  itemsCarrito,
  total,
  notas,
  setNotas,
  comensalNombre,
  setComensalNombre,
  comensalTelefono,
  setComensalTelefono,
  comensalConsentimiento,
  setComensalConsentimiento,
  envio,
  onEnviar,
  onAgregarLinea,
  onQuitarLinea,
  apodo,
  setApodo,
}) {
  if (!abierto) return null

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center">
      <div
        className="absolute inset-0 bg-carbon-950/50"
        onClick={envio.estado === 'enviando' ? undefined : onCerrar}
        aria-hidden="true"
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Tu pedido"
        className="relative w-full sm:max-w-md sm:mx-4 bg-papel-100 rounded-t-3xl sm:rounded-2xl max-h-[90dvh] overflow-y-auto overscroll-contain animate-fadeInUp"
        style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
      >
        {envio.estado === 'exito' ? (
          <ConfirmacionPedido pedido={envio.pedido} onCerrar={onCerrar} />
        ) : (
          <div className="p-5 sm:p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-display font-semibold text-xl text-carbon-950">Tu pedido</h2>
              <button
                onClick={onCerrar}
                aria-label="Cerrar"
                className="w-9 h-9 -mr-1.5 flex items-center justify-center rounded-full text-carbon-900/40 hover:bg-carbon-950/5"
              >
                <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                  <path d="M6 6l12 12M18 6L6 18" />
                </svg>
              </button>
            </div>

            {itemsCarrito.length === 0 ? (
              <p className="text-sm text-carbon-900/50 py-6 text-center">Tu pedido está vacío.</p>
            ) : (
              <div className="space-y-2.5 mb-4">
                {itemsCarrito.map(({ clave, item, cantidad, sinTexto }) => (
                  <div key={clave} className="flex items-center justify-between gap-3 text-sm py-1">
                    <span className="min-w-0 text-carbon-950 font-medium">
                      <span className="tabular-nums text-carbon-600">{cantidad}×</span> {item.nombre}
                      <br />
                      {sinTexto && <span className="mt-1 inline-block rounded-full bg-sin-suave px-2 py-0.5 text-[11px] font-semibold text-sin first-letter:uppercase">{sinTexto}</span>}
                    </span>
                    <span className="flex items-center gap-2 shrink-0">
                      <span className="font-medium text-carbon-950 whitespace-nowrap">
                        {formatoPrecio.format(item.precio * cantidad)}
                      </span>
                      <button
                        type="button"
                        onClick={() => onQuitarLinea(clave)}
                        disabled={envio.estado === 'enviando'}
                        aria-label={`Quitar una unidad de ${item.nombre}${sinTexto ? ` ${sinTexto}` : ''}`}
                        className="w-8 h-8 rounded-lg bg-papel-200 text-carbon-950 flex items-center justify-center"
                      >
                        <IconoMenos className="w-4 h-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => onAgregarLinea(clave)}
                        disabled={envio.estado === 'enviando'}
                        aria-label={`Agregar una unidad de ${item.nombre}${sinTexto ? ` ${sinTexto}` : ''}`}
                        className="w-8 h-8 rounded-lg flex items-center justify-center"
                        style={{ backgroundColor: 'var(--color-acento)', color: 'var(--color-sobre-acento)' }}
                      >
                        <IconoMas className="w-4 h-4" />
                      </button>
                    </span>
                  </div>
                ))}
              </div>
            )}

            <div className="flex items-center justify-between text-base font-semibold border-t border-carbon-950/10 pt-3 mb-5">
              <span className="text-carbon-950">Total</span>
              <span className="tabular-nums">{formatoPrecio.format(total)}</span>
            </div>

            <label htmlFor="apodo-pedido" className="block text-[13px] font-medium text-carbon-950 mb-1.5">
              ¿A nombre de quién? (opcional)
            </label>
            <input
              id="apodo-pedido"
              value={apodo}
              onChange={(e) => setApodo(e.target.value)}
              name="apodo"
              placeholder="Tu nombre o apodo…"
              maxLength={40}
              autoComplete="given-name"
              className="w-full rounded-xl border border-carbon-950/10 bg-white px-3.5 h-11 text-base mb-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-carbon-950/40"
            />
            <p className="text-[11px] text-carbon-900/40 mb-4">Así el mesero sabe a quién darle cada plato.</p>

            <label htmlFor="notas-pedido" className="block text-[13px] font-medium text-carbon-950 mb-1.5">
              Notas para cocina (opcional)
            </label>
            <textarea
              id="notas-pedido"
              name="notas"
              autoComplete="off"
              value={notas}
              onChange={(e) => setNotas(e.target.value)}
              placeholder="Ej. sin cebolla, término de la carne, alergias…"
              rows={2}
              maxLength={500}
              className="w-full rounded-xl border border-carbon-950/10 bg-white px-3.5 py-2.5 text-base mb-5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-carbon-950/40"
            />

            <div className="rounded-xl border border-carbon-950/10 bg-white p-4 mb-5">
              <label className="flex items-start gap-2.5 mb-3">
                <input
                  type="checkbox"
                  checked={comensalConsentimiento}
                  onChange={(e) => setComensalConsentimiento(e.target.checked)}
                  className="mt-0.5 w-4 h-4 shrink-0 accent-current"
                  style={{ color: 'var(--color-acento)' }}
                />
                {/* Texto de consentimiento PLACEHOLDER: el aviso de
                    privacidad real (que datos se guardan, por cuanto
                    tiempo, con quien se comparten) esta pendiente de
                    revision legal -- documentacion-tecnica.md §15.2. Esto
                    cubre solo el mecanismo tecnico de RF-13 (nada se
                    guarda sin este check marcado), no el texto definitivo. */}
                <span className="text-xs text-carbon-900/60 leading-relaxed">
                  Acepto compartir mi nombre y/o teléfono para que el restaurante pueda
                  contactarme sobre este pedido si es necesario (opcional).
                </span>
              </label>

              {comensalConsentimiento && (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  <input
                    value={comensalNombre}
                    onChange={(e) => setComensalNombre(e.target.value)}
                    name="nombre"
                    autoComplete="name"
                    aria-label="Tu nombre"
                    placeholder="Tu nombre (opcional)…"
                    maxLength={150}
                    className="rounded-xl border border-carbon-950/10 bg-white px-3.5 h-11 text-base focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-carbon-950/40"
                  />
                  <input
                    value={comensalTelefono}
                    onChange={(e) => setComensalTelefono(e.target.value)}
                    name="telefono"
                    type="tel"
                    autoComplete="tel"
                    aria-label="Tu teléfono"
                    placeholder="Tu teléfono (opcional)…"
                    inputMode="tel"
                    maxLength={30}
                    className="rounded-xl border border-carbon-950/10 bg-white px-3.5 h-11 text-base focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-carbon-950/40"
                  />
                </div>
              )}
            </div>

            {envio.estado === 'error' && (
              <div role="alert" className="mb-4 text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
                {envio.error}
              </div>
            )}

            <button
              onClick={onEnviar}
              disabled={envio.estado === 'enviando' || itemsCarrito.length === 0}
              className="w-full rounded-xl h-14 px-5 flex items-center justify-between text-[15px] font-semibold transition-opacity duration-150 disabled:opacity-50"
              style={{ backgroundColor: 'var(--color-acento)', color: 'var(--color-sobre-acento)' }}
            >
              <span>{envio.estado === 'enviando' ? 'Enviando…' : 'Enviar pedido'}</span>
              <span className="tabular-nums">{formatoPrecio.format(total)}</span>
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

function ConfirmacionPedido({ pedido, onCerrar }) {
  return (
    <div className="p-6 sm:p-8 text-center">
      <div className="w-14 h-14 rounded-full bg-emerald-50 text-emerald-600 flex items-center justify-center mx-auto mb-4">
        <svg viewBox="0 0 24 24" className="w-7 h-7" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M20 6L9 17l-5-5" />
        </svg>
      </div>
      <h2 className="font-display font-semibold text-xl text-carbon-950 mb-1">Pedido enviado</h2>
      <p className="text-sm text-carbon-900/50 mb-6">
        {pedido?.id ? `Tu pedido #${pedido.id} quedó en el pedido de la mesa.` : 'Tu pedido quedó en el pedido de la mesa.'} El
        mesero pasará a confirmarlo antes de mandarlo a la cocina.
      </p>
      <button
        onClick={onCerrar}
        className="w-full rounded-xl h-12 text-sm font-semibold"
        style={{ backgroundColor: 'var(--color-acento)', color: 'var(--color-sobre-acento)' }}
      >
        Seguir viendo la carta
      </button>
    </div>
  )
}
