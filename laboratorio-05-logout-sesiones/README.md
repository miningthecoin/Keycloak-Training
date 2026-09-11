# Laboratorio 05 · Cierre de sesión y tiempos de vida de sesión

**Tipo:** práctico · **Duración:** 60 min · **Funcionalidad de Keycloak:** RP-Initiated Logout y tiempos de sesión y token del realm

## 1. Objetivo

Que "Cerrar sesión" cierre la sesión de verdad: en la aplicación **y** en
Keycloak. Y que una sesión abandonada caduque sola. Al terminar:

- El botón **Cerrar sesión** invalida la sesión local de Spring y, a
  continuación, la sesión SSO de Keycloak. Pulsar **Clientes** después
  vuelve a pedir usuario y contraseña.
- La sesión desaparece de **Sessions** en la Admin Console.
- Tras 3 minutos sin actividad, la sesión caduca en ambos sitios.
- La aplicación sigue sin incluir ninguna librería de Keycloak.

Fuentes oficiales:

- Server Administration Guide, *OIDC Logout* y *RP-Initiated Logout*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_oidc-logout>
- Server Administration Guide, *Managing user sessions* y *Session and
  token timeouts*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#managing-user-sessions>
  y <https://www.keycloak.org/docs/latest/server_admin/index.html#_timeouts>
- Server Administration Guide, *Logout settings* del cliente OIDC:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#logout-settings>
- OpenID Connect en Keycloak, endpoint de logout (solo por redirección
  del navegador, nunca invocado directamente desde la aplicación):
  <https://www.keycloak.org/securing-apps/oidc-layers>

## 2. Conceptos

### 2.1 Dos sesiones independientes

Desde el laboratorio 04 hay **dos sesiones** distintas para el mismo
usuario, y es fundamental no confundirlas:

| Sesión | Quién la guarda | Cómo se identifica | Caducidad por defecto |
|---|---|---|---|
| Sesión **local** de la aplicación | Spring (Tomcat) en el puerto 8081 | cookie `JSESSIONID` | 30 min sin actividad |
| Sesión **SSO** del realm | Keycloak en el puerto 8080 | cookie `KEYCLOAK_IDENTITY` (dominio de Keycloak) | *SSO Session Idle* 30 min, *SSO Session Max* 10 h |

Cuando `ana` inicia sesión, Keycloak crea la sesión SSO y, dentro de ella,
una **sesión de cliente** para `aplicacion-base` (la guía la llama *client
session*; una por cada aplicación que participa en el SSO). La aplicación,
por su parte, guarda en su sesión local el `OidcUser` con el ID Token.

Las dos viven separadas. Cerrar una **no** cierra la otra:

- Si solo se invalida la sesión local (lo que hace Spring por defecto),
  la sesión SSO sigue viva. La próxima petición a `/privada` redirige a
  Keycloak, que ve su cookie y devuelve un `code` nuevo **sin pedir
  contraseña**. El usuario cree que salió, pero no.
- Si solo caduca la sesión SSO (por inactividad en Keycloak), la sesión
  local sigue mostrando `/privada` hasta que ella misma caduque. La
  aplicación no consulta a Keycloak en cada petición: solo valida el ID
  Token al iniciar sesión.

Este laboratorio resuelve lo primero con RP-Initiated Logout y lo segundo
alineando la caducidad de ambas sesiones.

### 2.2 RP-Initiated Logout

Es el cierre de sesión que **inicia la aplicación** (el *Relying Party*)
redirigiendo el navegador al endpoint de logout de Keycloak. La guía lo
describe en *OIDC Logout → RP-Initiated Logout*. Keycloak publica el
endpoint como `end_session_endpoint` en el documento de descubrimiento:

```
http://localhost:8080/realms/curso/protocol/openid-connect/logout
```

Parámetros que enviará nuestra aplicación:

- **`id_token_hint`**: el ID Token que Keycloak emitió al iniciar sesión.
  Le dice a Keycloak qué sesión cerrar y de qué cliente. Según la guía,
  si no se envía, Keycloak puede pedir al usuario que **confirme** el
  cierre en una página intermedia.
- **`post_logout_redirect_uri`**: adónde devolver al usuario. La guía
  exige que vaya acompañada de `id_token_hint` o `client_id`, y que
  coincida con una de las **Valid post logout redirect URIs** del cliente.
  Es la misma lógica que las *Valid redirect URIs* del laboratorio 04:
  Keycloak nunca redirige a una URL que no esté registrada.

```
Navegador                Aplicación (8081)              Keycloak (8080)
   │  POST /logout (+ _csrf)   │                              │
   │─────────────────────────▶│ invalida sesión local        │
   │  302 → /logout?id_token_hint&post_logout_redirect_uri   │
   │◀─────────────────────────│                              │
   │  GET /logout?id_token_hint=...&post_logout_redirect_uri=...
   │────────────────────────────────────────────────────────▶│
   │                          │        cierra sesión SSO     │
   │  302 → http://localhost:8081/                            │
   │◀────────────────────────────────────────────────────────│
   │  GET /   (Sesión: anónimo)│                              │
   │─────────────────────────▶│                              │
```

### 2.3 Front-channel y back-channel logout: fuera de alcance

La guía describe otros dos mecanismos que **no** usaremos:

- **Front-Channel Logout**: Keycloak avisa a las demás aplicaciones del
  SSO cargando `iframes` en su página de logout. El cliente tiene la
  opción *Front channel logout* activada por defecto; la dejamos como está
  porque nuestra aplicación es la única del realm y es ella quien inicia
  el cierre.
- **Back-Channel Logout**: Keycloak envía un `POST` con un *logout token*
  a la URL que registre cada aplicación, sin pasar por el navegador. Es
  el mecanismo que la guía recomienda cuando hay varias aplicaciones o
  cuando un administrador cierra sesiones desde la consola. Requiere un
  endpoint en la aplicación que no implementaremos en este curso.

Con una sola aplicación, RP-Initiated Logout es suficiente y es el
comportamiento estándar de Spring Security.

### 2.4 Tiempos de vida en Keycloak

Se configuran en **Realm settings**, pestañas *Sessions* y *Tokens*.
Definiciones tomadas de *Session and token timeouts*:

| Ajuste | Qué controla | Por defecto | En este lab |
|---|---|---|---|
| **SSO Session Idle** | Si el usuario está inactivo más de este tiempo, la sesión SSO se invalida. El contador se reinicia cuando un cliente pide autenticación o refresca un token. | 30 min | **3 min** |
| **SSO Session Max** | Tiempo máximo de vida de la sesión SSO, haya o no actividad. | 10 h | **1 h** |
| Client Session Idle / Max | Lo mismo, pero para la sesión de cada cliente dentro del SSO. A cero, heredan los valores SSO. | 0 | sin cambios |
| **Access Token Lifespan** | Tiempo de vida del *access token* que Keycloak emite. | 5 min | **1 min** |

Sobre los **3 minutos**: PCI DSS 8.2.8 exige que una sesión inactiva más
de **15 minutos** obligue a autenticarse de nuevo. Usamos 3 minutos solo
para poder **observar** la caducidad durante la clase; en un sistema real
el valor sería 15 minutos o menos.

Sobre el **Access Token Lifespan**: lo configuramos ahora porque es el
tercer tiempo importante del realm, pero en el estado actual de la
aplicación **no tiene ningún efecto visible**. La aplicación solo usa el
ID Token para iniciar sesión; el *access token* no se envía a ninguna API
hasta el laboratorio 11. No busques todavía un cambio de comportamiento
por este valor.

### 2.5 Qué hace Spring Security por nosotros

- Ya expone `POST /logout`: invalida la sesión HTTP, borra el
  `SecurityContext` y la cookie `JSESSIONID`. Exige el token **CSRF**
  porque cerrar sesión cambia estado en el servidor; por eso el botón es
  un formulario `POST` y no un enlace.
- `OidcClientInitiatedLogoutSuccessHandler` es su implementación estándar
  de RP-Initiated Logout: lee el `end_session_endpoint` del documento de
  descubrimiento y construye la redirección con `id_token_hint` y
  `post_logout_redirect_uri`. Nada específico de Keycloak.
- `server.servlet.session.timeout` fija la caducidad de la sesión local.

## 3. Relación con OWASP y PCI DSS

- **OWASP A07:2021 Fallos de identificación y autenticación.** Incluye
  explícitamente "no invalidar correctamente las sesiones al cerrar
  sesión o tras un periodo de inactividad". Es exactamente la debilidad
  del ejercicio ANTES.
- **OWASP ASVS V3 Gestión de sesión.** V3.3 *Terminación de sesión*: el
  cierre de sesión debe invalidar la sesión en el servidor, de forma que
  la cookie anterior deje de servir; y la sesión debe expirar tras un
  periodo de inactividad. Tras este laboratorio, "Cerrar sesión" invalida
  las dos sesiones y ambas caducan por inactividad.
- **PCI DSS 8.2.8.** Una sesión inactiva más de 15 minutos exige volver a
  autenticarse. El requisito habla de la **sesión** del usuario, no del
  token. Aquí se implementa con *SSO Session Idle* en Keycloak y con la
  caducidad de la sesión local en la aplicación (3 minutos en clase, 15 o
  menos en producción).

## 4. Ejercicio ANTES: "salir" no cierra la sesión SSO

**Propósito.** Evidenciar que, en el estado del laboratorio 04, el cierre
de sesión de Spring solo es local y Keycloak sigue reconociendo al usuario.

1. Arranca tu aplicación tal como quedó en el laboratorio 04:
   ```bash
   cd ~/keycloak-curso/aplicacion_base
   export KEYCLOAK_CLIENT_SECRET='el-secreto-de-tu-cliente'
   mvn spring-boot:run
   ```
2. Ventana de incógnito → <http://localhost:8081> → **Clientes** → entra
   con `ana` / `ana123`. La cabecera muestra `Sesión: ana`.
3. Admin Console → realm `curso` → **Sessions**. Aparece `ana` con el
   cliente `aplicacion-base`.
4. La aplicación no tiene botón de salir, pero Spring Security ya expone
   la ruta. Abre <http://localhost:8081/logout>. Verás una página de
   Spring titulada **Confirm Log Out?** con un botón *Log Out*: es el
   formulario `POST /logout` con su token CSRF. Púlsalo.
5. Llegas a `http://localhost:8081/login?logout` con el mensaje *You have
   been signed out*. Vuelve a <http://localhost:8081>: la cabecera dice
   `Sesión: anónimo`. Parece que saliste.
6. Pulsa **Clientes**.

**Resultado esperado.**
- Vuelves a `/privada` como `Sesión: ana` **sin que Keycloak haya pedido
  la contraseña**. La redirección a Keycloak fue instantánea porque su
  cookie de sesión SSO seguía viva.
- En **Sessions** la sesión de `ana` sigue ahí. Solo se cerró la sesión
  local de la aplicación.

Con `curl` se ve el detalle: el `POST /logout` responde con una
redirección a la propia aplicación, sin pasar por Keycloak.

```bash
# JSESSIONID: cópialo de las DevTools del navegador (Application → Cookies → localhost:8081)
# _csrf: valor del campo oculto en el código fuente de http://localhost:8081/logout
curl -s -o /dev/null -D - -b "JSESSIONID=<valor>" --data-urlencode "_csrf=<token>" \
  http://localhost:8081/logout | grep -i '^location'
```

Resultado esperado: `location: http://localhost:8081/login?logout`.

Un ordenador compartido, un quiosco o un portátil prestado: quien toque
"Clientes" después de que `ana` "salga" entra como `ana`. Detén la
aplicación con `Ctrl+C`.

## 5. Configuración de Keycloak, paso a paso

Con Keycloak arrancado y la Admin Console en el realm **`curso`**:

### 5.1 URI de retorno tras el logout

1. **Clients → aplicacion-base**, pestaña **Settings**.
2. Sección **Access settings** → *Valid post logout redirect URIs*:
   `http://localhost:8081/` (con la barra final; la comparación es exacta).
3. **Save**.

En la sección **Logout settings** de la misma pestaña verás *Front channel
logout* activado y *Backchannel logout URL* vacío. Déjalos como están
(ver 2.3).

### 5.2 Tiempos de sesión

1. **Realm settings**, pestaña **Sessions**.
2. *SSO Session Idle*: `3` **Minutes**.
3. *SSO Session Max*: `1` **Hours**.
4. **Save** (botón al pie de la sección *SSO Session Settings*).

### 5.3 Tiempo de vida del access token

1. **Realm settings**, pestaña **Tokens**.
2. *Access Token Lifespan*: `1` **Minutes**.
3. **Save**.

Recuerda (2.4): este valor no cambiará nada visible hasta el laboratorio 11.

## 6. Cambios en la aplicación

Trabaja sobre `~/keycloak-curso/aplicacion_base`. El resultado completo
está en `aplicacion_base_lab-05/`.

### 6.1 `SeguridadConfig.java`: logout en la app y en Keycloak

Añade dos imports:

```java
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
```

En `cadenaDeFiltros`, después del bloque `.oauth2Login(...)`, añade el
bloque `.logout(...)`:

```java
            // ---- Inicio de sesión con OpenID Connect ----------------
            .oauth2Login(oidc -> oidc
                .authorizationEndpoint(endpoint -> endpoint
                    .authorizationRequestResolver(resolutorConPkce(registros))
                )
            )
            // ---- Cierre de sesión (LABORATORIO 05) -------------------
            // Spring Security ya expone POST /logout (protegido con token
            // CSRF): invalida la sesión HTTP y borra la cookie JSESSIONID.
            // Eso solo cierra la sesión LOCAL. Con este manejador, al
            // terminar el logout local el navegador es enviado a Keycloak
            // para cerrar también la sesión SSO (ver método de abajo).
            .logout(salida -> salida
                .logoutSuccessHandler(cierreDeSesionEnKeycloak(registros))
            );
```

Y al final de la clase, el método nuevo:

```java
    /**
     * LABORATORIO 05: RP-Initiated Logout (OpenID Connect).
     *
     * OidcClientInitiatedLogoutSuccessHandler es la implementación estándar
     * de Spring Security. Tras el logout local, redirige el navegador al
     * "end_session_endpoint" que Keycloak publica en su documento de
     * descubrimiento (/realms/curso/.well-known/openid-configuration),
     * añadiendo:
     *
     *   - id_token_hint: el ID Token de la sesión que se cierra. Keycloak
     *     lo usa para saber qué sesión terminar y a qué cliente pertenece,
     *     sin pedir confirmación al usuario.
     *   - post_logout_redirect_uri: adónde volver al terminar. Debe estar
     *     en "Valid post logout redirect URIs" del cliente en Keycloak.
     *
     * "{baseUrl}" lo sustituye Spring por http://localhost:8081, así que
     * el usuario acaba en la portada, ya sin sesión en ningún sitio.
     */
    private LogoutSuccessHandler cierreDeSesionEnKeycloak(ClientRegistrationRepository registros) {
        OidcClientInitiatedLogoutSuccessHandler manejador =
            new OidcClientInitiatedLogoutSuccessHandler(registros);
        manejador.setPostLogoutRedirectUri("{baseUrl}/");
        return manejador;
    }
```

Actualiza también el comentario de cabecera de la clase para que
mencione el laboratorio 05 (ver el archivo completo en
`aplicacion_base_lab-05/`).

### 6.2 `fragmentos.html`: botón "Cerrar sesión"

Sustituye el bloque `<span class="identidad">…</span>` por:

```html
    <span class="identidad">
        Sesión: <strong th:text="${usuario}">anónimo</strong>
        <a th:unless="${autenticado}" th:href="@{/oauth2/authorization/keycloak}">Iniciar sesión</a>
        <form th:if="${autenticado}" th:action="@{/logout}" method="post" class="salir">
            <button type="submit">Cerrar sesión</button>
        </form>
    </span>
```

Y añade al comentario de la cabecera:

```html
  LABORATORIO 05: si hay sesión se muestra el botón "Cerrar sesión".
  Es un formulario POST a /logout, la ruta que Spring Security crea por
  defecto. Debe ser POST y no un enlace GET: cerrar sesión cambia estado
  en el servidor y Spring exige el token CSRF para aceptarlo. Gracias a
  la integración Thymeleaf + Spring Security, th:action añade solo el
  campo oculto "_csrf" al formulario (mira el código fuente de la página).
```

No hace falta escribir el campo oculto a mano: con `th:action`, Thymeleaf
lo inserta automáticamente. Lo comprobarás en 7.2.

### 6.3 `estilos.css`: el botón como un enlace más

Añade tras la regla `.identidad`:

```css
/* LABORATORIO 05: el botón "Cerrar sesión" se muestra como un enlace
   más de la cabecera, aunque por debajo sea un formulario POST. */
.salir {
    display: inline;
    margin-left: 0.5rem;
}

.salir button {
    font: inherit;
    color: var(--acento);
    background: none;
    border: none;
    padding: 0;
    text-decoration: underline;
    cursor: pointer;
}
```

Y cambia la última línea del archivo por:

```css
a:focus-visible, button:focus-visible { outline: 3px solid var(--acento); outline-offset: 2px; }
```

### 6.4 `application.yml`: caducidad de la sesión local

Amplía el bloque `server:` del principio del archivo:

```yaml
server:
  port: 8081
  servlet:
    session:
      # ------------------------------------------------------------
      # LABORATORIO 05: caducidad por inactividad de la sesión LOCAL.
      #
      # La aplicación mantiene su propia sesión HTTP (cookie JSESSIONID),
      # independiente de la sesión SSO de Keycloak. Spring Boot la caduca
      # a los 30 minutos por defecto. La igualamos al "SSO Session Idle"
      # configurado en el realm (3 minutos, valor didáctico) para que
      # ambas sesiones expiren a la vez y la caducidad se pueda observar
      # en clase. En producción PCI DSS 8.2.8 exige 15 minutos o menos.
      # ------------------------------------------------------------
      timeout: 3m
```

Sin esta línea, tras caducar la sesión SSO la aplicación seguiría
mostrando `/privada` durante hasta 30 minutos (ver 2.1).

### 6.5 Arrancar

```bash
cd ~/keycloak-curso/aplicacion_base
export KEYCLOAK_CLIENT_SECRET='el-secreto-de-tu-cliente'
mvn spring-boot:run
```

## 7. Ejercicio DESPUÉS: el cierre de sesión es real

### 7.1 En el navegador

1. Ventana de incógnito → <http://localhost:8081> → **Clientes** → entra
   con `ana` / `ana123`. En la cabecera aparece `Sesión: ana` y el botón
   **Cerrar sesión**.
2. Admin Console → **Sessions**: `ana` con `aplicacion-base`.
3. Pulsa **Cerrar sesión**. En una fracción de segundo pasas por Keycloak
   y vuelves a la portada con `Sesión: anónimo`.
4. Recarga **Sessions** en la Admin Console.
5. Pulsa **Clientes**.

**Resultado esperado.**
- En el paso 4, la sesión de `ana` **ha desaparecido**.
- En el paso 5, Keycloak muestra la **pantalla de login** y pide usuario
  y contraseña. Ya no hay sesión SSO que reutilizar.

### 7.2 Inspeccionar la redirección con curl

Inicia sesión otra vez en el navegador y, sin cerrarla, copia dos valores:

- `JSESSIONID`: DevTools → *Application* → *Cookies* → `localhost:8081`.
- `_csrf`: en el código fuente de la página (`Ctrl+U`), el campo oculto
  del formulario de la cabecera. Observa que Thymeleaf lo añadió solo:
  ```html
  <form action="/logout" method="post" class="salir">
  <input type="hidden" name="_csrf" value="..."/>
  ```

Ejecuta el `POST /logout` desde `curl` con esa sesión:

```bash
curl -s -o /dev/null -D - -b "JSESSIONID=<valor>" --data-urlencode "_csrf=<token>" \
  http://localhost:8081/logout | grep -i '^location' | tr '&' '\n'
```

Resultado esperado:

```
location: http://localhost:8080/realms/curso/protocol/openid-connect/logout?id_token_hint=eyJ...
post_logout_redirect_uri=http://localhost:8081/
```

Compara con el ANTES: ahora la redirección va al `end_session_endpoint`
de Keycloak, con el ID Token de la sesión y la URI de retorno registrada.
Este `curl` ya invalidó la sesión local; pega la URL del `location` en
el navegador para completar el cierre en Keycloak y comprueba que
**Sessions** queda vacío.

### 7.3 Keycloak valida la URI de retorno

Simula una aplicación que intenta devolver al usuario a un sitio no
registrado:

```bash
curl -s -o /dev/null -w "HTTP %{http_code}\n" \
  "http://localhost:8080/realms/curso/protocol/openid-connect/logout?client_id=aplicacion-base&post_logout_redirect_uri=http://atacante.test/"
```

Resultado esperado: `HTTP 400`. Abre la URL en el navegador: Keycloak
muestra *Invalid redirect uri*. Igual que en el laboratorio 04, Keycloak
solo redirige a URIs registradas.

Prueba ahora sin `id_token_hint` pero con la URI correcta, en el
navegador con sesión iniciada:

```
http://localhost:8080/realms/curso/protocol/openid-connect/logout?client_id=aplicacion-base&post_logout_redirect_uri=http://localhost:8081/
```

Resultado esperado: Keycloak muestra una página **Logging out** que
pregunta *Do you want to log out?*. Sin el ID Token no sabe con certeza
qué sesión cerrar y pide confirmación, tal como describe la guía.
Nuestra aplicación no pasa por aquí porque siempre envía `id_token_hint`.

### 7.4 Caducidad por inactividad

1. Inicia sesión con `ana` y comprueba que **Sessions** la lista.
2. No toques la aplicación ni la Admin Console durante **3 minutos y
   medio** (el reloj de la sesión SSO se reinicia con cada autenticación).
3. Recarga **Sessions**.
4. En la aplicación pulsa **Clientes**.

**Resultado esperado.**
- Paso 3: la sesión de `ana` ya no aparece. *SSO Session Idle* actuó.
- Paso 4: la sesión local también caducó (3 minutos), la aplicación
  redirige a Keycloak y Keycloak pide usuario y contraseña.

Si en el paso 4 la aplicación mostrara `/privada` directamente, revisa
6.4: la sesión local no está caducando.

## 8. Lista de verificación

- [ ] *Valid post logout redirect URIs* del cliente contiene exactamente `http://localhost:8081/`.
- [ ] *SSO Session Idle* = 3 min, *SSO Session Max* = 1 h, *Access Token Lifespan* = 1 min.
- [ ] `SeguridadConfig` tiene el bloque `.logout(...)` con `OidcClientInitiatedLogoutSuccessHandler` y `{baseUrl}/`.
- [ ] La cabecera muestra **Cerrar sesión** solo con sesión iniciada, como formulario `POST` con campo `_csrf`.
- [ ] `application.yml` fija `server.servlet.session.timeout: 3m`.
- [ ] Tras **Cerrar sesión**, **Clientes** pide contraseña y **Sessions** no lista a `ana`.
- [ ] El `POST /logout` redirige al `end_session_endpoint` con `id_token_hint` y `post_logout_redirect_uri`.
- [ ] Una `post_logout_redirect_uri` no registrada devuelve `HTTP 400`.
- [ ] Tras 3,5 minutos de inactividad, la sesión desaparece y hay que volver a autenticarse.

## 9. Punto de control

`keycloak/curso-realm.json` incluye el cliente con
`post.logout.redirect.uris`, los tres tiempos del realm y el secreto
`secreto-lab04-cambialo-en-produccion`. Si restauras desde este archivo,
usa ese valor en `KEYCLOAK_CLIENT_SECRET`.

## 10. Problemas frecuentes

**Al pulsar Cerrar sesión, Keycloak muestra `Invalid redirect uri`**
La URI de 5.1 no coincide letra por letra con `http://localhost:8081/`.
Revisa la barra final y el puerto. Keycloak compara de forma exacta.

**Al pulsar Cerrar sesión, Keycloak pregunta *Do you want to log out?***
No llegó `id_token_hint`. Ocurre si el logout no pasa por
`OidcClientInitiatedLogoutSuccessHandler` (bloque `.logout(...)` ausente)
o si el usuario no inició sesión por OIDC.

**`POST /logout` responde 403**
Falta el token CSRF. El formulario debe usar `th:action="@{/logout}"`
para que Thymeleaf añada el campo `_csrf`; un `action="/logout"` escrito
a mano no lo incluye.

**Aparece la página *Confirm Log Out?* de Spring**
Se hizo `GET /logout` (un enlace) en vez de `POST`. El botón debe estar
dentro del formulario.

**Tras Cerrar sesión, la cabecera sigue diciendo `Sesión: ana`**
El navegador volvió a `/privada` y Keycloak reutilizó la sesión SSO: el
cierre fue solo local. Comprueba que el `location` del `POST /logout`
apunta a Keycloak (7.2).

**Pasados los 3 minutos la aplicación sigue mostrando `/privada`**
La sesión local no caduca: revisa la indentación de
`server.servlet.session.timeout` en `application.yml` y reinicia la
aplicación. Los cambios en `application.yml` no se aplican en caliente.

**En Sessions aparecen varias sesiones de `ana`**
Cada ventana de incógnito o navegador distinto crea su propia sesión
SSO. **Cerrar sesión** solo cierra la de ese navegador. Para cerrar todas
usa *Action → Sign out all active sessions* en la Admin Console.

## 11. Siguiente laboratorio

`laboratorio-06-autorizacion-rbac`: hoy **cualquier** usuario autenticado
ve `/privada`. El siguiente laboratorio crea roles en Keycloak y hace que
la aplicación exija el rol adecuado.
