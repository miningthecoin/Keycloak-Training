# Laboratorio 14 · Cierre: passkeys, autorización fina y acceso temporal

**Tipo:** demostración guiada · **Duración:** 60 min · **Funcionalidades de Keycloak:** WebAuthn Passwordless y passkeys, Authorization Services, y la Admin REST API con cuenta de servicio

## 1. Objetivo

Este laboratorio **rompe a propósito la regla del curso de "una funcionalidad
por laboratorio"**, y conviene decir por qué antes de empezar.

Los laboratorios 03 a 12 construyeron una aplicación segura paso a paso, y
cada paso se podía copiar tal cual al trabajo del lunes. Este no enseña a
integrar nada nuevo: enseña **hacia dónde seguir**. Son las tres preguntas
que aparecen en cuanto lo anterior ya funciona:

- *"¿Podemos quitar las contraseñas?"* → **Parte A**, passkeys.
- *"El rol se nos ha quedado grueso, ¿podemos afinar más?"* → **Parte B**,
  Authorization Services.
- *"¿Y los accesos de administración temporales, con aprobación?"* →
  **Parte C**, acceso *just-in-time*.

Cada parte es una demostración de 15–25 minutos con su ejercicio ANTES y
DESPUÉS reducido. Ninguna toca la aplicación Spring. Al terminar:

- `ana` entra escribiendo **solo su usuario**: Windows Hello (o una llave
  FIDO2, o su móvil) la autentica sin contraseña y sin código OTP.
- Los mismos recursos se protegen con reglas que **combinan rol y
  atributo**, no solo rol, y se comprueba con la herramienta *Evaluate* y
  con el grant UMA.
- Un servicio concede y retira acceso de administración **por API, con
  mínimo privilegio y con rastro en los eventos de auditoría** del
  laboratorio 12.
- `ana` y `luis` siguen entrando **exactamente igual que antes** por la vía
  clásica, y todas las `aplicacion_base_lab-XX` siguen funcionando sin
  tocar `application.yml`, ni el secreto, ni el `issuer-uri`.

### Beneficios de implementarlo

- **Las passkeys son resistentes al phishing por diseño.** La credencial
  está atada al *origin* (`localhost`, o el dominio real). Una web falsa no
  puede pedirla: el navegador ni la ofrece. Es la diferencia de fondo con
  el TOTP del laboratorio 09.
- **Se acaban las contraseñas que reutilizar, filtrar o rotar.** No hay
  secreto compartido que robar: la clave privada no sale del dispositivo.
- **La autorización deja de ser "todo o nada".** Con recursos, políticas y
  permisos se expresa "este gestor, sobre esta cartera", sin inventar un rol
  nuevo por cada combinación.
- **Los permisos se pueden cambiar sin tocar roles ni desplegar.** Cambiar
  un atributo de usuario invierte una decisión de acceso.
- **Los accesos privilegiados dejan de ser permanentes.** Se conceden, se
  auditan y se retiran, que es literalmente lo que pide PCI DSS 7.2.4.

### Un ejemplo de las amenazas que evita

Es martes y `ana` recibe un correo del "soporte de la Cooperativa": hay que
revalidar el acceso al portal de clientes. El enlace lleva a
`portal-cooperativa-andina.example`, una copia pixel a pixel de la pantalla
de login. `ana` escribe su usuario y su contraseña. La web falsa los
reenvía **en ese mismo instante** al portal real, que responde pidiendo el
segundo factor; la web falsa se lo pide a `ana`, `ana` copia los seis
dígitos de su móvil, y el atacante los reenvía dentro de la ventana de 30
segundos. Está dentro. El 2FA del laboratorio 09 no lo ha impedido: un
código TOTP es un secreto que el usuario puede ser engañado para entregar.

Con una passkey, ese ataque **no llega a empezar**. La credencial de `ana`
se registró con un *Relying Party ID* concreto. Cuando la web falsa pide
autenticación, el navegador busca credenciales para
`portal-cooperativa-andina.example` y no encuentra ninguna: la de `ana` es
para otro origen y el navegador **no la expone**. No hay nada que `ana`
pueda teclear, copiar o confirmar por error. La protección no depende de
que se dé cuenta del engaño.

Y en la segunda mitad del laboratorio, aunque el atacante hubiera entrado:
con la autorización fina de la parte B solo vería la cartera que le
corresponda a esa cuenta, y con la parte C no encontraría cuentas de
administración permanentes esperando a ser tomadas.

Fuentes oficiales (Server Administration Guide):

- *W3C Web Authentication (WebAuthn)* y *Managing policy*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#webauthn_server_administration_guide>,
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_webauthn-policy>
- *Passwordless WebAuthn together with Two-Factor*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_webauthn_passwordless>
- *LoginLess WebAuthn*, con *Windows Hello* y *Supported Passkeys*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_webauthn_loginless>,
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_webauthn-supported-keys>
- *Passkeys*, *Conditional UI or autofill*, *Modal UI* y *Passkey
  Mediation*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#passkeys_server_administration_guide>,
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_passkeys-conditional-ui>,
  <https://www.keycloak.org/docs/latest/server_admin/index.html#passkeys-authentication-with-modal-ui>,
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_passkeys-mediation>
- *Recovery Codes*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_recovery-codes>
- *Authentication flows* y *Using a service account*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_authentication-flows>,
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_service_accounts>
- *User profile* (atributos declarativos):
  <https://www.keycloak.org/docs/latest/server_admin/index.html#user-profile>

Authorization Services Guide:

- *Enabling authorization services* y *Resource server settings*:
  <https://www.keycloak.org/docs/latest/authorization_services/index.html#_resource_server_enable_authorization>,
  <https://www.keycloak.org/docs/latest/authorization_services/index.html#resource_server_settings>
- *Role-based policy*, *Regex-Based Policy* y *Group-based policy*:
  <https://www.keycloak.org/docs/latest/authorization_services/index.html#_policy_rbac>,
  <https://www.keycloak.org/docs/latest/authorization_services/index.html#_policy_regex>,
  <https://www.keycloak.org/docs/latest/authorization_services/index.html#_policy_group>
- *Creating scope-based permissions* y *Policy decision strategies*:
  <https://www.keycloak.org/docs/latest/authorization_services/index.html#_permission_create_scope>,
  <https://www.keycloak.org/docs/latest/authorization_services/index.html#_permission_decision_strategies>
- *Evaluating and testing policies* y *The evaluation context*:
  <https://www.keycloak.org/docs/latest/authorization_services/index.html#_policy_evaluation_overview>,
  <https://www.keycloak.org/docs/latest/authorization_services/index.html#the-evaluation-context>
- *Obtaining permissions* (grant UMA) y *Requesting party token*:
  <https://www.keycloak.org/docs/latest/authorization_services/index.html#_service_obtaining_permissions>,
  <https://www.keycloak.org/docs/latest/authorization_services/index.html#_service_rpt_overview>

Y además:

- Admin REST API: <https://www.keycloak.org/docs-api/latest/rest-api/index.html>
- *Enabling and disabling features*: <https://www.keycloak.org/server/features>

---

# PARTE A · Passwordless con passkeys (25 min)

## 2.A Conceptos

### 2.A.1 Qué es realmente una passkey

Una **passkey** es una credencial WebAuthn con dos propiedades concretas:

| Propiedad | Qué significa | Opción en Keycloak |
|---|---|---|
| *Discoverable credential* | La clave se guarda **en el autenticador** junto con el usuario al que pertenece, de modo que el dispositivo puede decir "yo soy `ana`" sin que nadie escriba el usuario | **Discoverable Credential** = `required` |
| *User verification* | El autenticador **comprueba quién eres** antes de firmar: PIN, huella o cara | **User Verification Requirement** = `required` |

Sin la primera, tienes una llave de segundo factor. Sin la segunda, tienes
algo que abre la cuenta a quien te lo robe. Con las dos, tienes un factor
que vale por sí solo: **algo que tienes** (el dispositivo) más **algo que
eres o sabes** (la biometría o el PIN), verificado localmente y sin que
ningún secreto viaje por la red.

Por eso es correcto que el flujo de este laboratorio **no pida OTP** cuando
se entra con passkey: no se está saltando el segundo factor, es que la
passkey ya lo incluye.

### 2.A.2 El Relying Party ID: por qué el phishing no funciona

Keycloak actúa como **Relying Party** de WebAuthn. Al registrar una
credencial le dice al navegador para qué dominio es: el **Relying Party
ID**. La guía es precisa:

> *The ID must be the origin's effective domain. […] If this entry is blank,
> Keycloak adapts the host part of Keycloak's base URL.*

Lo dejamos **vacío**, así que se deriva de `localhost`. Dos consecuencias
que hay que entender:

1. **Es lo que rompe el phishing.** El navegador solo ofrece credenciales
   cuyo RP ID coincide con el dominio que las pide. Una web falsa en otro
   dominio no puede obtener la passkey de `ana` ni engañándola.
2. **Cambiar de host invalida las credenciales.** Si mañana publicas
   Keycloak en `sso.cooperativa-andina.test`, las passkeys registradas
   contra `localhost` **dejan de servir** y todo el mundo tiene que volver
   a registrar. No es un fallo: es la misma atadura al dominio que da la
   seguridad. Por eso, en producción, el RP ID se decide **antes** de que
   la gente empiece a registrar, y se fija al dominio estable (por ejemplo
   `cooperativa-andina.test`, que cubre todos sus subdominios).

### 2.A.3 Las otras opciones de la política

Keycloak mantiene **dos políticas WebAuthn separadas**, y es deliberado: la
guía explica que los requisitos de una passkey sin contraseña suelen ser
más estrictos que los de un segundo factor.

| Opción | Valor que usamos | Por qué |
|---|---|---|
| Relying Party Entity Name | `Cooperativa Andina` | Es el nombre que el sistema operativo enseña al pedir la huella o el PIN. El valor de fábrica es `keycloak`, poco tranquilizador para un usuario |
| Relying Party ID | *(vacío)* | Se deriva del host (2.A.2) |
| Signature Algorithms | `ES256`, `RS256` | **RS256 es obligatorio para Windows Hello**: la guía lo dice literalmente en *Windows Hello*. Sin él, el portátil no puede registrarse |
| Attestation Conveyance Preference | `none` | No pedimos al autenticador que certifique marca y modelo. Pedir `direct` obligaría a gestionar un almacén de confianza con los certificados de cada fabricante |
| Authenticator Attachment | `not specified` | Admite **a la vez** autenticadores de plataforma (Windows Hello, Touch ID) y externos (llave USB, móvil por QR). Fijarlo a `platform` dejaría fuera las llaves |
| **Discoverable Credential** | `required` | Es lo que la convierte en passkey (2.A.1) |
| User Verification Requirement | `required` | El autenticador debe comprobar quién eres |
| Timeout | `0` | Lo decide el autenticador |
| Avoid Same Authenticator Registration | `Off` | Permite registrar el mismo dispositivo más de una vez |

> **Un aviso sobre el nombre de la opción.** En 26.7.3 la consola muestra
> **Discoverable Credential** (`required` / `preferred` / `discouraged` /
> *not specified*) y, justo debajo, **Require Discoverable Credential**
> (`Yes` / `No`). La segunda está **deprecada**: la guía dice que
> *"is planned to be removed in the future and is only used when
> Discoverable Credential is left as not specified"*. Usa siempre la
> primera. Si ves guías antiguas que hablan de "Require Discoverable
> Credential = Yes", se refieren a lo mismo.

### 2.A.4 El flujo: por qué hay que cambiarlo

El flujo `browser` de fábrica empieza con **Username Password Form**: una
pantalla que pide usuario y contraseña juntos. Ahí no cabe el passwordless,
porque para ofrecer una passkey hay que saber primero a quién se la pides
(o dejar que el dispositivo lo diga).

La guía describe la solución en *Passwordless WebAuthn together with
Two-Factor*:

> *The WebAuthn Browser Forms subflow contains Username Form as the first
> authenticator. Delete the default Username Password Form authenticator and
> add the Username Form authenticator. […] There will be a required subflow,
> which can be named Passwordless Or Two-factor […] The flow contains
> WebAuthn Passwordless Authenticator as the first alternative. The second
> alternative will be a subflow named Password And Two-factor Webauthn.*

Nuestra variante cambia una cosa respecto al ejemplo de la guía: dentro de
`Password And Two-factor` ponemos **el sub-flujo condicional de 2FA del
laboratorio 09** (OTP) en vez de un segundo factor WebAuthn. Así la vía
clásica queda exactamente como estaba y no hay regresión.

### 2.A.5 `Condition - credential`: lo que evita pedir dos veces

Dentro del sub-flujo condicional hay una ejecución llamada **Condition -
credential**, configurada con `credentials = webauthn-passwordless`. La
guía la describe así:

> *The step checks if the authentication process has already authenticated
> the user with a passwordless WebAuthn credential (passkey), avoiding 2FA
> in that case.*

Tiene una opción `Included` que por defecto está desactivada, lo que
significa, según la descripción del propio authenticator: *"it will be true
if none of the credentials configured have been used"*. Es decir: la
condición se cumple —y por tanto el 2FA se ejecuta— **solo si NO se usó una
passkey**.

### 2.A.6 Passkeys en las pantallas: Conditional UI y Modal UI

Aquí llega la parte agradable: en 26.7.3 **no hay que programar nada ni
activar ninguna feature de arranque**. `passkeys` es una *feature soportada
y activada por defecto* (lo puedes comprobar en la tabla *Supported
features* de <https://www.keycloak.org/server/features>). Basta activarla
en el realm.

Una vez activa, la pantalla de usuario ofrece dos caminos:

- **Conditional UI (autofill).** El campo de usuario se marca con
  `autocomplete="username webauthn"`. Al pulsarlo, el navegador despliega
  las passkeys que conoce para ese sitio y, si eliges una, el login termina
  ahí.
- **Modal UI.** Un botón **Sign in with Passkey** que abre el diálogo del
  sistema. La guía explica por qué existiendo la anterior: *"because
  conditional UI can sometimes not show all the credentials to the user, the
  modal UI can always be initiated"* — es el camino para las llaves USB y
  los móviles por QR, que el navegador no puede enumerar solo.

El desplegable **Passkey Mediation** decide qué pasa al cargar la página.
Se corresponde con el parámetro `mediation` de `navigator.credentials.get()`:

| Valor | Comportamiento |
|---|---|
| `conditional` (por defecto) | No se abre ningún diálogo. Las passkeys se ofrecen solo por el autocompletado del campo de usuario |
| `none` | Nada automático; queda el botón *Sign in with Passkey* |
| `optional` | Se abre el diálogo de selección al cargar, y se puede descartar |
| `required` | Se abre el diálogo y hay que autenticarse o descartarlo a mano para seguir |
| `silent` | Intenta autenticar sin interacción. Poco probable que funcione |

Usamos `conditional`, el valor por defecto y el menos intrusivo.

### 2.A.7 Códigos de recuperación: el plan para "perdí el móvil"

La guía los define como *"a number of sequential one-time passwords
(currently 12)"* y es explícita sobre su papel:

> *Due to its nature, the Recovery Codes work normally as a backup for
> another 2FA methods.*

Se añaden como **Recovery Authentication Code Form**, en *Alternative*,
junto al *OTP Form*. Detalles que importan:

- Se muestran **una sola vez**, al generarlos. Keycloak los guarda
  hasheados: no hay forma de recuperarlos después.
- Se consumen **en orden**: la pantalla pide "Recovery code #1", luego el
  #2, etc. Cada uno vale una vez.
- Se pueden regenerar cuando se quiera, lo que invalida los anteriores.

## 3.A Relación con OWASP, ASVS y PCI DSS

- **OWASP A07:2021 Fallos de identificación y autenticación.** La categoría
  señala la ausencia de MFA y el uso de credenciales que se pueden robar o
  reutilizar. Una passkey elimina el secreto compartido.
- **OWASP ASVS V2.2 (autenticadores generales).** Pide resistencia a
  ataques de suplantación y canales fuera de banda seguros. WebAuthn con
  verificación de usuario es el ejemplo de referencia.
- **OWASP ASVS V2.8 (autenticadores de un solo uso).** Es donde encaja el
  TOTP del laboratorio 09, y es justo el nivel que la parte A supera: el
  ASVS advierte de que los OTP son *phishable*.
- **PCI DSS 8.4.** Exige MFA para el acceso al entorno de datos de
  titulares de tarjeta. Una passkey con verificación de usuario cumple el
  requisito de dos factores independientes de 8.5.
- **NIST SP 800-63B, AAL3.** Fija el nivel más alto de garantía de
  autenticación y exige un **autenticador criptográfico de hardware**
  resistente a la suplantación. Una passkey ligada al dispositivo (como la
  de Windows Hello, con `transports: ["internal"]`) va en esa dirección;
  una passkey **sincronizada** entre dispositivos por iCloud o Google, no,
  porque la clave sale del hardware. Es un matiz a tener presente antes de
  prometer AAL3 en una auditoría.

## 4.A Ejercicio ANTES: una contraseña y un código que se pueden regalar

**Propósito.** Ver el estado del laboratorio 12 y nombrar su límite.

### 4.A.0 Levantar el entorno

Este laboratorio usa la aplicación tal como quedó en el 12, sin tocarla.
Necesitas **Keycloak y la aplicación corriendo a la vez**.

```bash
# 1. Keycloak, si no está ya en marcha
cd ~/keycloak-curso/keycloak
docker compose up -d
curl -s -o /dev/null -w "Keycloak: %{http_code}\n" \
  http://localhost:8080/realms/curso/.well-known/openid-configuration
# esperado: Keycloak: 200

# 2. La aplicación de este laboratorio
cd ~/keycloak-curso/laboratorio-14-cierre-authz-passkeys/aplicacion_base_lab-14
export KEYCLOAK_CLIENT_SECRET='<el secreto de Clients → aplicacion-base → Credentials>'
mvn spring-boot:run
```

Déjala corriendo en esa terminal durante todo el laboratorio y abre otra
para los `curl` de las partes B y C. Comprueba desde la segunda terminal:

```bash
curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" http://localhost:8081/privada
# esperado: 302 http://localhost:8081/oauth2/authorization/keycloak
```

> **Ejecuta como tu usuario normal, nunca con `sudo` ni como `root`.** El
> repositorio de dependencias de Maven es **por usuario** (`~/.m2`): como
> `root` estarías usando uno vacío y Maven intentaría descargarlo todo otra
> vez. Si algo falla ahí, mira 10.A.0.

Ahora sí:

1. Ventana de incógnito → <http://localhost:8081> → **Clientes**.
2. Entra como `ana` / `Andina*Segura2026` y su código TOTP.

**Resultado esperado.** Una sola pantalla con usuario y contraseña, y
después la del código de seis dígitos. `ana` entra.

3. En la Admin Console, **Users → ana → Credentials**.

**Resultado esperado.** Dos credenciales: `password` y `otp`.

### 4.A.1 El límite, dicho con precisión

Todo lo que `ana` necesita para entrar son **dos datos que ella puede
teclear**: una contraseña y seis dígitos. Y todo lo que una persona puede
teclear en la web verdadera, puede teclearlo en una falsa.

Repasa el ejemplo de la sección 1: el atacante no rompe el TOTP, no lo
adivina ni lo intercepta. Se lo **pide a `ana`**, y `ana` se lo da, porque
la pantalla es idéntica y ella cree estar donde debe. El segundo factor del
laboratorio 09 protege contra la contraseña filtrada (*credential
stuffing*), que era su objetivo y lo cumple; **no** protege contra el
phishing en tiempo real.

Anota la diferencia, porque es la única que importa aquí:

| | Contraseña + TOTP | Passkey |
|---|---|---|
| ¿Hay un secreto que el usuario pueda entregar? | Sí, dos | No |
| ¿Está atado al dominio? | No | Sí, por el RP ID |
| ¿Funciona en una web falsa? | Sí | No |

Cierra la sesión.

## 5.A Configuración de Keycloak

Admin Console, realm **`curso`**.

### 5.A.1 La política WebAuthn Passwordless

1. **Authentication** → pestaña **Policies** → subpestaña **WebAuthn
   Passwordless Policy**.
2. Rellena según la tabla de 2.A.3:
   - *Relying Party Entity Name*: `Cooperativa Andina`
   - *Relying Party ID*: **déjalo vacío**
   - *Signature Algorithms*: `ES256` y `RS256`
   - *Attestation Conveyance Preference*: `none`
   - *Authenticator Attachment*: `not specified`
   - *Discoverable Credential*: `required`
   - *User Verification Requirement*: `required`
   - *Timeout*: `0`
   - *Avoid Same Authenticator Registration*: Off
3. **Save**.

> Varios de estos valores **ya vienen así de fábrica** en la política
> *passwordless* (no en la de 2FA): `Discoverable Credential` y `User
> Verification` ya están en `required`, y los algoritmos ya incluyen RS256.
> Es lo que dice la guía: *"By default, Keycloak sets User Verification
> Requirement to required and Discoverable Credential to required for the
> passwordless scenario to work properly"*. Cámbialos igualmente para saber
> dónde están.

### 5.A.2 Comprobar las acciones requeridas

1. **Authentication** → pestaña **Required actions**.
2. Comprueba que están *Enabled*:
   - **Webauthn Register Passwordless**
   - **Recovery Authentication Codes**
3. **No** las marques como *Set as default action*.

En 26.7.3 las dos vienen habilitadas de serie.

### 5.A.3 Activar las passkeys en las pantallas

1. **Realm settings** → pestaña **Login**.
2. En **Login Screen Customization**, activa **Enable Passkeys**.
3. Aparece el desplegable **Passkey Mediation**: déjalo en `conditional`
   (2.A.6).
4. **Save**.

No hace falta ninguna opción de arranque ni recrear el contenedor:
`passkeys` es una feature soportada y activada por defecto en 26.7.3.

### 5.A.4 El flujo `browser passwordless`

**Authentication** → pestaña **Flows**.

1. En la fila de **browser**, menú **⋮** → **Duplicate**. Nombre:
   `browser passwordless` → **Duplicate**.

   Keycloak copia el flujo entero y **prefija los sub-flujos** con el nombre
   nuevo: verás `browser passwordless forms` y
   `browser passwordless Browser - Conditional 2FA`.

2. **Borra dos ejecuciones** con el icono de papelera:
   - **Username Password Form**
   - **browser passwordless Browser - Conditional 2FA**

   > La consola **no permite mover un sub-flujo a otro padre**. El
   > condicional de 2FA tiene que quedar un nivel más abajo, así que se
   > borra y se reconstruye. Borrarlo primero además libera su nombre para
   > poder reutilizarlo.

3. En la fila **browser passwordless forms**, botón **+**:
   - **Add step** → **Username Form** → **Add** → requisito **Required**.
   - **Add sub-flow** → nombre `Passwordless Or Two-factor` → **Add** →
     requisito **Required**.

4. En la fila **Passwordless Or Two-factor**, botón **+**:
   - **Add step** → **WebAuthn Passwordless Authenticator** → **Add** →
     requisito **Alternative**.
   - **Add sub-flow** → nombre `Password And Two-factor` → **Add** →
     requisito **Alternative**.

5. En la fila **Password And Two-factor**, botón **+**:
   - **Add step** → **Password Form** → **Add** → requisito **Required**.
   - **Add sub-flow** → nombre
     `browser passwordless Browser - Conditional 2FA` → **Add** →
     requisito **Conditional**.

6. En la fila de ese sub-flujo condicional, botón **+** → **Add
   condition** / **Add step**, en este orden:
   - **Condition - user configured** → **Required**
   - **Condition - credential** → **Required**
   - **OTP Form** → **Alternative**
   - **Recovery Authentication Code Form** → **Alternative**

7. Configura la condición: en la fila **Condition - credential**, icono de
   **⚙ (Settings)**:
   - *Alias*: `browser-passwordless-conditional-credential`
   - *Credentials*: selecciona **webauthn-passwordless**
   - **Save**

**Árbol final** (compáralo con el tuyo antes de seguir):

```
Cookie                                                     ALTERNATIVE
Kerberos                                                   DISABLED
Identity Provider Redirector                               ALTERNATIVE
browser passwordless Organization                          ALTERNATIVE
  browser passwordless Browser - Conditional Organization  CONDITIONAL
    Condition - user configured                            REQUIRED
    Organization Identity-First Login                      ALTERNATIVE
browser passwordless forms                                 ALTERNATIVE
  Username Form                                            REQUIRED
  Passwordless Or Two-factor                               REQUIRED
    WebAuthn Passwordless Authenticator                    ALTERNATIVE
    Password And Two-factor                                ALTERNATIVE
      Password Form                                        REQUIRED
      browser passwordless Browser - Conditional 2FA       CONDITIONAL
        Condition - user configured                        REQUIRED
        Condition - credential                             REQUIRED
        OTP Form                                           ALTERNATIVE
        Recovery Authentication Code Form                  ALTERNATIVE
```

> **El sub-flujo Organization** viene de fábrica en el flujo `browser` de
> 26.7.3 y la copia lo arrastra. No lo toques: la feature `organization` no
> está activa en este realm, así que no se ejecuta.

### 5.A.5 Vincular el flujo al realm

1. Dentro del flujo `browser passwordless`, menú **Action** (arriba a la
   derecha) → **Bind flow**.
2. *Choose binding type*: **Browser flow** → **Save**.

> **Antes de pulsar, ten a mano la vuelta atrás.** Si algo sale mal, se
> deshace vinculando de nuevo el flujo `browser`, que sigue intacto.
> Consulta 10.A.1 si te quedas fuera.

**Por qué a nivel de realm y no solo al cliente.** Se podría vincular solo
a `aplicacion-base` con **Clients → aplicacion-base → Advanced →
Authentication flow overrides → Browser Flow**. Aquí no sirve: `ana` tiene
que **registrar** su passkey en la **Account Console**, que es otro cliente
(`account-console`). Si el flujo nuevo solo aplicara a la aplicación, la
Account Console seguiría con el flujo viejo y no habría manera de usar la
passkey para entrar en ella. Cuando el passwordless es una capacidad del
realm, se vincula al realm; el *override* por cliente es para lo contrario:
exigir **más** a una aplicación concreta (por ejemplo, un flujo con step-up
solo para la de administración).

### 5.A.6 `ana` registra su passkey

> **Ventana normal, no de incógnito.** La guía avisa en *Windows Hello*:
> *"some browsers don't allow access to platform Passkey (like Windows
> Hello) inside private windows"*. Usa **Microsoft Edge** o **Chrome** en
> una ventana normal. Y lee 10.A.2 antes de empezar: hay un error muy
> frecuente en este paso.

1. <http://localhost:8080/realms/curso/account> → entra como `ana` /
   `Andina*Segura2026` (+ su código TOTP).
2. **Account security → Signing in**.
3. En la sección **Passwordless**, **Set up a Passkey**.
4. Windows Hello (o tu autenticador) pedirá el PIN, la huella o la cara.
5. **El navegador abrirá un cuadro de diálogo pidiendo una etiqueta** para
   la credencial (*"Please input your registered passkey's label"*, con
   `Passkey (Default Label)` propuesto). Escribe algo reconocible
   —`portatil-ana`— y acepta. **Este paso no es opcional** (10.A.2).

**Las tres variantes que puede usar un alumno:**

| Variante | Cómo | Qué queda registrado |
|---|---|---|
| **Windows Hello / Touch ID** | El propio portátil pide PIN o huella | `transports: ["internal"]`: autenticador de plataforma, ligado a ese equipo |
| **Llave USB FIDO2** (YubiKey, Feitian…) | Insertar la llave y tocarla; pedirá el PIN de la llave | `transports: ["usb"]`: portable entre equipos |
| **Móvil por QR** | Elegir "usar otro dispositivo"; el navegador muestra un QR que se escanea con el móvil | Autenticación entre dispositivos (CDA), por Bluetooth de proximidad |

> **Contexto seguro obligatorio.** WebAuthn solo funciona sobre **HTTPS** o
> sobre **`localhost`**. En este curso funciona por ser `localhost`. En
> cuanto publiques Keycloak en una dirección de red real, sin TLS no hay
> passkeys (laboratorio 13).

### 5.A.7 `ana` genera sus códigos de recuperación

En la misma página **Account security → Signing in**:

1. Sección **Recovery codes** → **Generate**.
2. Keycloak muestra **12 códigos**. Cópialos, descárgalos o imprímelos.
3. Marca **I have saved these codes somewhere safe** y confirma.

**Se muestran una sola vez.** Si los pierdes, no se recuperan: hay que
generar un juego nuevo, lo que invalida el anterior.

## 6.A Cambios en la aplicación

**Ninguno.** La aplicación no se entera de cómo se ha autenticado el
usuario, y esa es exactamente la ventaja.

`aplicacion-base` recibe el mismo ID Token de siempre, firmado por Keycloak
y emitido **solo** cuando el usuario ha superado el flujo completo. Que ese
flujo haya sido una passkey, o una contraseña más un OTP, o una contraseña
más un código de recuperación, es una decisión del servidor de identidad.
Es el mismo argumento del laboratorio 09, llevado más lejos: allí añadimos
un factor sin tocar código; aquí **quitamos la contraseña** sin tocar
código.

Si quieres comprobar que el ID Token es idéntico, la sección 7.A.3 lo hace
con el evento `LOGIN` y con **Client scopes → Evaluate**.

**No hace falta ni reiniciarla.** Si la dejaste corriendo desde 4.A.0, sigue
valiendo: todos los cambios de este laboratorio son del lado de Keycloak, y
la aplicación los ve en el siguiente inicio de sesión. Si la paraste, vuelve
a arrancarla con el bloque de 4.A.0.

## 7.A Ejercicio DESPUÉS: entrar sin contraseña

### 7.A.1 `ana` entra solo con su usuario

1. Ventana normal de Edge o Chrome → <http://localhost:8081> → **Clientes**.
2. **Escribe solo `ana`** y pulsa **Sign in**.

**Resultado esperado.** Windows Hello pide el PIN o la huella y `ana` entra
directamente en `/privada`. **No hay pantalla de contraseña ni de código
OTP.** La cabecera muestra `Sesión: ana [gestor-clientes]`.

Fíjate también en la primera pantalla: el campo de usuario ofrece la passkey
por autocompletado (Conditional UI) y hay un botón **Sign in with Passkey**
(Modal UI). Salida real de los campos que trae ese formulario:

```
name="authenticatorData"  name="clientDataJSON"  name="credentialId"
name="error"  name="login"  name="signature"  name="userHandle"
name="username"  name="viewport"
```

Los seis campos de WebAuthn conviven con el de usuario: eso es la
integración de passkeys de 2.A.6.

### 7.A.2 La credencial registrada

**Users → ana → Credentials**. Salida real por API:

```
password
password-history
webauthn-passwordless    Passkey (keycloak test Label)
otp                      samsung
recovery-authn-codes     Recovery codes
```

Y el detalle de la credencial WebAuthn:

```json
{
  "type": "webauthn-passwordless",
  "userLabel": "Passkey (keycloak test Label)",
  "credentialData": {
    "aaguid": "08987058-cadc-4b81-b6e1-30de50dcbe96",
    "attestationStatementFormat": "none",
    "transports": ["internal"],
    "counter": 0
  }
}
```

**Resultado esperado.** Tres cosas que leer:

- El **AAGUID `08987058-cadc-4b81-b6e1-30de50dcbe96`** identifica el tipo de
  autenticador: es el de **Windows Hello**. Si hubieras usado una YubiKey,
  sería otro. Es el identificador que se pondría en *Acceptable AAGUIDs*
  para admitir solo ciertos modelos, y la guía avisa de que para fiarse de
  él hace falta `attestation = direct`.
- `attestationStatementFormat: none` confirma la política de 5.A.1.
- `transports: ["internal"]`: autenticador de plataforma, la clave vive en
  ese portátil.

### 7.A.3 El evento LOGIN dice **cómo** entró

**Events → User events**, o por API. Evento real del inicio de sesión con
passkey:

```json
{
  "type": "LOGIN",
  "clientId": "account-console",
  "userId": "b7182d02-65e7-4a19-a314-c6f1b50436cb",
  "sessionId": "e-AjkgM5342vEomvHFUIhfmI",
  "ipAddress": "172.18.0.1",
  "details": {
    "credential_type": "webauthn-passwordless",
    "public_key_credential_aaguid": "08987058-cadc-4b81-b6e1-30de50dcbe96",
    "public_key_credential_label": "Passkey (keycloak test Label)",
    "public_key_credential_id": "-9WHt1uAmsUXiGqQ5UJ1…",
    "auth_method": "openid-connect",
    "username": "ana"
  }
}
```

**Resultado esperado.** El campo **`credential_type`** es el que importa:
dice con qué se autenticó de verdad. Compara los eventos de una sesión de
enrolamiento completa (salida real):

```
2026-09-19T00:58:00Z   LOGIN              recovery-authn-codes
2026-09-19T00:58:00Z   UPDATE_CREDENTIAL  recovery-authn-codes
2026-09-19T00:57:05Z   LOGIN              otp
2026-09-19T00:57:05Z   UPDATE_TOTP        otp
2026-09-19T00:53:09Z   LOGIN              webauthn-passwordless
```

Esto es auditoría del laboratorio 12 haciendo un trabajo nuevo: **responder
a "¿con qué factor entró esta persona?"**, que es justo lo que un auditor de
PCI DSS 8.4 pregunta.

### 7.A.4 `luis`, sin passkey, sigue igual

1. Cierra sesión. **Clientes** → escribe `luis` → **Sign in**.

**Resultado esperado.** No hay passkey para `luis`, así que la pantalla
siguiente pide su **contraseña**. Entra con `luis123`, y como no tiene el
rol `gestor-clientes`, recibe **Acceso denegado** (laboratorio 06).

La única diferencia respecto al laboratorio 12 es **cosmética**: usuario y
contraseña ahora están en dos pantallas en lugar de una. Ningún usuario ha
perdido el acceso.

### 7.A.5 "Perdí el dispositivo": entrar con un código de recuperación

1. Cierra sesión. **Clientes** → escribe `ana` → **Sign in**.
2. Cuando el navegador ofrezca la passkey, **descártala** y pulsa **Try
   another way**.

**Resultado esperado.** Keycloak ofrece las alternativas que tiene
configuradas para `ana`. Salida real de las opciones presentes en esa
pantalla:

```
password
authenticator application
recovery authentication code
```

3. Elige **Password**, escribe `Andina*Segura2026` y continúa.
4. En la pantalla del segundo factor, pulsa **Try another way** otra vez y
   elige **Recovery Authentication Code**.

**Resultado esperado.** La pantalla pide un código **concreto y en orden**:

```
Recovery code #1
```

5. Escribe el primero de los 12 códigos que guardaste en 5.A.7.

**Resultado esperado.** `ana` entra. El evento queda como
`LOGIN credential_type=recovery-authn-codes`. Ese código **ya no vale**: el
próximo login pedirá el #2.

### 7.A.6 Revocar

En **Account security → Signing in**, `ana` puede borrar su passkey (icono
de papelera) y regenerar los códigos. Un administrador hace lo mismo desde
**Users → ana → Credentials**.

Compruébalo: borra la passkey, vuelve a entrar y verás que la primera
pantalla ya no la ofrece y que el flujo cae en la vía de contraseña. Vuelve
a registrarla para seguir con el laboratorio.

### 7.A.7 ✅ Comprobación de no regresión (parte A)

Antes de pasar a la parte B, confirma las dos vías:

| Quién | Cómo entra | Resultado esperado |
|---|---|---|
| `ana` | solo usuario + Windows Hello | entra en `/privada`, sin contraseña ni OTP |
| `ana` | *Try another way* → contraseña + OTP | entra en `/privada` |
| `luis` | usuario + contraseña | entra y recibe *Acceso denegado* (lab 06) |

Verificación real por `curl` de la vía clásica de `luis`, contra el flujo
nuevo ya vinculado:

```
pantalla 1 pide solo usuario: SI
boton Sign in with Passkey: SI
luis entra: SI
```

Y de que `ana` **sigue necesitando el OTP** por la vía de contraseña:

```
tras la contrasena, pide OTP: SI
entro sin OTP (code=): no (correcto)
```

---

# PARTE B · Autorización fina con Authorization Services (20 min)

## 2.B Conceptos

### 2.B.1 Dónde se queda corto el RBAC del laboratorio 06

En el laboratorio 06, `/privada` exige el rol `gestor-clientes`. La regla es
binaria: o tienes el rol y lo ves todo, o no lo tienes y no ves nada.

Ahora la Cooperativa tiene dos carteras comerciales, **norte** y **sur**, y
`ana` solo debería ver la suya. Con roles, las salidas son malas:

- **Un rol por cartera** (`gestor-norte`, `gestor-sur`). Funciona con dos.
  Con veinte sucursales y tres niveles de acceso son sesenta roles, y cada
  alta de sucursal toca Keycloak y el código.
- **Comprobarlo en la aplicación.** Se puede, pero entonces la regla de
  negocio vive repartida entre Keycloak y el código, y cada aplicación la
  reimplementa a su manera.

**Authorization Services** ofrece la tercera vía: expresar la regla como
datos, en un sitio, combinando lo que quieras.

### 2.B.2 Las cinco piezas

| Pieza | Qué es | En este laboratorio |
|---|---|---|
| **Resource server** | El cliente que protege recursos | `api-clientes` |
| **Scope** | Una acción sobre un recurso | `ver` |
| **Resource** | Lo que se protege | `cartera-norte`, `cartera-sur` |
| **Policy** | Una condición reutilizable. **No** concede nada por sí sola | `es gestor`, `cartera norte`, `cartera sur` |
| **Permission** | Une recurso + scope + políticas, con una estrategia de decisión | `ver cartera-norte`, `ver cartera-sur` |

La separación entre *policy* y *permission* es el punto que cuesta al
principio, y es lo que da la flexibilidad: una política se escribe una vez y
se reutiliza en muchos permisos.

### 2.B.3 Estrategias de decisión

Un permiso con varias políticas necesita saber cómo combinarlas:

| Estrategia | Concede si… |
|---|---|
| **Unanimous** (por defecto) | **todas** las políticas dicen que sí |
| **Affirmative** | **al menos una** dice que sí |
| **Consensus** | hay más síes que noes; el empate deniega |

Usamos **Unanimous**: hay que ser gestor **y** tener la cartera correcta.
Con *Affirmative*, cualquier gestor vería las dos carteras, que es
precisamente lo que queremos evitar.

### 2.B.4 La política Regex y de dónde salen los atributos

Elegimos una **Regex policy** porque la guía dice exactamente lo que
necesitamos:

> *This policy resolves attributes available from the current identity.*

Y sobre esa identidad:

> *The Identity is built based on the OAuth2 Access Token […] if you are
> using a Protocol Mapper to include a custom claim in an OAuth2 Access
> Token you can also access this claim from a policy.*

De ahí salen **dos requisitos** que es fácil pasar por alto y que son la
causa de la mitad de los "no me funciona":

1. El atributo `cartera` tiene que **existir** en el usuario. En 26.7.3 los
   *unmanaged attributes* están desactivados por defecto, así que hay que
   **declararlo en el User profile** antes de poder asignarlo.
2. El atributo tiene que **llegar al token** como claim, mediante un
   *protocol mapper*.

### 2.B.5 Por qué un cliente aparte y no `aplicacion-base`

Podríamos activar Authorization en `aplicacion-base`. **No lo hacemos**, y
por una razón que conviene entender:

- Activar Authorization convierte al cliente en *resource server* y obliga a
  habilitarle **Service accounts roles**, lo que le crea una cuenta de
  servicio. `aplicacion-base` es un cliente con navegador y usuario; no
  necesita ninguna.
- Sobre todo: así **garantizamos que la parte B no puede romper el login**.
  `aplicacion-base` sale de este laboratorio tal como entró.

Siguiendo el mismo patrón del laboratorio 11 con `api-conciliacion`, el
*resource server* es un cliente **sin ningún flujo habilitado**, tal como lo
describe la guía: *"a service client is typically a client without any
flows enabled […] it represents an OAuth 2 Resource Server"*.

### 2.B.6 Cómo se integraría de verdad, y por qué no lo hacemos aquí

Keycloak evalúa; alguien tiene que **preguntar y obedecer**. Hay dos vías:

1. **Pedir un RPT con el grant UMA.** La aplicación pide al *token
   endpoint* un *Requesting Party Token* con
   `grant_type=urn:ietf:params:oauth:grant-type:uma-ticket`, y lee el claim
   `authorization.permissions`. Es estándar OAuth 2 y se puede hacer desde
   Spring sin dependencias de Keycloak. **Es la vía que usaríamos**, y la
   demostramos con `curl` en 7.B.3.
2. **El *policy enforcer*.** Una librería que intercepta cada petición y
   consulta a Keycloak. Es cómoda, pero es `org.keycloak.*`: **prohibida por
   la regla de oro del curso** (`securing-apps/overview` deja los
   adaptadores como último recurso).

No integramos la primera en la aplicación porque este laboratorio es una
demostración de 20 minutos y porque hacerlo bien implica caché de
decisiones, renovación del RPT y manejo de errores: un laboratorio entero.
Lo que sí hacemos es **ver el RPT real** y su claim.

## 3.B Relación con OWASP, ASVS y PCI DSS

- **OWASP A01:2021 Pérdida de control de acceso.** La categoría describe
  exactamente el ANTES: permisos demasiado amplios, y comprobaciones que no
  distinguen entre registros del mismo tipo.
- **OWASP ASVS V4.1 (diseño del control de acceso).** Pide que las reglas se
  apliquen en servidores de confianza y que exista una política
  centralizada, no comprobaciones dispersas. Un *resource server* con
  políticas declarativas es la forma canónica.
- **PCI DSS 7.2.1.** Exige un modelo de control de acceso que conceda según
  la **necesidad de conocer** y la función laboral. "Gestor de la cartera
  norte" es necesidad de conocer; "gestor" a secas, no.
- **PCI DSS 7.2.2.** Asignar privilegios según el puesto y **el mínimo
  necesario**.

## 4.B Ejercicio ANTES: el rol no sabe de carteras

**Propósito.** Comprobar que con lo que hay en el laboratorio 12 no se puede
expresar la regla.

1. Admin Console → **Clients** → `aplicacion-base` → busca una pestaña
   **Authorization**.

**Resultado esperado.** **No existe.** Sin Authorization Services no hay
recursos, ni políticas, ni nada que evaluar: la única pregunta que el realm
sabe responder es "¿tiene el rol?".

2. **Users** → `ana` → **Role mapping**, y lo mismo con `luis`.

**Resultado esperado.**

| | `ana` | `luis` |
|---|---|---|
| `gestor-clientes` | sí (heredado del grupo `operaciones`) | no |
| ¿Cartera norte o sur? | **no se puede expresar** | **no se puede expresar** |

3. **Users → ana → Attributes**.

**Resultado esperado.** No hay ningún atributo, y si intentas añadir
`cartera` la consola no te deja guardarlo: el **User profile** del realm
solo declara `username`, `email`, `firstName` y `lastName`, y los atributos
no declarados están desactivados (2.B.4).

La conclusión: para `ana` y `luis`, el realm solo tiene una palanca, el rol,
y está o no está.

## 5.B Configuración de Keycloak

### 5.B.1 Declarar el atributo `cartera`

1. **Realm settings** → pestaña **User profile** → **Create attribute**.
2. *Name*: `cartera`. *Display name*: `Cartera comercial`.
3. *Permissions*: **Who can edit?** solo `admin`. **Who can view?** `admin`
   y `user`.
4. Deja *Required field* desactivado. **Create**.

> Que el usuario **no pueda editarlo** es deliberado: un atributo que decide
> permisos no puede estar en manos de quien se ve afectado por ellos.
> Si el alumno lo pudiera editar desde su Account Console, se asignaría la
> cartera que quisiera.

### 5.B.2 Dar la cartera a `ana`

1. **Users** → `ana` → pestaña **Attributes** (ahora aparece `cartera`).
2. Valor: `norte`. **Save**.

A `luis` no le pongas nada.

### 5.B.3 El cliente `api-clientes`

1. **Clients** → **Create client**. *Client ID*: `api-clientes`. *Name*:
   `API de clientes (resource server del laboratorio 14)`. **Next**.
2. *Capability config*:
   - **Client authentication**: **On**
   - **desmarca Standard flow**, *Direct access grants* y todo lo demás
   - activa **Authorization** ← esto es lo que lo convierte en resource
     server
   - Verás que **Service accounts roles** se activa solo y no se puede
     desmarcar: Authorization lo exige.
   - **Next** → **Save**.

Aparece la pestaña **Authorization**. Sus subpestañas son las de 2.B.2, más
*Settings*, *Evaluate* y *Export*.

> En **Authorization → Settings** verás *Policy Enforcement Mode:
> Enforcing* y *Decision Strategy: Unanimous*. Déjalos. *Enforcing*
> significa, según la guía, que *"requests are denied by default even when
> there is no policy associated with a given resource"*: denegar por
> defecto es lo correcto.
>
> En 26.7.3 la pestaña Authorization **nace vacía**: no se crean
> "Default Resource" ni "Default Policy". Si tu versión te los crea,
> bórralos antes de seguir para que las pruebas coincidan.

### 5.B.4 El mapper que lleva `cartera` al token

1. `api-clientes` → pestaña **Client scopes** → enlace
   **`api-clientes-dedicated`**.
2. **Add mapper → By configuration → User Attribute**.
3. Configura:
   - *Name*: `cartera`
   - *User Attribute*: `cartera`
   - *Token Claim Name*: `cartera`
   - *Claim JSON Type*: `String`
   - **Add to ID token**: **Off**
   - **Add to access token**: **On**
   - **Add to userinfo**: Off
   - *Add to token introspection*: On
4. **Save**.

> **Por qué solo al access token.** Las políticas leen la identidad del
> *access token* (2.B.4), así que ahí hace falta. En el ID Token no pinta
> nada: es información de autorización, no de identidad.

### 5.B.5 El scope, los recursos, las políticas y los permisos

Todo en la pestaña **Authorization** de `api-clientes`.

**Scope** (subpestaña *Authorization Scopes* → **Create authorization
scope**):

- *Name*: `ver`. *Display name*: `Ver la cartera`. **Save**.

**Recursos** (subpestaña *Resources* → **Create resource**), dos veces:

| Name | Display name | Type | Authorization scopes |
|---|---|---|---|
| `cartera-norte` | `Cartera comercial del norte` | `urn:api-clientes:cartera` | `ver` |
| `cartera-sur` | `Cartera comercial del sur` | `urn:api-clientes:cartera` | `ver` |

**Políticas** (subpestaña *Policies* → **Create policy**):

1. Tipo **Role**:
   - *Name*: `es gestor`
   - *Description*: `El solicitante tiene el rol de realm gestor-clientes (laboratorio 06)`
   - *Realm roles*: añade **`gestor-clientes`** y marca **Required**
   - **Save**
2. Tipo **Regex**:
   - *Name*: `cartera norte`
   - *Target Claim*: `cartera`
   - *Regex Pattern*: `^norte$`
   - *Logic*: `Positive`
   - **Save**
3. Igual, `cartera sur` con patrón `^sur$`.

> Los anclas `^` y `$` no son adorno: sin ellos, `norte` también casaría
> con un valor como `norte-y-sur`.

**Permisos** (subpestaña *Permissions* → **Create permission** → **Create
scope-based permission**):

| Name | Resource | Authorization scopes | Policies | Decision strategy |
|---|---|---|---|---|
| `ver cartera-norte` | `cartera-norte` | `ver` | `es gestor`, `cartera norte` | **Unanimous** |
| `ver cartera-sur` | `cartera-sur` | `ver` | `es gestor`, `cartera sur` | **Unanimous** |

### 5.B.6 El scope mapping que hace falta (y que casi todo el mundo olvida)

Si vas ahora a *Evaluate*, `ana` te dará **DENY** en las dos carteras, y la
política `es gestor` saldrá en `DENY` aunque `ana` tenga el rol.

No es un error de la política. Es la **regla de la intersección** del
laboratorio 07: `api-clientes` tiene *Full scope allowed* **OFF** y ningún
scope mapping, así que el token que Keycloak genera para evaluar solo lleva
`default-roles-curso`. El rol existe en el usuario pero **no llega al
token**, y la política solo ve el token.

1. `api-clientes` → **Client scopes** → **`api-clientes-dedicated`** →
   pestaña **Scope**.
2. *Full scope allowed* sigue **Off**.
3. **Assign role** → *Filter by realm roles* → marca **`gestor-clientes`**
   → **Assign**.

Es exactamente lo que hiciste en 5.4 del laboratorio 11, y por el mismo
motivo.

## 6.B Cambios en la aplicación

**Ninguno, y esta vez es el argumento central de la parte B.**

Toda la regla —"gestor **y** cartera norte"— vive en Keycloak como datos.
No hay un `if` en Java que la exprese, ni un despliegue que la cambie.
Cambiar quién ve qué es cambiar un atributo.

Lo que **sí** haría falta para que la aplicación obedeciera es lo de 2.B.6:
pedir un RPT con el grant UMA y leer `authorization.permissions`, o el
*policy enforcer*, que está prohibido. Queda como trabajo posterior al
curso, y 7.B.3 muestra exactamente qué devolvería esa llamada.

Al final de esta parte, en 7.B.5, se comprueba que el ID Token de `ana`
**no ha cambiado** ni un claim.

## 7.B Ejercicio DESPUÉS

### 7.B.1 Evaluate: la misma pregunta, dos respuestas

1. `api-clientes` → **Authorization** → pestaña **Evaluate**.
2. En *Identity Information*, *User*: `ana`.
3. En *Permissions*, añade los recursos `cartera-norte` y `cartera-sur`.
4. **Evaluate**.

**Resultado esperado** (salida real):

```
ana    cartera-norte  PERMIT   es gestor=PERMIT / cartera norte=PERMIT
ana    cartera-sur    DENY     es gestor=PERMIT / cartera sur=DENY
```

5. Repite con `luis`:

```
luis   cartera-norte  DENY     es gestor=DENY   / cartera norte=DENY
luis   cartera-sur    DENY     es gestor=DENY   / cartera sur=DENY
```

**Lee el desglose, que es lo valioso.** A `ana` le falla *solo* la política
de cartera cuando pregunta por el sur; a `luis` le fallan las dos. El motivo
de cada denegación está explícito. Eso es lo que no se puede obtener de un
`hasRole`.

### 7.B.2 Cambiar el permiso sin tocar un solo rol

1. **Users → ana → Attributes**: cambia `cartera` de `norte` a `sur`.
   **Save**.
2. Vuelve a **Evaluate**.

**Resultado esperado** (salida real):

```
=== ana con cartera=norte ===
cartera-norte   PERMIT
cartera-sur     DENY

=== se cambia SOLO el atributo: cartera=sur ===
cartera-norte   DENY
cartera-sur     PERMIT

=== se restaura cartera=norte ===
cartera-norte   PERMIT
cartera-sur     DENY
```

Y los roles de `ana`, sin tocar en ningún momento:

```json
["default-roles-curso","gestor-clientes","uma_authorization","offline_access"]
```

**Restaura `cartera` a `norte`** antes de seguir.

### 7.B.3 El grant UMA: pedir un RPT con `curl`

Ahora la pregunta desde fuera. Necesitamos un access token; el de `ana` solo
puede nacer en la aplicación (su flujo es Authorization Code con navegador,
y *Direct access grants* está desactivado desde el laboratorio 04, con
razón). Así que usamos el del proceso del laboratorio 11, que **sí** se
obtiene por `curl`, para ver cómo responde el servidor cuando **deniega**:

```bash
TOKEN_URL=http://localhost:8080/realms/curso/protocol/openid-connect/token
AT=$(curl -s -X POST "$TOKEN_URL" -d grant_type=client_credentials \
      -d client_id=servicio-conciliacion -d "client_secret=$SECRETO_CONCILIACION" | jq -r .access_token)

curl -s -X POST "$TOKEN_URL" -H "Authorization: Bearer $AT" \
  -d "grant_type=urn:ietf:params:oauth:grant-type:uma-ticket" \
  -d "audience=api-clientes" \
  -d "permission=cartera-norte#ver"
```

Salida real:

```json
{"error":"access_denied","error_description":"not_authorized"}
```

con **HTTP 403**. Y con `response_mode=decision`, que según la guía devuelve
solo la decisión global, lo mismo.

Es correcto: ese token es de una cuenta de servicio que no es gestor ni
tiene cartera. **Las tres piezas de la petición**, por si las quieres
reutilizar:

- `audience`: el *resource server* al que se pregunta. La guía lo marca como
  obligatorio en cuanto usas `permission`.
- `permission`: el formato es `RECURSO#SCOPE`.
- El **access token va como `Authorization: Bearer`**: es la identidad cuyos
  permisos se evalúan.

### 7.B.4 Ver el claim `authorization.permissions` en un PERMIT

Para ver el token que devuelve un PERMIT, concedemos **temporalmente** a esa
cuenta de servicio lo que le falta, miramos, y lo deshacemos. Es un rodeo
didáctico: en un sistema real, quien pide el RPT es la aplicación en nombre
de `ana`.

Con el rol `gestor-clientes` y el atributo `cartera=norte` puestos a la
cuenta de servicio, su token pasa a ser:

```json
{"azp":"servicio-conciliacion","cartera":"norte",
 "realm_access":{"roles":["gestor-clientes","lector-conciliacion"]}}
```

Y la misma petición de RPT devuelve **HTTP 200** con un token cuyo payload
contiene:

```json
{
  "authorization": {
    "permissions": [
      {
        "scopes": ["ver"],
        "rsid": "d58664fc-88b2-4345-9dd2-8adf3e58c4d8",
        "rsname": "cartera-norte"
      }
    ]
  },
  "aud": "api-clientes",
  "azp": "servicio-conciliacion"
}
```

**Eso es lo que leería la aplicación**: no "tiene el rol X", sino **"puede
hacer `ver` sobre `cartera-norte`"**. La decisión ya está tomada; la
aplicación solo la aplica.

Y la granularidad se mantiene: con el mismo token, pidiendo `cartera-sur`:

```json
{"error":"access_denied","error_description":"not_authorized"}
```

**Deshaz la concesión temporal** (quita el rol, el atributo, el mapper y el
scope mapping añadidos). Comparación real del token antes y después del
revert:

```
antes:   {"azp":"servicio-conciliacion","cartera":null,"realm_access":{"roles":["lector-conciliacion"]},...}
despues: {"azp":"servicio-conciliacion","cartera":null,"realm_access":{"roles":["lector-conciliacion"]},...}
IDENTICOS: el revert ha dejado el token exactamente como estaba.
```

y el RPT vuelve a denegarse con 403.

### 7.B.5 ✅ Comprobación de no regresión (parte B)

`aplicacion-base` **no se ha tocado**. Compruébalo:

1. **Clients → aplicacion-base → Settings**: no hay interruptor
   *Authorization* activado y no existe la pestaña **Authorization**.
2. **Clients → aplicacion-base → Client scopes → Evaluate**: elige el
   usuario `ana` y mira **Generated ID Token**.

**Resultado esperado.** El mismo ID Token del laboratorio 07:
`realm_access.roles` con `gestor-clientes`, **sin `email`** y **sin**
ningún claim `cartera` (el mapper vive en `api-clientes`, no aquí).

3. Entra en la aplicación como `ana` y como `luis`.

**Resultado esperado.** `ana` ve la tabla de clientes; `luis` recibe *Acceso
denegado*. Igual que en el laboratorio 12.

---

# PARTE C · Acceso privilegiado con aprobación (15 min)

## 2.C Conceptos

### 2.C.1 Keycloak no aprueba: ejecuta

Conviene decirlo sin rodeos: **Keycloak no incluye un flujo de aprobación
humana**. No hay una pantalla donde un supervisor acepte "dale a Luis acceso
de administración durante cuatro horas".

Y está bien que no la haya. La aprobación es un **proceso de la
organización**: tiene un solicitante, un motivo, un aprobador, un plazo y
un registro. Eso vive en la herramienta que ya se use (el gestor de
tickets, el ITSM, el flujo de RRHH). Lo que Keycloak aporta es la otra
mitad: **ejecutar el resultado y dejar constancia**.

El patrón se llama **acceso *just-in-time***:

```
   Herramienta de la organización          Keycloak
   ------------------------------          --------
   solicitud  →  aprobación      ──────▶   PUT  /users/{id}/groups/{grupo}
   (quién, por qué, cuánto)                 → admin event: quién ejecutó, cuándo
                                            → el usuario gana el rol
   caducidad del ticket          ──────▶   DELETE /users/{id}/groups/{grupo}
                                            → admin event
                                            → el usuario lo pierde
```

La trazabilidad queda repartida a propósito: **por qué** se concedió, en la
herramienta; **qué** se ejecutó y **cuándo**, en los eventos de
administración del laboratorio 12.

### 2.C.2 Las alternativas, y por qué no las usamos

- **Solicitudes de permiso UMA.** Authorization Services permite que un
  usuario pida acceso a un recurso de **otro usuario**, y que el dueño
  apruebe o rechace desde su Account Console (**My Resources**). Es una
  aprobación humana de verdad, pero sirve para recursos con dueño (mis
  documentos, mis fotos), no para conceder roles de administración. Se
  queda fuera por ámbito, no por calidad.
- **Un *authenticator* propio por SPI.** Se podría programar un paso de
  autenticación que consulte a un aprobador. Implica escribir un *provider*
  Java con clases `org.keycloak.*` y desplegarlo en el servidor:
  **prohibido por la regla del curso**.

### 2.C.3 Mínimo privilegio: `manage-users`, no `realm-admin`

Cada realm tiene un cliente interno llamado **`realm-management`** con los
roles de administración. Entre ellos están `manage-users`, `view-users`,
`manage-clients`… y **`realm-admin`**.

`realm-admin` es un **rol compuesto que contiene todos los demás**. Dárselo
a nuestro servicio significaría que un secreto filtrado permite crear
clientes, cambiar flujos de autenticación, **borrar los eventos de
auditoría** y modificar la propia política de contraseñas. La guía avisa:

> *Always make sure to review the users granted with the admin or
> realm-admin roles to avoid any potential privilege escalation.*

Nuestro servicio solo tiene que meter y sacar usuarios de un grupo, así que
recibe **únicamente `manage-users`**.

Y aun así, **`manage-users` no es poca cosa**: permite crear usuarios,
cambiar contraseñas y modificar atributos de cualquier usuario del realm.
Es el rol más pequeño que hace el trabajo, no un rol inofensivo. En
producción, lo que sigue a esto es acotarlo todavía más con permisos
administrativos de grano fino, y rotar el secreto.

### 2.C.4 El scope mapping también aquí

Como en el laboratorio 11 y en 5.B.6: además de darle el rol a la **cuenta
de servicio**, hay que incluirlo en el **scope del cliente**, porque el
token lleva la intersección de ambos. Con *Full scope allowed* en Off —que
es lo correcto— sin scope mapping el token saldría sin `manage-users` y la
API respondería 403.

## 3.C Relación con OWASP, ASVS y PCI DSS

- **OWASP A01:2021 Pérdida de control de acceso.** Incluye la elevación de
  privilegios y los permisos que se conceden y nunca se revisan.
- **OWASP ASVS V4.1.** Control de acceso aplicado en servidor de confianza,
  con el **mínimo privilegio** por defecto.
- **PCI DSS 7.2.4.** Exige **revisar los accesos de usuario al menos cada
  seis meses** y retirar los que no correspondan. Un acceso que **caduca
  solo** no llega a esa revisión con un privilegio olvidado.
- **PCI DSS 7.2.5.** Las cuentas de aplicación y de sistema deben tener
  **solo los privilegios necesarios**. Es literalmente 2.C.3.
- **PCI DSS 10.2.1.2.** Registrar **todas** las acciones de los usuarios con
  acceso administrativo. Es lo que hacen los *admin events*.

## 4.C Ejercicio ANTES: a mano, con la cuenta de todos

**Propósito.** Ver cómo se concede hoy un acceso de administración.

1. Admin Console, entrando con **`admin` / `admin`** del realm `master`.
2. **Groups** → crea el grupo, mete a `luis` a mano.
3. **Events → Admin events** y mira el registro.

**Resultado esperado.** El evento existe y dice que el usuario `admin`, con
el cliente `security-admin-console` y desde `172.18.0.1`, añadió a `luis` a
un grupo.

Y ahora las preguntas que el evento **no** responde:

| Pregunta | ¿Está en el evento? |
|---|---|
| ¿Quién lo ejecutó? | Sí: `admin` |
| ¿**Quién era esa persona**? | **No.** `admin`/`admin` es una cuenta compartida |
| ¿Quién lo **aprobó**? | **No** |
| ¿Por qué? ¿Con qué ticket? | **No** |
| ¿Hasta cuándo? | **No. Es permanente** |
| ¿Quién se acordará de quitarlo? | **Nadie** |

Ese último punto es el que incumple PCI DSS 7.2.4, y es el que se acumula:
dentro de un año, `luis` seguirá siendo administrador de la aplicación
porque una tarde hizo falta.

**Saca a `luis` del grupo** antes de seguir.

## 5.C Configuración de Keycloak

### 5.C.1 El rol y el grupo

1. **Realm roles** → **Create role**:
   - *Role name*: `administrador-app`
   - *Description*: `Administra la aplicacion. Se concede de forma temporal y aprobada (laboratorio 14).`
   - **Save**
2. **Groups** → **Create group** → *Name*: `administracion` → **Create**.
3. Entra en el grupo → **Role mapping** → **Assign role** → *Filter by realm
   roles* → marca `administrador-app` → **Assign**.

> `administrador-app` **no se usa en la aplicación**. Si quisieras usarlo,
> sería una línea en `SeguridadConfig`, exactamente como el laboratorio 06
> hizo con `gestor-clientes`:
> `.requestMatchers("/admin/**").hasRole("administrador-app")`.
> Aquí solo nos interesa que aparezca y desaparezca del token.

### 5.C.2 El cliente `aprobador-accesos`

1. **Clients** → **Create client**. *Client ID*: `aprobador-accesos`.
   *Name*: `Ejecutor de accesos aprobados`. **Next**.
2. *Capability config*, igual que en el laboratorio 11:
   - **Client authentication**: **On**
   - **desmarca Standard flow** y *Direct access grants*
   - **marca Service accounts roles**
   - **Next** → *Login settings* vacío → **Save**.
3. Pestaña **Credentials** → copia el secreto:
   ```bash
   export SECRETO_APROBADOR='pega-aquí-el-secreto'
   ```

### 5.C.3 El rol `manage-users`, en los dos sitios

**A la cuenta de servicio:**

1. `aprobador-accesos` → pestaña **Service accounts roles**.
2. **Assign role** → cambia el filtro a **Filter by clients** → busca
   `manage-users` del cliente **`realm-management`** → **Assign**.

**Al scope del cliente** (2.C.4):

3. `aprobador-accesos` → **Client scopes** →
   **`aprobador-accesos-dedicated`** → pestaña **Scope**.
4. *Full scope allowed* debe estar **Off**.
5. **Assign role** → **Filter by clients** → `manage-users` de
   `realm-management` → **Assign**.

**Comprobación** (salida real):

```
service account roles (realm-management): ["manage-users"]
scope mappings   (realm-management): ["manage-users"]
fullScopeAllowed: false
```

### 5.C.4 Que el rol nuevo llegue al token de la aplicación

`aplicacion-base` tiene *Full scope allowed* **Off** desde el laboratorio 07
y solo `gestor-clientes` en su scope. Si no añadimos el rol nuevo, `luis`
podrá estar en el grupo y **el token no lo reflejará**.

1. **Clients** → `aplicacion-base` → **Client scopes** →
   **`aplicacion-base-dedicated`** → pestaña **Scope**.
2. **Assign role** → *Filter by realm roles* → marca `administrador-app` →
   **Assign**.

Debe quedar así:

```
scope de aplicacion-base: ["gestor-clientes","administrador-app"]
```

> Este es el **único cambio del laboratorio 14 sobre `aplicacion-base`**, es
> un scope mapping, y no afecta a `ana` (que no tiene ese rol) ni al login
> de nadie. Si te lo saltas, 7.C.4 no funcionará y el motivo será este.

## 6.C Cambios en la aplicación

**Ninguno.** La aplicación ya sabe hacer su parte desde el laboratorio 06: el
`GrantedAuthoritiesMapper` de `SeguridadConfig` lee `realm_access.roles` del
ID Token y crea una autoridad `ROLE_*` por cada rol. Si aparece un rol
nuevo, la cabecera lo muestra sin que nadie toque nada.

Y aquí se ve, por fin, la consecuencia práctica de una decisión del
laboratorio 06: los roles se leen del **ID Token emitido en el login**. Por
eso, cuando el servicio retira el acceso, `luis` **no lo pierde al
instante**: lo pierde en su siguiente inicio de sesión. Es el compromiso
entre simplicidad y frescura que ya estaba documentado, y en 7.C.5 se ve.

## 7.C Ejercicio DESPUÉS

Todo con `curl`, con `SECRETO_APROBADOR` exportado. Primero, los
identificadores:

```bash
KC=http://localhost:8080
TOKEN_ADMIN=$(curl -s -X POST $KC/realms/master/protocol/openid-connect/token \
  -d client_id=admin-cli -d username=admin -d password=admin -d grant_type=password | jq -r .access_token)
LUIS=$(curl -s -H "Authorization: Bearer $TOKEN_ADMIN" \
  "$KC/admin/realms/curso/users?username=luis&exact=true" | jq -r '.[0].id')
GRUPO=$(curl -s -H "Authorization: Bearer $TOKEN_ADMIN" \
  "$KC/admin/realms/curso/groups" | jq -r '.[] | select(.name=="administracion") | .id')
```

### 7.C.1 El token del servicio, y lo poco que puede

```bash
AT=$(curl -s -X POST $KC/realms/curso/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=aprobador-accesos \
  -d "client_secret=$SECRETO_APROBADOR" | jq -r .access_token)
```

Salida real de la respuesta y del payload:

```json
{"token_type":"Bearer","expires_in":60,"scope":"email profile"}
```

```json
{
  "azp": "aprobador-accesos",
  "preferred_username": "service-account-aprobador-accesos",
  "resource_access": { "realm-management": { "roles": ["manage-users"] } }
}
```

**Resultado esperado.** `resource_access` lleva **un solo rol**. Ni
`realm-admin`, ni `manage-clients`, ni `manage-events`. Eso es 2.C.3 hecho
dato, y vive **60 segundos** (laboratorio 05).

### 7.C.2 La ejecución de la aprobación

```bash
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X PUT \
  -H "Authorization: Bearer $AT" \
  -H "X-Ticket-Aprobacion: CAB-2026-0917-0042" \
  "$KC/admin/realms/curso/users/$LUIS/groups/$GRUPO"
```

Salida real:

```
HTTP 204
```

Y `luis`, antes y después:

```
ANTES   grupos: []                  roles: ["default-roles-curso","uma_authorization","offline_access"]
DESPUES grupos: ["administracion"]  roles: ["default-roles-curso","administrador-app","uha_authorization","offline_access"]
```

> **La cabecera del ticket es solo ilustrativa.** Keycloak **no la guarda**
> (se comprueba en 7.C.3). Está ahí para que veas dónde *querrías* que
> estuviera la referencia de la aprobación y para subrayar el reparto de
> 2.C.1: el "por qué" vive en la herramienta externa, el "qué" en Keycloak.
> Correlacionarlos es trabajo de quien monte el proceso: por marca de
> tiempo y por usuario afectado.

### 7.C.3 El evento de administración

```bash
curl -s -H "Authorization: Bearer $TOKEN_ADMIN" "$KC/admin/realms/curso/admin-events?max=1" | jq .
```

Salida real:

```
2026-09-18T23:39:41Z  CREATE  GROUP_MEMBERSHIP  users/03e6b8df-.../groups/9e9bc84f-...  0c4504c9-51d5-4c40-ba9c-e37c623a0d28
```

El `authDetails.clientId` es un identificador interno; se resuelve a nombre
con una consulta más:

```json
{"clientId":"aprobador-accesos","name":"Ejecutor de accesos aprobados"}
```

**Resultado esperado.** El evento dice **qué** (`CREATE` sobre
`GROUP_MEMBERSHIP`), **sobre quién** (la ruta lleva el id de `luis` y el del
grupo), **quién lo ejecutó** (`aprobador-accesos`, no una persona
compartiendo `admin`/`admin`) y **desde dónde**. Compáralo con la tabla de
4.C: la primera fila ya no dice "una cuenta compartida".

Y el evento completo confirma lo dicho sobre la cabecera:

```json
{
  "operationType": "DELETE",
  "resourceType": "GROUP_MEMBERSHIP",
  "resourcePath": "users/03e6b8df-.../groups/9e9bc84f-...",
  "authDetails": { "clientId": "0c4504c9-...", "userId": "f7c45cc3-...", "ipAddress": "172.18.0.1" },
  "representation": "{\"id\":\"9e9bc84f-...\",\"name\":\"administracion\",\"path\":\"/administracion\",\"realmRoles\":[\"administrador-app\"],\"clientRoles\":{}}"
}
```

No hay rastro de `X-Ticket-Aprobacion`: la `representation` es el grupo y
nada más.

### 7.C.4 `luis` vuelve a entrar y estrena rol

Con la aplicación corriendo (4.A.0), entra como `luis` / `luis123`.

**Resultado esperado.** La cabecera muestra ahora
`Sesión: luis [administrador-app]`. Sigue recibiendo *Acceso denegado* en
`/privada`, porque esa ruta exige `gestor-clientes` (laboratorio 06) y el
rol nuevo no se usa en ninguna regla; lo que ha cambiado es que **el token
lo lleva**.

Verificación real, con el ID Token que emite `aplicacion-base`:

```
ANTES de la aprobacion:   {"preferred_username":"luis","realm_access":null}
DESPUES de la aprobacion: {"preferred_username":"luis","realm_access":{"roles":["administrador-app"]}}
```

### 7.C.5 La caducidad

```bash
AT2=$(curl -s -X POST $KC/realms/curso/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=aprobador-accesos \
  -d "client_secret=$SECRETO_APROBADOR" | jq -r .access_token)

curl -s -o /dev/null -w "HTTP %{http_code}\n" -X DELETE \
  -H "Authorization: Bearer $AT2" \
  "$KC/admin/realms/curso/users/$LUIS/groups/$GRUPO"
```

Salida real:

```
HTTP 204
```

> El token se vuelve a pedir porque el anterior **ya ha caducado**: 60
> segundos (laboratorio 05). En un script largo hay que refrescarlo en cada
> llamada, y eso es una virtud, no una molestia: un secreto filtrado da
> tokens de un minuto.

El evento correspondiente aparece junto al anterior:

```
2026-09-18T23:39:42Z   DELETE   GROUP_MEMBERSHIP   users/03e6b8df-.../groups/9e9bc84f-...
2026-09-18T23:39:41Z   CREATE   GROUP_MEMBERSHIP   users/03e6b8df-.../groups/9e9bc84f-...
```

Un segundo entre la concesión y la retirada, las dos con autor. Eso es un
acceso con principio y fin, que es lo que pedía PCI DSS 7.2.4.

Y el ID Token de `luis` en su siguiente inicio de sesión:

```
{"preferred_username":"luis","realm_access":null}
```

### 7.C.6 Prueba negativa: el servicio equivocado no puede

```bash
AT3=$(curl -s -X POST $KC/realms/curso/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=servicio-conciliacion \
  -d "client_secret=$SECRETO_CONCILIACION" | jq -r .access_token)

curl -s -w "\nHTTP %{http_code}\n" -X PUT -H "Authorization: Bearer $AT3" \
  "$KC/admin/realms/curso/users/$LUIS/groups/$GRUPO"
```

Salida real:

```json
{"error":"HTTP 403 Forbidden"}
HTTP 403
```

Y el token que lo intentó:

```json
{"azp":"servicio-conciliacion","realm_access":{"roles":["lector-conciliacion"]},"resource_access":null}
```

**Resultado esperado.** `403`. El proceso nocturno del laboratorio 11 tiene
un token válido, firmado y sin caducar, y **aun así no puede** tocar
usuarios: `resource_access` es `null`, no tiene ningún rol de
`realm-management`. Un token válido no es un token todopoderoso; autenticar
no es autorizar.

### 7.C.7 ✅ Comprobación de no regresión (parte C)

| Quién | Cómo entra | Resultado esperado |
|---|---|---|
| `ana` | usuario + Windows Hello | entra en `/privada` |
| `ana` | contraseña + OTP | entra en `/privada` |
| `luis` | usuario + contraseña | entra y recibe *Acceso denegado* |

Y `luis` ya no está en `administracion`:

```
grupos de luis: []
roles de luis : ["default-roles-curso","uma_authorization","offline_access"]
```

## 8. Lista de verificación

**Parte A**

- [ ] **WebAuthn Passwordless Policy**: RP Entity Name `Cooperativa Andina`, RP ID vacío, `ES256`+`RS256`, attestation `none`, attachment *not specified*, **Discoverable Credential `required`**, **User Verification `required`**.
- [ ] **Required actions**: *Webauthn Register Passwordless* y *Recovery Authentication Codes* habilitadas, ninguna como acción por defecto.
- [ ] **Realm settings → Login**: *Enable Passkeys* On, *Passkey Mediation* `conditional`.
- [ ] El flujo `browser passwordless` tiene **exactamente** el árbol de 5.A.4.
- [ ] *Condition - credential* está configurado con `credentials = webauthn-passwordless`.
- [ ] El flujo está vinculado como **Browser flow** del realm, y el flujo `browser` original **sigue intacto**.
- [ ] `ana` tiene credenciales `password`, `webauthn-passwordless`, `otp` y `recovery-authn-codes`.
- [ ] `ana` entra escribiendo solo su usuario, sin contraseña ni OTP.
- [ ] El evento `LOGIN` de esa sesión trae `credential_type: webauthn-passwordless` y el AAGUID.
- [ ] `luis`, sin passkey, entra con contraseña y recibe *Acceso denegado*.
- [ ] Un código de recuperación permite entrar, se pide **en orden** y no sirve dos veces.
- [ ] La passkey y los códigos se pueden revocar desde la Account Console.

**Parte B**

- [ ] `cartera` está declarado en **Realm settings → User profile**, editable solo por `admin`.
- [ ] `ana` tiene `cartera = norte`; `luis` no tiene el atributo.
- [ ] `api-clientes` existe, **sin ningún flujo**, con *Authorization* activado.
- [ ] El mapper `cartera` está en `api-clientes-dedicated`, **solo en el access token**.
- [ ] Hay scope `ver`, recursos `cartera-norte` y `cartera-sur`, tres políticas y dos permisos **Unanimous**.
- [ ] `api-clientes` tiene `gestor-clientes` en su scope mapping (si no, todo da DENY).
- [ ] **Evaluate**: `ana` PERMIT norte / DENY sur; `luis` DENY en las dos.
- [ ] Cambiar `cartera` a `sur` invierte el resultado **sin tocar roles**, y restaurarlo lo devuelve.
- [ ] El grant UMA devuelve `403 access_denied / not_authorized` cuando no procede, y `authorization.permissions` cuando sí.
- [ ] **`aplicacion-base` no tiene pestaña Authorization** y su ID Token es el del laboratorio 07.

**Parte C**

- [ ] Existen el rol `administrador-app` y el grupo `administracion` con ese rol.
- [ ] `aprobador-accesos` es confidencial, **solo** con *Service accounts roles*.
- [ ] Su cuenta de servicio tiene **únicamente** `manage-users` de `realm-management`, y ese rol está **también** en su scope mapping, con *Full scope allowed* Off.
- [ ] `aplicacion-base` tiene `administrador-app` en su scope mapping.
- [ ] El `PUT` devuelve `204` y `luis` gana el rol; el `DELETE` devuelve `204` y lo pierde.
- [ ] Los dos **admin events** (`CREATE` y `DELETE` sobre `GROUP_MEMBERSHIP`) llevan `aprobador-accesos` como autor.
- [ ] La cabecera del ticket **no** aparece en el evento.
- [ ] `luis` ve `administrador-app` en la cabecera tras volver a entrar, y lo pierde en el login siguiente a la retirada.
- [ ] El token de `servicio-conciliacion` recibe **403** en la misma llamada.

**Global**

- [ ] Keycloak responde en <http://localhost:8080> y la aplicación en <http://localhost:8081> (4.A.0).
- [ ] `ana` y `luis` entran por la vía clásica exactamente como en el laboratorio 12.
- [ ] Ninguna `aplicacion_base_lab-XX` ha cambiado: ni `application.yml`, ni el secreto, ni el `issuer-uri`.
- [ ] `keycloak/docker-compose.yml` **no** se ha tocado en este laboratorio.

## 9. Punto de control

`keycloak/curso-realm.json` parte del punto de control del laboratorio 12 y
añade:

```
webAuthnPolicyPasswordless*        política de 5.A.1 + PasskeysEnabled + Mediation
browserFlow                        "browser passwordless"
authenticationFlows                todos los flujos, incluidos los cuatro nuevos
authenticatorConfig                browser-passwordless-conditional-credential
components                         User profile con el atributo "cartera"
clients                            + api-clientes (con authorizationSettings) y aprobador-accesos
roles.realm                        + administrador-app
groups                             + administracion
scopeMappings                      + api-clientes→gestor-clientes, aplicacion-base→administrador-app
clientScopeMappings                realm-management→aprobador-accesos→manage-users
users                              ana con cartera=norte; dos cuentas de servicio nuevas
```

### Lo que el archivo NO lleva, a propósito

**Las credenciales WebAuthn, OTP y de recuperación de `ana`.** Por la misma
razón que el laboratorio 09 no exportaba el secreto TOTP, y aquí con más
motivo: una passkey **está ligada a un dispositivo físico concreto**, el del
autor. Exportarla no solo sería repartir un factor de autenticación: es que
**no funcionaría en el equipo de nadie más**, porque la clave privada vive
en el hardware que la generó.

Quien restaure el realm encontrará a `ana` con:

```json
{"username":"ana","requiredActions":["CONFIGURE_TOTP"],
 "attributes":{"cartera":["norte"]},"groups":["/operaciones"]}
```

es decir: contraseña, la acción requerida de TOTP del laboratorio 09, y
**sin passkey**. Registrar la suya es el paso 5.A.6, que es el que hay que
hacer de todos modos.

**Las claves de firma del realm.** El archivo incluye solo el componente del
*User profile*, no el `KeyProvider`: las claves criptográficas de un realm
no se comparten en un repositorio.

**Los secretos reales de los clientes.** Como en el laboratorio 11, van
marcadores didácticos:

```
api-clientes        secreto-api-clientes-lab14-cambialo-en-produccion
aprobador-accesos   secreto-aprobador-lab14-cambialo-en-produccion
```

En tu Keycloak, los secretos de verdad están en la pestaña **Credentials**
de cada cliente.

### Verificación de la importación

Importado en un contenedor temporal en el puerto 8082, **sin un solo error
en el log**. Comprobado sobre el realm ya importado:

- Los flujos de fábrica **se crean igualmente** pese a que el archivo lleva
  `authenticationFlows`: `browser, browser passwordless, clients, direct
  grant, docker auth, first broker login, registration, reset credentials`.
- `browserFlow` = `browser passwordless`, con el árbol de 5.A.4 idéntico y
  `Condition - credential` con `{"credentials":"webauthn-passwordless"}`.
- La política passwordless llega completa, con `PasskeysEnabled: true` y
  `Mediation: conditional`.
- `ana` llega con `["password"]` y nada más, con `cartera: ["norte"]` y con
  `CONFIGURE_TOTP` pendiente. Su login por contraseña redirige a
  `/login-actions/required-action?execution=CONFIGURE_TOTP`.
- El User profile trae `["username","email","firstName","lastName","cartera"]`.
- `api-clientes` llega con sus dos recursos, sus cinco políticas y su scope
  mapping, y **Evaluate da el mismo resultado**: `ana` PERMIT norte / DENY
  sur, `luis` DENY en las dos.
- Los grupos llegan con sus roles (`administracion → administrador-app`,
  `operaciones → gestor-clientes`).
- `aprobador-accesos` obtiene token y ejecuta `PUT` y `DELETE` con `204`.
- `luis` completa un login real por el flujo nuevo, con la pantalla de
  usuario ofreciendo la passkey y la de contraseña después.

## 10. Problemas frecuentes

### 10.A Parte A

**10.A.0 `mvn spring-boot:run` falla con `Could not resolve host` o `Unknown host repo.maven.apache.org`**
Maven no encuentra las dependencias y trata de descargarlas. Dos causas, y
la primera es la habitual:

1. **Estás ejecutando como `root` o con `sudo`.** El repositorio local de
   Maven es por usuario: el tuyo está en `~/.m2` y el de `root` está vacío.
   Sal de la sesión de root (`exit`, hasta ver tu usuario en el prompt) y
   repite. El error habla de la red, pero la causa es esta.
2. **No tienes DNS.** Compruébalo:
   ```bash
   getent hosts repo.maven.apache.org && echo "DNS OK" || echo "DNS caido"
   ```
   Si ya descargaste las dependencias alguna vez, puedes trabajar sin red
   añadiendo `-o` (*offline*), que usa solo la caché:
   ```bash
   mvn -o spring-boot:run
   ```

**10.A.1 Me he quedado fuera: el flujo nuevo no deja entrar a nadie**
Es el riesgo de 5.A.5 y tiene solución desde fuera del navegador. Vincula
otra vez el flujo original:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -d client_id=admin-cli -d username=admin -d password=admin -d grant_type=password | jq -r .access_token)
curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"realm":"curso","browserFlow":"browser"}' http://localhost:8080/admin/realms/curso
```

Funciona porque el realm `master` y su Admin Console tienen **su propio**
flujo, que no has tocado.

**10.A.2 «Passkey registration result is invalid. Error: prompt() is not supported»**
El error más frecuente de este laboratorio, y no es culpa tuya. Lo que pasa,
en el código del tema de Keycloak 26.7.3
(`theme/base/login/resources/js/webauthnRegister.js`): cuando
`navigator.credentials.create()` **ya ha tenido éxito**, la función
`returnSuccess()` llama a `window.prompt()` para pedirte la etiqueta de la
credencial. El campo que la recibe es `<input type="hidden">`: **no hay
alternativa visible**. Si el navegador bloquea `prompt()`, la excepción se
captura y se envía al servidor como error de registro.

Consecuencias y solución:

- **La passkey sí se creó en tu dispositivo.** Puede quedarte una credencial
  huérfana para `localhost` en *Configuración → Cuentas → Claves de acceso*
  de Windows. No impide reintentar.
- **Cambiar el tema de login no sirve**: `keycloak.v2` y `base` comparten el
  mismo JavaScript.
- **Usa Edge o Chrome en una ventana normal**, no privada, y **acepta el
  cuadro de diálogo** de la etiqueta. Si el navegador te ofreció "impedir
  que esta página cree más diálogos" y lo aceptaste, cierra **todas** las
  pestañas de `localhost:8080` y vuelve a empezar.
- Y compruébalo en los eventos, que para eso está el laboratorio 12: cada
  intento fallido deja `UPDATE_CREDENTIAL_ERROR` y
  `CUSTOM_REQUIRED_ACTION_ERROR` con `error: invalid_user_credentials`.

**10.A.3 Windows Hello no aparece, o la ventana de incógnito no funciona**
La guía lo avisa en *Windows Hello*: algunos navegadores no dan acceso a los
autenticadores de plataforma en ventanas privadas. Usa una ventana normal.
Y comprueba que la política lleva **RS256** en *Signature Algorithms*: la
guía lo exige explícitamente para Windows Hello.

**10.A.4 `ana` entra con passkey pero yo esperaba que pidiera OTP**
Es el comportamiento correcto, y lo decide `Condition - credential` (2.A.5).
Una passkey con verificación de usuario ya son dos factores. Si aun así
quisieras exigir 2FA **siempre**, pon esa condición en **Disabled**; la
guía lo menciona en la sección *Passkeys*.

**10.A.5 Después de duplicar el flujo, los nombres no coinciden con el README**
Keycloak prefija los sub-flujos copiados con el nombre del flujo nuevo
(`browser passwordless forms`). Es normal y el README ya lo refleja.

**10.A.6 No puedo arrastrar el sub-flujo de 2FA a su sitio**
No se puede: la consola no mueve sub-flujos entre padres. Por eso 5.A.4 lo
borra y lo reconstruye un nivel más abajo.

**10.A.7 El código de recuperación correcto no me lo acepta**
Van **en orden**. La pantalla dice cuál toca (`Recovery code #1`, luego el
#2…). Y cada uno vale una sola vez. Si los has perdido, genera un juego
nuevo desde la Account Console: el anterior queda invalidado.

### 10.B Parte B

**10.B.1 Evaluate da DENY a `ana` aunque tiene el rol**
Es lo de 5.B.6, y es el fallo número uno de esta parte. Mira el desglose:
si `es gestor` sale en `DENY`, el problema no es la política sino que el
rol **no llega al token**, porque `api-clientes` tiene *Full scope allowed*
Off y ningún scope mapping. Asigna `gestor-clientes` en su scope.

**10.B.2 La política Regex nunca casa**
Tres causas, en orden de frecuencia: (1) falta el **mapper** de 5.B.4, así
que el claim no existe; (2) el mapper está solo en el ID Token, y las
políticas leen el **access token**; (3) el atributo no está en el usuario
porque no lo declaraste en el **User profile** (5.B.1).

**10.B.3 No puedo guardar el atributo `cartera` en `ana`**
En 26.7.3 los atributos no declarados están desactivados por defecto. Hay
que declararlo en **Realm settings → User profile** (5.B.1). La alternativa
—activar *Unmanaged attributes* en **Realm settings → General**— existe,
pero declarar el atributo es lo correcto: le da nombre, permisos y
validaciones.

**10.B.4 El grant UMA devuelve 403 y no sé si es el token o la política**
Distínguelo con `response_mode=decision`: si el token fuera inválido
tendrías un `401`, no un `403` con `access_denied`. Un `403` significa que
el servidor evaluó y **denegó**. Para ver por qué, usa *Evaluate* con ese
mismo usuario.

**10.B.5 Si la Regex no me funciona, ¿hay alternativa?**
Sí: una **Group policy**. Crea grupos `cartera-norte` y `cartera-sur`, mete
a los usuarios y sustituye las políticas Regex por políticas de grupo. La
ventaja es que la pertenencia a grupo se resuelve **en el servidor**, sin
depender de que un claim llegue al token, así que no tiene el problema de
10.B.2. La desventaja es que multiplica los grupos.

### 10.C Parte C

**10.C.1 El `PUT` devuelve 403 con el token de `aprobador-accesos`**
Falta el rol en uno de los **dos** sitios de 5.C.3. Decodifica el token y
mira `resource_access`: si no aparece `realm-management: [manage-users]`, o
no se lo asignaste a la cuenta de servicio, o falta el scope mapping.

**10.C.2 `luis` no ve `administrador-app` en la cabecera**
Dos causas: (1) falta el scope mapping de 5.C.4 en `aplicacion-base`; (2)
`luis` no ha vuelto a iniciar sesión. Los roles viajan en el ID Token
emitido **en el login** (laboratorio 06, 2.5): hasta que no haya uno nuevo,
la aplicación no se entera.

**10.C.3 El `curl` devuelve 401 a mitad de un script**
El token de servicio dura **60 segundos** (laboratorio 05). Pídelo otra vez
en cada llamada.

**10.C.4 No encuentro la cuenta de servicio en la lista de Users**
No aparece ahí, igual que en el laboratorio 11. Se llega desde el cliente:
**Clients → aprobador-accesos → Service accounts roles**, y ahí hay un
enlace al usuario `service-account-aprobador-accesos`.

**10.C.5 No veo los admin events**
Tienen que estar activados, y lo están desde el laboratorio 12
(**Realm settings → Events → Admin events settings**). Recuerda que solo se
registra lo que ocurre **después** de activarlos.

## 11. Qué queda fuera y por dónde seguir

Este laboratorio abre tres puertas y no cruza ninguna del todo. Dónde
continuar:

**Endurecimiento — `laboratorio-13-endurecimiento` (opcional).** Todo el
curso ha corrido en `start-dev`, por HTTP, con `admin`/`admin`. El
laboratorio 13 es material de consulta sobre HTTPS, redirect URIs estrictas,
políticas de cliente de OAuth 2.1, rotación de secretos y administrador
permanente. Es especialmente relevante después de esta sesión: **sin HTTPS
no hay passkeys** fuera de `localhost` (5.A.6).

**Laboratorios 15 a 17, si el curso continúa:**

- **15 · Autogestión y SMTP.** *Forgot password*, verificación de correo,
  *Update Email* y el *Email Event Listener* que el laboratorio 12 dejó
  fuera por no haber servidor de correo. Es lo que falta para que un usuario
  que pierde **todos** sus factores pueda recuperarse sin llamar a nadie.
- **16 · Federación con LDAP / Active Directory.** El laboratorio 10 delegó
  la autenticación en GitHub; este delega el **almacén de usuarios**, que es
  el caso normal en una empresa. Ojo al aviso de la guía sobre WebAuthn y
  el tamaño de los identificadores federados.
- **17 · Step-up authentication.** Exigir un factor adicional solo para las
  operaciones sensibles, en vez de para toda la sesión, con *Condition -
  Level of Authentication* y el claim `acr`. Es la continuación natural de
  la parte A, y encaja con las *client policies* del laboratorio 13.

**Y de las láminas de estado del arte (laboratorio 02):**

- **DPoP**, para que un token robado no sirva en otra máquina (*sender
  constrained tokens*). Feature soportada en 26.7.3.
- **FAPI**, el perfil de seguridad del sector financiero, que Keycloak sabe
  exigir con *client policies*.
- **Token exchange**, para que un servicio actúe en nombre de otro sin
  reenviar credenciales.
- **Organizations**, multi-tenancy dentro de un realm (su sub-flujo ya lo
  has visto pasar en 5.A.4).
- **SAML**, para integrar aplicaciones que no hablan OIDC.

Y una última idea con la que cerrar el curso: de las tres partes de hoy, la
que más reduce el riesgo real no es la más sofisticada. Authorization
Services es potente y el acceso *just-in-time* es elegante, pero lo que
de verdad corta la clase de ataque más común —el phishing de credenciales—
es la parte A. Si de este laboratorio solo te llevas una cosa al trabajo,
que sean las passkeys.
