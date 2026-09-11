# Laboratorio 04 · Autenticación con OpenID Connect (Authorization Code + PKCE)

**Tipo:** práctico · **Duración:** 90 min · **Funcionalidad de Keycloak:** cliente OpenID Connect confidencial con *Standard flow* y PKCE

## 1. Objetivo

Registrar la aplicación como **cliente** del realm `curso` y hacer que la
sección `/privada` exija iniciar sesión en Keycloak. Al terminar:

- Un visitante anónimo que pida `/privada` es enviado a la pantalla de
  login de Keycloak.
- Tras autenticarse, vuelve a la aplicación y la cabecera muestra su
  nombre de usuario.
- La aplicación no guarda contraseñas, no tiene formulario de login y no
  incluye ninguna librería de Keycloak: solo Spring Security y el
  protocolo estándar.

Fuentes oficiales:

- Getting started – Docker, sección *Secure the first application*:
  <https://www.keycloak.org/getting-started/getting-started-docker>
- Planificación (por qué no usar adaptadores):
  <https://www.keycloak.org/securing-apps/overview>
- OpenID Connect en Keycloak (flujos, endpoints, PKCE):
  <https://www.keycloak.org/securing-apps/oidc-layers>
- Server Administration Guide, *Managing OpenID Connect clients* y
  *Mitigating security threats → Unspecific redirect URIs*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html>

## 2. Conceptos

### 2.1 Cliente confidencial frente a cliente público

Un **cliente** es la aplicación registrada en Keycloak. Se clasifica según
pueda o no guardar un secreto:

| Tipo | Ejemplo | Puede guardar un secreto | En Keycloak |
|---|---|---|---|
| Confidencial | Aplicación web con servidor (nuestra app Spring) | Sí, en el servidor | *Client authentication: On* |
| Público | SPA en el navegador, app móvil | No, cualquiera vería el código | *Client authentication: Off* |

Nuestra app es confidencial: el secreto vive en el servidor, en una
variable de entorno, y se usa para que Keycloak sepa que es realmente
nuestra aplicación la que canjea el código.

### 2.2 Authorization Code flow

Es el flujo que la guía OIDC de Keycloak recomienda para aplicaciones
web. Los flujos *Implicit* y *Resource Owner Password* están
desaconsejados por RFC 9700 y eliminados en OAuth 2.1; por eso los
dejamos desactivados en el cliente.

```
Navegador                Aplicación (8081)              Keycloak (8080)
   │  GET /privada             │                              │
   │─────────────────────────▶│  no hay sesión               │
   │  302 → Keycloak /auth     │                              │
   │◀─────────────────────────│                              │
   │  GET /auth?client_id&redirect_uri&state&nonce&code_challenge│
   │────────────────────────────────────────────────────────▶│
   │  pantalla de login; usuario escribe ana / ana123        │
   │◀───────────────────────────────────────────────────────▶│
   │  302 → /login/oauth2/code/keycloak?code=...&state=...    │
   │◀────────────────────────────────────────────────────────│
   │  GET /login/oauth2/code/keycloak?code=...&state=...      │
   │─────────────────────────▶│                              │
   │                          │ POST /token (code, secret,   │
   │                          │   code_verifier)             │
   │                          │─────────────────────────────▶│
   │                          │ ID Token + Access Token      │
   │                          │◀─────────────────────────────│
   │                          │ valida ID Token con JWKS     │
   │  302 → /privada (sesión creada)                          │
   │◀─────────────────────────│                              │
```

Elementos de seguridad del flujo:

- **`state`**: valor aleatorio que la app genera y comprueba a la vuelta.
  Evita CSRF en el login. Spring lo hace solo.
- **`nonce`**: valor aleatorio que viaja al ID Token; evita reutilizar un
  token de otra sesión. Spring lo hace solo.
- **`code_challenge` / `code_verifier` (PKCE)**: la app envía el hash del
  verificador al pedir el código y el verificador original al canjearlo.
  Si alguien intercepta el `code`, no puede canjearlo sin el verificador.
  Spring solo lo activa por defecto en clientes públicos; lo activaremos
  también en el nuestro (recomendación OAuth 2.1).
- **Validación del ID Token**: Spring comprueba firma (con las claves
  públicas del realm, `jwks_uri`), emisor (`iss`), audiencia (`aud` =
  nuestro `client_id`), caducidad (`exp`) y `nonce`. Es la validación
  local que la guía OIDC de Keycloak recomienda en lugar de llamar al
  endpoint de introspección en cada petición.

### 2.3 Redirect URIs: por qué deben ser exactas

Keycloak solo devuelve el `code` a URIs incluidas en *Valid redirect
URIs*. El capítulo *Mitigating security threats* advierte de que un
comodín demasiado amplio (`http://localhost:8081/*` o `*`) permite a un
atacante redirigir el código a una página que controle. Registraremos la
URI exacta que Spring usa: `http://localhost:8081/login/oauth2/code/keycloak`.

### 2.4 Qué hace Spring Security por nosotros

Con `spring-boot-starter-oauth2-client` y una configuración `issuer-uri`,
Spring:

1. Descarga `/.well-known/openid-configuration` al arrancar y aprende los
   endpoints de Keycloak.
2. Crea la ruta `/oauth2/authorization/keycloak` que inicia el login.
3. Crea la ruta `/login/oauth2/code/keycloak` que recibe el `code`.
4. Canjea el `code`, valida el ID Token y crea una sesión HTTP local con
   un `OidcUser` como principal.

Nada de esto es específico de Keycloak. Cambiar de proveedor sería
cambiar el `issuer-uri`.

## 3. Relación con OWASP y PCI DSS

- **OWASP A07:2021 Fallos de identificación y autenticación.** La app no
  identifica a nadie. Tras el laboratorio, la identificación la realiza
  un componente dedicado, con protocolo estándar y validación de tokens.
- **OWASP ASVS V2.1 / V3.5**: autenticación centralizada; uso de
  protocolos probados en lugar de código propio; tokens validados
  criptográficamente.
- **PCI DSS 8.2.1**: todos los usuarios tienen un identificador único
  antes de acceder a datos del titular de la tarjeta. **8.2.2**: no se
  usan credenciales compartidas. **8.6.2**: las credenciales de
  aplicación (nuestro *client secret*) no se codifican en el código
  fuente.

## 4. Ejercicio ANTES: la sección privada es pública

**Propósito.** Evidenciar que `/privada` no comprueba nada.

1. Arranca la aplicación base:
   ```bash
   cd ~/keycloak-curso/aplicacion_base
   mvn spring-boot:run
   ```
2. Desde otra terminal, pide la sección privada sin ninguna credencial:
   ```bash
   curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:8081/privada
   curl -s http://localhost:8081/privada | grep -o '\*\*\*\* \*\*\*\* \*\*\*\* [0-9]*'
   ```
3. Abre <http://localhost:8081/privada> en una ventana de incógnito.

**Resultado esperado.**
- `HTTP 200` y las tres referencias de tarjeta (`**** **** **** 4242`,
  `0005`, `1117`) en la respuesta de `curl`.
- En incógnito, la tabla completa con `Sesión: anónimo`.

Un script, un buscador o cualquier persona con la URL obtiene datos de
titulares de tarjeta. Detén la aplicación con `Ctrl+C`.

## 5. Configuración de Keycloak, paso a paso

Con Keycloak arrancado (`docker compose up -d` en `~/keycloak-curso/keycloak`)
y la Admin Console abierta en el realm **`curso`**:

### 5.1 Crear el cliente

1. Menú **Clients → Create client**.
2. **General settings**:
   - *Client type*: `OpenID Connect`
   - *Client ID*: `aplicacion-base`
   - *Name*: `Aplicación base del curso`
   - **Next**.
3. **Capability config**:
   - *Client authentication*: **On** (cliente confidencial).
   - *Authorization*: Off.
   - *Authentication flow*: marca solo **Standard flow**. Desmarca
     *Direct access grants* si aparece marcado. Deja *Implicit flow*,
     *Service accounts roles* y los demás sin marcar.
   - **Next**.
4. **Login settings**:
   - *Root URL*: `http://localhost:8081`
   - *Home URL*: `/`
   - *Valid redirect URIs*: `http://localhost:8081/login/oauth2/code/keycloak`
   - *Valid post logout redirect URIs*: déjalo vacío (laboratorio 05).
   - *Web origins*: `http://localhost:8081`
   - **Save**.

### 5.2 Exigir PKCE

1. En el cliente recién creado, pestaña **Advanced**.
2. Sección **Advanced settings** → *Proof Key for Code Exchange Code
   Challenge Method*: `S256`.
3. **Save** (el botón de esa sección).

Con esto Keycloak **rechaza** cualquier petición de autorización que no
traiga `code_challenge` con método `S256`: responde con un error en lugar
de emitir un código. Lo comprobaremos en el ejercicio final.

### 5.3 Obtener el secreto

1. Pestaña **Credentials**.
2. *Client Authenticator*: `Client Id and Secret`.
3. Copia el valor de *Client secret*. Lo usarás en la variable de entorno
   del paso 6.6. No lo pegues en ningún archivo del repositorio.

## 6. Cambios en la aplicación

Trabaja sobre `~/keycloak-curso/aplicacion_base`. El resultado completo
está en `aplicacion_base_lab-04/`.

### 6.1 `pom.xml`: añadir Spring Security y el cliente OAuth2

Sustituye el comentario `LABORATORIO 04` dentro de `<dependencies>` por:

```xml
        <!--
          LABORATORIO 04: soporte OpenID Connect nativo de Spring Security.
          Ninguna dependencia org.keycloak.*: la aplicación habla el
          protocolo estándar y Keycloak es simplemente el proveedor.
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-client</artifactId>
        </dependency>
```

### 6.2 `application.yml`: registrar el cliente

Añade al final del archivo, dentro de `spring:`:

```yaml
  security:
    oauth2:
      client:
        registration:
          keycloak:
            # Debe coincidir con el "Client ID" creado en la Admin Console
            client-id: aplicacion-base
            # El secreto NUNCA se escribe en el código ni se sube al repo.
            # Se lee de la variable de entorno KEYCLOAK_CLIENT_SECRET.
            client-secret: ${KEYCLOAK_CLIENT_SECRET}
            # Flujo recomendado para aplicaciones web (RFC 9700 / OAuth 2.1)
            authorization-grant-type: authorization_code
            # "openid" convierte OAuth 2.0 en OpenID Connect (habrá ID Token).
            # "profile" y "email" piden nombre y correo del usuario.
            scope: openid, profile, email
            # URL a la que Keycloak devuelve el "code". Debe estar en la
            # lista "Valid redirect URIs" del cliente en Keycloak.
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
        provider:
          keycloak:
            # Emisor de los tokens. Spring descarga de aquí la
            # configuración OIDC y las claves públicas (JWKS).
            issuer-uri: http://localhost:8080/realms/curso
```

Cuidado con la indentación: `security` va al mismo nivel que
`application` y `thymeleaf`.

### 6.3 Nueva clase `seguridad/SeguridadConfig.java`

Crea el paquete `com.curso.keycloak.seguridad` y dentro:

```java
package com.curso.keycloak.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;

/**
 * LABORATORIO 04: reglas de acceso e inicio de sesión con OpenID Connect.
 *
 *   - Rutas públicas: la portada "/" y los recursos estáticos.
 *   - Resto de rutas: exigen usuario autenticado.
 *   - Método de autenticación: OpenID Connect contra Keycloak, con el
 *     flujo Authorization Code y PKCE (S256).
 *
 * No hay ninguna clase de Keycloak aquí. Todo es Spring Security estándar.
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    @Bean
    SecurityFilterChain cadenaDeFiltros(HttpSecurity http,
                                        ClientRegistrationRepository registros) throws Exception {
        http
            // ---- Autorización por ruta ------------------------------
            .authorizeHttpRequests(reglas -> reglas
                // La portada y los estáticos siguen siendo públicos
                .requestMatchers("/", "/estilos.css").permitAll()
                // Cualquier otra ruta (por ejemplo /privada) requiere login
                .anyRequest().authenticated()
            )
            // ---- Inicio de sesión con OpenID Connect ----------------
            .oauth2Login(oidc -> oidc
                // Sustituye el generador de la petición de autorización
                // por uno que añade PKCE (ver método de abajo)
                .authorizationEndpoint(endpoint -> endpoint
                    .authorizationRequestResolver(resolutorConPkce(registros))
                )
            );

        return http.build();
    }

    /**
     * PKCE (Proof Key for Code Exchange, RFC 7636).
     *
     * Spring Security lo activa solo para clientes públicos (sin secreto).
     * Nuestro cliente es confidencial, pero OAuth 2.1 y la guía OIDC de
     * Keycloak recomiendan PKCE también para confidenciales.
     */
    private OAuth2AuthorizationRequestResolver resolutorConPkce(ClientRegistrationRepository registros) {
        DefaultOAuth2AuthorizationRequestResolver resolutor =
            new DefaultOAuth2AuthorizationRequestResolver(
                registros,
                OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        resolutor.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolutor;
    }
}
```

### 6.4 `PortalControlador.java`: mostrar quién es el usuario

Añade dos imports:

```java
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
```

Sustituye el método `usuarioActual()` y añade `autenticado()`:

```java
    /**
     * LABORATORIO 04: Spring inyecta el usuario autenticado por OpenID
     * Connect (OidcUser). Sus datos proceden del ID Token que emitió
     * Keycloak y que Spring ya validó (firma, emisor, audiencia, caducidad).
     * Si nadie ha iniciado sesión, el parámetro llega nulo.
     */
    @ModelAttribute("usuario")
    public String usuarioActual(@AuthenticationPrincipal OidcUser usuario) {
        if (usuario == null) {
            return "anónimo";
        }
        // "preferred_username" es el claim estándar de OIDC con el nombre
        // de usuario; Keycloak lo rellena con el username del realm.
        return usuario.getPreferredUsername();
    }

    /**
     * Indica a las vistas si hay sesión iniciada, para mostrar el enlace
     * "Iniciar sesión" solo cuando corresponde.
     */
    @ModelAttribute("autenticado")
    public boolean autenticado(@AuthenticationPrincipal OidcUser usuario) {
        return usuario != null;
    }
```

El método `privada()` no cambia: Spring Security ya no deja llegar a él
sin sesión.

### 6.5 `fragmentos.html`: enlace de inicio de sesión

Sustituye la línea `<span class="identidad">…</span>` por:

```html
    <span class="identidad">
        Sesión: <strong th:text="${usuario}">anónimo</strong>
        <a th:unless="${autenticado}" th:href="@{/oauth2/authorization/keycloak}">Iniciar sesión</a>
    </span>
```

La ruta `/oauth2/authorization/keycloak` no la programamos nosotros: la
crea Spring Security a partir del nombre del registro (`keycloak`).

### 6.6 Arrancar con el secreto en el entorno

```bash
cd ~/keycloak-curso/aplicacion_base
export KEYCLOAK_CLIENT_SECRET='pega-aquí-el-secreto-copiado-en-5.3'
mvn spring-boot:run
```

En el log verás que Spring Security se activa. Si Keycloak no está
arrancado, la app fallará al inicio porque no puede descargar el
documento de descubrimiento del `issuer-uri`.

## 7. Ejercicio DESPUÉS: la sección privada exige identidad

**Propósito.** Comprobar que el acceso sin sesión se rechaza, que el flujo
lleva PKCE y que la identidad mostrada proviene de Keycloak.

### 7.1 Sin credenciales

```bash
curl -s -o /dev/null -w "HTTP %{http_code} → %{redirect_url}\n" http://localhost:8081/privada
```

Resultado esperado: `HTTP 302 → http://localhost:8081/oauth2/authorization/keycloak`.
Ya no hay datos en la respuesta. Compruébalo:

```bash
curl -s http://localhost:8081/privada | grep -c '\*\*\*\*'
```

Resultado esperado: `0`.

### 7.2 Inspeccionar la petición de autorización

```bash
curl -s -o /dev/null -w "%{redirect_url}\n" http://localhost:8081/oauth2/authorization/keycloak | tr '&' '\n'
```

Resultado esperado: una URL a `http://localhost:8080/realms/curso/protocol/openid-connect/auth`
con, entre otros, los parámetros:

```
response_type=code
client_id=aplicacion-base
scope=openid profile email
state=...
redirect_uri=http://localhost:8081/login/oauth2/code/keycloak
nonce=...
code_challenge=...
code_challenge_method=S256
```

Relaciona cada uno con la sección 2.2.

### 7.3 Flujo completo en el navegador

1. Ventana de incógnito → <http://localhost:8081>. La portada se ve;
   `Sesión: anónimo` y el enlace *Iniciar sesión*.
2. Pulsa **Clientes**. Eres redirigido a la pantalla de login de Keycloak
   (realm `curso`).
3. Entra con `ana` / `ana123`.
4. Vuelves a `/privada`. La cabecera muestra `Sesión: ana` y la tabla.
5. Ve a la Admin Console → realm `curso` → **Sessions**. La sesión de
   `ana` ahora lista el cliente `aplicacion-base`.

### 7.4 PKCE es obligatorio

Simula un cliente que no envía `code_challenge` (por ejemplo, una app mal
programada o un atacante intentando el flujo sin PKCE):

```bash
curl -s -o /dev/null -w "HTTP %{http_code}\n%{redirect_url}\n" \
  "http://localhost:8080/realms/curso/protocol/openid-connect/auth?response_type=code&client_id=aplicacion-base&redirect_uri=http://localhost:8081/login/oauth2/code/keycloak&scope=openid&state=x" \
  | tr '&' '\n'
```

Resultado esperado:

```
HTTP 302
http://localhost:8081/login/oauth2/code/keycloak?error=invalid_request
error_description=Missing+parameter%3A+code_challenge_method
state=x
iss=http%3A%2F%2Flocalhost%3A8080%2Frealms%2Fcurso
```

Keycloak no muestra la pantalla de login ni emite ningún `code`: devuelve
`error=invalid_request` a la `redirect_uri` registrada. Así es como OAuth
2.0 comunica los errores cuando la URI de retorno es válida; compáralo con
7.5, donde la URI no es válida y sí aparece una página de error.

Si abres esa misma URL en el navegador, la redirección llega a la app.
Spring Security recibe `error` en su ruta de callback y muestra su página
`/login?error` (*Please sign in*). No es un fallo de configuración: es la
aplicación rechazando la respuesta de error.

Repite la prueba añadiendo `&code_challenge=abcabcabcabcabcabcabcabcabcabcabcabcabcabcabc&code_challenge_method=plain`
a la URL. El mensaje cambia a *Invalid parameter: code challenge method is
not matching the configured one*: Keycloak solo acepta `S256`.

### 7.5 Redirect URI no registrada

```bash
xdg-open "http://localhost:8080/realms/curso/protocol/openid-connect/auth?response_type=code&client_id=aplicacion-base&redirect_uri=http://atacante.test/robo&scope=openid&state=x&code_challenge=abc&code_challenge_method=S256" 2>/dev/null || echo "Abre la URL en el navegador"
```

Resultado esperado: error *Invalid parameter: redirect_uri*. Keycloak
nunca enviará un código a una URI que no esté en la lista blanca.

## 8. Lista de verificación

- [ ] Existe el cliente `aplicacion-base`, confidencial, solo *Standard flow*.
- [ ] *Valid redirect URIs* contiene exactamente `http://localhost:8081/login/oauth2/code/keycloak`.
- [ ] PKCE `S256` configurado en *Advanced*.
- [ ] `pom.xml` incluye `spring-boot-starter-security` y `spring-boot-starter-oauth2-client`, y ningún `org.keycloak`.
- [ ] `KEYCLOAK_CLIENT_SECRET` se exporta en la terminal y no aparece en ningún archivo.
- [ ] `curl /privada` devuelve `302` a `/oauth2/authorization/keycloak`.
- [ ] La petición de autorización incluye `code_challenge_method=S256`, `state` y `nonce`.
- [ ] Tras el login la cabecera muestra `Sesión: ana`.
- [ ] Una petición sin PKCE o con `redirect_uri` desconocida es rechazada.

## 9. Punto de control

`keycloak/curso-realm.json` incluye el cliente con el secreto
`secreto-lab04-cambialo-en-produccion`. Si restauras desde este archivo,
usa ese valor en `KEYCLOAK_CLIENT_SECRET`.

## 10. Problemas frecuentes

**La app no arranca: `Unable to resolve Configuration with the provided Issuer`**
Keycloak no está arrancado o el realm no se llama `curso`. Comprueba
`curl http://localhost:8080/realms/curso/.well-known/openid-configuration`.

**Keycloak muestra `Invalid parameter: redirect_uri`**
La URI registrada no coincide letra por letra con
`http://localhost:8081/login/oauth2/code/keycloak`. Revisa barra final,
`http` vs `https` y el puerto.

**Tras el login, error `[invalid_client] ... Invalid client credentials`**
La variable `KEYCLOAK_CLIENT_SECRET` está vacía o mal copiada. Ejecuta
`echo $KEYCLOAK_CLIENT_SECRET` en la misma terminal donde lanzas Maven.

**Error `[invalid_id_token] ... The iss claim is not valid`**
El navegador entró a Keycloak por un nombre de host distinto al
`issuer-uri` (por ejemplo `127.0.0.1` vs `localhost`). Usa siempre
`localhost` en ambos.

**Pantalla de login de Spring (formulario azul) en vez de Keycloak**
Falta la sección `spring.security.oauth2.client` en `application.yml` o
está mal indentada. Spring cae en el formulario por defecto. Si la
pantalla aparece en `/login?error` justo después de una prueba del paso
7.4, es el comportamiento esperado: Keycloak devolvió un error a la app.

**Bucle de redirecciones entre la app y Keycloak**
Suele ser el reloj: el ID Token parece caducado o del futuro. Comprueba
`date` en WSL y en el contenedor (`docker exec keycloak date`).

## 11. Siguiente laboratorio

`laboratorio-05-logout-sesiones`: cerrar la sesión en la app **y** en
Keycloak (RP-initiated logout) y ajustar los tiempos de vida de sesión y
tokens.
