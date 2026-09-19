#!/usr/bin/env bash
# =============================================================================
# LABORATORIO 11 · validar-token.sh · "Resource server didáctico"
#
# Este script hace el papel de la API "api-conciliacion": recibe un access
# token y decide si lo acepta o lo rechaza, exactamente como haría un
# servidor de recursos al recibir la cabecera  Authorization: Bearer <token>.
#
# Comprueba, solo con bash, curl y jq (sin librerías externas):
#   1. Estructura JWT: tres partes separadas por puntos.
#   2. Cabecera: alg RS256 y typ JWT.
#   3. kid: la clave con la que se firmó existe en el JWKS del realm.
#   4. iss: el emisor es nuestro realm.
#   5. typ: es un token Bearer (no un ID Token ni un refresh token).
#   6. azp: el cliente autorizado es el que esperamos.
#   7. aud: la audiencia incluye api-conciliacion (el token es PARA esta API).
#   8. exp: no ha caducado.
#   9. scope: existe y se muestra.
#  10. realm_access.roles: contiene lector-conciliacion (autorización).
#
# ┌───────────────────────────────────────────────────────────────────────┐
# │  AVISO IMPORTANTE: ESTE SCRIPT NO VERIFICA LA FIRMA DEL TOKEN.        │
# │                                                                       │
# │  Una API real hace una comprobación más, y es la que da valor a      │
# │  todas las demás: verificar criptográficamente la firma RS256 con la  │
# │  clave pública del realm, la que publica el JWKS bajo el mismo `kid`  │
# │  de la cabecera. Sin esa verificación, cualquiera puede editar el     │
# │  payload (cambiar exp, aud o roles) y pasar todas las comprobaciones  │
# │  de arriba. El ejercicio 7.8 del README lo demuestra.                 │
# │                                                                       │
# │  Aquí solo se descarga el JWKS y se comprueba que el `kid` existe,    │
# │  con fines didácticos: hacer RSA-SHA256 a mano en bash no es          │
# │  razonable. En Java lo hace Spring Security (spring-boot-starter-     │
# │  oauth2-resource-server) con la URL del JWKS; en otros lenguajes,     │
# │  cualquier librería JWT/JOSE.                                         │
# └───────────────────────────────────────────────────────────────────────┘
#
# Uso:
#   scripts/validar-token.sh "$TOKEN"
#   echo "$TOKEN" | scripts/validar-token.sh
#
# Variables de entorno (opcionales, con sus valores por defecto):
#   KEYCLOAK_URL=http://localhost:8080   REALM=curso
#   AUDIENCIA_ESPERADA=api-conciliacion  CLIENTE_ESPERADO=servicio-conciliacion
#   ROL_ESPERADO=lector-conciliacion
#
# Código de salida: 0 si pasa todas las comprobaciones, 1 si falla alguna.
# =============================================================================

set -u

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
REALM="${REALM:-curso}"
AUDIENCIA_ESPERADA="${AUDIENCIA_ESPERADA:-api-conciliacion}"
CLIENTE_ESPERADO="${CLIENTE_ESPERADO:-servicio-conciliacion}"
ROL_ESPERADO="${ROL_ESPERADO:-lector-conciliacion}"

EMISOR_ESPERADO="$KEYCLOAK_URL/realms/$REALM"
URL_JWKS="$EMISOR_ESPERADO/protocol/openid-connect/certs"

# --- El token llega por argumento o por la entrada estándar -----------------
if [ $# -ge 1 ]; then
  TOKEN="$1"
else
  read -r TOKEN
fi
# Se admite pegar la cabecera completa: "Authorization: Bearer <token>"
TOKEN="${TOKEN##* }"

for herramienta in curl jq base64; do
  command -v "$herramienta" >/dev/null || { echo "Falta '$herramienta' (sudo apt install -y $herramienta)"; exit 2; }
done

# --- Utilidades --------------------------------------------------------------
FALLOS=0
ok()    { printf '  [OK]    %s\n' "$1"; }
fallo() { printf '  [FALLO] %s\n' "$1"; FALLOS=$((FALLOS + 1)); }

# Los segmentos de un JWT van en base64url (RFC 7515): alfabeto con '-' y '_'
# y sin relleno '='. base64 -d espera el alfabeto clásico y el relleno, así
# que se traduce el alfabeto y se añade el relleno que falte.
decodificar_base64url() {
  local s
  s=$(printf '%s' "$1" | tr '_-' '/+')
  while [ $(( ${#s} % 4 )) -ne 0 ]; do s="$s="; done
  printf '%s' "$s" | base64 -d 2>/dev/null
}

echo "Resource server didáctico · validando token para '$AUDIENCIA_ESPERADA'"
echo "(recuerda: NO se verifica la firma; ver el aviso en la cabecera del script)"
echo

# --- 1. Estructura -----------------------------------------------------------
NUM_PARTES=$(printf '%s' "$TOKEN" | awk -F. '{print NF}')
if [ "$NUM_PARTES" -eq 3 ]; then
  ok "estructura: 3 partes (cabecera.payload.firma)"
else
  fallo "estructura: se esperaban 3 partes separadas por '.', hay $NUM_PARTES"
  echo; echo "Token RECHAZADO."; exit 1
fi

CABECERA=$(decodificar_base64url "$(printf '%s' "$TOKEN" | cut -d. -f1)")
PAYLOAD=$(decodificar_base64url "$(printf '%s' "$TOKEN" | cut -d. -f2)")

if ! printf '%s' "$CABECERA" | jq -e . >/dev/null 2>&1 || ! printf '%s' "$PAYLOAD" | jq -e . >/dev/null 2>&1; then
  fallo "la cabecera o el payload no son JSON válido"
  echo; echo "Token RECHAZADO."; exit 1
fi

# --- 2. Cabecera -------------------------------------------------------------
ALG=$(printf '%s' "$CABECERA" | jq -r '.alg // empty')
TYP_CAB=$(printf '%s' "$CABECERA" | jq -r '.typ // empty')
KID=$(printf '%s' "$CABECERA" | jq -r '.kid // empty')
if [ "$ALG" = "RS256" ] && [ "$TYP_CAB" = "JWT" ]; then
  ok "cabecera: alg=$ALG typ=$TYP_CAB kid=${KID:0:12}…"
else
  fallo "cabecera: se esperaba alg=RS256 y typ=JWT, hay alg=$ALG typ=$TYP_CAB"
fi

# --- 3. kid presente en el JWKS del realm ------------------------------------
# Un API real cachea el JWKS y, con la clave (n, e) que tiene ese kid,
# VERIFICA LA FIRMA del token. Este script se queda en "la clave existe".
JWKS=$(curl -s --max-time 5 "$URL_JWKS")
if [ -z "$JWKS" ]; then
  fallo "jwks: no se pudo descargar $URL_JWKS"
elif printf '%s' "$JWKS" | jq -e --arg kid "$KID" '.keys[] | select(.kid == $kid and .use == "sig")' >/dev/null; then
  ok "jwks: el kid existe en $URL_JWKS (aquí un API real verificaría la firma)"
else
  fallo "jwks: el kid '$KID' no está entre las claves de firma del realm"
fi

# --- 4. iss ------------------------------------------------------------------
ISS=$(printf '%s' "$PAYLOAD" | jq -r '.iss // empty')
if [ "$ISS" = "$EMISOR_ESPERADO" ]; then
  ok "iss: $ISS"
else
  fallo "iss: se esperaba $EMISOR_ESPERADO, hay '$ISS'"
fi

# --- 5. typ ------------------------------------------------------------------
TYP=$(printf '%s' "$PAYLOAD" | jq -r '.typ // empty')
if [ "$TYP" = "Bearer" ]; then
  ok "typ: Bearer (es un access token, no un ID Token ni un refresh token)"
else
  fallo "typ: se esperaba Bearer, hay '$TYP'"
fi

# --- 6. azp ------------------------------------------------------------------
AZP=$(printf '%s' "$PAYLOAD" | jq -r '.azp // empty')
if [ "$AZP" = "$CLIENTE_ESPERADO" ]; then
  ok "azp: $AZP (cliente que obtuvo el token)"
else
  fallo "azp: se esperaba $CLIENTE_ESPERADO, hay '$AZP'"
fi

# --- 7. aud ------------------------------------------------------------------
# aud puede ser una cadena o un array; se normaliza a array.
AUDS=$(printf '%s' "$PAYLOAD" | jq -c '[.aud // empty] | flatten')
if printf '%s' "$AUDS" | jq -e --arg a "$AUDIENCIA_ESPERADA" 'index($a) != null' >/dev/null; then
  ok "aud: $AUDS incluye '$AUDIENCIA_ESPERADA' (el token es PARA esta API)"
else
  fallo "aud: $AUDS no incluye '$AUDIENCIA_ESPERADA'; este token no es para esta API"
fi

# --- 8. exp ------------------------------------------------------------------
AHORA=$(date +%s)
EXP=$(printf '%s' "$PAYLOAD" | jq -r '.exp // 0')
IAT=$(printf '%s' "$PAYLOAD" | jq -r '.iat // 0')
if [ "$EXP" -gt "$AHORA" ]; then
  ok "exp: caduca en $((EXP - AHORA)) s (vida total exp-iat = $((EXP - IAT)) s)"
else
  fallo "exp: caducó hace $((AHORA - EXP)) s ($(date -d "@$EXP" '+%H:%M:%S')); token caducado"
fi

# --- 9. scope ----------------------------------------------------------------
SCOPE=$(printf '%s' "$PAYLOAD" | jq -r '.scope // empty')
if [ -n "$SCOPE" ]; then
  ok "scope: \"$SCOPE\""
else
  fallo "scope: ausente"
fi

# --- 10. realm_access.roles --------------------------------------------------
ROLES=$(printf '%s' "$PAYLOAD" | jq -c '.realm_access.roles // []')
if printf '%s' "$ROLES" | jq -e --arg r "$ROL_ESPERADO" 'index($r) != null' >/dev/null; then
  ok "realm_access.roles: $ROLES incluye '$ROL_ESPERADO'"
else
  fallo "realm_access.roles: $ROLES no incluye '$ROL_ESPERADO'; sin permiso de lectura"
fi

# --- Veredicto ---------------------------------------------------------------
echo
if [ "$FALLOS" -eq 0 ]; then
  echo "Token ACEPTADO: 10 de 10 comprobaciones superadas (firma NO verificada)."
  exit 0
else
  echo "Token RECHAZADO: $FALLOS comprobación(es) fallida(s)."
  exit 1
fi
