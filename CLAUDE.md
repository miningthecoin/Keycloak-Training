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
demostrado con curl) · 12 auditoría de eventos · 13 endurecimiento ·
14 cierre (Authorization Services, passkeys).

## Estado actual

- Hechos: README raíz, `aplicacion_base`, `keycloak/docker-compose.yml`,
  labs 00, 03, 04, 05, 06, 07 y 08 completos.
- Pendientes: labs 01, 02, 09 a 14.
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
