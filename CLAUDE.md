# Curso "Desarrollo seguro con Keycloak"

Repositorio de un curso práctico de 16 h para desarrolladores junior.
Este archivo resume las decisiones tomadas al diseñarlo. Léelo entero
antes de tocar nada y no contradigas estas reglas sin preguntar.

## Fuentes permitidas para Keycloak

Todo lo relativo a Keycloak se basa ÚNICAMENTE en https://www.keycloak.org/guides
y https://www.keycloak.org/documentation (versión de referencia 26.7.3).
Si necesitas un dato de Keycloak que no sale ahí, dilo en vez de inventarlo.
Enlaza la sección oficial concreta en cada README de laboratorio.

## Reglas de arquitectura (no negociables)

- Aplicación: Spring Boot 3.5.x + Thymeleaf, Java 21, Maven. Lo más simple posible.
- PROHIBIDO usar Keycloak Client Adapters ni ninguna dependencia `org.keycloak.*`.
  La guía https://www.keycloak.org/securing-apps/overview los deja como último
  recurso; usamos el soporte OIDC nativo de Spring Security
  (`spring-boot-starter-oauth2-client`, `spring-boot-starter-security`).
- Flujos: Authorization Code + PKCE (S256) para usuarios; Client Credentials
  para máquina a máquina. Nunca Implicit ni Password grant.
- El client secret se lee de la variable de entorno `KEYCLOAK_CLIENT_SECRET`.
  Nunca se escribe en `application.yml` ni se sube al repositorio.
- Puertos: Keycloak 8080 (Docker, `start-dev`), aplicación 8081.
- Keycloak se arranca con `keycloak/docker-compose.yml`. Realm del curso: `curso`.
  Usuario de pruebas: `ana` / `ana123` en los labs 03 a 07 (contraseña débil a
  propósito). El lab 08 la endurece: desde ahí, `ana` / `Andina*Segura2026`.
  Segundo usuario (creado en el lab 06): `luis` / `luis123`, que conserva la
  contraseña débil a propósito para demostrar que la política no afecta a los
  usuarios existentes y para el ejercicio de fuerza bruta.
  Cliente OIDC: `aplicacion-base`, confidencial, solo Standard flow, PKCE S256,
  redirect URI exacta `http://localhost:8081/login/oauth2/code/keycloak`.
- Entorno de los alumnos: WSL2 con Ubuntu 24.04, repo clonado en `~/` (nunca en `/mnt/c`).

## Convenciones de contenido

- Idioma: español en todo (README, comentarios de código, nombres de clases y
  variables en español: `SeguridadConfig`, `PortalControlador`, `usuarioActual`).
- Cada sección de código lleva un comentario en español que explica qué hace y
  qué laboratorio la introdujo (`LABORATORIO 04: ...`).
- Cada laboratorio incorpora UNA sola funcionalidad de Keycloak y preserva la
  compatibilidad con el anterior.
- Estructura obligatoria de cada laboratorio práctico:
  `laboratorio-XX-nombre/README.md`, `keycloak/curso-realm.json` (realm exportado
  al terminar el lab) y `aplicacion_base_lab-XX/` (app con todo lo implementado
  hasta ese lab, nada de labs posteriores).
- El README de cada laboratorio sigue siempre este esqueleto:
  1 Objetivo · 2 Conceptos · 3 Relación con OWASP y PCI DSS · 4 Ejercicio ANTES
  (pasos detallados + resultado esperado que evidencia la debilidad) ·
  5 Configuración de Keycloak paso a paso · 6 Cambios en la aplicación con el
  código completo · 7 Ejercicio DESPUÉS (pasos + resultado esperado que evidencia
  la corrección) · 8 Lista de verificación · 9 Punto de control · 10 Problemas
  frecuentes · 11 Siguiente laboratorio.
- `aplicacion_base/` es la app de trabajo del alumno y debe quedarse SIEMPRE en
  el estado inicial (sin seguridad). No le añadas funcionalidades de laboratorios.
- Los laboratorios 01 y 02 son solo teóricos (README sin parte práctica).

## Plan de laboratorios (ver tabla completa en README.md)

00 entorno · 01 fundamentos OAuth2/OIDC/SAML/JWT · 02 estado del arte ·
03 Keycloak conceptos + instalación (realm, usuario) · 04 autenticación OIDC
(cliente + PKCE) · 05 logout y sesiones · 06 RBAC · 07 client scopes, claims y
audiencia · 08 políticas de contraseña y fuerza bruta · 09 2FA TOTP ·
10 login federado con GitHub · 11 máquina a máquina (client credentials, solo
demostrado con curl) · 12 auditoría de eventos · 13 endurecimiento (OPCIONAL,
solo README) · 14 cierre en tres partes (passkeys, Authorization Services,
acceso just-in-time), que continúa del 12, no del 13.

## Estado actual

- Hechos: README raíz, `aplicacion_base`, `keycloak/docker-compose.yml`,
  labs 00, 03, 04, 05, 06, 07, 08, 09, 10, 11, 12 y 14 completos.
- Pendientes: labs 01 y 02.
- Lab 13 (endurecimiento): **OPCIONAL, no desarrollado y no previsto**.
  Solo tiene `README.md` de una página que explica por qué es opcional
  (es trabajo de plataforma, no de desarrollo seguro), remite a la lámina
  43 de la presentación y a
  `server_admin/index.html#mitigating_security_threats`, y manda al lab 14.
  NO tiene `keycloak/curso-realm.json` ni `aplicacion_base_lab-13`, a
  propósito. **El lab 14 parte del punto de control del lab 12.**
- Tarea aparte pendiente (acordada el 2026-09-16, después del lab 11): el
  Keycloak en marcha del autor tiene `passwordAge(3)` en la password policy
  mientras los JSON de los labs 08 a 12 llevan `passwordHistory(3)`. Se
  corregirá en ambos sitios a `passwordHistory(4)` por PCI DSS 8.3.7.
- Lab 14 (2026-09-18): cierre en tres partes, 60 min, demostración guiada.
  Es la ÚNICA excepción a "una funcionalidad por laboratorio" y el README lo
  justifica en la sección 1. La app NO cambia: `aplicacion_base_lab-14` es
  copia exacta de `aplicacion_base_lab-12`.
  PARTE A, passwordless: `webAuthnPolicyPasswordless*` con RP Entity Name
  "Cooperativa Andina", RP ID vacío, ES256+RS256 (RS256 lo EXIGE la guía
  para Windows Hello), attestation `none`, attachment `not specified`.
  OJO: en 26.7.3 la opción viva es **`Discoverable Credential`**
  (`webAuthnPolicyPasswordlessResidentKey`); `Require Discoverable
  Credential` está DEPRECADA. Ambas ya venían en `required` por defecto en
  la política passwordless, igual que User Verification.
  **`passkeys` NO es preview: es feature soportada y ACTIVADA POR DEFECTO**
  (tabla "Supported features" de keycloak.org/server/features). No hace
  falta ninguna opción de arranque y `keycloak/docker-compose.yml` NO se
  toca. Se activa por realm con `webAuthnPolicyPasswordlessPasskeysEnabled`
  + `webAuthnPolicyPasswordlessMediation: conditional` (Realm settings →
  Login → Enable Passkeys). Lo DEPRECADO es
  `passkeys-conditional-ui-authenticator`.
  Flujo `browser passwordless` (duplicado de `browser`, vinculado como
  Browser flow del realm; el `browser` original queda intacto):
  Cookie/Kerberos/IdP Redirector/Organization + `browser passwordless
  forms` → Username Form (Required) → `Passwordless Or Two-factor`
  (Required) → WebAuthn Passwordless Authenticator (Alternative) +
  `Password And Two-factor` (Alternative) → Password Form (Required) +
  `browser passwordless Browser - Conditional 2FA` (Conditional) →
  Condition - user configured + Condition - credential + OTP Form +
  Recovery Authentication Code Form (los dos últimos Alternative).
  HECHOS VERIFICADOS: duplicar prefija los sub-flujos con el nombre del
  flujo; la consola NO mueve sub-flujos de padre, así que el condicional de
  2FA se BORRA y se RECONSTRUYE un nivel más abajo (hay que replicar su
  config `{"credentials":"webauthn-passwordless"}`, leerla antes de
  borrar); el flujo `browser` de 26.7.3 trae un sub-flujo `Organization`
  que la copia arrastra y se deja tal cual. `Condition - credential` tiene
  opción `included` (por defecto false = "true si NO se usó esa
  credencial"), y por eso la passkey se salta el OTP legítimamente.
  PARTE B, Authorization Services: **NO se activa en `aplicacion-base`**
  (decisión del autor). Resource server = cliente nuevo `api-clientes`, sin
  flujos, Authorization ON (esto fuerza Service accounts roles). Scope
  `ver`; recursos `cartera-norte`/`cartera-sur`; políticas `es gestor`
  (role, required) + `cartera norte`/`cartera sur` (regex `^norte$`/`^sur$`
  sobre Target Claim `cartera`); permisos scope-based Unanimous.
  `cartera` hay que DECLARARLO en Realm settings → User profile (los
  unmanaged attributes están OFF por defecto) y necesita un mapper User
  Attribute en `api-clientes-dedicated`, SOLO access token (id.token.claim
  false) para no tocar el ID Token de ana.
  TRAMPA IMPORTANTE, verificada: sin `gestor-clientes` en el scope mapping
  de `api-clientes` (Full scope allowed OFF), Evaluate da DENY a ana con
  `es gestor=DENY` — es la regla de la intersección del lab 07.
  Resultados reales: ana PERMIT norte / DENY sur; luis DENY ambas; cambiar
  solo el atributo invierte el resultado. Grant UMA con
  `audience=api-clientes` + `permission=cartera-norte#ver` → 403
  `{"error":"access_denied","error_description":"not_authorized"}` (NO
  "request_denied" como el ejemplo de la guía). Para enseñar
  `authorization.permissions` se hizo una concesión TEMPORAL a la service
  account de `servicio-conciliacion` (rol + atributo + mapper + scope
  mapping), y el revert dejó el token IDÉNTICO (comprobado con diff).
  En 26.7.3 activar Authorization **NO crea** Default Resource/Policy/
  Permission (verificado por las dos vías, create y update).
  PARTE C, acceso just-in-time: cliente `aprobador-accesos` (confidencial,
  solo service account) con `manage-users` de `realm-management` en la
  service account **Y** en su scope mapping (Full scope allowed OFF); rol
  `administrador-app` y grupo `administracion`. Endpoints verificados:
  **PUT** y **DELETE** `/admin/realms/curso/users/{id}/groups/{groupId}`
  (204 los dos; el POST que a veces se cita es INCORRECTO). Admin events
  `CREATE`/`DELETE` sobre `GROUP_MEMBERSHIP` con `authDetails.clientId` =
  `aprobador-accesos`. La cabecera `X-Ticket-Aprobacion` NO se guarda (la
  `representation` es solo el grupo). Prueba negativa: el token de
  `servicio-conciliacion` recibe 403.
  ÚNICO cambio sobre `aplicacion-base` en todo el lab: añadir
  `administrador-app` a su scope mapping, sin el cual el ID Token de luis
  no lleva el rol. Verificado con ID Tokens reales: `realm_access: null` →
  `{"roles":["administrador-app"]}` → `realm_access: null`.
  JSON: partial-export + usuarios curados del lab 12. NO lleva las
  credenciales webauthn/otp/recovery de ana (la passkey está ligada al
  hardware del autor y no funcionaría en otro equipo), NO lleva el
  `KeyProvider` (claves de firma) y lleva secretos didácticos
  `secreto-api-clientes-lab14-...` y `secreto-aprobador-lab14-...`.
  Validado por import en 8082 sin errores: los flujos de fábrica SE CREAN
  igualmente pese a llevar `authenticationFlows`, Evaluate da el mismo
  resultado y ana redirige a `required-action?execution=CONFIGURE_TOTP`.
  PROBLEMA FRECUENTE documentado y diagnosticado en el código del tema
  (`theme/base/login/resources/js/webauthnRegister.js`): tras un
  `navigator.credentials.create()` correcto, `returnSuccess()` llama a
  `window.prompt()` para la etiqueta, y el campo destino es `hidden` sin
  alternativa visible. Si el navegador bloquea `prompt()` sale
  "Passkey registration result is invalid. Error: prompt() is not
  supported" y Keycloak descarta el registro. Cambiar de tema NO sirve
  (`keycloak.v2` y `base` comparten el mismo JS). Solución: Edge/Chrome en
  ventana NORMAL (la guía avisa de que algunas ventanas privadas bloquean
  Windows Hello) y aceptar el diálogo.
  AVISO SOBRE EL KEYCLOAK DEL AUTOR (2026-09-18): `ana` había perdido su
  credencial OTP (solo tenía `password`), pese a que el JSON del lab 12 la
  deja con `CONFIGURE_TOTP`. Se le reasignó la acción requerida y el autor
  enroló TOTP, passkey (Windows Hello, AAGUID
  `08987058-cadc-4b81-b6e1-30de50dcbe96`) y códigos de recuperación. Ahora
  tiene password + webauthn-passwordless + otp + recovery-authn-codes.
  La app NO tiene ruta `/perfil` (solo `/`, `/privada`, `/denegado`): para
  inspeccionar el ID Token se usa Client scopes → Evaluate, como el lab 07.
- Lab 12 (2026-09-17): auditoría de eventos. En el realm: `eventsEnabled`,
  `eventsExpiration` 2592000 s (30 días, didáctico; el README explica que PCI
  DSS 10.5.1 pide 12 meses con 3 disponibles y que la BD de Keycloak NO es el
  almacén de largo plazo), `adminEventsEnabled` + `adminEventsDetailsEnabled`
  (Include representation), listener `jboss-logging` (email NO: no hay SMTP).
  Saved types: los 103 por defecto + `INTROSPECT_TOKEN_ERROR` añadido a mano
  = 104. Los tipos que NO vienen por defecto son los de alta frecuencia
  (introspect_token, refresh_token, user_info_request, client_info,
  invalid_signature, pushed_authorization_request, identity_provider_response,
  identity_provider_retrieve_token, register_node, unregister_node,
  user_session_deleted); el README lo explica como criterio de diseño.
  ÚNICO archivo compartido modificado: `keycloak/docker-compose.yml`, cuyo
  `command` pasa a
  `start-dev --import-realm --spi-events-listener--jboss-logging--success-level=info`
  (la guía documenta esa opción en "The logging event listener"); exige
  `docker compose up -d` para recrear el contenedor, NO `down -v`, y la config
  de eventos sobrevive porque vive en el volumen. Verificado que tras recrear
  el contenedor `events/config` sigue intacto.
  App: `aplicacion_base_lab-12` = lab 11 + `seguridad/AuditoriaDeAcceso.java`
  (@EventListener sobre AuthenticationSuccessEvent, LogoutSuccessEvent y
  AuthorizationDeniedEvent; logger SLF4J "auditoria"; registra usuario, sub,
  sid, azp y ruta; NUNCA tokens) + bean `AuthorizationEventPublisher`
  (SpringAuthorizationEventPublisher) en `SeguridadConfig`, sin el cual Spring
  deniega en silencio. OJO con `AuthorizationDeniedEvent.getObject()`: en
  Spring Security 6.5 es un `HttpServletRequest` (no un
  `RequestAuthorizationContext`), por eso el método `ruta()` contempla los dos;
  con solo el segundo se imprime el toString del wrapper.
  Hechos verificados en 26.7.3: el listener recibe TODOS los eventos aunque el
  almacén solo guarde los Saved types (se ve `USER_INFO_REQUEST` en el log y
  no en Events); `LOGIN_ERROR` no trae `sessionId` (no hubo sesión); la
  representación de la regeneración de secreto sale ENMASCARADA
  (`"value":"**********"`), la del rol trae el objeto entero; la
  representación es el estado NUEVO, Keycloak no guarda diff; la propia
  activación de la auditoría queda como primer admin event
  (UPDATE REALM events/config).
  Correlación demostrada: el `sid` del ID Token que escribe la app es el mismo
  `sessionId` de los eventos LOGIN/LOGOUT/CODE_TO_TOKEN de Keycloak.
  Verificación mía: el autor hizo en el navegador los pasos de `ana` (TOTP del
  lab 09, no automatizable) y `luis`; el resto por curl. El login de `luis`
  para capturar la línea DENEGADO se automatizó con curl paso a paso (GET
  /privada -> GET /oauth2/authorization/keycloak -> form de Keycloak -> POST
  credenciales -> GET callback -> GET /privada 403), no con `curl -L`, que
  pierde la petición de autorización de la sesión.
  JSON validado por import en 8082 con la misma opción del listener: config
  intacta y un LOGIN_ERROR + LOGIN guardados tras importar.
- Lab 11 (2026-09-16): máquina a máquina con Client Credentials, demostrado
  solo con curl. Cliente `servicio-conciliacion` (confidencial, Standard
  flow OFF, Direct access grants OFF, Service accounts roles ON, Full scope
  allowed OFF con scope mapping de `lector-conciliacion`); rol de realm
  `lector-conciliacion` asignado SOLO a la service account
  (`service-account-servicio-conciliacion`, que NO aparece en la lista de
  Users de la consola: se llega desde la pestaña Service accounts roles);
  client scope `api-conciliacion-audiencia` con mapper Audience, Default
  solo en `servicio-conciliacion`. `aplicacion-base` no se toca. Además,
  cliente `api-conciliacion` sin ningún flujo (confidencial) que representa
  al resource server, siguiendo "Hardcoded audience" de la guía; el mapper
  usa Included Client Audience = api-conciliacion. Hizo falta porque la
  introspección (`/token/introspect`) exige que el cliente que pregunta esté
  en el `aud` del token (sección "Token introspection audience validation"):
  como `servicio-conciliacion` devolvía siempre `active: false` con
  `reason="Client 'servicio-conciliacion' is not in the token audience"` en
  el log. La app NO cambia: `aplicacion_base_lab-11` es copia del lab 10.
  Scripts: `scripts/validar-token.sh` (resource server didáctico: descarga
  el JWKS y comprueba kid, iss, aud, exp, typ, azp, scope y realm_access;
  NO verifica la firma, y el README 7.8 demuestra la consecuencia con un
  payload manipulado que el script acepta y la introspección rechaza con
  "Access token JWT check failed"), `scripts/api-insegura.sh` +
  `scripts/conciliacion.properties` (el ANTES: API key estática ficticia).
  Hechos verificados en 26.7.3: token de 60 s sin refresh token; secreto
  erróneo con client_id existente -> `unauthorized_client` "Invalid client
  or Invalid client credentials" (HTTP 401); `invalid_client` solo si el
  client_id no existe; client_credentials contra `aplicacion-base` ->
  `unauthorized_client` "Client not enabled to retrieve service account";
  Regenerate invalida el secreto anterior al instante. El JSON lleva los
  secretos `secreto-lab11-cambialo-en-produccion` y
  `secreto-api-lab11-cambialo-en-produccion`, y la service account como
  usuario con `serviceAccountClientId` y `realmRoles`; validado por import
  en 8082 (grant + script + introspección OK). En el Keycloak del autor el
  secreto de `servicio-conciliacion` es el regenerado en la prueba 7.7
  (léelo en Credentials).
- Lab 10 (2026-09-12): login federado con GitHub (identity brokering).
  Proveedor `github` en el realm; el botón "Sign in with GitHub" aparece en la
  pantalla de login y redirige a github.com/login/oauth/authorize con el
  redirect_uri del broker `.../realms/curso/broker/github/endpoint`. First
  broker login por defecto (Review Profile, Create User If Unique). La app NO
  cambia: `aplicacion_base_lab-10` es copia del lab 09. El usuario federado se
  crea SIN rol -> recibe /denegado hasta que un admin le da `gestor-clientes`
  (federar autentica, no autoriza). El `curso-realm.json` lleva el IdP con
  clientId/clientSecret = marcadores TU_GITHUB_CLIENT_ID/TU_GITHUB_CLIENT_SECRET;
  NUNCA el secreto real de GitHub. Verificación mía: solo el cableado Keycloak
  (botón + redirección + import en 8082). El login real de GitHub lo hace el
  alumno con su propia OAuth App; no automatizable con curl. En tu Keycloak
  queda el IdP `github` creado con marcadores: pon tu Client ID/Secret para el
  QA.
- Lab 09 (2026-09-12): segundo factor TOTP. OTP Policy en valores por defecto
  (TOTP, SHA1, 6 dígitos, periodo 30, ventana 1). A `ana` se le asigna la
  acción requerida CONFIGURE_TOTP; `luis` queda de un factor por contraste.
  No se modifica el flujo Browser: el sub-flujo condicional "Browser -
  Conditional 2FA" ya pide OTP solo si el usuario tiene credencial OTP. La
  app NO cambia: `aplicacion_base_lab-09` es copia del lab 08.
  El `curso-realm.json` NO lleva ningún secreto TOTP (sería repartir el 2.º
  factor); deja a `ana` con CONFIGURE_TOTP para que cada alumno enrole su
  propio autenticador. Verificado con curl+python (cálculo TOTP sobre los
  bytes ASCII del campo `totpSecret`): enrolamiento crea credencial otp,
  login exige contraseña+código, el código correcto entra. Notas para probar
  a mano: (1) el token admin caduca a 60 s por accessTokenLifespan del lab 05,
  refréscalo por llamada en scripts largos; (2) los fallos de OTP cuentan para
  la fuerza bruta del lab 08, limpiar con DELETE attack-detection; (3) reusable
  code está OFF: no reutilizar un código dentro de su ventana de 30 s.
- Lab 08 (2026-09-12): password policy
  `length(12) and digits(1) and lowerCase(1) and upperCase(1) and specialChars(1)
  and notUsername(undefined) and notEmail(undefined) and passwordHistory(3)`;
  fuerza bruta en modo temporal (`bruteForceProtected`, failureFactor 5,
  waitIncrement 60 s, maxFailureWait 300 s). `ana` pasa a `Andina*Segura2026`
  vía required action UPDATE_PASSWORD; `luis` conserva `luis123` a propósito.
  La aplicación NO cambia: `aplicacion_base_lab-08` es copia exacta del lab 07.
  Verificado: la política solo actúa al establecer contraseñas (los existentes
  siguen entrando), el bloqueo llega al 5.º fallo si los intentos se espacian
  más de 1 s (con menos, salta antes la regla Quick login check y el contador
  marca 2), y el mensaje es `Invalid username or password.` tanto para
  contraseña mala como para cuenta bloqueada.
- IMPORTANTE para los JSON de realm a partir del lab 08: si el archivo lleva
  `passwordPolicy`, las credenciales NO pueden ir en claro. Keycloak valida los
  `"value"` contra la política durante la importación y, como `luis123` no la
  cumple, falla el realm entero con `invalidPasswordMinSpecialCharsMessage`.
  El `curso-realm.json` del lab 08 lleva las credenciales hasheadas
  (`secretData`/`credentialData`, Argon2), obtenidas con `kc.sh export` sobre un
  contenedor temporal. Regenerarlas exportando, nunca escribiéndolas a mano.
- Lab 07 (2026-09-11): en el cliente `aplicacion-base`, Full scope allowed
  OFF con scope mapping solo de `gestor-clientes`, y `email` de Default a
  Optional; en la app solo cambia `scope: openid, profile`. Efecto: ID Token
  con un rol y sin email, access token sin `aud: account` ni
  `resource_access.account` (Audience resolve deja de actuar al no haber
  roles de cliente). No se añade mapper Audience: la audiencia de la API se
  fija en el lab 11. JSON = lab 06 + `fullScopeAllowed`, scopes del cliente
  y `scopeMappings`; validado en contenedor temporal. El README del lab 07
  añade en la sección 1 "Beneficios" y "Un ejemplo de las amenazas que evita"
  (petición del autor; mantener en labs siguientes).
- Lab 06 (2026-09-11): rol de realm `gestor-clientes`, grupo `operaciones`,
  usuario `luis`/`luis123` sin rol, mapper "realm roles" con Add to ID token;
  en la app `GrantedAuthoritiesMapper` (realm_access.roles -> ROLE_*),
  `hasRole("gestor-clientes")` en /privada, página /denegado (403) y roles en
  la cabecera. El `curso-realm.json` del lab 06 incluye TODOS los client
  scopes por defecto y los defaultDefault/OptionalClientScopes: si un JSON
  lleva `clientScopes`, Keycloak ya no crea los scopes por defecto al
  importar (verificado en contenedor temporal). Los usuarios del JSON llevan
  `realmRoles: ["default-roles-curso"]` para reproducir lo que ve el alumno.
- Lab 05 (2026-09-10): RP-Initiated Logout con
  `OidcClientInitiatedLogoutSuccessHandler`, botón "Cerrar sesión" (POST
  /logout con CSRF), `server.servlet.session.timeout: 3m` alineado con SSO
  Session Idle = 3 min, SSO Session Max = 1 h, Access Token Lifespan = 1 min
  (sin efecto visible hasta el lab 11). Verificado con curl contra Keycloak
  26.7.3: logout real, URI no registrada -> 400, caducidad por inactividad a
  los 180 s sin ventana extra. Front/back-channel logout quedan fuera de alcance.
- Verificado el 2026-09-10: `aplicacion_base_lab-04` compila (Spring Boot
  3.5.0, Java 21) y el flujo completo del README del lab 04 (login de `ana`,
  PKCE S256, `redirect_uri` inválida) funciona contra Keycloak 26.7.3 con el
  `curso-realm.json` del lab importado.
- Al restaurar Keycloak con `docker compose` desde la raíz, el contenedor
  anterior (creado cuando el compose vivía en el lab 03) se recrea; el
  volumen `keycloak_keycloak_datos` se conserva.

## Cómo probar

```bash
# Keycloak
cd keycloak && docker compose up -d && docker compose logs -f
# Admin: http://localhost:8080 (admin/admin). Realm curso, usuario ana/ana123.

# App base (sin seguridad)
cd aplicacion_base && mvn spring-boot:run     # http://localhost:8081/privada -> 200

# App lab 04 (requiere cliente aplicacion-base creado según laboratorio-04/README.md)
export KEYCLOAK_CLIENT_SECRET='<secreto de la pestaña Credentials>'
cd laboratorio-04-autenticacion-oidc/aplicacion_base_lab-04 && mvn spring-boot:run
curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" http://localhost:8081/privada
# esperado: 302 http://localhost:8081/oauth2/authorization/keycloak
```

Para restaurar Keycloak al estado de un lab: copiar su `keycloak/curso-realm.json`
a `keycloak/import/`, `docker compose down -v && docker compose up -d`.

## Al crear un laboratorio nuevo

1. Parte de `aplicacion_base_lab-(XX-1)/`, cópialo a `aplicacion_base_lab-XX/`.
2. Añade solo la funcionalidad de ese lab, comentada en español.
3. Actualiza `keycloak/curso-realm.json` con la configuración nueva.
4. Escribe el README con el esqueleto de 11 secciones y enlaces a keycloak.org.
5. Actualiza la sección "Estado actual" de este archivo.
