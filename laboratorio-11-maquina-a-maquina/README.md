# Laboratorio 11 · Máquina a máquina: Client Credentials y service account

**Tipo:** práctico · **Duración:** 60 min · **Funcionalidad de Keycloak:** service account, flujo *Client Credentials*, audiencia del access token y validación del token con el JWKS del realm

## 1. Objetivo

Hasta ahora siempre había una persona detrás del navegador. En este
laboratorio no la hay: un **proceso nocturno de conciliación** de la
Cooperativa Andina tiene que llamar a una API interna a las tres de la
madrugada, sin que nadie escriba una contraseña. Al terminar:

- Existe el cliente `servicio-conciliacion`, confidencial, **sin** Standard
  flow ni Direct access grants, con *Service accounts roles* activado.
  Representa al proceso; no tiene pantalla de login ni usuario humano.
- Ese cliente obtiene con `curl` un access token con sus propias
  credenciales (`grant_type=client_credentials`).
- El token lleva `aud: "api-conciliacion"`, el rol `lector-conciliacion` y
  caduca en 60 segundos.
- Un "servidor de recursos didáctico", `scripts/validar-token.sh`, hace lo
  que haría la API: recibe el token, descarga el JWKS del realm y comprueba
  sus claims. Rechaza tokens caducados, de otro cliente o para otra
  audiencia.
- Se comprueba con `curl` que un secreto erróneo no obtiene token, que
  `aplicacion-base` no puede usar este flujo y que **rotar el secreto**
  invalida el anterior al instante.
- **La aplicación Spring no cambia ni una línea.**

### Beneficios de implementarlo

- **Credenciales que caducan solas.** El proceso presenta un token de 60 s.
  Si se filtra un token, el atacante tiene un minuto; si se filtra una API
  key estática, tiene hasta que alguien se dé cuenta.
- **Identidad propia para cada sistema.** El proceso nocturno es
  `servicio-conciliacion`, con su rol `lector-conciliacion`, no "la clave
  de la API" que comparten cinco sistemas. Cada token dice quién lo pidió
  (`azp`) y qué puede hacer (`realm_access`).
- **Un token, un destinatario.** El claim `aud` fija para qué API sirve el
  token. Robado y reenviado a otro servicio, no vale.
- **Revocación sin romper a nadie más.** Rotar el secreto de
  `servicio-conciliacion` solo afecta a `servicio-conciliacion`.
- **La API no depende de Keycloak en cada petición.** Valida el token en
  local con la clave pública del realm. Keycloak puede estar caído y la API
  sigue aceptando tokens válidos.

### Un ejemplo de las amenazas que evita

En 2019 alguien escribió en `conciliacion.properties` la línea
`api.key=CA-CONCILIACION-2019-…` y la API de conciliación empezó a aceptar
esa clave. Con los años, la misma clave se copió al proceso de informes, a
un script de un contratista y a un cuaderno de Jupyter de analítica. En
2026 el portátil del contratista se vende de segunda mano con el disco sin
borrar.

Quien encuentre esa clave puede llamar a la API de conciliación **hoy**,
porque la clave no caduca. Puede llamar a **cualquier** operación, porque
la clave no distingue leer de exportar. En el log solo constará "clave
válida", así que nadie sabrá si fue el proceso nocturno o el desconocido. Y
cuando la Cooperativa quiera cortar el acceso, tendrá que cambiar la clave
en la API y, la misma noche, en los otros tres consumidores, o los rompe.
Como no sabe cuántas copias hay, probablemente no la cambie.

Tras este laboratorio, el proceso nocturno pide un token de un minuto con
un secreto que solo él conoce; el token dice `azp: servicio-conciliacion`,
`aud: api-conciliacion` y `roles: [lector-conciliacion]`; y si el secreto
se filtra, se pulsa *Regenerate* y solo hay que actualizar un sitio.

Fuentes oficiales:

- *Using a service account* (Server Administration Guide):
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_service_accounts>
- *Confidential client credentials*: Client ID and Secret, Signed JWT,
  Signed JWT with client secret, X509 Certificate (mTLS):
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_client-credentials>
- *Audience support*, con *Hardcoded audience* y *Audience resolve*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#audience-support>,
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_audience_hardcoded>
- *Token introspection audience validation*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#token-introspection-audience-validation>
- *Mitigating security threats*: *Limiting scope* y *Limit token audience*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#limiting-scope>,
  <https://www.keycloak.org/docs/latest/server_admin/index.html#limit-token-audience>
- Guía *OpenID Connect* de *Securing applications*: *Token endpoint*,
  *Client credentials*, *Certificate endpoint*, *Introspection endpoint* y
  *Validating access tokens*:
  <https://www.keycloak.org/securing-apps/oidc-layers#_token_endpoint>,
  <https://www.keycloak.org/securing-apps/oidc-layers#_client_credentials>,
  <https://www.keycloak.org/securing-apps/oidc-layers#_certificate_endpoint>,
  <https://www.keycloak.org/securing-apps/oidc-layers#_token_introspection_endpoint>,
  <https://www.keycloak.org/securing-apps/oidc-layers#_validating_access_tokens>

## 2. Conceptos

### 2.1 Client Credentials: el cliente actúa por sí mismo

La guía lo define así: *Client Credentials se usa cuando los clientes
(aplicaciones y servicios) quieren obtener acceso en su propio nombre, y no
en nombre de un usuario; por ejemplo, servicios en segundo plano que
aplican cambios al sistema en general y no para un usuario concreto*. Añade
que Keycloak permite autenticar a ese cliente con un secreto o con un par
de claves, y que el flujo no forma parte de OpenID Connect, sino de OAuth
2.0.

Es el único flujo del curso sin persona. Por eso no hay redirecciones, ni
pantalla de login, ni PKCE, ni ID Token: solo una petición POST al *token
endpoint* con las credenciales del cliente y un access token de vuelta.

```
Proceso nocturno (servicio-conciliacion)          Keycloak (8080)          API (api-conciliacion)
   │ POST /realms/curso/protocol/openid-connect/token │                         │
   │ grant_type=client_credentials                   │                         │
   │ client_id + client_secret ─────────────────────▶│                         │
   │◀──────────────── { access_token, expires_in=60 }│                         │
   │                                                  │                         │
   │ GET /conciliacion/informes                       │                         │
   │ Authorization: Bearer <access_token> ─────────────────────────────────────▶│
   │                                                  │   valida el token en    │
   │                                                  │   local con el JWKS     │
   │◀──────────────────────────────────────────────────────────────── 200 / 401 │
```

Fíjate en que Keycloak **solo habla con el cliente**. La API no aparece
hasta la segunda mitad, y cuando aparece, no habla con Keycloak.

### 2.2 La service account: un usuario interno con roles propios

Según *Using a service account*, cada cliente OIDC tiene una *service
account* incorporada. Al activar **Service accounts roles**, Keycloak crea
automáticamente un usuario interno llamado
`service-account-servicio-conciliacion`. Es un usuario del realm en todo
menos en una cosa: no tiene contraseña ni puede iniciar sesión en un
navegador. Existe para que el token de `client_credentials` tenga un `sub`
y unos roles.

Dos detalles prácticos:

- **No aparece en la lista de Users** de la Admin Console (ni en su
  buscador). Se llega a él desde la pestaña **Service accounts roles** del
  cliente, que enlaza su nombre. Por la API de administración sí se obtiene
  buscándolo por nombre exacto.
- **Los roles del token son una intersección**, como en el laboratorio 06:
  los *role scope mappings* del cliente (pestaña Scope del scope dedicado,
  con *Full scope allowed*) **y** los roles asignados a la service account.
  Si falta una de las dos mitades, el token no lleva el rol.

¿Por qué darle a la service account **su propio rol**, `lector-conciliacion`,
y no `gestor-clientes` ni los roles de `ana`? Porque el proceso nocturno no
es Ana. Si el proceso tuviera los roles de una persona, sus acciones se
confundirían en los logs con las de esa persona, heredaría permisos que no
necesita (mínimo privilegio) y, cuando esa persona cambiara de puesto o se
fuera, el proceso se rompería o, peor, seguiría con permisos de alguien que
ya no está. Una identidad de sistema tiene roles de sistema.

### 2.3 Quién recibe el token: el cliente, no la API

Este punto corrige un error frecuente. **Keycloak emite el token al
cliente**, al que se lo pidió con sus credenciales. Keycloak **no envía
nada a la API**, no la avisa, no le manda una copia. La API no "compara
dos tokens" ni consulta a Keycloak si ese token existe: **recibe un único
token**, en la cabecera `Authorization: Bearer`, del propio cliente, y **lo
valida por sí misma**.

¿Cómo puede validarlo sin preguntar? Porque el access token de Keycloak es
un JWT firmado (RS256) con la clave privada del realm, y la clave pública
correspondiente se publica en el *certificate endpoint*, que devuelve las
claves del realm en formato JSON Web Key (JWKS):

```
http://localhost:8080/realms/curso/protocol/openid-connect/certs
```

La guía es explícita en *Validating access tokens*: los tokens son JWT
firmados y por eso *se pueden validar en local con la clave pública del
realm*, bien fijándola en el código, bien *buscándola y cacheándola desde
el certificate endpoint por el Key ID (kid) incluido en el JWS*. La API
descarga el JWKS una vez, se lo guarda, y con él verifica cada token sin
tocar Keycloak. Si Keycloak se cae a las tres de la madrugada, la API sigue
aceptando tokens válidos hasta que caduquen.

Una vez comprobada la firma, la API lee los claims y decide:

| Claim | Pregunta que responde | Valor esperado en este lab |
|---|---|---|
| `iss` | ¿Lo emitió mi Keycloak? | `http://localhost:8080/realms/curso` |
| `aud` | ¿Es para mí? | contiene `api-conciliacion` |
| `exp` | ¿Sigue vigente? | posterior a ahora |
| `typ` | ¿Es un access token? | `Bearer` |
| `azp` | ¿Qué cliente lo pidió? | `servicio-conciliacion` |
| `realm_access.roles` | ¿Qué puede hacer? | contiene `lector-conciliacion` |
| `scope` | ¿Qué scopes concedió Keycloak? | presente |

Esta secuencia, firma primero y claims después, es la que haría Spring
Security con `spring-boot-starter-oauth2-resource-server` y la URL del
JWKS. En este laboratorio la reproduce `scripts/validar-token.sh` **salvo
la verificación de la firma**, que se explica en 2.4.

### 2.4 Validar en local o preguntar a Keycloak: la introspección

La alternativa a validar en local es el *introspection endpoint*:

```
http://localhost:8080/realms/curso/protocol/openid-connect/token/introspect
```

Según la guía, *sirve para recuperar el estado activo de un token* y *solo
pueden invocarlo clientes confidenciales*. La misma guía advierte del
coste: *hay que hacer una invocación de red al servidor de Keycloak, lo que
puede ser lento y puede sobrecargar el servidor si hay demasiadas
peticiones de validación al mismo tiempo*. Por eso la recomendación
oficial es validar en local, y dejar la introspección para dos casos:

- **Tokens opacos**: si el token no es un JWT legible, no queda otra que
  preguntar.
- **Revocación inmediata**: la validación local acepta un token firmado y
  no caducado aunque Keycloak lo haya revocado o el secreto del cliente
  haya rotado. Si se necesita que la revocación surta efecto antes de que
  el token caduque, hay que preguntar. La otra forma de acotar ese riesgo
  es lo que ya hace este realm: tokens de un minuto.

Hay una regla más, documentada en *Token introspection audience
validation*: **el endpoint de introspección comprueba que el cliente que
pregunta está en el `aud` del token**; si no, responde `active: false`. En
este laboratorio eso significa que quien puede introspeccionar el token es
**la API** (`api-conciliacion`), que es su audiencia, y **no** el cliente
`servicio-conciliacion` que lo obtuvo. Tiene lógica: la introspección es
una herramienta del servidor de recursos.

### 2.5 Audiencia: la API es un cliente sin flujos

En el laboratorio 07 limpiamos el `aud` del access token de
`aplicacion-base` y dejamos la audiencia explícita para cuando existiera
una API. Ahora existe. Según *Audience support*, hay que hacer dos cosas:
*limitar la audiencia del token* y *configurar los servicios para que
verifiquen la audiencia*.

Para lo primero, la guía describe el mapper **Audience** (*hardcoded
audience*): *un protocol mapper que añade el client ID del servicio
indicado como audiencia del token*. Y sobre ese servicio dice que *un
service client suele ser un cliente sin ningún flujo habilitado, al que
puede que nunca se le emitan tokens directamente; representa un OAuth 2
Resource Server*. Así que la API se registra en Keycloak como cliente
`api-conciliacion` con todos los flujos apagados: no obtiene tokens, solo
los recibe. Registrarla sirve para dos cosas: dar nombre a la audiencia y
poder llamar a la introspección (2.4).

El mapper va dentro de un client scope propio, `api-conciliacion-audiencia`,
asignado como **Default** únicamente a `servicio-conciliacion`. Así cada
token de ese cliente lleva `aud: "api-conciliacion"` sin pedirlo en `scope`,
y `aplicacion-base` sigue exactamente como quedó en el laboratorio 07. Esto
es *Limit token audience* de *Mitigating security threats* en la práctica.

### 2.6 El token caduca en 60 segundos: enlace con el laboratorio 05

El access token de `client_credentials` obedece al mismo **Access Token
Lifespan** del realm que fijamos en el laboratorio 05 (1 minuto). Entonces
no tenía efecto visible, porque la aplicación usa el ID Token en el login
y no vuelve a presentar el access token. Aquí sí se ve: `expires_in: 60`.

Además, la guía indica que por defecto *solo se devuelve el access token:
no hay refresh token ni se crea sesión de usuario en Keycloak*, y que *al
caducar el access token hace falta volver a autenticarse*, lo cual *no
supone sobrecarga porque no se crean sesiones*. El proceso nocturno,
sencillamente, pide otro token cuando le hace falta.

### 2.7 Formas de autenticar al cliente

*Confidential client credentials* enumera los métodos que ofrece el
desplegable **Client Authenticator** de la pestaña Credentials:

- **Client ID and Secret**: el de este laboratorio. Un secreto compartido,
  enviado en `Authorization: Basic` o como parámetros `client_id` y
  `client_secret` del POST. Se rota con el botón **Regenerate**.
- **Signed JWT**: el cliente firma una aserción con su clave privada y
  Keycloak la verifica con el certificado o con la URL JWKS del cliente.
  *Permite autenticar sin un secreto compartido.* Es el `private_key_jwt`
  de OpenID Connect.
- **Signed JWT with Client Secret**: la aserción se firma con el secreto.
- **X509 Certificate**: Keycloak valida el certificado que el cliente
  presenta en el *handshake* TLS (mTLS).

Este laboratorio se queda en el secreto por simplicidad. Signed JWT y mTLS
son las opciones a considerar en producción, y el laboratorio 13 vuelve
sobre ello.

### 2.8 Por qué la aplicación no cambia

`aplicacion-base` es un cliente **con** usuario y **con** navegador. Este
laboratorio trata de un cliente **sin** interfaz y **sin** usuario, que
vive en otro proceso y se demuestra con `curl`. Nada de lo que hace la
aplicación Spring interviene. Ver la sección 6.

## 3. Relación con OWASP, ASVS y PCI DSS

- **OWASP A07:2021 Fallos de identificación y autenticación.** Una API key
  estática y compartida es una credencial sin caducidad ni rotación, y
  cualquiera que la posea "es" el servicio. Client Credentials sustituye la
  clave eterna por un secreto por cliente y tokens de vida corta.
- **OWASP A02:2021 Fallos criptográficos.** Una clave en un archivo de
  configuración, en el repositorio o en un portátil, es un secreto
  expuesto. El secreto del cliente no va al repositorio (regla del curso
  desde el laboratorio 04) y el token que sí viaja está firmado y caduca.
- **OWASP ASVS V2.10 (autenticación de servicios).** Las credenciales de
  servicio no deben ser secretos por defecto ni estar embebidas en el
  código, y deben poder gestionarse y rotarse. Es exactamente lo que se
  demuestra en 7.7.
- **PCI DSS 8.6 (cuentas de sistema y aplicación).** El requisito trata
  las cuentas que no usa una persona. **8.6.2** prohíbe que las contraseñas
  de esas cuentas estén embebidas en código, scripts o archivos de
  configuración. **8.6.3** exige protegerlas contra el mal uso y cambiarlas
  periódicamente. El ANTES incumple ambas; el DESPUÉS las cubre con un
  secreto fuera del repositorio, un token de un minuto y rotación con un
  botón.

## 4. Ejercicio ANTES: la API key eterna

**Propósito.** Ver, sin crear nada en Keycloak, cómo se ha resuelto
tradicionalmente el acceso máquina a máquina y por qué falla. El
laboratorio incluye una "API" simulada, `scripts/api-insegura.sh`, que
acepta la clave estática de `scripts/conciliacion.properties`. Mira ambos
archivos antes de ejecutarlos.

```bash
cd ~/keycloak-curso/laboratorio-11-maquina-a-maquina
cat scripts/conciliacion.properties
```

```properties
api.url=http://localhost:8082/conciliacion
api.key=CA-CONCILIACION-2019-7f3a9c1e5b2d4a8f
```

1. Llama a la API con la clave, dos veces, a dos recursos distintos, y una
   tercera con una clave inventada:

   ```bash
   scripts/api-insegura.sh CA-CONCILIACION-2019-7f3a9c1e5b2d4a8f /conciliacion/informes
   scripts/api-insegura.sh CA-CONCILIACION-2019-7f3a9c1e5b2d4a8f /conciliacion/exportar-todo
   scripts/api-insegura.sh clave-robada-o-inventada /conciliacion/informes
   ```

   Salida real:

   ```
   2026-09-16 21:52:32 200 /conciliacion/informes clave válida (¿de quién? no se sabe)
   2026-09-16 21:52:32 200 /conciliacion/exportar-todo clave válida (¿de quién? no se sabe)
   2026-09-16 21:52:32 401 /conciliacion/informes clave incorrecta
   ```

2. Mira la única "validación" que hace la API:

   ```bash
   grep -n 'CLAVE_RECIBIDA" = ' scripts/api-insegura.sh
   ```

   Es una comparación de cadenas. Ni fecha, ni firma, ni destinatario.

3. Simula la revocación tras una filtración: cambia la clave en la API y
   vuelve a llamar con la antigua.

   ```bash
   sed -i 's/^api.key=.*/api.key=CA-CONCILIACION-2026-nueva/' scripts/conciliacion.properties
   scripts/api-insegura.sh CA-CONCILIACION-2019-7f3a9c1e5b2d4a8f /conciliacion/informes
   git checkout -- scripts/conciliacion.properties     # restaurar
   ```

   Salida real:

   ```
   2026-09-16 22:04:19 401 /conciliacion/informes clave incorrecta
   ```

**Resultado esperado y lo que evidencia.**

| Problema | Evidencia en el ANTES | Cómo lo resuelve el DESPUÉS |
|---|---|---|
| **No caduca.** La clave lleva "2019" en el nombre y en 2026 sigue abriendo. No hay ninguna fecha que comprobar. | Paso 1, líneas `200`; paso 2. | Token con `exp`, 60 s de vida (7.4). |
| **No se acota por audiencia ni por permiso.** La misma cadena abrió `/informes` y `/exportar-todo`. | Paso 1, dos `200` con la misma clave. | `aud: api-conciliacion` y `roles: [lector-conciliacion]` en el token (7.2, 7.3). |
| **No identifica qué servicio la usó.** El log dice "clave válida (¿de quién? no se sabe)". Todos los consumidores son indistinguibles. | Paso 1, texto del log. | `azp`, `client_id` y `sub` de la service account en cada token (7.2). |
| **Si se filtra, no se puede revocar sin romper a todos.** Cambiar la clave dejó fuera al proceso legítimo (paso 3) y, en la vida real, a cada copia que exista. | Paso 3, `401` para el consumidor legítimo. | *Regenerate* del secreto de un solo cliente (7.7). |

Además, la clave está en un archivo de configuración: es justo lo que PCI
DSS 8.6.2 prohíbe. El archivo está en el repositorio a propósito, como
ejemplo de lo que no se hace; el secreto real del DESPUÉS no lo estará.

## 5. Configuración de Keycloak, paso a paso

Con la Admin Console en el realm **`curso`**:

### 5.1 El rol del proceso

1. Menú **Realm roles** → **Create role**.
2. *Role name*: `lector-conciliacion`. *Description*: `Puede leer los
   datos de conciliación (proceso nocturno, sin usuario)`. **Save**.

No se asigna a `ana`, a `luis` ni al grupo `operaciones`. Es un rol de
sistema.

### 5.2 El cliente del proceso nocturno

1. Menú **Clients** → **Create client**.
2. *Client type*: OpenID Connect. *Client ID*: `servicio-conciliacion`.
   *Name*: `Servicio de conciliación nocturna`. **Next**.
3. *Capability config*:
   - **Client authentication**: **On** (cliente confidencial).
   - *Authentication flow*: **desmarca Standard flow**, deja **Direct
     access grants** desmarcado y **marca Service accounts roles**. Nada
     más.
   - **Next**.
4. *Login settings*: déjalo todo vacío. No hay redirecciones porque no hay
   navegador. **Save**.

Al guardar, Keycloak ha creado el usuario interno
`service-account-servicio-conciliacion` (2.2).

### 5.3 El secreto

1. Cliente `servicio-conciliacion` → pestaña **Credentials**.
2. *Client Authenticator*: **Client Id and Secret**. Copia el **Client
   Secret**.

Guárdalo en una variable de tu terminal de WSL, nunca en un archivo del
repositorio:

```bash
export SECRETO_CONCILIACION='pega-aquí-el-secreto'
```

### 5.4 Solo el rol que necesita (Full scope allowed)

Como en el laboratorio 07:

1. Cliente `servicio-conciliacion` → pestaña **Client scopes** → enlace
   `servicio-conciliacion-dedicated`.
2. Pestaña **Scope** → desactiva **Full scope allowed**.
3. **Assign role** → *Filter by realm roles* → marca `lector-conciliacion`
   → **Assign**.

### 5.5 El rol de la service account

1. Cliente `servicio-conciliacion` → pestaña **Service accounts roles**.
2. **Assign role** → *Filter by realm roles* → marca `lector-conciliacion`
   → **Assign**.

En esa misma pestaña verás el enlace al usuario
`service-account-servicio-conciliacion`. Púlsalo: es un usuario normal del
realm, sin credenciales. Si vuelves a **Users**, comprobarás que la lista
no lo muestra.

Recuerda que hacen falta 5.4 **y** 5.5: el token lleva la intersección.

### 5.6 La API como cliente sin flujos

1. **Clients** → **Create client**. *Client ID*: `api-conciliacion`.
   *Name*: `API de conciliación`. **Next**.
2. **Client authentication**: **On**. En *Authentication flow* **desmarca
   Standard flow** y deja todo lo demás desmarcado, incluido *Service
   accounts roles*. **Next** → **Save**.
3. Pestaña **Credentials**: copia su secreto a otra variable. Solo se usa
   en 7.8, para la introspección:

   ```bash
   export SECRETO_API='pega-aquí-el-secreto-de-api-conciliacion'
   ```

Este cliente no puede obtener tokens de ninguna forma (se comprueba en
7.5). Solo existe para ser audiencia y para poder preguntar por un token.

### 5.7 El client scope de audiencia

1. Menú **Client scopes** → **Create client scope**.
2. *Name*: `api-conciliacion-audiencia`. *Type*: **None** (no debe ser
   default del realm). *Protocol*: OpenID Connect. Deja **Include in token
   scope** activado. **Save**.
3. Pestaña **Mappers** → **Configure a new mapper** → **Audience**.
4. *Name*: `audiencia api-conciliacion`. *Included Client Audience*:
   `api-conciliacion`. **Add to access token**: On. **Add to ID token**:
   Off. **Add to token introspection**: On. **Save**.

### 5.8 Asignar el scope solo a `servicio-conciliacion`

1. **Clients** → `servicio-conciliacion` → pestaña **Client scopes** →
   **Add client scope**.
2. Marca `api-conciliacion-audiencia` → **Add** → **Default**.

No lo añadas a `aplicacion-base`: su configuración del laboratorio 07 no
se toca.

## 6. Cambios en la aplicación

**Ninguno.** El laboratorio 11 no toca `aplicacion_base`. La carpeta
`aplicacion_base_lab-11/` es una copia idéntica de `aplicacion_base_lab-10/`
(solo cambia el párrafo "Estado" de su `README.md`).

El motivo no es pereza: el laboratorio trata de un **cliente sin interfaz y
sin usuario**. `servicio-conciliacion` es un proceso que corre solo; su
"aplicación" es la línea de `curl` que pide el token y la que llama a la
API. La aplicación Spring, con su navegador, su login y su `OidcUser`, no
interviene en ningún punto de este flujo. Y la API, que sería la otra
pieza de código, se sustituye a propósito por `scripts/validar-token.sh`
para que se vea claim a claim qué debe comprobar un servidor de recursos.

## 7. Ejercicio DESPUÉS: tokens de un minuto, con dueño y con destinatario

Todo se hace desde una terminal de WSL, en la carpeta del laboratorio, con
la variable `SECRETO_CONCILIACION` de 5.3 exportada. En las salidas que
siguen, los secretos aparecen ofuscados y los tokens truncados; el resto es
literal.

```bash
cd ~/keycloak-curso/laboratorio-11-maquina-a-maquina
chmod +x scripts/*.sh
TOKEN_URL=http://localhost:8080/realms/curso/protocol/openid-connect/token
```

### 7.1 Obtener el token

```bash
curl -s -X POST "$TOKEN_URL" \
  -d grant_type=client_credentials \
  -d client_id=servicio-conciliacion \
  -d "client_secret=$SECRETO_CONCILIACION" | jq .
```

Salida real:

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwi…",
  "expires_in": 60,
  "refresh_expires_in": 0,
  "token_type": "Bearer",
  "not-before-policy": 0,
  "scope": "email api-conciliacion-audiencia profile"
}
```

**Resultado esperado.** HTTP 200, un `access_token`, `expires_in: 60` (el
Access Token Lifespan del laboratorio 05) y **ningún** `refresh_token`
(`refresh_expires_in: 0`): tal como dice la guía, por defecto no hay
refresh token ni sesión. Guarda el token en una variable para lo que
sigue:

```bash
TOKEN=$(curl -s -X POST "$TOKEN_URL" -d grant_type=client_credentials \
  -d client_id=servicio-conciliacion -d "client_secret=$SECRETO_CONCILIACION" | jq -r .access_token)
```

Alternativa equivalente, con las credenciales en `Authorization: Basic`,
que es la forma que muestra la guía:

```bash
curl -s -X POST "$TOKEN_URL" -u "servicio-conciliacion:$SECRETO_CONCILIACION" \
  -d grant_type=client_credentials | jq '{token_type, expires_in}'
```

```json
{"token_type":"Bearer","expires_in":60}
```

### 7.2 Leer el token: cabecera y payload

Un JWT son tres partes en base64url separadas por puntos. `base64 -d`
espera el alfabeto clásico y el relleno `=`, así que se traduce antes de
decodificar. Define una función y úsala:

```bash
decodificar() { local s; s=$(printf '%s' "$1" | tr '_-' '/+'); while [ $(( ${#s} % 4 )) -ne 0 ]; do s="$s="; done; printf '%s' "$s" | base64 -d 2>/dev/null; }

decodificar "$(echo "$TOKEN" | cut -d. -f1)" | jq .     # cabecera
decodificar "$(echo "$TOKEN" | cut -d. -f2)" | jq .     # payload
```

Cabecera (salida real):

```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "UaYOafke6itdHJ_Y2I-GFAf7K4MO0i2onQBrl84EkJE"
}
```

Payload (salida real):

```json
{
  "exp": 1789610236,
  "iat": 1789610176,
  "jti": "trrtcc:ef0bd9e7-ba90-4d36-6851-a3247414f0e6",
  "iss": "http://localhost:8080/realms/curso",
  "aud": "api-conciliacion",
  "sub": "4da1da6a-9a04-4582-89d4-bc87671e97a4",
  "typ": "Bearer",
  "azp": "servicio-conciliacion",
  "acr": "1",
  "realm_access": {
    "roles": [
      "lector-conciliacion"
    ]
  },
  "scope": "email api-conciliacion-audiencia profile",
  "email_verified": false,
  "clientHost": "172.18.0.1",
  "preferred_username": "service-account-servicio-conciliacion",
  "clientAddress": "172.18.0.1",
  "client_id": "servicio-conciliacion"
}
```

**Resultado esperado.** Fíjate en cada claim:

- `typ: "Bearer"`: es un access token, no un ID Token.
- `azp: "servicio-conciliacion"` y `client_id`: el cliente que lo pidió.
  Ya sabemos **quién** llama, cosa que la API key no decía.
- `aud: "api-conciliacion"`: el token es **para** la API, gracias al mapper
  de 5.7. Nada de `account` (laboratorio 07).
- `sub`: el id de la service account. Compruébalo en la pestaña **Service
  accounts roles** del cliente → enlace al usuario: la URL termina en ese
  mismo id.
- `exp - iat`: 60 segundos. `decodificar "$(echo "$TOKEN" | cut -d. -f2)" | jq '.exp - .iat'`
  devuelve `60`.
- **No hay persona**: el `preferred_username` es
  `service-account-servicio-conciliacion`, no `ana` ni `luis`; no hay
  `name`, `given_name` ni `email`, porque la service account no los tiene.
  `clientHost` y `clientAddress` (del scope `service_account`) dicen desde
  qué IP se pidió el token.
- `realm_access.roles`: **solo** `lector-conciliacion`. Ni
  `default-roles-curso`, ni `uma_authorization`, ni `offline_access`: es
  el efecto de 5.4 (Full scope allowed OFF), igual que en el lab 07.
- `scope`: los client scopes concedidos; `api-conciliacion-audiencia`
  aparece porque tiene *Include in token scope* activado. No hay `openid`:
  no es OpenID Connect, es OAuth 2.0 puro.

### 7.3 El servidor de recursos didáctico acepta el token

Lee primero la cabecera de `scripts/validar-token.sh`: explica qué
comprueba y, sobre todo, qué **no** comprueba. Después:

```bash
scripts/validar-token.sh "$TOKEN"; echo "código de salida: $?"
```

Salida real:

```
Resource server didáctico · validando token para 'api-conciliacion'
(recuerda: NO se verifica la firma; ver el aviso en la cabecera del script)

  [OK]    estructura: 3 partes (cabecera.payload.firma)
  [OK]    cabecera: alg=RS256 typ=JWT kid=UaYOafke6itd…
  [OK]    jwks: el kid existe en http://localhost:8080/realms/curso/protocol/openid-connect/certs (aquí un API real verificaría la firma)
  [OK]    iss: http://localhost:8080/realms/curso
  [OK]    typ: Bearer (es un access token, no un ID Token ni un refresh token)
  [OK]    azp: servicio-conciliacion (cliente que obtuvo el token)
  [OK]    aud: ["api-conciliacion"] incluye 'api-conciliacion' (el token es PARA esta API)
  [OK]    exp: caduca en 59 s (vida total exp-iat = 60 s)
  [OK]    scope: "email api-conciliacion-audiencia profile"
  [OK]    realm_access.roles: ["lector-conciliacion"] incluye 'lector-conciliacion'

Token ACEPTADO: 10 de 10 comprobaciones superadas (firma NO verificada).
código de salida: 0
```

**Resultado esperado.** Diez comprobaciones en verde y salida 0. El
script ha descargado el JWKS del realm y ha encontrado el `kid` de la
cabecera entre las claves de firma. **Ahí es donde una API real haría una
cosa más: verificar la firma RS256 con esa clave pública.** El script no
lo hace porque hacer RSA en bash no es razonable; lo demás es idéntico a lo
que hace un resource server. El ejercicio 7.8 muestra qué pasa por no
verificarla.

Si quieres ver el JWKS que descarga:

```bash
curl -s http://localhost:8080/realms/curso/protocol/openid-connect/certs | jq '.keys[] | {kid, kty, alg, use}'
```

```json
{"kid":"UaYOafke6itdHJ_Y2I-GFAf7K4MO0i2onQBrl84EkJE","kty":"RSA","alg":"RS256","use":"sig"}
{"kid":"Kf7_BuKUQ1ynTG3A4yS5qABXaHTAiPsjpmjKyXTUHxs","kty":"RSA","alg":"RSA-OAEP","use":"enc"}
```

### 7.4 Prueba negativa 1: el token caduca

Espera a que pase el minuto y vuelve a validar el mismo token:

```bash
sleep 61
scripts/validar-token.sh "$TOKEN"; echo "código de salida: $?"
```

Salida real (solo las líneas que cambian):

```
  [FALLO] exp: caducó hace 2 s (21:53:32); token caducado
  ...
Token RECHAZADO: 1 comprobación(es) fallida(s).
código de salida: 1
```

**Resultado esperado.** El token que hace un minuto era válido ya no lo
es, y la API no ha tenido que hacer nada: la fecha va dentro del token. Es
la diferencia frontal con la API key de 2019 del ANTES, que hoy sigue
abriendo. Y es el Access Token Lifespan del laboratorio 05 en acción: si
lo subieras a una hora, el proceso ganaría comodidad y un token robado
ganaría una hora.

### 7.5 Prueba negativa 2: `aplicacion-base` no puede usar este flujo

`aplicacion-base` tiene *Service accounts roles* desactivado desde el
laboratorio 04. Intenta el mismo grant con su secreto
(`KEYCLOAK_CLIENT_SECRET`, el de la pestaña Credentials):

```bash
curl -s -w '\nHTTP %{http_code}\n' -X POST "$TOKEN_URL" \
  -d grant_type=client_credentials \
  -d client_id=aplicacion-base \
  -d "client_secret=$KEYCLOAK_CLIENT_SECRET"
```

Salida real:

```
{"error":"unauthorized_client","error_description":"Client not enabled to retrieve service account"}
HTTP 401
```

**Resultado esperado.** Keycloak no emite el token: el cliente existe y el
secreto es correcto, pero el flujo no está habilitado para él. Cada cliente
tiene solo los flujos que necesita: `aplicacion-base` el Standard flow,
`servicio-conciliacion` el Client Credentials. Lo mismo ocurre con
`api-conciliacion`, que no tiene ninguno:

```
{"error":"unauthorized_client","error_description":"Client not enabled to retrieve service account"}
HTTP 401
```

### 7.6 Prueba negativa 3: secreto incorrecto

```bash
curl -s -w '\nHTTP %{http_code}\n' -X POST "$TOKEN_URL" \
  -d grant_type=client_credentials \
  -d client_id=servicio-conciliacion \
  -d client_secret=secreto-equivocado
```

Salida real:

```
{"error":"unauthorized_client","error_description":"Invalid client or Invalid client credentials"}
HTTP 401
```

**Resultado esperado.** HTTP 401 sin token. Observa que Keycloak 26.7.3
responde `unauthorized_client` con la descripción *Invalid client or
Invalid client credentials* cuando el `client_id` existe y el secreto es
erróneo. El código `invalid_client` aparece cuando el `client_id` no
existe:

```bash
curl -s -w '\nHTTP %{http_code}\n' -X POST "$TOKEN_URL" \
  -d grant_type=client_credentials -d client_id=no-existe -d client_secret=x
```

```
{"error":"invalid_client","error_description":"Invalid client or Invalid client credentials"}
HTTP 401
```

En ambos casos la descripción es la misma, para no revelar si el cliente
existe. Es el equivalente del mensaje único `Invalid username or password`
del laboratorio 08.

### 7.7 Rotación: el secreto anterior deja de valer al instante

1. Admin Console → **Clients** → `servicio-conciliacion` → pestaña
   **Credentials** → **Regenerate** → confirma. Copia el secreto nuevo.
2. Pide un token con el secreto **antiguo**, el que sigue en
   `SECRETO_CONCILIACION`:

   ```bash
   curl -s -w '\nHTTP %{http_code}\n' -X POST "$TOKEN_URL" \
     -d grant_type=client_credentials \
     -d client_id=servicio-conciliacion \
     -d "client_secret=$SECRETO_CONCILIACION"
   ```

   Salida real (secreto anterior `eCV6YN••••••••`):

   ```
   {"error":"unauthorized_client","error_description":"Invalid client or Invalid client credentials"}
   HTTP 401
   ```

3. Actualiza la variable con el secreto nuevo y repite:

   ```bash
   export SECRETO_CONCILIACION='el-secreto-nuevo'
   curl -s -X POST "$TOKEN_URL" -d grant_type=client_credentials \
     -d client_id=servicio-conciliacion -d "client_secret=$SECRETO_CONCILIACION" | jq .
   ```

   Salida real (secreto nuevo `PDnIWf••••••••`):

   ```json
   {
     "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwi…",
     "expires_in": 60,
     "refresh_expires_in": 0,
     "token_type": "Bearer",
     "not-before-policy": 0,
     "scope": "email api-conciliacion-audiencia profile"
   }
   ```

**Resultado esperado.** El secreto anterior muere en el momento de pulsar
*Regenerate*, y solo hay que actualizarlo en un sitio: el proceso
nocturno. `aplicacion-base`, `ana`, `luis` y cualquier otro cliente siguen
como estaban. Compáralo con el paso 3 del ANTES, donde cambiar la clave
rompía a todos los consumidores a la vez. Esto es PCI DSS 8.6.3 con un
botón.

### 7.8 Por qué la firma importa: un token manipulado

El script de 7.3 no verifica la firma. Vamos a ver qué se paga por eso.
Toma el token caducado de 7.4, edita su payload para que caduque dentro de
un año y añádele un rol que no tiene, y vuelve a ensamblarlo con la
**firma original**:

```bash
codificar() { base64 -w0 | tr '/+' '_-' | tr -d '='; }
CABECERA=$(echo "$TOKEN" | cut -d. -f1)
FIRMA=$(echo "$TOKEN" | cut -d. -f3)
PAYLOAD_FALSO=$(decodificar "$(echo "$TOKEN" | cut -d. -f2)" \
  | jq -c '.exp = (.exp + 31536000) | .realm_access.roles += ["administrador"]')
FALSO="$CABECERA.$(printf '%s' "$PAYLOAD_FALSO" | codificar).$FIRMA"

diff <(decodificar "$(echo "$TOKEN" | cut -d. -f2)" | jq .) \
     <(decodificar "$(echo "$FALSO" | cut -d. -f2)" | jq .)
```

Salida real del `diff`:

```
2c2
<   "exp": 1789610236,
---
>   "exp": 1821146236,
13c13,14
<       "lector-conciliacion"
---
>       "lector-conciliacion",
>       "administrador"
```

1. Pásaselo al script:

   ```bash
   scripts/validar-token.sh "$FALSO"; echo "código de salida: $?"
   ```

   Salida real (líneas relevantes):

   ```
     [OK]    exp: caduca en 31536059 s (vida total exp-iat = 31536060 s)
     [OK]    realm_access.roles: ["lector-conciliacion","administrador"] incluye 'lector-conciliacion'

   Token ACEPTADO: 10 de 10 comprobaciones superadas (firma NO verificada).
   código de salida: 0
   ```

   **El script se lo traga.** Hemos fabricado la API key eterna del ANTES
   con un editor de texto, y encima con un rol de más. Todo lo que
   comprueba el script está en el payload, y el payload lo escribe quien
   quiera. Lo único que no se puede fabricar es la firma, y la firma es lo
   que el script no mira. Por eso el aviso de su cabecera está en
   mayúsculas: **una API real verifica la firma antes que nada**.

2. Ahora pregúntale a Keycloak por ese token. Quien pregunta es la API,
   `api-conciliacion`, porque es la audiencia del token (2.4):

   ```bash
   curl -s -X POST http://localhost:8080/realms/curso/protocol/openid-connect/token/introspect \
     -u "api-conciliacion:$SECRETO_API" -d "token=$FALSO"
   ```

   Salida real:

   ```json
   {"active":false}
   ```

   Y en el log de Keycloak (`docker compose logs keycloak | grep INTROSPECT`)
   queda el motivo:

   ```
   WARN [org.keycloak.events] type="INTROSPECT_TOKEN_ERROR", realmName="curso", clientId="api-conciliacion", ... error="invalid_token", reason="Access token JWT check failed", client_auth_method="client-secret"
   ```

   *JWT check failed*: la firma no corresponde al contenido. Keycloak sí
   verifica lo que el script no verifica.

3. Para contrastar, introspecciona un token válido recién emitido (pide
   uno nuevo antes, el de 7.1 ya caducó):

   ```bash
   TOKEN=$(curl -s -X POST "$TOKEN_URL" -d grant_type=client_credentials \
     -d client_id=servicio-conciliacion -d "client_secret=$SECRETO_CONCILIACION" | jq -r .access_token)
   curl -s -X POST http://localhost:8080/realms/curso/protocol/openid-connect/token/introspect \
     -u "api-conciliacion:$SECRETO_API" -d "token=$TOKEN" | jq '{active, aud, azp, realm_access, username}'
   ```

   Salida real:

   ```json
   {
     "active": true,
     "aud": "api-conciliacion",
     "azp": "servicio-conciliacion",
     "realm_access": {
       "roles": [
         "lector-conciliacion"
       ]
     },
     "username": "service-account-servicio-conciliacion"
   }
   ```

4. Y una última comprobación: intenta la misma introspección como
   `servicio-conciliacion`, el cliente que obtuvo el token:

   ```bash
   curl -s -X POST http://localhost:8080/realms/curso/protocol/openid-connect/token/introspect \
     -u "servicio-conciliacion:$SECRETO_CONCILIACION" -d "token=$TOKEN"
   ```

   Salida real:

   ```json
   {"active":false}
   ```

   En el log: `reason="Client 'servicio-conciliacion' is not in the token
   audience"`. Es la validación de audiencia de la introspección (2.4): el
   token es para `api-conciliacion`, y solo su destinatario puede preguntar
   por él. El cliente que lo pidió no está en el `aud`, y por tanto no.

**Resultado esperado.** El token manipulado pasa el script (salida 0) y
Keycloak lo declara `active: false`; el token auténtico da `active: true`
con sus claims; y `servicio-conciliacion` no puede introspeccionar. Tres
lecciones en una: la firma es la que sostiene todo lo demás, la
introspección es la herramienta del servidor de recursos, y esa
verificación de firma que aquí ha hecho Keycloak por red es la que una API
real hace en local con el JWKS, sin coste de red, en cada petición.

### 7.9 Antes y después, en una tabla

| | API key eterna (ANTES) | Client Credentials (DESPUÉS) |
|---|---|---|
| Caducidad | Ninguna | 60 s (`exp`) |
| Destinatario | Cualquiera | `aud: api-conciliacion` |
| Permisos | Todo o nada | `roles: [lector-conciliacion]` |
| Quién llama | Desconocido | `azp`, `client_id`, `sub` |
| Revocación | Cambiar la clave en todos los consumidores | *Regenerate* en un cliente |
| Dónde vive el secreto | Archivo de configuración (PCI 8.6.2) | Fuera del repositorio, en el entorno del proceso |
| Validación en la API | Comparar dos cadenas | Firma + `iss`, `aud`, `exp`, `typ`, `azp`, roles |

## 8. Lista de verificación

- [ ] Existe el rol de realm `lector-conciliacion` y **no** está asignado a `ana`, `luis` ni al grupo `operaciones`.
- [ ] `servicio-conciliacion`: Client authentication On, Standard flow **Off**, Direct access grants **Off**, Service accounts roles **On**, sin redirect URIs.
- [ ] En `servicio-conciliacion-dedicated` → Scope, Full scope allowed Off y solo `lector-conciliacion`.
- [ ] Pestaña Service accounts roles de `servicio-conciliacion`: `lector-conciliacion` asignado; el enlace lleva al usuario `service-account-servicio-conciliacion`.
- [ ] `api-conciliacion`: Client authentication On y ningún flujo activado.
- [ ] Client scope `api-conciliacion-audiencia` con mapper Audience → *Included Client Audience* `api-conciliacion`, solo en access token.
- [ ] Ese scope es Default en `servicio-conciliacion` y **no** está en `aplicacion-base`.
- [ ] `curl` con `grant_type=client_credentials` devuelve HTTP 200, `expires_in: 60` y sin refresh token.
- [ ] El payload lleva `typ: Bearer`, `azp: servicio-conciliacion`, `aud: api-conciliacion`, `realm_access.roles: ["lector-conciliacion"]` y `preferred_username: service-account-servicio-conciliacion`.
- [ ] `scripts/validar-token.sh` acepta el token recién emitido y lo rechaza por `exp` pasado un minuto.
- [ ] `aplicacion-base` y `api-conciliacion` reciben `unauthorized_client` con este grant.
- [ ] Un secreto erróneo recibe HTTP 401 sin token.
- [ ] Tras *Regenerate*, el secreto anterior recibe HTTP 401 y el nuevo obtiene token.
- [ ] El token manipulado pasa el script pero la introspección como `api-conciliacion` devuelve `active: false`.
- [ ] `aplicacion_base` no ha cambiado en todo el laboratorio.

## 9. Punto de control

`keycloak/curso-realm.json` parte del punto de control del laboratorio 10
y añade:

- El rol de realm `lector-conciliacion`.
- El cliente `servicio-conciliacion` (confidencial, solo *Service accounts
  roles*, `fullScopeAllowed: false`) con `service_account` y
  `api-conciliacion-audiencia` entre sus `defaultClientScopes`, y el
  cliente `api-conciliacion` sin flujos.
- El client scope `api-conciliacion-audiencia` con el mapper
  `oidc-audience-mapper` (`included.client.audience: api-conciliacion`).
- En `scopeMappings`, `lector-conciliacion` para `servicio-conciliacion`.
- En `users`, el usuario interno `service-account-servicio-conciliacion`
  con `serviceAccountClientId` y el rol `lector-conciliacion`. Así es como
  Keycloak exporta la asignación de roles de una service account.

Los secretos del JSON son de laboratorio:
`secreto-lab11-cambialo-en-produccion` para `servicio-conciliacion` y
`secreto-api-lab11-cambialo-en-produccion` para `api-conciliacion`, con el
mismo criterio que el secreto de `aplicacion-base` desde el laboratorio 04.
Tras importar, si quieres reproducir 7.7, regenera el de
`servicio-conciliacion` en la consola. El proveedor `github` sigue con los
marcadores del laboratorio 10.

Importación verificada en un contenedor temporal: el grant
`client_credentials` con `secreto-lab11-cambialo-en-produccion` devuelve un
token con `aud: api-conciliacion` y `roles: [lector-conciliacion]`, el
script lo acepta y la introspección como `api-conciliacion` responde
`active: true`.

## 10. Problemas frecuentes

**`unauthorized_client` · "Client not enabled to retrieve service account"**
El cliente no tiene *Service accounts roles* activado (5.2), o estás usando
el `client_id` equivocado (`aplicacion-base`, `api-conciliacion`).

**`unauthorized_client` · "Invalid client or Invalid client credentials"**
El secreto no es el actual. Si acabas de pulsar *Regenerate*, actualiza
`SECRETO_CONCILIACION`. Si el `client_id` no existe, el error es
`invalid_client` con la misma descripción.

**El token no lleva `realm_access` o no lleva `lector-conciliacion`**
Falta una de las dos mitades de la intersección: el rol en la pestaña
Service accounts roles (5.5) o el *scope mapping* con Full scope allowed
desactivado (5.4).

**El token lleva `aud: ["api-conciliacion", "account"]` y roles de más**
Full scope allowed sigue activado en `servicio-conciliacion-dedicated`.
Los roles por defecto meten roles de `account` y *Audience resolve* los
convierte en audiencia (laboratorio 07, 2.3).

**El token no lleva `aud`**
El client scope `api-conciliacion-audiencia` no está asignado como
*Default* al cliente (5.8), o el mapper no tiene *Add to access token*
activado (5.7).

**El script dice "jwks: no se pudo descargar"**
Keycloak no está arrancado o `KEYCLOAK_URL` apunta a otro sitio. El script
usa `http://localhost:8080` por defecto; cámbialo con
`KEYCLOAK_URL=http://localhost:8082 scripts/validar-token.sh "$TOKEN"` si
pruebas contra otro puerto.

**El script dice "exp: caducó hace N s" nada más pedir el token**
El reloj de WSL va desfasado respecto al del contenedor. Compara `date +%s`
con el `iat` del token. Reiniciar WSL (`wsl --shutdown` desde Windows)
suele resincronizarlo.

**La introspección devuelve `active: false` para un token válido**
Estás preguntando como un cliente que no está en el `aud` del token, por
ejemplo `servicio-conciliacion`. Pregunta como `api-conciliacion`, con su
secreto (5.6). El motivo exacto queda en el log de Keycloak como
`INTROSPECT_TOKEN_ERROR`.

**`base64: invalid input` al decodificar**
Estás decodificando el token entero o sin traducir base64url. Usa la
función `decodificar` de 7.2 sobre un solo segmento.

**No encuentro `service-account-servicio-conciliacion` en Users**
Es lo esperado: la lista de Users no muestra las service accounts. Entra
por Clients → `servicio-conciliacion` → Service accounts roles → enlace al
usuario.

## 11. Siguiente laboratorio

`laboratorio-12-auditoria-eventos`: en este laboratorio ya hemos mirado el
log de Keycloak para entender por qué una introspección fallaba. El
siguiente laboratorio activa y explota los **eventos** de usuario y de
administración: quién obtuvo un token, quién falló al autenticarse, quién
regeneró un secreto, y cómo consultarlo sin leer logs a mano.
