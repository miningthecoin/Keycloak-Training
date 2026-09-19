# Laboratorio 10 · Inicio de sesión federado con GitHub

**Tipo:** práctico · **Duración:** 60 min · **Funcionalidad de Keycloak:** identity brokering, proveedor social GitHub y *first login flow*

## 1. Objetivo

Que un usuario pueda entrar en la aplicación con su cuenta de GitHub, sin
que la Cooperativa Andina cree ni custodie una contraseña para él. Al
terminar:

- La pantalla de login de Keycloak ofrece, además de usuario y contraseña
  del realm, un botón **Sign in with GitHub**.
- Al pulsarlo, el usuario se autentica en GitHub y vuelve a Keycloak, que
  crea una cuenta local vinculada a su identidad de GitHub.
- La Cooperativa nunca ve ni guarda la contraseña de GitHub de ese
  usuario.
- Ese usuario federado, recién creado, **no tiene rol**: al pulsar
  Clientes recibe *Acceso denegado* hasta que un administrador le asigne
  `gestor-clientes`.
- **La aplicación no cambia ni una línea.**

### Beneficios de implementarlo

- **Una base de credenciales menos que proteger.** Cada almacén de
  contraseñas es un objetivo. Delegar en GitHub significa no guardar esas
  contraseñas, no gestionar sus reseteos y no ser el responsable si se
  filtran.
- **El usuario reutiliza una identidad que ya cuida.** Sin otra contraseña
  que recordar, y hereda el segundo factor que ya tenga en GitHub.
- **Alta y baja centralizadas en el proveedor.** Si GitHub desactiva o
  bloquea la cuenta, ese usuario deja de poder entrar, sin que la
  Cooperativa tenga que hacer nada.
- **Sin coste para la aplicación.** El brokering ocurre entre Keycloak y
  GitHub. La aplicación recibe el mismo ID Token de siempre y no sabe por
  qué proveedor entró el usuario.
- **Separación limpia de responsabilidades.** GitHub responde "quién es";
  Keycloak decide "qué puede hacer aquí" (el rol). Federar no regala
  permisos.

### Un ejemplo de las amenazas que evita

La Cooperativa incorpora a cinco desarrolladores de una consultora externa
por un proyecto de tres meses. Sin federación, alguien tiene que crearles
cinco usuarios en el realm, ponerles contraseñas iniciales, comunicárselas
por un canal que quizá no es seguro, y acordarse de borrar las cinco
cuentas cuando el proyecto acabe. Si se olvida una, queda una credencial
viva, con contraseña conocida, que nadie vigila: es la puerta trasera
clásica del contratista que ya se fue.

Con federación, esos cinco entran con su cuenta de GitHub corporativa. La
Cooperativa no crea ni comunica contraseñas. Y el día que la consultora
les retire el acceso a GitHub, dejan de poder entrar en el portal
automáticamente, porque la identidad que se apaga es la de GitHub, no una
copia olvidada en el realm.

Fuentes oficiales (Server Administration Guide):

- *Integrating identity providers* y *Brokering overview*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_general-idp-config>
- *Social identity providers* y *GitHub* (pasos de la OAuth App):
  <https://www.keycloak.org/docs/latest/server_admin/index.html#social-identity-providers>
- *First login flow* y sus autenticadores por defecto:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_identity_broker_first_login>

## 2. Conceptos

### 2.1 Keycloak como *identity broker*

La guía lo define así: un *identity broker* es un intermediario que conecta
las aplicaciones (los *service providers*) con los proveedores de identidad
externos. La aplicación solo confía en Keycloak; Keycloak, a su vez,
confía en GitHub. La aplicación nunca habla con GitHub ni sabe que existe.

El flujo del broker, resumido de la guía:

```
Navegador            Aplicación (8081)      Keycloak (8080)        GitHub
   │ GET /privada        │                      │                    │
   │ 302 a Keycloak      │                      │                    │
   │ pantalla de login con botón "GitHub"       │                    │
   │ pulsa GitHub        │                      │                    │
   │ 302 a github.com/login/oauth/authorize ───────────────────────▶│
   │ el usuario entra en GitHub y autoriza      │                    │
   │ 302 al endpoint del broker con un code ◀───────────────────────│
   │                     │  Keycloak canjea el code con GitHub,      │
   │                     │  obtiene el perfil, crea/vincula usuario  │
   │ 302 a la aplicación con el ID Token de KEYCLOAK                 │
   │◀────────────────────│                      │                    │
```

Lo importante: la aplicación recibe un ID Token **de Keycloak**, idéntico
al de cualquier otro login. GitHub queda detrás.

### 2.2 La OAuth App de GitHub y la Redirect URI

Para que GitHub confíe en Keycloak hay que registrar una **OAuth App** en
GitHub. Esa app tiene su propio *Client ID* y *Client Secret* (distintos
del cliente `aplicacion-base`; estos son de la relación Keycloak↔GitHub).

El dato que los une es la **Authorization callback URL**, que debe ser
exactamente la *Redirect URI* que Keycloak reserva para este proveedor:

```
http://localhost:8080/realms/curso/broker/github/endpoint
```

El segmento `github` es el *alias* del proveedor. Si le pusieras otro
alias, la URL cambiaría. Es la misma idea de "URI exacta" del laboratorio
04, ahora del lado del proveedor externo.

### 2.3 El secreto de GitHub no va al repositorio

El *Client Secret* de la OAuth App es una credencial real, igual que
`KEYCLOAK_CLIENT_SECRET`. Por la regla del curso, **no se sube al
repositorio**. En el punto de control (sección 9), el proveedor `github`
lleva los valores `TU_GITHUB_CLIENT_ID` y `TU_GITHUB_CLIENT_SECRET` como
marcadores: cada alumno crea su propia OAuth App y los sustituye. Un
secreto de GitHub en el repositorio sería una credencial filtrada.

### 2.4 El *first login flow*: qué pasa la primera vez

Cuando alguien entra por GitHub por primera vez, Keycloak ejecuta el flujo
**first broker login**. Sus autenticadores por defecto, según la guía:

- **Review Profile.** Muestra al usuario el perfil que Keycloak ha traído
  de GitHub (nombre, correo) para que lo confirme o complete.
- **Create User If Unique.** Si no existe ninguna cuenta en el realm con
  ese correo o usuario, crea una cuenta local nueva y la vincula a GitHub.
  Fin del flujo.
- **Handle Existing Account.** Si ya existe una cuenta con ese correo,
  entra en juego el enlace de cuentas (*account linking*). La guía avisa
  de que vincular automáticamente sería un **agujero de seguridad**,
  porque no siempre se puede confiar en el correo que envía el proveedor
  externo. Por eso el enlace pide confirmación (por correo, si hay SMTP, o
  reautenticación con contraseña).

En este laboratorio el realm no tiene SMTP, así que si el correo de GitHub
coincidiera con el de `ana`, Keycloak pediría reautenticación con
contraseña antes de enlazar. Para el ejercicio usaremos una cuenta de
GitHub con un correo distinto, de modo que se cree un usuario nuevo.

### 2.5 Federar es autenticar, no autorizar

Este es el punto que ata el laboratorio con los anteriores. El usuario que
entra por GitHub se crea **sin ningún rol del realm**. GitHub dice quién
es, pero no qué puede hacer en la Cooperativa. Al pulsar Clientes, ese
usuario recibirá *Acceso denegado* (laboratorio 06), exactamente igual que
`luis`. Para que vea la sección, un administrador tiene que asignarle
`gestor-clientes`, o meterlo en el grupo `operaciones`.

Es la separación de responsabilidades en estado puro: la autenticación se
delega, la autorización se queda en casa.

### 2.6 Por qué la aplicación no cambia

Como en los laboratorios 08 y 09, no se toca `aplicacion_base`. El botón
de GitHub, el flujo del broker y la creación del usuario ocurren en
Keycloak. La aplicación recibe el mismo `OidcUser` de siempre; el claim
`preferred_username` traerá el nombre del usuario federado y todo lo demás
funciona igual.

## 3. Relación con OWASP, ASVS y PCI DSS

- **OWASP A07:2021 Fallos de identificación y autenticación.** Cada
  almacén de credenciales propio es superficie de ataque y fuente de
  errores (contraseñas débiles, reseteos inseguros, cuentas huérfanas).
  Delegar en un proveedor probado reduce esa superficie.
- **OWASP ASVS V2 (identidad federada).** Cubre la confianza en
  proveedores externos: validar la respuesta del proveedor, no enlazar
  cuentas a ciegas por correo, y tratar la información externa como no
  plenamente fiable. Es justo lo que hace el *first broker login* al pedir
  confirmación antes de enlazar.
- **PCI DSS.** Aquí conviene ser honesto: PCI no tiene un requisito que
  diga "usa login social". La relación es indirecta y va por el requisito
  8 en su espíritu: cuantos menos almacenes de credenciales propios
  mantengas, menos superficie que proteger, rotar y auditar. No se fuerza
  un mapeo que no existe.

## 4. Ejercicio ANTES: toda identidad vive en el realm

### 4.0 Preparar el entorno (empieces donde empieces)

> **Lee esto aunque vengas del laboratorio 09 sin apagar la máquina.** Si
> llevas Keycloak y la aplicación en marcha y tu realm está sano, salta a
> 4.1. En cualquier otro caso —máquina recién encendida, realm a medias, o
> te incorporas ahora al curso— sigue los cuatro pasos.

Este laboratorio parte del **punto de control del laboratorio 09**: realm
`curso` con el cliente OIDC y PKCE (04), cierre de sesión y tiempos (05),
rol `gestor-clientes` y grupo `operaciones` (06), scopes mínimos (07),
política de contraseñas y fuerza bruta (08), y la *OTP Policy* con `ana`
pendiente de registrar su autenticador (09).

**Paso 1. Traer los laboratorios nuevos.**

```bash
cd ~/keycloak-curso
git pull
```

Es seguro: los laboratorios 10 a 14 **no modifican `aplicacion_base/`**, así
que lo que hayas escrito ahí durante el curso se queda intacto.

**Paso 2. Restaurar Keycloak al estado del laboratorio 09.**

```bash
cd ~/keycloak-curso
cp laboratorio-09-2fa-totp/keycloak/curso-realm.json keycloak/import/
cd keycloak
docker compose down -v
docker compose up -d
```

> ### ⚠ La `-v` no es opcional
>
> `--import-realm` **solo importa si el realm no existe**. Keycloak lo dice
> en su log: `Strategy: IGNORE_EXISTING`. Si tu volumen ya tiene un realm
> `curso` —y lo tiene, si hiciste los laboratorios 03 a 09—, copiar el JSON
> y hacer `docker compose up -d` **no cambia absolutamente nada**, y encima
> el log acaba diciendo `Import finished successfully`.
>
> `docker compose down -v` **borra el volumen**, que es lo que permite que
> la importación ocurra. Perderás el realm que tuvieras y el historial de
> eventos. Es lo que queremos: partir todos del mismo sitio.

**Paso 3. Comprobar que la importación ocurrió de verdad.**

```bash
docker compose logs | grep "Realm 'curso'"
```

Solo hay dos resultados posibles:

```
Realm 'curso' imported                          <-- bien, sigue
Realm 'curso' already exists. Import skipped    <-- te faltó la -v, repite el paso 2
```

**Paso 4. Arrancar la aplicación.**

```bash
cd ~/keycloak-curso/laboratorio-10-login-federado-github/aplicacion_base_lab-10
export KEYCLOAK_CLIENT_SECRET='secreto-lab04-cambialo-en-produccion'
mvn spring-boot:run
```

> **El secreto ha cambiado.** Al restaurar el realm, el de `aplicacion-base`
> pasa a ser el del archivo: `secreto-lab04-cambialo-en-produccion`. El que
> usabas antes ya no vale. Puedes confirmarlo en **Clients →
> aplicacion-base → Credentials**.
>
> **Ejecuta como tu usuario, nunca con `sudo` ni como `root`**: el
> repositorio de Maven es por usuario (`~/.m2`) y como `root` estarías
> usando uno vacío.

Deja la aplicación corriendo en esa terminal y comprueba desde otra:

```bash
curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" http://localhost:8081/privada
# esperado: 302 http://localhost:8081/oauth2/authorization/keycloak
```

**Qué vas a notar del reinicio**, y es normal:

- **`ana` vuelve a ver el código QR** la primera vez que entre. El punto de
  control la deja con la acción requerida *Configure OTP* a propósito, para
  que cada persona registre su propio autenticador (laboratorio 09, 9).
- **`luis` entra con `luis123`**, sin segundo factor, como siempre.
- **El historial de Events está vacío.** No importa: los eventos se activan
  en el laboratorio 12.

### 4.1 Toda identidad vive en el realm

**Propósito.** Ver que, sin federación, la única forma de entrar es con un
usuario que exista y tenga contraseña dentro del realm `curso`.

1. Ventana de incógnito → <http://localhost:8081> → **Clientes**.
2. Observa la pantalla de login de Keycloak.

**Resultado esperado.** Solo hay campos de **usuario y contraseña**. No
existe ninguna alternativa. Cualquiera que necesite entrar tiene que ser
dado de alta a mano por un administrador, que además le fija y le comunica
una contraseña.

Confírmalo en la Admin Console: **Identity Providers** está vacío.

## 5. Configuración de Keycloak, paso a paso

### 5.1 Crear la OAuth App en GitHub

1. En GitHub: foto de perfil → **Settings** → **Developer settings** →
   **OAuth Apps** → **New OAuth App**.
2. Rellena:
   - *Application name*: `Cooperativa Andina (curso Keycloak)` o el que
     quieras.
   - *Homepage URL*: `http://localhost:8081`
   - *Authorization callback URL*:
     `http://localhost:8080/realms/curso/broker/github/endpoint`
3. **Register application**.
4. Copia el **Client ID**. Pulsa **Generate a new client secret** y copia
   el **Client Secret** (solo se muestra una vez).

> No subas estos valores a ningún archivo del repositorio.

### 5.2 Crear el proveedor GitHub en Keycloak

1. Admin Console, realm `curso` → menú **Identity Providers**.
2. En **Add provider**, elige **GitHub**.
3. Verás el campo **Redirect URI** ya relleno con
   `http://localhost:8080/realms/curso/broker/github/endpoint`. Es el que
   pusiste como callback en GitHub; comprueba que coincide letra por letra.
4. Pega el **Client ID** y el **Client Secret** de la OAuth App.
5. Activa **JSON Format** (la guía lo pide para recuperar los tokens del
   IdP en JSON y para que se puedan refrescar).
6. **Add**.

### 5.3 Revisar el *first login flow*

1. Menú **Authentication** → pestaña **Flows** → **First broker login**.
2. Observa, sin cambiar nada, los autenticadores de 2.4: *Review Profile*,
   *Create User If Unique* y el sub-flujo *Handle Existing Account*.

## 6. Cambios en la aplicación

**Ninguno.** El laboratorio 10 no toca `aplicacion_base`. La carpeta
`aplicacion_base_lab-10/` es idéntica a `aplicacion_base_lab-09/`.

## 7. Ejercicio DESPUÉS: entrar con GitHub

Usa una cuenta de GitHub cuyo correo **no** coincida con el de `ana`
(`ana.torres@ejemplo.test`), para que se cree un usuario nuevo en lugar de
disparar el enlace de cuentas (2.4).

### 7.1 El botón aparece y lleva a GitHub

1. Ventana de incógnito → <http://localhost:8081> → **Clientes**.

**Resultado esperado.** Bajo el formulario de usuario y contraseña aparece
ahora el botón **Sign in with GitHub**.

2. Púlsalo.

**Resultado esperado.** El navegador va a `github.com`, a la pantalla de
autorización de tu OAuth App. Si es la primera vez, GitHub pide que
autorices a la aplicación a ver tu correo.

### 7.2 Vuelta a Keycloak y creación del usuario

1. Autoriza en GitHub.

**Resultado esperado.** Vuelves a Keycloak, que muestra la pantalla
**Review Profile** (o *Update Account Information*) con los datos traídos
de GitHub. Complétalos si falta algo y confirma.

2. Llegas a la portada de la aplicación, con la cabecera `Sesión: <tu
   usuario de GitHub>`.

Compruébalo en la Admin Console: **Users** muestra el usuario nuevo, y en
su pestaña **Identity provider links** aparece el vínculo con `github`.

### 7.3 Federar no da permisos

1. Con ese usuario, pulsa **Clientes**.

**Resultado esperado.** Recibe **Acceso denegado** (HTTP 403). Está
autenticado, pero no tiene el rol `gestor-clientes` (2.5).

2. Como administrador: **Users → <usuario de GitHub> → Role mapping →
   Assign role → `gestor-clientes`** (o mételo en el grupo `operaciones`).
3. El usuario cierra sesión, vuelve a entrar con GitHub y pulsa Clientes.

**Resultado esperado.** Ahora sí ve la tabla, con la cabecera
`[gestor-clientes]`.

## 8. Lista de verificación

- [ ] En GitHub existe una OAuth App con callback `http://localhost:8080/realms/curso/broker/github/endpoint`.
- [ ] En Keycloak, **Identity Providers** contiene `github`, habilitado, con tu Client ID/Secret y *JSON Format* activado.
- [ ] La pantalla de login muestra el botón **Sign in with GitHub**.
- [ ] Pulsarlo redirige a `github.com/login/oauth/authorize` con tu `client_id` y el `redirect_uri` del broker.
- [ ] Tras autorizar, se crea un usuario en **Users** con vínculo a `github` en *Identity provider links*.
- [ ] El usuario federado recibe *Acceso denegado* en Clientes hasta que se le asigna `gestor-clientes`.
- [ ] `aplicacion_base` no ha cambiado en todo el laboratorio.

## 9. Punto de control

`keycloak/curso-realm.json` parte del punto de control del laboratorio 09
y añade el proveedor de identidad `github`, habilitado y con *JSON Format*.

**El Client ID y el Client Secret son marcadores**
(`TU_GITHUB_CLIENT_ID`, `TU_GITHUB_CLIENT_SECRET`), no credenciales
reales. Un secreto de GitHub en un repositorio sería una credencial
filtrada, así que cada alumno debe:

1. Crear su propia OAuth App en GitHub (sección 5.1).
2. Tras importar el realm, ir a **Identity Providers → github** y sustituir
   los marcadores por su Client ID y Client Secret reales.

Hasta que no lo haga, el botón de GitHub aparecerá pero la autenticación
fallará, porque `TU_GITHUB_CLIENT_ID` no es una app real.

## 10. Problemas frecuentes

### Al preparar el entorno (4.0)

**El log dice `Realm 'curso' already exists. Import skipped`**
Hiciste `docker compose up -d` sin borrar el volumen. `--import-realm` usa
la estrategia `IGNORE_EXISTING`: si el realm existe, **ignora el archivo**.
Repite el paso 2 de 4.0 **con la `-v`**:

```bash
cd ~/keycloak-curso/keycloak
docker compose down -v && docker compose up -d
docker compose logs | grep "Realm 'curso'"
```

**La aplicación arranca pero el login falla con `invalid_client_credentials`**
El `KEYCLOAK_CLIENT_SECRET` que exportaste es el de antes de restaurar el
realm. Tras la importación es `secreto-lab04-cambialo-en-produccion`
(4.0, paso 4). Compruébalo en **Clients → aplicacion-base → Credentials**.

**`mvn spring-boot:run` falla con `Could not resolve host`**
Dos causas. La primera es la habitual: **estás como `root` o con `sudo`**, y
el repositorio de Maven es por usuario (`~/.m2`); sal de esa sesión y
repite. La segunda es que no tengas DNS: si ya descargaste las dependencias
alguna vez, `mvn -o spring-boot:run` trabaja solo con la caché.

**`docker compose up -d` dice que el nombre `keycloak` ya está en uso**
Tienes un contenedor de otra copia del repositorio. El compose fija
`container_name: keycloak`, así que solo puede haber uno. Párala desde la
carpeta antigua con `docker compose down -v`, o elimínalo con
`docker rm -f keycloak`.

**`ana` me pide registrar el autenticador otra vez**
Es lo esperado tras restaurar: el punto de control la deja con la acción
requerida *Configure OTP* para que cada persona enrole su propio
dispositivo. No se reparten segundos factores en un archivo.

### Con el proveedor de GitHub

**GitHub responde "The redirect_uri MUST match the registered callback URL"**
La *Authorization callback URL* de la OAuth App no coincide exactamente con
`http://localhost:8080/realms/curso/broker/github/endpoint`. Revisa
protocolo, puerto, el alias `github` y la ausencia de barra final de más.

**Tras autorizar en GitHub, Keycloak da un error de cliente inválido**
El Client ID o el Client Secret pegados en el proveedor `github` no son los
de la OAuth App, o quedaron los marcadores del punto de control. Pégalos de
nuevo en **Identity Providers → github**.

**El botón de GitHub no aparece**
El proveedor `github` no existe o está deshabilitado. Revisa **Identity
Providers**. Si acabas de crearlo, recarga la pantalla de login.

**Keycloak pide reautenticación con contraseña tras volver de GitHub**
El correo de la cuenta de GitHub coincide con el de un usuario que ya
existe en el realm (por ejemplo `ana`). Es el enlace de cuentas de 2.4
protegiéndote. Usa una cuenta de GitHub con otro correo, o completa el
enlace a propósito.

**El usuario federado no ve Clientes**
Es lo esperado: federar no da rol (2.5). Asígnale `gestor-clientes` o
mételo en el grupo `operaciones`.

**No quiero que Keycloak cree usuarios nuevos automáticamente**
La guía explica cómo desactivarlo en el *First Broker Login* poniendo
*Create User If Unique* y *Confirm Link Existing Account* en *Disabled*.
Fuera del alcance de este laboratorio.

## 11. Siguiente laboratorio

`laboratorio-11-maquina-a-maquina`: hasta ahora siempre hay una persona
detrás del navegador. El siguiente laboratorio cubre el acceso de un
sistema a otro sin usuario, con el flujo *Client Credentials* y una cuenta
de servicio, demostrado con `curl`.
