#!/usr/bin/env bash
# Copia de seguridad diaria de Mezzalia: base de datos + fotos subidas.
# Se programa con cron (ver docs/despliegue.md). Guarda en ~/respaldos-mezzalia
# y borra las de mas de 14 dias. Para sacar copias fuera del servidor, sincroniza
# esa carpeta a otro lugar (rclone a Google Drive/S3, o el respaldo del VPS).
set -euo pipefail
cd "$(dirname "$0")"
DESTINO="${DESTINO_RESPALDOS:-$HOME/respaldos-mezzalia}"
FECHA="$(date +%Y-%m-%d_%H%M)"
mkdir -p "$DESTINO"

# Base de datos: volcado consistente sin bloquear tablas (InnoDB).
docker compose exec -T mysql sh -c \
  'exec mysqldump --single-transaction --routines --no-tablespaces -u root -p"$MYSQL_ROOT_PASSWORD" carta_digital' \
  | gzip > "$DESTINO/base_$FECHA.sql.gz"

# Fotos subidas por los restaurantes.
docker compose run --rm --no-deps --user 0 -v "$DESTINO:/respaldo" --entrypoint sh backend \
  -c "tar czf /respaldo/fotos_$FECHA.tar.gz -C /data uploads"

find "$DESTINO" -type f -mtime +14 -delete
echo "Respaldo listo: $DESTINO (base_$FECHA.sql.gz, fotos_$FECHA.tar.gz)"
