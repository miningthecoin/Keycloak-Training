# Laboratorio 07 · Minimización de claims y audiencia

**Tipo:** práctico · **Duración:** 60 min · **Funcionalidad de Keycloak:** client scopes, role scope mappings y audiencia del token

## 1. Objetivo

Que los tokens que Keycloak emite para la aplicación lleven **solo lo que
la aplicación necesita** y **sirvan solo donde deben servir**. Al terminar:

- El ID Token de `ana` contiene un único rol, `gestor-clientes`, en vez de
  cuatro. El de `luis` no contiene ninguno.
- El correo electrónico deja de viajar en el token: la aplicación no lo usa
  y ya no lo pide.
- El access token deja de llevar `aud: "account"` y los roles de la
  Account Console. Un token emitido para nuestra aplicación ya no vale
  para llamar a otro servicio de Keycloak.
- La cabecera de la aplicación pasa de `ana [default-roles-curso,
  gestor-clientes, offline_access, uma_authorization]` a
  `ana [gestor-clientes]`.
- La aplicación cambia una sola línea: el parámetro `scope`.

### Beneficios de implementarlo

- **Menos datos expuestos.** Cada claim que viaja en un token acaba en
  cookies, logs, cabeceras y volcados de depuración. Lo que no está, no se
  filtra.
- **Menos privilegios en circulación.** Un token que solo nombra el rol que
  la aplicación usa no revela la estructura interna del realm ni concede
  permisos sobre otros servicios.
- **Un token, un destinatario.** Con la audiencia bajo control, un token
  robado o reenviado sirve para una sola cosa, no para todas.
- **Trazabilidad de decisiones.** Queda escrito en la configuración del
  cliente qué roles y qué claims tiene permitido recibir. Eso es auditable;
  "todo por defecto" no lo es.
- **Preparación del terreno.** Cuando en el laboratorio 11 aparezca una
  API, la audiencia se fijará sobre un cliente que ya no reparte tokens
  válidos para cualquiera.

### Un ejemplo de las amenazas que evita

Imagina que la Cooperativa Andina contrata a una empresa externa para un
servicio de informes. La aplicación, para pedir un informe, le envía el
access token de `ana`. Hoy ese token dice `aud: "account"` y lleva los
roles `manage-account` y `manage-account-links`. Un desarrollador curioso
del proveedor, o un atacante que haya comprometido su servidor, puede
tomar ese token y llamar con él a la API de la Account Console de
Keycloak: cambiar el correo de `ana`, vincular otra identidad a su cuenta,
o revisar sus sesiones. Nadie se lo dio para eso, pero el token lo permite
porque nadie limitó su audiencia.

Segunda escena. El mismo proveedor guarda los tokens en sus logs "para
depurar". Meses después, esos logs se filtran. En cada token aparecen el
correo de cada empleado y la lista completa de roles del realm, incluido
`uma_authorization` y `offline_access`, que a un atacante le dicen qué
capacidades tiene el servidor de identidad y por dónde intentar
escalar. La cooperativa no supo nunca que sus tokens contaban tanto.

Tras este laboratorio, el token de `ana` dice: "usuario `ana`, rol
`gestor-clientes`, emitido para `aplicacion-base`". Nada más. El proveedor
no puede hacer nada con él en la Account Console, y si se filtra, revela lo
mínimo.

Fuentes oficiales (Server Administration Guide):

- *Client scopes*, scopes predefinidos, *Include in token scope* y *Link
  client scope with the client* (Default frente a Optional):
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_client_scopes>
  y <https://www.keycloak.org/docs/latest/server_admin/index.html#_client_scopes_linking>
- *Role scope mappings* y *Full scope allowed*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_role_scope_mappings>
- *Audience support*, con *Audience resolve* y *Hardcoded audience*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#audience-support>
- *Evaluating client scopes*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_client_scopes_evaluate>

## 2. Conceptos

### 2.1 Claims, scopes y client scopes

Un **claim** es un dato dentro del token (`preferred_username`, `email`,
`realm_access`…). Un **scope** es lo que la aplicación pide en la petición
de autorización con el parámetro `scope` de OAuth 2.0. En Keycloak, cada
scope se corresponde con un **client scope**: un paquete de *protocol
mappers* (qué claims se escriben) y de *role scope mappings* (qué roles
pueden entrar) que se comparte entre clientes.

Keycloak trae varios predefinidos. `profile`, `email`, `address` y `phone`
vienen de la especificación OpenID Connect y solo llevan mappers de claims.
`roles` y `web-origins` son propios de Keycloak: `roles` escribe los roles
del usuario y resuelve audiencias (2.3).

Cada client scope se vincula a un cliente de una de dos formas (*Link
client scope with the client*):

| Tipo | Cuándo se aplica | Ejemplo en este lab |
|---|---|---|
| **Default** | Siempre, pida lo que pida la aplicación en `scope` | `profile`, `roles`, `basic`, `acr`, `web-origins` |
| **Optional** | Solo si la aplicación lo incluye en `scope` | `email` (a partir de este lab), `phone`, `address` |

Por eso quitar `email` del `scope` de la aplicación no basta por sí solo:
mientras `email` sea *Default* en el cliente, Keycloak lo añade igual. Hay
que cambiarlo a *Optional* en Keycloak **y** dejar de pedirlo en la
aplicación. Son las dos mitades del mismo control.

### 2.2 Role scope mappings y "Full scope allowed"

En el laboratorio 06 vimos que los roles del token son la intersección de
los roles del usuario y los que el cliente tiene permitido recibir. La
guía es explícita: *por defecto, cada cliente recibe todos los role
mappings del usuario*, porque el cliente nace con **Full scope allowed**
activado. Y añade que ese interruptor es útil en desarrollo y que en
producción se recomienda desactivarlo y declarar los roles que cada cliente
necesita.

Desactivarlo y asignar solo `gestor-clientes` tiene un efecto en cadena:

- Desaparecen del token `default-roles-curso`, `offline_access` y
  `uma_authorization`.
- Desaparecen los roles de cliente de `account` (`manage-account`,
  `view-profile`…), que llegaban a través del rol compuesto por defecto.
- Al no haber roles de cliente, *Audience resolve* ya no añade `account`
  como audiencia (2.3).
- `luis`, que no tiene `gestor-clientes`, se queda sin claim
  `realm_access`. La aplicación lo tolera: el mapeador del laboratorio 06
  contempla que el claim no exista.

### 2.3 Audiencia: para quién es el token

El claim `aud` dice a quién va dirigido un token. Según *Audience support*:

- El **ID Token** lleva siempre una sola audiencia: el cliente para el que
  se emitió (`aplicacion-base`). Lo exige OpenID Connect, y Spring lo
  valida en cada login (laboratorio 04).
- El **access token** no lleva automáticamente al cliente que lo pidió.
  Sus audiencias las añaden dos mappers: **Audience resolve** (dentro del
  scope `roles`: añade cada cliente del que el usuario tenga roles de
  cliente en el token) y **Audience**, uno explícito ("hardcoded") que
  añade el cliente o la URL que se indique.

Así se explica el `aud: "account"` del ANTES: los roles por defecto dan a
todo usuario roles del cliente `account`, y *Audience resolve* convierte
eso en audiencia. Un access token de nuestra aplicación era, sin que nadie
lo pidiera, un token válido para la API de la Account Console.

La guía recomienda, en entornos con poca confianza entre servicios,
limitar las audiencias del token y que cada servicio verifique la suya. En
este laboratorio hacemos la primera mitad: dejamos el access token sin
audiencias sobrantes. La segunda, una audiencia explícita para una API y
su verificación, llegará en el laboratorio 11, cuando exista esa API. No
tiene sentido fijar hoy una audiencia hacia un servicio que no existe.

### 2.4 Qué cambia en la aplicación

Solo el parámetro `scope`: de `openid profile email` a `openid profile`.
La aplicación únicamente lee `preferred_username`, que viene de `profile`.
Todo lo demás (validación del ID Token, roles, logout) sigue igual, y por
eso la cabecera muestra sola el cambio.

## 3. Relación con OWASP, ASVS y PCI DSS

- **OWASP A01:2021 Pérdida de control de acceso.** Un token que concede,
  sin querer, permisos sobre la Account Console es un control de acceso
  roto por exceso: no por lo que la aplicación comprueba, sino por lo que
  el token autoriza en otros sitios.
- **OWASP A04:2021 Diseño inseguro.** Emitir tokens "con todo por defecto"
  es una decisión de diseño, no un bug. El principio de mínimo privilegio
  aplica también a lo que se escribe dentro de un token.
- **OWASP ASVS V4.1.3.** Principio de mínimo privilegio: los usuarios solo
  acceden a las funciones y datos para los que están autorizados. Aquí se
  aplica al contenido del token, no solo a las rutas.
- **PCI DSS 7.2.1 y 7.2.2.** El modelo de control de acceso define qué
  necesita cada función, y el acceso se concede con el mínimo privilegio.
  Un token que lleva roles y audiencias que la función no requiere
  incumple ese mínimo aunque la aplicación nunca los use.

## 4. Ejercicio ANTES: el token cuenta y permite de más

**Propósito.** Ver, sin tocar nada, qué llevan los tokens que Keycloak
emite para la aplicación en el estado del laboratorio 06.

1. Admin Console → menú **Clients** → haz clic en `aplicacion-base` → en la
   fila de pestañas del cliente entra en **Client scopes** (la pestaña
   dentro del cliente, no el menú lateral del mismo nombre) → subpestaña
   **Evaluate**, junto a *Setup*. En *User* escribe `ana` y selecciónala.
   Abre **Generated ID token**.
2. Abre **Generated access token** para la misma `ana`.
3. Arranca tu aplicación tal como quedó en el laboratorio 06 y entra como
   `ana`:
   ```bash
   cd ~/keycloak-curso/aplicacion_base
   export KEYCLOAK_CLIENT_SECRET='el-secreto-de-tu-cliente'
   mvn spring-boot:run
   ```
4. Desde otra terminal, mira qué pide la aplicación a Keycloak:
   ```bash
   curl -s -o /dev/null -w "%{redirect_url}\n" http://localhost:8081/oauth2/authorization/keycloak | tr '&' '\n' | grep '^scope='
   ```

**Resultado esperado.**
- Paso 1: el ID Token trae `realm_access.roles` con **cuatro** roles
  (`default-roles-curso`, `offline_access`, `gestor-clientes`,
  `uma_authorization`) y los claims `email` y `email_verified`. La
  aplicación usa un rol y ningún correo.
- Paso 2: el access token trae `"aud": "account"` y
  `resource_access.account.roles` con `manage-account`,
  `manage-account-links` y `view-profile`. La aplicación no ha pedido
  nada de eso.
- Paso 3: cabecera `Sesión: ana [default-roles-curso, gestor-clientes,
  offline_access, uma_authorization]`.
- Paso 4: `scope=openid%20profile%20email`.

Cierra la sesión y detén la aplicación con `Ctrl+C`.

## 5. Configuración de Keycloak, paso a paso

Con la Admin Console en el realm **`curso`**:

### 5.1 Solo los roles que la aplicación necesita

1. **Clients** → `aplicacion-base` → pestaña **Client scopes**.
2. Haz clic en el primer enlace de la lista, `aplicacion-base-dedicated`.
3. Pestaña **Scope** → desactiva **Full scope allowed**. Aparece la tabla
   de roles permitidos, vacía.
4. **Assign role** → *Filter by realm roles* → marca `gestor-clientes` →
   **Assign**.

Si te saltas el paso 4, `ana` también recibirá *Acceso denegado*: con la
tabla vacía ningún rol entra en el token.

### 5.2 `email` pasa a Optional

1. Vuelve a **Clients** → `aplicacion-base` → **Client scopes**.
2. En la fila `email`, columna *Assigned type*, cambia **Default** por
   **Optional**.

No se elimina el scope: sigue disponible para quien lo pida en `scope`.
Simplemente deja de ir "de oficio".

## 6. Cambios en la aplicación

Trabaja sobre `~/keycloak-curso/aplicacion_base`. El resultado completo
está en `aplicacion_base_lab-07/`.

### 6.1 `application.yml`: pedir solo lo necesario

Dentro de `spring.security.oauth2.client.registration.keycloak`, sustituye
la línea `scope` y su comentario por:

```yaml
            # "openid" convierte OAuth 2.0 en OpenID Connect (habrá ID Token).
            # "profile" pide los claims de perfil, entre ellos
            # "preferred_username", el único que la aplicación usa.
            #
            # LABORATORIO 07: se retira "email". La aplicación nunca leía
            # el correo, y cada claim que viaja en un token sin necesidad
            # es dato expuesto de más. En Keycloak, el client scope "email"
            # pasa de Default a Optional: solo se incluye si la aplicación
            # lo pide aquí. Es el parámetro "scope" de OAuth 2.0 en acción.
            scope: openid, profile
```

No hay más cambios. Ni Java ni plantillas.

### 6.2 Arrancar

```bash
cd ~/keycloak-curso/aplicacion_base
export KEYCLOAK_CLIENT_SECRET='el-secreto-de-tu-cliente'
mvn spring-boot:run
```

## 7. Ejercicio DESPUÉS: el token dice lo justo

### 7.1 En el navegador

1. Ventana de incógnito → <http://localhost:8081> → **Clientes** → entra
   como `ana` / `ana123`.
2. **Cerrar sesión** → **Clientes** → entra como `luis` / `luis123`.

**Resultado esperado.**
- Paso 1: `ana` ve la tabla y la cabecera dice `Sesión: ana
  [gestor-clientes]`. Un solo rol.
- Paso 2: `luis` recibe **Acceso denegado** y la cabecera dice `Sesión:
  luis` **sin corchetes**: su ID Token ya no trae ningún rol.

### 7.2 Con curl: qué pide ahora la aplicación

```bash
curl -s -o /dev/null -w "%{redirect_url}\n" http://localhost:8081/oauth2/authorization/keycloak | tr '&' '\n' | grep '^scope='
```

Resultado esperado: `scope=openid%20profile`.

### 7.3 En Evaluate: qué llevan ahora los tokens

1. **Clients** → `aplicacion-base` → pestaña **Client scopes** →
   subpestaña **Evaluate** (la misma ruta que en 4.1), usuario `ana`. Mira
   **Generated ID token** y **Generated access token**.
2. Repite con `luis`.
3. Con `ana`, escribe `email` en el campo *Scope parameter* de Evaluate y
   vuelve a mirar el ID Token.

**Resultado esperado.**
- Paso 1, ID Token de `ana`: `realm_access.roles` = `["gestor-clientes"]`
  y **sin** `email` ni `email_verified`. Access token: **sin** claim
  `aud` y **sin** `resource_access`; `realm_access` solo con
  `gestor-clientes`; `"scope": "openid profile"`.
- Paso 2, `luis`: ni ID Token ni access token llevan `realm_access`.
- Paso 3: al pedir `email` explícitamente, el claim vuelve a aparecer. Es
  *Optional*: disponible, pero solo bajo demanda.

### 7.4 El login sigue siendo válido

La aplicación valida en cada login que el `aud` del ID Token contiene su
`client_id`. Nada de lo anterior lo afecta: el ID Token conserva
`"aud": "aplicacion-base"` porque lo exige OpenID Connect. Lo que hemos
limpiado es el access token, cuyo `aud` sí es configurable.

## 8. Lista de verificación

- [ ] En `aplicacion-base-dedicated` → Scope, *Full scope allowed* está desactivado y la tabla contiene solo `gestor-clientes`.
- [ ] En Clients → `aplicacion-base` → Client scopes, `email` figura como *Optional*; `profile` y `roles` siguen como *Default*.
- [ ] `application.yml` pide `scope: openid, profile`.
- [ ] La petición de autorización lleva `scope=openid profile`.
- [ ] Cabecera de `ana`: `[gestor-clientes]`. Cabecera de `luis`: sin corchetes, y 403 en `/privada`.
- [ ] Evaluate: el ID Token de `ana` no trae `email`; el access token no trae `aud` ni `resource_access`.
- [ ] Evaluate con `email` en *Scope parameter*: el claim vuelve.

## 9. Punto de control

`keycloak/curso-realm.json` parte del punto de control del laboratorio 06
y añade en el cliente `fullScopeAllowed: false`, `email` en
`optionalClientScopes`, y un bloque `scopeMappings` que concede
`gestor-clientes` a `aplicacion-base`. Sigue incluyendo todos los client
scopes por defecto del realm (ver laboratorio 06, sección 9). El secreto
del cliente sigue siendo `secreto-lab04-cambialo-en-produccion`.

## 10. Problemas frecuentes

**Tras el laboratorio, `ana` recibe Acceso denegado**
Se desactivó *Full scope allowed* pero no se asignó `gestor-clientes` en
la pestaña Scope (5.1, paso 4). Compruébalo en Evaluate: el ID Token de
`ana` no tendrá `realm_access`.

**El ID Token sigue trayendo `email`**
O `email` sigue como *Default* en el cliente (5.2), o la aplicación sigue
pidiendo `email` en `scope` (6.1) y, al ser *Optional*, Keycloak lo sirve.
Hacen falta las dos cosas. Recuerda reiniciar la aplicación tras cambiar
`application.yml`.

**El access token sigue con `aud: "account"`**
*Full scope allowed* sigue activado. Con él, los roles de `account` entran
en el token y *Audience resolve* añade la audiencia.

**Keycloak responde `invalid_scope` al iniciar sesión**
La aplicación pide en `scope` un nombre que no existe como client scope
en el realm, o uno que no está vinculado al cliente ni como *Default* ni
como *Optional*. Revisa la ortografía en `application.yml`.

**La cabecera sigue mostrando cuatro roles**
La sesión es anterior al cambio. Cierra sesión y vuelve a entrar: los
roles se leen del ID Token emitido en el login (laboratorio 06, 2.5).

## 11. Siguiente laboratorio

`laboratorio-08-politicas-password-fuerza-bruta`: `ana123` y `luis123`
son contraseñas de juguete y Keycloak acepta intentos ilimitados. El
siguiente laboratorio impone una política de contraseñas y activa la
detección de fuerza bruta.
