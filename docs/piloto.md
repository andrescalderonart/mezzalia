# Guía del piloto con un bar

Cómo llevar Mezzalia a un gastrobar durante **4 a 6 semanas**, sin costo para el bar,
a cambio de medir si resuelve el problema. Sale del estudio de mercado
([docs/estudio-de-mercado-2026-11.md](estudio-de-mercado-2026-11.md)): lo que hay que probar no es si la app
funciona, sino si el bar la usa y si cambia algo que el dueño pueda ver.

Requisito previo: Mezzalia en internet con HTTPS y copias de seguridad
([docs/despliegue.md](despliegue.md)).

## 1. Antes de empezar: el acuerdo con el bar

Déjalo por escrito, aunque sea un correo o un mensaje:

- Duración (4 a 6 semanas) y que es **gratis** durante el piloto.
- Qué ganan ellos: la carta digital con su plato desarmado, la cuenta por mesa y el
  reporte de qué quitan sus clientes.
- Qué te dan ellos: el registro de platos devueltos antes y durante el piloto, 15
  minutos de entrevista al final y permiso para usar los datos agregados y anónimos.
- **La carta impresa sigue en las mesas** (la SIC la exige; el QR la complementa).
- **No reemplaza a ningún mesero ni a su POS**: el cajero sigue facturando en su
  sistema, con la ayuda de la "Cuenta" impresa de Mezzalia.
- Al final, la pregunta: ¿pagarían COP 79.900 a 99.900 al mes por seguir?

## 2. Medir la línea base (la semana antes)

Sin esto no hay "antes y después". Pídele al bar que anote durante una semana normal:

| Qué | Cómo |
|---|---|
| Platos devueltos o rehechos | Una hoja en la cocina: fecha, plato, motivo |
| Pedidos con cambios ("sin…") | Que el mesero marque en su libreta los pedidos con un "sin" |
| Mesas atendidas por noche | Del POS |

## 3. Dar de alta el bar (un día)

En el panel (`https://panel.tudominio.co`):

1. **Restaurantes** (superadministrador): crea el bar y su administrador inicial. Dale
   al dueño su usuario y contraseña en persona.
2. **Sucursales**: confirma la sucursal (la mayoría de bares tiene una sola).
3. **Usuarios**: crea un usuario por mesero, con rol Mesero, y asígnale la sucursal.
4. **Carta**: crea las categorías y los platos con precio. Empieza por **los 10 a 15
   platos que más se venden**, no por toda la carta.
5. **Ingredientes y fotos por capas**: solo para los platos donde la gente sí quita
   cosas (hamburguesas, sándwiches, ensaladas, bowls, tacos). Sigue
   [docs/guia-fotos-ingredientes.md](guia-fotos-ingredientes.md): una foto del plato
   desarmado con fondo liso y el panel separa las capas solo. Anota **cuánto tiempo y
   dinero** te tomó cada plato: es una de las métricas del piloto.
6. **Mesas**: crea una por mesa física, **Imprimir** el QR y pégalo en la mesa junto a
   la carta impresa.

## 4. Capacitación (30 minutos, antes del primer servicio)

Con los meseros, en el bar, cada uno con su celular:

1. Entrar al panel y elegir la sucursal. Abajo están Pedidos, Cocina, Carta y Mesas.
2. Un compañero pide desde el QR de una mesa con "sin cebolla". Ver cómo llega a
   **Por confirmar**, revisar con la mesa y tocar **Confirmar mesa**.
3. Ver el pedido en **Cocina**, con el "SIN" en rojo. Si la cocina tiene una tablet o
   pantalla, dejar abierta la vista de Cocina.
4. Un compañero toca **Pedir la cuenta**: en Pedidos aparece el aviso con
   **Ver cuenta**. Imprimirla o mostrarla al cajero para pasarla al POS.
5. Repetir que la propina es **voluntaria**: se pregunta antes de facturar.

## 5. Durante el piloto

- Pasa por el bar la primera noche y una noche cada semana.
- Revisa **Qué quitan** en el panel cada semana: es el reporte que el dueño no tenía.
- Anota todo lo que pidan o les moleste. Esas notas deciden qué se construye después.

## 6. Qué medir

| Pregunta | Dónde sale | Umbral que justificaría seguir |
|---|---|---|
| ¿Cuánto se personaliza? | Reporte **Qué quitan** (% de platos con un "sin") | 10–15 % o más |
| ¿Baja el error? | Hoja de platos devueltos: antes vs. durante | Caída clara |
| ¿Se piden más rondas? | Pedidos por mesa por noche, antes vs. durante | Más rondas por hora de mesa |
| ¿Lo visual decide? | Platos con capas vs. sin capas: ¿cuáles se piden más? | Diferencia medible |
| ¿El mesero lo adopta? | % de mesas confirmadas sin que tú estés | Uso sin los fundadores |
| ¿Es sostenible el alta? | Tu registro de horas y pesos por plato | ≤ 2–3 meses de suscripción |
| ¿Funciona sin redigitar? | Preguntar al cajero | La cuenta impresa le sirve |

Los umbrales son propuestas para discutir con los socios, no cifras del sector.

## 7. Cierre (última semana)

Entrevista de 15 minutos con el dueño y uno o dos meseros:

1. ¿Qué cambió en el servicio? ¿Algo empeoró?
2. ¿Qué le faltó? (facturación, pagos, inventario, otra cosa)
3. ¿Qué POS usan? ¿Les serviría que el pedido llegara solo a su POS?
4. **¿Pagarían COP 79.900 a 99.900 al mes por seguir usándolo?** Si no, ¿qué tendría
   que tener para que sí?

Con 2 o 3 bares respondiendo esto se decide el siguiente paso: integración con el POS
que más se repita, ajustes al producto o cambiar de rumbo.
