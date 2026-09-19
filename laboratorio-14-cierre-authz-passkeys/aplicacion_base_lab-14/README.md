# Aplicación base

Aplicación web mínima en Spring Boot sobre la que se realizan todos los
laboratorios del curso. **Trabaja siempre sobre esta carpeta.** Si te
pierdes, cada laboratorio incluye una copia con todo lo implementado hasta
ese punto en `laboratorio-XX/aplicacion_base_lab-XX/`.

## Qué contiene

| Ruta        | Sección  | Estado                                                        |
|-------------|----------|---------------------------------------------------------------|
| `/`         | Pública  | Portada informativa. Abierta a todo el mundo.                 |
| `/privada`  | Privada  | Datos de clientes. Exige inicio de sesión (lab 04) y el rol `gestor-clientes` (lab 06). |
| `/denegado` | —        | Página de acceso denegado, HTTP 403 (lab 06).                 |
| `/logout`   | —        | Cierre de sesión local y en Keycloak (lab 05). Solo POST.     |

Estado: laboratorio 14 completado. **Esta carpeta es una copia exacta de
`aplicacion_base_lab-12/`**: el laboratorio 14 no cambia ni una línea de
código.

No es pereza, es el argumento del laboratorio. Sus tres partes ocurren
enteras dentro de Keycloak:

- **Passwordless con passkeys** (parte A) vive en el flujo de
  autenticación. La aplicación recibe el mismo ID Token, emitido solo
  cuando el usuario ha superado el flujo completo; si fue con una passkey
  o con contraseña más OTP le resulta invisible, igual que en el
  laboratorio 09.
- **Authorization Services** (parte B) se demuestra en un *resource
  server* aparte, el cliente `api-clientes`, con la herramienta Evaluate y
  el grant UMA por `curl`. Integrarlo de verdad exigiría que la aplicación
  pidiera un RPT y leyera `authorization.permissions`, o el *policy
  enforcer*, que es una librería `org.keycloak.*` y está prohibida por la
  regla de oro del curso.
- **Acceso privilegiado temporal** (parte C) lo ejecuta un servicio
  externo contra la Admin REST API. La aplicación solo observa el
  resultado: `luis` aparece con un rol más en la cabecera tras volver a
  iniciar sesión, gracias al `GrantedAuthoritiesMapper` del laboratorio
  06, que ya estaba escrito.

Sigue vigente todo lo anterior: autenticación OpenID Connect con
Authorization Code + PKCE (04), RP-Initiated Logout y caducidad de sesión
(05), autorización por rol de realm y página `/denegado` (06), scopes
mínimos (07) y el registro de auditoría `seguridad/AuditoriaDeAcceso.java`
con el bean `AuthorizationEventPublisher` de `SeguridadConfig` (12).

## Estructura

```
aplicacion_base/
├── pom.xml                                   Dependencias Maven
└── src/main/
    ├── java/com/curso/keycloak/
    │   ├── AplicacionBase.java               Arranque de Spring Boot
    │   ├── seguridad/
    │   │   └── SeguridadConfig.java          Acceso, login OIDC (04), logout (05), roles (06)
    │   └── web/
    │       ├── PortalControlador.java        Rutas /, /privada y /denegado
    │       └── Cliente.java                  Modelo de datos ficticio
    └── resources/
        ├── application.yml                   Puerto 8081, cliente OIDC, scopes (07) y caducidad
        ├── templates/                        Vistas Thymeleaf
        │   ├── fragmentos.html               Cabecera (nombre, roles, "Cerrar sesión") y pie
        │   ├── publica.html
        │   ├── privada.html
        │   └── denegado.html                 Acceso denegado (lab 06)
        └── static/estilos.css
```

## Requisitos (Ubuntu 24.04 en WSL)

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk maven curl jq
java -version   # debe indicar 21
mvn -version
```

## Ejecutar

Necesita el secreto del cliente `aplicacion-base` (pestaña *Credentials*
del cliente en la Admin Console de Keycloak):

```bash
cd aplicacion_base
export KEYCLOAK_CLIENT_SECRET='pega-aquí-el-secreto'
mvn spring-boot:run
```

Abrir en el navegador de Windows: <http://localhost:8081>

La aplicación usa el puerto **8081** porque Keycloak ocupará el **8080**.

## Regla de oro del curso

La aplicación **no** utiliza los *Keycloak Client Adapters* (ninguna
dependencia `org.keycloak.*`). La documentación oficial recomienda usar el
soporte OpenID Connect del propio ecosistema —en nuestro caso Spring
Security— y dejar los adaptadores como último recurso:
<https://www.keycloak.org/securing-apps/overview>
