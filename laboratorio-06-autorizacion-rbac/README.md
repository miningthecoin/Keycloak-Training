# Laboratorio 06 · Autorización basada en roles (RBAC)

**Tipo:** práctico · **Duración:** 90 min · **Funcionalidad de Keycloak:** roles de realm, grupos y roles en el token

## 1. Objetivo

Que estar autenticado no baste para ver los datos de clientes. Al terminar:

- Existe el rol de realm `gestor-clientes` y el grupo `operaciones`, que
  tiene ese rol. `ana` pertenece al grupo y hereda el rol.
- Existe un segundo usuario, `luis`, autenticado como cualquier otro pero
  sin grupo ni rol.
- Keycloak incluye los roles del usuario en el ID Token y la aplicación
  los convierte en autoridades de Spring Security.
- `/privada` exige el rol `gestor-clientes`: `ana` la ve, `luis` recibe
  la página **Acceso denegado** con HTTP 403.
- La cabecera muestra los roles junto al nombre del usuario.
- La aplicación sigue sin incluir ninguna librería de Keycloak.

Fuentes oficiales (Server Administration Guide):

- *Assigning permissions using roles and groups*, con *Creating a realm
  role*, *Assigning role mappings*, *Using default roles* y *Role scope
  mappings*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#assigning-permissions-using-roles-and-groups>
- *Groups* y *Groups compared to roles*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#proc-managing-groups_server_administration_guide>
  y <https://www.keycloak.org/docs/latest/server_admin/index.html#con-comparing-groups-roles_server_administration_guide>
- *Role mappings in the token* y *Role protocol mappers* (client scope
  `roles`, mappers *realm roles* y *client roles*):
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_oidc_token_role_mappings>
- *Evaluating Client Scopes* (pestaña Evaluate):
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_client_scopes_evaluate>

## 2. Conceptos

### 2.1 Autenticación no es autorización

Hasta ahora la aplicación solo pregunta **quién eres** (autenticación,
laboratorio 04). Este laboratorio añade la segunda pregunta: **qué puedes
hacer** (autorización). Son comprobaciones distintas y Spring las trata
de forma distinta: a quien **no se ha identificado** lo redirige al login
(en una API sería un 401), y a quien **sí se ha identificado pero no
tiene permiso** le responde 403. Por eso un visitante anónimo que pida
`/privada` sigue yendo a Keycloak y no a `/denegado`.

### 2.2 Roles y grupos en Keycloak

La guía lo resume así: *los grupos son colecciones de usuarios a los que
se aplican roles y atributos; los roles definen tipos de usuario, y las
aplicaciones asignan permisos y control de acceso a los roles*.

| Concepto | Qué es | En este lab |
|---|---|---|
| **Rol de realm** | Un nombre global del realm que la aplicación usará para decidir | `gestor-clientes` |
| **Rol de cliente** | Igual, pero en el espacio de nombres de un cliente concreto | no se usa |
| **Grupo** | Conjunto de usuarios con atributos y *role mappings* comunes; los miembros los heredan | `operaciones`, con el rol `gestor-clientes` |
| **Role mapping** | Asignación de un rol a un usuario o a un grupo | el grupo tiene el rol; `ana` está en el grupo |

**Por qué el rol se asigna al grupo y no directamente a `ana`.** Con dos
usuarios daría igual; con doscientos, no:

- **Escala.** Alta o baja de una persona = entrar o salir de un grupo.
  No hay que recordar qué roles lleva cada puesto.
- **Auditoría.** La pregunta "¿quién puede ver clientes?" se responde
  mirando los miembros de `operaciones`, no revisando usuario a usuario.
- **PCI DSS 7.2.2.** El acceso se asigna *según la función o
  clasificación del puesto*, con el mínimo privilegio necesario. El grupo
  es la función; el rol es el privilegio.

Keycloak también crea **roles por defecto** para todo usuario nuevo
(*Using default roles*): el rol compuesto `default-roles-curso`, que
incluye `offline_access` y `uma_authorization`. Los verás en el token de
`ana` y de `luis`; no los usaremos y el laboratorio 07 se ocupará de que
el token lleve solo lo necesario.

### 2.3 Cómo llegan los roles al token

Según *Role mappings in the token*, los roles que Keycloak escribe en un
token son la **intersección** de:

1. Los roles asignados al usuario, incluidos los heredados de sus grupos
   y los expandidos de roles compuestos.
2. Los *role scope mappings* que el cliente tiene permitidos. Por
   defecto el cliente tiene *Full scope allowed*, es decir, todos.

Los escribe el client scope **`roles`**, que es *default* en todos los
clientes del realm y contiene tres *protocol mappers*: **realm roles**
(claim `realm_access.roles`), **client roles** (claim
`resource_access.<cliente>.roles`) y **audience resolve**. Para un usuario
con los roles `role1` y `role2`, el claim queda así:

```json
"realm_access": { "roles": [ "role1", "role2" ] }
```

### 2.4 Por qué activar "Add to ID token"

La guía dice que, por defecto, los roles se añaden **al access token y a
la introspección**, no al ID Token. Y nuestra aplicación no usa el access
token: Spring construye el `OidcUser` a partir del **ID Token** (es el
único token que valida y guarda en la sesión). Si no cambiamos el mapper,
la aplicación nunca vería los roles.

Por eso en el mapper *realm roles* activaremos **Add to ID token**. Es un
cambio de una casilla, pero enseña algo importante: cada claim viaja en
el token que uno decide. El laboratorio 07 revisará qué claims van en cada
token y por qué.

### 2.5 Qué hace la aplicación con los roles

Tras el login, Spring representa al usuario con autoridades genéricas
(`OIDC_USER`, `SCOPE_openid`, …) que no dicen nada de roles. El
laboratorio añade un `GrantedAuthoritiesMapper` que:

1. Conserva esas autoridades.
2. Lee `realm_access.roles` del ID Token.
3. Añade una autoridad `ROLE_<rol>` por cada rol. `hasRole("x")` comprueba
   `ROLE_x`; el guion de `gestor-clientes` no es problema.

Detalle que importa: los roles se leen del ID Token **emitido al iniciar
sesión**. Si un administrador cambia los roles de un usuario, la
aplicación no lo nota hasta que ese usuario vuelve a iniciar sesión y
recibe un ID Token nuevo. Lo comprobarás sacando a `ana` del grupo.
Leer de `userinfo` no cambiaría nada: Spring lo consulta una sola vez, en
el login, y además Keycloak no incluye los roles ahí por defecto.

## 3. Relación con OWASP, ASVS y PCI DSS

- **OWASP A01:2021 Pérdida de control de acceso.** Es la primera categoría
  del Top 10. Incluye "eludir las comprobaciones de control de acceso" y
  "acceder a datos ajenos". En el estado del laboratorio 05, cualquier
  cuenta del realm ve datos de titulares de tarjeta. Tras este
  laboratorio, solo quien tiene la función lo hace.
- **OWASP ASVS V4.1 Diseño del control de acceso.** El control se aplica
  en el servidor (V4.1.1), con el principio de mínimo privilegio (V4.1.3)
  y sin depender de que el usuario "no conozca la URL". Aquí lo aplica
  Spring Security en cada petición, a partir de un token firmado por
  Keycloak.
- **PCI DSS 7.2.** 7.2.1: existe un modelo de control de acceso definido.
  7.2.2: el acceso se asigna según la función del puesto y con el mínimo
  privilegio. El grupo `operaciones` y el rol `gestor-clientes` son ese
  modelo.

## 4. Ejercicio ANTES: cualquier cuenta ve los clientes

**Propósito.** Evidenciar que, en el estado del laboratorio 05, un usuario
recién creado y sin ningún permiso accede a los datos de clientes.

### Preparación del ejercicio: crear a `luis`

Crea un segundo usuario igual que creaste a `ana` en el laboratorio 03:

1. Admin Console → realm `curso` → **Users** → **Add user**.
2. *Username* `luis`, *Email* `luis.herrera@ejemplo.test`, *Email
   verified* activado, *First name* `Luis`, *Last name* `Herrera` →
   **Create**.
3. Pestaña **Credentials** → **Set password** → `luis123`, *Temporary*
   desactivado → **Save**.
4. No le asignes nada más. Sin grupo, sin rol. Esa es la situación de
   partida.

### Pasos

1. Arranca tu aplicación tal como quedó en el laboratorio 05:
   ```bash
   cd ~/keycloak-curso/aplicacion_base
   export KEYCLOAK_CLIENT_SECRET='el-secreto-de-tu-cliente'
   mvn spring-boot:run
   ```
2. Ventana de incógnito → <http://localhost:8081> → **Clientes** → entra
   con `luis` / `luis123`.
3. Copia el `JSESSIONID` de `luis` (DevTools → *Application* → *Cookies*
   → `localhost:8081`) y prueba desde la terminal:
   ```bash
   curl -s -o /dev/null -w "HTTP %{http_code}\n" -b "JSESSIONID=<valor>" http://localhost:8081/privada
   ```

**Resultado esperado.**
- Paso 2: `luis` ve la tabla completa con las tres tarjetas y la
  cabecera dice `Sesión: luis`. Nadie le ha dado permiso para nada;
  basta con existir en el realm.
- Paso 3: `HTTP 200`.

La aplicación pregunta "¿quién eres?" pero nunca "¿puedes ver esto?".
Cierra la sesión de `luis` con **Cerrar sesión** y detén la aplicación
con `Ctrl+C`.

## 5. Configuración de Keycloak, paso a paso

Con la Admin Console en el realm **`curso`**:

### 5.1 Crear el rol de realm

1. **Realm roles** → **Create role**.
2. *Role name*: `gestor-clientes`. *Description*: `Puede consultar el
   listado de clientes (sección privada)`.
3. **Save**.

### 5.2 Crear el grupo y darle el rol

1. **Groups** → **Create group** → *Name*: `operaciones` → **Create**.
2. Entra en el grupo → pestaña **Role mapping** → **Assign role**.
3. En el diálogo, filtra por *Filter by realm roles*, marca
   `gestor-clientes` → **Assign**.

### 5.3 Meter a `ana` en el grupo

1. **Users** → `ana` → pestaña **Groups** → **Join Group**.
2. Marca `operaciones` → **Join**.
3. Comprueba en la pestaña **Role mapping** de `ana`: aparece
   `gestor-clientes` con *Inherited* = True. No se lo has asignado a
   ella; lo hereda del grupo.

`luis` se queda como está: sin grupo y sin rol.

### 5.4 Incluir los roles de realm en el ID Token

1. **Client scopes**. La lista está paginada y muestra 10 de 15: busca
   `roles` en el buscador o pasa a la página 2. Haz clic en el **nombre**
   `roles` (el enlace, no el desplegable *Default*).
2. Dentro del scope aparecen las pestañas *Settings*, **Mappers** y
   *Scope*. Entra en **Mappers** y haz clic en `realm roles`.
3. Activa **Add to ID token**. Deja el resto como está (*Add to access
   token* activado, *Add to userinfo* desactivado, *Token Claim Name*
   `realm_access.roles`).
4. **Save**.

Este ajuste es del realm, no del cliente: afecta a todos los clientes que
usen el scope `roles`. Nos vale porque solo tenemos uno.

## 6. Cambios en la aplicación

Trabaja sobre `~/keycloak-curso/aplicacion_base`. El resultado completo
está en `aplicacion_base_lab-06/`.

### 6.1 `SeguridadConfig.java`: roles, regla de acceso y página 403

Añade estos imports:

```java
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
```

En `cadenaDeFiltros`, sustituye la regla de `/privada` y conecta el
mapeador y la página de acceso denegado:

```java
            // ---- Autorización por ruta ------------------------------
            .authorizeHttpRequests(reglas -> reglas
                // La portada y los estáticos siguen siendo públicos
                .requestMatchers("/", "/estilos.css").permitAll()
                // LABORATORIO 06: /privada exige el rol de realm
                // "gestor-clientes". hasRole("x") comprueba la autoridad
                // "ROLE_x", que es la que crea el mapeador de abajo.
                // El orden importa: esta regla va ANTES de anyRequest().
                .requestMatchers("/privada").hasRole("gestor-clientes")
                // Cualquier otra ruta (por ejemplo /denegado) requiere login
                .anyRequest().authenticated()
            )
            // ---- Inicio de sesión con OpenID Connect ----------------
            .oauth2Login(oidc -> oidc
                .authorizationEndpoint(endpoint -> endpoint
                    .authorizationRequestResolver(resolutorConPkce(registros))
                )
                // LABORATORIO 06: al terminar el login, Spring pasa las
                // autoridades del usuario por este mapeador, que añade los
                // roles de Keycloak (ver método rolesDeKeycloak).
                .userInfoEndpoint(usuario -> usuario
                    .userAuthoritiesMapper(rolesDeKeycloak())
                )
            )
            // ---- Cierre de sesión (LABORATORIO 05) -------------------
            .logout(salida -> salida
                .logoutSuccessHandler(cierreDeSesionEnKeycloak(registros))
            )
            // ---- Acceso denegado (LABORATORIO 06) --------------------
            // Un usuario AUTENTICADO sin el rol necesario recibe la página
            // /denegado con estado HTTP 403. Un visitante ANÓNIMO no llega
            // aquí: autenticación y autorización son comprobaciones
            // distintas, y a quien no se ha identificado Spring lo envía
            // al login (redirección), no a /denegado.
            .exceptionHandling(errores -> errores
                .accessDeniedPage("/denegado")
            );
```

Y añade el bean del mapeador:

```java
    /**
     * LABORATORIO 06: de los roles de Keycloak a las autoridades de Spring.
     *
     * Tras validar el ID Token, Spring representa al usuario con una
     * autoridad OidcUserAuthority (que lleva dentro el ID Token) y una
     * autoridad "SCOPE_x" por cada scope concedido. Ninguna de ellas dice
     * qué ROLES tiene el usuario. Este mapeador:
     *
     *   1. Conserva las autoridades originales (OIDC_USER, SCOPE_*).
     *   2. Lee el claim "realm_access" del ID Token, que Keycloak rellena
     *      con el mapper "realm roles" del client scope "roles":
     *         "realm_access": { "roles": [ "gestor-clientes", ... ] }
     *   3. Añade una autoridad "ROLE_<rol>" por cada rol, que es el
     *      formato que esperan hasRole(...) y las reglas de acceso.
     *
     * Por qué se lee del ID TOKEN y no del endpoint userinfo: el ID Token
     * se emite y se firma en el momento del inicio de sesión, y Spring lo
     * guarda en la sesión local. Los roles que contiene son los que el
     * usuario tenía EN ESE INSTANTE. Si un administrador quita un rol en
     * Keycloak, la aplicación no se entera hasta que el usuario vuelve a
     * iniciar sesión y recibe un ID Token nuevo. Es lo que se observa en
     * el ejercicio de sacar a "ana" del grupo. userinfo se consulta una
     * sola vez en el login, así que tampoco daría roles "en vivo"; y
     * además Keycloak, por defecto, no incluye los roles en userinfo.
     *
     * No hay ninguna clase de Keycloak: solo un claim JSON y la API
     * estándar de Spring Security.
     */
    @Bean
    GrantedAuthoritiesMapper rolesDeKeycloak() {
        return autoridades -> {
            Set<GrantedAuthority> resultado = new HashSet<>(autoridades);
            for (GrantedAuthority autoridad : autoridades) {
                if (autoridad instanceof OidcUserAuthority oidc) {
                    Map<String, Object> realmAccess = oidc.getIdToken().getClaimAsMap("realm_access");
                    if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
                        for (Object rol : roles) {
                            resultado.add(new SimpleGrantedAuthority("ROLE_" + rol));
                        }
                    }
                }
            }
            return resultado;
        };
    }
```

Actualiza el comentario de cabecera de la clase con el párrafo del
laboratorio 06 (ver el archivo completo en `aplicacion_base_lab-06/`).

### 6.2 `PortalControlador.java`: roles en la cabecera y ruta `/denegado`

Añade dos imports:

```java
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
```

Y dos métodos:

```java
    /**
     * LABORATORIO 06: nombres de los roles del usuario, para mostrarlos en
     * la cabecera ("Sesión: ana [gestor-clientes]").
     *
     * Se leen del objeto Authentication y no del OidcUser: las autoridades
     * "ROLE_*" las añade el mapeador de SeguridadConfig al Authentication,
     * mientras que el OidcUser conserva solo las originales del ID Token.
     * Se filtran las que empiezan por "ROLE_" y se quita el prefijo, que es
     * un detalle interno de Spring. Un visitante anónimo no tiene roles.
     */
    @ModelAttribute("roles")
    public List<String> roles(Authentication autenticacion) {
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof OidcUser)) {
            return List.of();
        }
        return autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(autoridad -> autoridad.startsWith("ROLE_"))
                .map(autoridad -> autoridad.substring("ROLE_".length()))
                .sorted()
                .toList();
    }

    /**
     * LABORATORIO 06: página de acceso denegado.
     *
     * No se llega aquí navegando: Spring Security reenvía (forward) a esta
     * ruta cuando un usuario autenticado intenta una URL para la que no
     * tiene rol, y mantiene el estado HTTP 403 en la respuesta.
     */
    @GetMapping("/denegado")
    public String denegado() {
        return "denegado";
    }
```

### 6.3 `templates/denegado.html`: la página de acceso denegado

Archivo nuevo:

```html
<!DOCTYPE html>
<!--
  LABORATORIO 06: página de ACCESO DENEGADO.

  La muestra Spring Security (exceptionHandling().accessDeniedPage) cuando
  un usuario autenticado pide una ruta para la que no tiene el rol
  necesario. La respuesta lleva estado HTTP 403. No confundir con el caso
  del visitante anónimo, que no llega aquí: a él se le redirige al login.
-->
<html lang="es" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Acceso denegado · Cooperativa Andina</title>
    <link rel="stylesheet" th:href="@{/estilos.css}">
</head>
<body>

<header th:replace="~{fragmentos :: cabecera}"></header>

<main>
    <h1>Acceso denegado</h1>
    <p>
        Has iniciado sesión como <strong th:text="${usuario}">usuario</strong>,
        pero tu cuenta no tiene el rol necesario para ver esta sección.
    </p>
    <p>
        La sección de clientes está reservada al rol
        <code>gestor-clientes</code>. Si crees que deberías tener acceso,
        pide a un administrador que te añada al grupo correspondiente.
    </p>
    <p><a th:href="@{/}">Volver a la portada</a></p>
</main>

<footer th:replace="~{fragmentos :: pie}"></footer>

</body>
</html>
```

### 6.4 `fragmentos.html`: los roles junto al nombre

Añade el `<span class="roles">` justo después del nombre:

```html
    <span class="identidad">
        Sesión: <strong th:text="${usuario}">anónimo</strong>
        <span class="roles" th:if="${!roles.isEmpty()}"
              th:text="'[' + ${#strings.listJoin(roles, ', ')} + ']'">[rol]</span>
        <a th:unless="${autenticado}" th:href="@{/oauth2/authorization/keycloak}">Iniciar sesión</a>
        <form th:if="${autenticado}" th:action="@{/logout}" method="post" class="salir">
            <button type="submit">Cerrar sesión</button>
        </form>
    </span>
```

Y en el comentario de la cabecera:

```html
  LABORATORIO 06: junto al nombre se muestran los roles del usuario entre
  corchetes ("ana [gestor-clientes]"). Vienen del atributo "roles" que
  rellena PortalControlador a partir del ID Token. Sin roles no se muestra
  nada, para que se note la diferencia entre ana y luis.
```

### 6.5 Arrancar

```bash
cd ~/keycloak-curso/aplicacion_base
export KEYCLOAK_CLIENT_SECRET='el-secreto-de-tu-cliente'
mvn spring-boot:run
```

## 7. Ejercicio DESPUÉS: solo el rol autorizado ve los clientes

### 7.1 En el navegador

1. Ventana de incógnito → <http://localhost:8081> → **Clientes** → entra
   con `luis` / `luis123`.
2. Pulsa **Cerrar sesión**. Vuelve a **Clientes** y entra con `ana` /
   `ana123`.

**Resultado esperado.**
- Paso 1: `luis` no ve la tabla. Llega a la página **Acceso denegado**,
  con la cabecera `Sesión: luis [default-roles-curso, offline_access,
  uma_authorization]` y el enlace a la portada. Son los roles por defecto
  de Keycloak (2.2); ninguno es `gestor-clientes`.
- Paso 2: `ana` ve la tabla y la cabecera dice `Sesión: ana
  [default-roles-curso, gestor-clientes, offline_access, uma_authorization]`.
  La diferencia entre ambas cabeceras es exactamente un rol.

### 7.2 Con curl: 403, 200 y redirección

Con las dos sesiones abiertas en dos ventanas de incógnito, copia el
`JSESSIONID` de cada una y prueba:

```bash
curl -s -o /dev/null -w "luis: HTTP %{http_code}\n" -b "JSESSIONID=<valor-de-luis>" http://localhost:8081/privada
curl -s -o /dev/null -w "ana:  HTTP %{http_code}\n" -b "JSESSIONID=<valor-de-ana>"  http://localhost:8081/privada
curl -s -o /dev/null -w "anónimo: HTTP %{http_code} -> %{redirect_url}\n" http://localhost:8081/privada
```

Resultado esperado:

```
luis: HTTP 403
ana:  HTTP 200
anónimo: HTTP 302 -> http://localhost:8081/oauth2/authorization/keycloak
```

Tres respuestas para tres situaciones: sin permiso, con permiso, sin
identificar (2.1). Comprueba también que el 403 de `luis` trae la página
`/denegado` y no las tarjetas:

```bash
curl -s -b "JSESSIONID=<valor-de-luis>" http://localhost:8081/privada | grep -c '\*\*\*\*'
```

Resultado esperado: `0`.

### 7.3 Los roles viajan en el token al iniciar sesión

1. Con `ana` dentro de `/privada`, ve a la Admin Console → **Users** →
   `ana` → **Groups** → **Leave** en la fila de `operaciones`.
2. Vuelve a la aplicación y recarga `/privada`.
3. Pulsa **Cerrar sesión**, luego **Clientes**, y entra de nuevo como
   `ana`.
4. Vuelve a la Admin Console → **Users** → `ana` → **Groups** → **Join
   Group** → `operaciones`. Cierra sesión y entra otra vez.

**Resultado esperado.**
- Paso 2: `ana` **sigue viendo** la tabla y `[… gestor-clientes …]` en
  la cabecera. Su ID Token se emitió antes del cambio y la aplicación no
  vuelve a preguntar a Keycloak.
- Paso 3: ahora recibe **Acceso denegado** y en la cabecera ya no está
  `gestor-clientes`. El ID Token nuevo refleja el cambio.
- Paso 4: vuelve a ver la tabla.

Conclusión práctica: quitar un permiso en Keycloak no expulsa a quien ya
está dentro. Con la caducidad de 3 minutos del laboratorio 05, la ventana
es corta; en un sistema real hay que dimensionarla a conciencia.

### 7.4 Ver el claim en la Admin Console (Evaluate)

Sin decodificar tokens a mano:

1. **Clients** → `aplicacion-base` → pestaña **Client scopes** → subpestaña
   **Evaluate**.
2. En *User*, escribe `ana`. Abre la subpestaña **Generated ID token**.
3. Repite con `luis`.

**Resultado esperado.**
- Para `ana`, el ID Token generado contiene:
  ```json
  "realm_access": {
    "roles": [ "default-roles-curso", "offline_access", "gestor-clientes", "uma_authorization" ]
  }
  ```
- Para `luis`, el mismo claim sin `gestor-clientes`.
- Si desactivas **Add to ID token** en el mapper (5.4) y repites, el claim
  desaparece del ID Token pero sigue en **Generated access token**. Vuelve
  a activarlo.

## 8. Lista de verificación

- [ ] Existe el rol de realm `gestor-clientes`.
- [ ] Existe el grupo `operaciones` con el rol `gestor-clientes` en su *Role mapping*.
- [ ] `ana` es miembro de `operaciones` y en su *Role mapping* el rol aparece como *Inherited*.
- [ ] Existe `luis` sin grupo ni rol.
- [ ] El mapper `realm roles` del client scope `roles` tiene *Add to ID token* activado.
- [ ] `SeguridadConfig` tiene el bean `GrantedAuthoritiesMapper`, la regla `hasRole("gestor-clientes")` para `/privada` y `accessDeniedPage("/denegado")`.
- [ ] Existe `denegado.html` y la cabecera muestra los roles.
- [ ] `luis` recibe 403 con la página de acceso denegado; `ana` recibe 200 con la tabla; el anónimo, 302 al login.
- [ ] Al sacar a `ana` del grupo, el cambio solo se nota tras volver a iniciar sesión.
- [ ] Evaluate muestra `realm_access.roles` con `gestor-clientes` para `ana` y sin él para `luis`.

## 9. Punto de control

`keycloak/curso-realm.json` incluye el rol, el grupo con su rol, `ana`
como miembro, `luis`, y el client scope `roles` con *Add to ID token*
activado. Como este archivo ya define client scopes, incluye **todos** los
scopes por defecto del realm (`profile`, `email`, `roles`, `web-origins`,
`acr`, `basic`, …) y cuáles son *default* y *optional*: si solo llevara
`roles`, Keycloak no crearía los demás al importar y el login fallaría por
falta de `profile` y `email`. El secreto del cliente sigue siendo
`secreto-lab04-cambialo-en-produccion`.

## 10. Problemas frecuentes

**`ana` también recibe Acceso denegado**
Casi siempre falta el paso 5.4: sin *Add to ID token*, el ID Token no
lleva `realm_access` y el mapeador no crea ninguna autoridad `ROLE_*`.
Compruébalo en Evaluate (7.4) mirando *Generated ID token*, no el access
token. Otra causa: `ana` no está en el grupo, o el grupo no tiene el rol.

**La cabecera no muestra corchetes para nadie**
Mismo origen: no llegan roles en el ID Token. Si Evaluate sí los muestra,
vuelve a iniciar sesión: el ID Token de la sesión actual es anterior al
cambio.

**`/privada` responde 403 pero muestra la página de error de Spring, no la mía**
Falta `exceptionHandling().accessDeniedPage("/denegado")` o la plantilla
`denegado.html` no existe. Revisa el nombre exacto del archivo.

**El anónimo recibe 403 en vez de ir al login**
Se ha puesto `/denegado` como `permitAll()` y además se ha alterado el
orden de reglas. Deja `/denegado` bajo `anyRequest().authenticated()` y
`/privada` con `hasRole(...)` antes de `anyRequest()`.

**`hasRole` no funciona y `hasAuthority("gestor-clientes")` tampoco**
`hasRole("gestor-clientes")` busca `ROLE_gestor-clientes`; el mapeador
debe añadir el prefijo `ROLE_`. Si prefieres no usar prefijo, cambia a
`hasAuthority("ROLE_gestor-clientes")`, pero no mezcles criterios.

**En Evaluate no aparece `realm_access` en ningún token**
El client scope `roles` no está como *default* del cliente. **Clients** →
`aplicacion-base` → **Client scopes**: `roles` debe figurar con *Assigned
type* = Default.

## 11. Siguiente laboratorio

`laboratorio-07-scopes-claims-audiencia`: el token de `ana` lleva más de
lo necesario (roles por defecto, claims que la aplicación no usa) y vale
para cualquier destinatario. El siguiente laboratorio recorta los claims
con client scopes y mappers, y fija la audiencia (`aud`) del token.
