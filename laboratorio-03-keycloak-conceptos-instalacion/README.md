# Laboratorio 03 · Keycloak: arquitectura, conceptos e instalación

**Tipo:** práctico · **Duración:** 60 min · **Funcionalidad de Keycloak:** realm y usuario

## 1. Objetivo

Al terminar tendrás Keycloak 26.7.3 ejecutándose en Docker dentro de tu
WSL, un realm llamado `curso` y un usuario `ana` capaz de iniciar sesión
en la consola de cuenta. Entenderás qué es cada pieza de la consola de
administración antes de conectar la aplicación en el laboratorio 04.

Este laboratorio **no modifica la aplicación base**.

Fuentes oficiales:

- Getting started – Docker: <https://www.keycloak.org/getting-started/getting-started-docker>
- Running Keycloak in a container: <https://www.keycloak.org/server/containers>
- Server Administration Guide, capítulo *Configuring realms*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#configuring-realms>
- Importing and exporting realms: <https://www.keycloak.org/server/importExport>

## 2. Conceptos

### 2.1 Qué es Keycloak

Keycloak es un servidor de identidad y acceso de código abierto. Implementa
los protocolos estándar **OpenID Connect**, **OAuth 2.0** y **SAML 2.0**,
de modo que una aplicación que hable cualquiera de ellos puede delegarle
la autenticación de usuarios sin escribir código de login, almacenamiento
de contraseñas ni gestión de sesiones. Lo estudiaste en los laboratorios
01 y 02; aquí verás dónde vive cada concepto en la consola.

### 2.2 Vocabulario que usarás todo el curso

| Término | Qué es | Dónde está en la consola |
|---|---|---|
| **Realm** | Un espacio aislado con sus propios usuarios, aplicaciones, roles y configuración. Equivale a un *tenant*. | Selector superior izquierdo |
| **Realm `master`** | El realm de administración que existe desde el arranque. Solo debe usarse para gestionar otros realms, **nunca** para aplicaciones. | Creado por defecto |
| **User** | Una persona (o cuenta) que puede autenticarse en el realm. | Menú *Users* |
| **Client** | Una aplicación o servicio que pide a Keycloak autenticar usuarios o emitir tokens. Nuestra app Spring será un client en el lab 04. | Menú *Clients* |
| **Role** | Una etiqueta que representa un permiso o función (`gestor`, `auditor`). Se asigna a usuarios o grupos. Lab 06. | Menú *Realm roles* |
| **Group** | Conjunto de usuarios que heredan roles y atributos. Lab 06. | Menú *Groups* |
| **Client scope** | Conjunto de *claims* que se incluyen en los tokens. Lab 07. | Menú *Client scopes* |
| **Authentication flow** | Secuencia de pasos que ejecuta el login (contraseña, OTP, etc.). Lab 09. | Menú *Authentication* |
| **Identity provider** | Un proveedor externo (GitHub, Google, otro Keycloak) en el que el realm confía. Lab 10. | Menú *Identity providers* |
| **Events** | Registro de lo que ocurre (logins, errores, cambios). Lab 12. | Menú *Events* |

### 2.3 Dos consolas

- **Admin Console** (`http://localhost:8080/admin`): para administradores.
  Aquí se configuran realms, clientes, roles, políticas.
- **Account Console** (`http://localhost:8080/realms/<realm>/account`):
  para los usuarios finales. Cada usuario gestiona su perfil, contraseña,
  segundo factor y sesiones. La usaremos como forma rápida de probar que
  un usuario puede autenticarse sin necesitar todavía la aplicación.

### 2.4 `start-dev` frente a `start`

La imagen se arranca con `start-dev`: modo desarrollo. Escucha HTTP sin
TLS, usa una base de datos H2 embebida y acepta cualquier nombre de host.
Es exactamente lo que queremos en un laboratorio y exactamente lo que
**no** debe llegar a producción. Keycloak lo recuerda en el log en cada
arranque. El laboratorio 13 explica qué cambia con `start`.

### 2.5 El usuario administrador temporal

`KC_BOOTSTRAP_ADMIN_USERNAME` y `KC_BOOTSTRAP_ADMIN_PASSWORD` crean un
administrador **temporal** en el realm `master` la primera vez que arranca.
Es suficiente para el curso; en el laboratorio 13 veremos cómo crear un
administrador permanente y eliminar el temporal.

## 3. Relación con OWASP y PCI DSS

- **OWASP A07 (fallos de identificación y autenticación).** Centralizar la
  identidad en un componente especializado, en lugar de implementarla en
  cada aplicación, es la primera medida estructural que recomienda ASVS
  (V2 *Authentication*). Cada aplicación que guarda contraseñas es otra
  superficie de ataque.
- **PCI DSS req. 8 (identificar usuarios y autenticar el acceso).** Exige
  identificadores únicos por usuario y un sistema de autenticación
  gestionado. Keycloak es ese sistema; el realm `curso` será nuestro
  "sistema de identidad" para el resto del curso.
- **Higiene de administración.** El realm `master` es el equivalente a la
  cuenta root. Crear un realm dedicado aplica separación de privilegios.

## 4. Ejercicio ANTES: no existe ninguna identidad verificable

**Propósito.** Constatar que hoy el sistema no tiene forma de saber quién
es quién.

1. Arranca la aplicación base:
   ```bash
   cd ~/keycloak-curso/aplicacion_base
   mvn spring-boot:run
   ```
2. Abre <http://localhost:8081/privada>.
3. Observa la cabecera: `Sesión: anónimo`.
4. Abre la misma URL en una ventana de incógnito. Mismo resultado.
5. Pregúntate: ¿dónde se guardarían los usuarios? ¿Quién comprobaría una
   contraseña? ¿Cómo sabría la aplicación que "Ana" es Ana?

**Resultado esperado.** No hay respuesta a ninguna de las preguntas. La
aplicación carece de un proveedor de identidad. Deja la app corriendo o
detenla con `Ctrl+C`; no se usa más en este laboratorio.

## 5. Configuración de Keycloak, paso a paso

### 5.1 Arrancar Keycloak

Usamos el `docker-compose.yml` de la carpeta `keycloak/` del repositorio
(equivale al `docker run` de la guía oficial, con persistencia de datos):

```bash
cd ~/keycloak-curso/keycloak
docker compose up -d
docker compose logs -f
```

Espera hasta ver una línea similar a:

```
Keycloak 26.7.3 on JVM (powered by Quarkus ...) started in ...
Listening on: http://0.0.0.0:8080
```

Y justo antes verás la advertencia:

```
Running the server in development mode. DO NOT use this configuration in production.
```

Sal del log con `Ctrl+C` (el contenedor sigue en marcha).

### 5.2 Entrar en la Admin Console

1. Abre <http://localhost:8080> en el navegador de Windows.
2. Inicia sesión con `admin` / `admin`.
3. Estás en el realm `master`. Fíjate en el selector de realm arriba a la
   izquierda.

### 5.3 Crear el realm `curso`

Siguiendo la guía oficial (*Create a realm*):

1. Abre el selector de realm y pulsa **Create realm**.
2. En *Realm name* escribe `curso`.
3. Pulsa **Create**.

Ahora el selector muestra `curso`. Todo lo que hagas a partir de aquí
sucede dentro de este realm.

Opcional, para que las pantallas de login muestren un nombre amigable:
**Realm settings → General → Display name**: `Cooperativa Andina · Curso Keycloak`.
Guarda.

### 5.4 Crear el usuario `ana`

Siguiendo la guía oficial (*Create a user*):

1. Menú **Users → Create new user** (o **Add user**).
2. Rellena:
   - *Username*: `ana`
   - *Email*: `ana.torres@ejemplo.test`
   - *First name*: `Ana`
   - *Last name*: `Torres`
   - *Email verified*: activado (evita que se pida verificar correo; no
     tenemos servidor SMTP)
3. Pulsa **Create**.
4. Ve a la pestaña **Credentials → Set password**.
5. *Password*: `ana123` (sí, es débil; el laboratorio 08 arreglará esto).
6. **Temporary: Off**. Si lo dejas en *On*, Keycloak obligará a cambiarla
   en el primer login.
7. Pulsa **Save** y confirma.

### 5.5 Explorar el menú (5 minutos)

Sin cambiar nada, recorre en el menú lateral: *Clients* (ya hay clientes
internos como `account` y `admin-cli`), *Realm roles* (existen
`default-roles-curso`, `offline_access`, `uma_authorization`),
*Authentication* (verás el flujo `browser`), *Identity providers*,
*Events*. Relaciona cada uno con la tabla de la sección 2.2.

### 5.6 Consultar el documento de descubrimiento OIDC

Keycloak publica la configuración del realm en una URL estándar que la
aplicación usará en el laboratorio 04:

```bash
curl -s http://localhost:8080/realms/curso/.well-known/openid-configuration | jq
```

Identifica en la salida: `issuer`, `authorization_endpoint`,
`token_endpoint`, `userinfo_endpoint`, `end_session_endpoint`, `jwks_uri`
y `code_challenge_methods_supported` (debe incluir `S256`).

## 6. Cambios en la aplicación

Ninguno. `aplicacion_base_lab-03/` es idéntica a `aplicacion_base/`.

## 7. Ejercicio DESPUÉS: existe una identidad verificable

**Propósito.** Comprobar que Ana puede demostrar quién es ante un sistema
de identidad, aunque la aplicación todavía no lo aproveche.

1. Abre una ventana de **incógnito** (para no arrastrar la sesión de
   `admin`).
2. Ve a <http://localhost:8080/realms/curso/account>.
3. Pulsa **Sign in**. Aparece la pantalla de login del realm `curso`
   (fíjate en el nombre del realm en la página).
4. Entra con `ana` / `ana123`.
5. Estás en la Account Console de Ana: revisa *Personal info*,
   *Account security → Signing in* (solo hay contraseña; el lab 09 añadirá
   TOTP) y *Device activity* (tu sesión actual).
6. Vuelve a la Admin Console (ventana normal), menú **Sessions**. Verás la
   sesión activa de `ana`. Ese es el estado que la aplicación consultará en
   el laboratorio 05.
7. Prueba a entrar con `ana` / `incorrecta`. Verás el mensaje de error
   genérico *Invalid username or password* (no revela cuál de los dos
   falló, según buenas prácticas OWASP).

**Resultado esperado.** Ana se autentica correctamente en la consola de
cuenta; la sesión aparece en la Admin Console; una contraseña incorrecta
es rechazada con un mensaje genérico. Ahora existe un proveedor de
identidad. Lo que falta es que la aplicación lo use: laboratorio 04.

## 8. Lista de verificación

- [ ] `docker compose ps` muestra el contenedor `keycloak` en estado *running*.
- [ ] Entras en <http://localhost:8080> con `admin` / `admin`.
- [ ] Existe el realm `curso`.
- [ ] Existe el usuario `ana` con contraseña no temporal y correo verificado.
- [ ] `curl .../curso/.well-known/openid-configuration` devuelve JSON con `issuer` = `http://localhost:8080/realms/curso`.
- [ ] Ana entra en la Account Console y su sesión aparece en *Sessions*.

## 9. Punto de control

`keycloak/curso-realm.json` contiene el realm tal como debe quedar al
terminar este laboratorio. Si necesitas restaurarlo, sigue las
instrucciones de `keycloak/README.md` en la raíz del repositorio.

Para exportar tu propio realm (incluye usuarios, útil para respaldos):

```bash
docker exec keycloak /opt/keycloak/bin/kc.sh export \
  --file /opt/keycloak/data/export-curso.json --realm curso --users realm_file
docker cp keycloak:/opt/keycloak/data/export-curso.json ./curso-realm.json
```

## 10. Problemas frecuentes

**El navegador de Windows no abre `localhost:8080`.**
Comprueba `docker compose ps`. Si el contenedor está arriba pero no
responde, cambia en `docker-compose.yml` la línea de puertos a
`"8080:8080"` (sin `127.0.0.1`) y reinicia con `docker compose up -d`.

**Login de admin falla la segunda vez que arrancas.**
Las variables `KC_BOOTSTRAP_ADMIN_*` solo crean el usuario en el primer
arranque con datos vacíos. Si hiciste `docker compose down -v` se crea de
nuevo; si no, la contraseña es la que tenía.

**Olvidé desmarcar *Temporary* en la contraseña.**
Al entrar, Keycloak pedirá una nueva. Ponla y anótala; o vuelve a
*Credentials → Reset password* con *Temporary: Off*.

**El correo no está verificado y pide verificación.**
No hay SMTP configurado. Edita el usuario y activa *Email verified*.

**Se ve la Admin Console de `master` en la ventana de incógnito.**
Estás abriendo `/admin` en vez de `/realms/curso/account`. Revisa la URL.

## 11. Siguiente laboratorio

`laboratorio-04-autenticacion-oidc`: registrar la aplicación como cliente
OIDC confidencial y exigir inicio de sesión para `/privada` con
Authorization Code + PKCE.
