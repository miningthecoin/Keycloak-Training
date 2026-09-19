#!/usr/bin/env bash
# =============================================================================
# LABORATORIO 11 · api-insegura.sh · el ANTES: una "API" con API key eterna
#
# Simula el patrón inseguro que este laboratorio sustituye: un servicio que
# acepta cualquier petición que traiga la clave estática guardada en
# conciliacion.properties. Es el "resource server" del ejercicio ANTES.
#
# NO USAR COMO MODELO. Está aquí para evidenciar sus problemas (README, 4):
#   - no caduca: la clave vale hoy, mañana y dentro de cinco años;
#   - no tiene audiencia ni permisos: la misma clave abre todo;
#   - no identifica al llamante: en el log solo consta "clave válida";
#   - no se puede revocar sin cambiar la clave en todos los consumidores.
#
# Uso: scripts/api-insegura.sh <clave> [recurso]
# =============================================================================

CONFIG="$(dirname "$0")/conciliacion.properties"
LOG="/tmp/api-insegura.log"

CLAVE_ESPERADA=$(grep '^api.key=' "$CONFIG" | cut -d= -f2-)
CLAVE_RECIBIDA="${1:-}"
RECURSO="${2:-/conciliacion/informes}"

# Única "validación": comparar dos cadenas. Ni fecha, ni firma, ni destinatario.
if [ "$CLAVE_RECIBIDA" = "$CLAVE_ESPERADA" ]; then
  echo "$(date '+%F %T') 200 $RECURSO clave válida (¿de quién? no se sabe)" | tee -a "$LOG"
  exit 0
else
  echo "$(date '+%F %T') 401 $RECURSO clave incorrecta" | tee -a "$LOG"
  exit 1
fi
