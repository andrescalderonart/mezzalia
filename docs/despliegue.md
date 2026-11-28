# Poner Mezzalia en internet

Guía para dejar Mezzalia funcionando en **un servidor propio (VPS)** con HTTPS,
base de datos MySQL y copias de seguridad diarias. Es la base para los pilotos
con bares. Tiempo estimado la primera vez: 1 a 2 horas.

Todo corre con Docker en una sola máquina:

| Servicio | Qué hace |
|---|---|
| `mysql` | Base de datos (MySQL 8.4). Solo visible dentro del servidor. |
| `backend` | La API de Mezzalia (Spring Boot). Corre como usuario sin privilegios. |
| `web` | Caddy: sirve la carta y el panel, saca y renueva solo el certificado HTTPS y pasa `/api` y `/uploads` al backend. |

Archivos: [`deploy/docker-compose.yml`](../deploy/docker-compose.yml),
[`deploy/Caddyfile`](../deploy/Caddyfile), [`deploy/.env.example`](../deploy/.env.example),
[`deploy/respaldo.sh`](../deploy/respaldo.sh), [`backend/Dockerfile`](../backend/Dockerfile),
[`deploy/Dockerfile.web`](../deploy/Dockerfile.web).

## 1. Lo que necesitas comprar o tener

- **Un VPS con Ubuntu 24.04**, mínimo **2 vCPU y 4 GB de RAM** (Java y MySQL juntos
  necesitan esa memoria). Cualquier proveedor sirve: Hetzner, DigitalOcean, Vultr,
  Linode o uno colombiano. Para pilotos alcanza el plan más pequeño con 4 GB.
- **Un dominio** (por ejemplo `mezzalia.co`) con acceso a su DNS.
- Un correo para los avisos de Let's Encrypt.

## 2. DNS

Crea dos registros **tipo A** apuntando a la IP pública del VPS:

| Nombre | Para |
|---|---|
| `carta.tudominio.co` | La carta que abre el comensal con el QR |
| `panel.tudominio.co` | El panel del restaurante (administrador y meseros) |

Espera a que respondan (`ping carta.tudominio.co` debe dar la IP del VPS) antes del
paso 5: Caddy necesita que el dominio ya apunte al servidor para sacar el certificado.

## 3. Preparar el servidor

Conéctate por SSH y ejecuta:

```bash
sudo apt update && sudo apt upgrade -y
```

```bash
curl -fsSL https://get.docker.com | sudo sh
```

```bash
sudo usermod -aG docker $USER
```

Cierra la sesión SSH y vuelve a entrar (para que el grupo `docker` aplique). Luego el
cortafuegos, dejando abiertos solo SSH y la web:

```bash
sudo ufw allow OpenSSH && sudo ufw allow 80 && sudo ufw allow 443 && sudo ufw enable
```

## 4. Bajar el código y configurar

```bash
git clone https://github.com/andrescalderonart/mezzalia.git && cd mezzalia/deploy
```

```bash
cp .env.example .env && chmod 600 .env
```

Genera un secreto distinto para cada una de estas variables con
`openssl rand -base64 32` y pégalo en `deploy/.env`:
`DB_PASSWORD`, `DB_ROOT_PASSWORD`, `JWT_SECRET`, `COMENSAL_ENCRYPTION_KEY`.

Completa también `DOMINIO_CARTA`, `DOMINIO_PANEL`, `ACME_EMAIL` y, solo para el primer
arranque, `MEZZALIA_PRIMER_SUPERADMIN_USUARIO` y `MEZZALIA_PRIMER_SUPERADMIN_PASSWORD`
(una frase larga que no uses en otro lado; la app rechaza contraseñas comunes).

> **Guarda una copia de `COMENSAL_ENCRYPTION_KEY` fuera del servidor** (por ejemplo
> en un gestor de contraseñas). Si se pierde, los teléfonos de comensales guardados
> quedan ilegibles para siempre.

## 5. Arrancar

```bash
docker compose up -d --build
```

La primera vez tarda varios minutos (compila el backend y las dos apps web). Para
ver cómo va:

```bash
docker compose ps
```

```bash
docker compose logs -f backend
```

Cuando el backend diga `Started MezzaliaApplication`, abre
`https://panel.tudominio.co` y entra con el superadministrador del `.env`.

## 6. Primer ingreso

1. En **Restaurantes**, crea el restaurante del piloto y su **administrador inicial**.
2. **Borra** las dos líneas `MEZZALIA_PRIMER_SUPERADMIN_*` de `deploy/.env` y aplica
   el cambio. La app ya ignora esas variables cuando existe un superadministrador,
   pero no deben quedar contraseñas en archivos:

   ```bash
   docker compose up -d
   ```

3. Entra como el administrador del restaurante y sigue la guía del piloto
   ([docs/piloto.md](piloto.md)).

## 7. Copias de seguridad (obligatorio antes del piloto)

`deploy/respaldo.sh` guarda la base de datos y las fotos en `~/respaldos-mezzalia`
y borra las de más de 14 días. Pruébalo a mano una vez:

```bash
./respaldo.sh
```

Prográmalo todos los días a las 4 a. m. con `crontab -e`, agregando esta línea
(ajusta la ruta si clonaste en otro lado):

```
0 4 * * * $HOME/mezzalia/deploy/respaldo.sh >> $HOME/respaldo-mezzalia.log 2>&1
```

Una copia que vive en el mismo servidor no protege si el servidor se pierde. Activa
también los respaldos automáticos del proveedor del VPS, o sincroniza
`~/respaldos-mezzalia` a otro lugar (por ejemplo con `rclone` a Google Drive).

### Restaurar (probarlo al menos una vez)

```bash
gunzip -c ~/respaldos-mezzalia/base_AAAA-MM-DD_HHMM.sql.gz | docker compose exec -T mysql sh -c 'exec mysql -u root -p"$MYSQL_ROOT_PASSWORD" carta_digital'
```

```bash
docker compose run --rm --no-deps --user 0 -v ~/respaldos-mezzalia:/respaldo --entrypoint sh backend -c "tar xzf /respaldo/fotos_AAAA-MM-DD_HHMM.tar.gz -C /data"
```

## 8. Operación diaria

| Para | Comando (dentro de `mezzalia/deploy`) |
|---|---|
| Actualizar a la última versión | `git pull && docker compose up -d --build` |
| Ver errores del backend | `docker compose logs --tail 200 backend` |
| Ver eventos de seguridad (logins fallidos, bloqueos, 403) | `docker compose logs backend \| grep SEGURIDAD` |
| Reiniciar todo | `docker compose restart` |
| Estado de los servicios | `docker compose ps` |

Antes de actualizar durante un piloto, haz un respaldo a mano (`./respaldo.sh`). Las
migraciones de la base de datos corren solas al arrancar el backend.

## 9. Lista de verificación antes de llevarlo a un bar

- [ ] `https://carta.tudominio.co` y `https://panel.tudominio.co` abren con candado.
- [ ] El QR impreso de una mesa abre la carta desde un celular con datos móviles (no wifi).
- [ ] Un pedido de prueba llega a "Por confirmar", se confirma y aparece en Cocina.
- [ ] La **Cuenta** de la mesa se imprime bien desde el celular o el computador del bar.
- [ ] `./respaldo.sh` corrió y el cron está programado.
- [ ] Probaste restaurar un respaldo.
- [ ] Borraste `MEZZALIA_PRIMER_SUPERADMIN_*` del `.env`.
- [ ] `COMENSAL_ENCRYPTION_KEY` está guardada fuera del servidor.

## Lo que esta guía no cubre todavía

- Las migraciones se prueban contra MySQL 8.4 en el CI (job `migraciones-mysql`),
  pero esta configuración de Docker no se ha ejecutado de punta a punta: la primera
  instalación real es también su primera prueba. Si algo falla, los logs
  (`docker compose logs`) dicen dónde.
- El token de sesión del panel sigue en `localStorage` (brecha anotada en
  [docs/calidad](calidad/README.md)); la cabecera CSP de Caddy reduce ese riesgo.
- Monitoreo y alertas: conecta un servicio gratuito de monitoreo de disponibilidad a
  `https://panel.tudominio.co/actuator/health` (responde `{"status":"UP"}` si la API
  y la base de datos funcionan). El espacio en disco hay que vigilarlo a mano por ahora
  (`df -h`).
