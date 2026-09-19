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

Estado: laboratorio 11 completado. La aplicación es idéntica a la de los
laboratorios 07, 08, 09 y 10 (autenticación OpenID Connect con
Authorization Code + PKCE, cierre de sesión RP-Initiated Logout, caducidad
de sesión por inactividad, autorización por rol de realm y scopes mínimos).
Los laboratorios 08, 09, 10 y 11 **no cambian ninguna línea de la
aplicación**: el 08 endurece la política de contraseñas y la fuerza bruta,
el 09 añade un segundo factor (TOTP), el 10 permite iniciar sesión con una
cuenta de GitHub (identity brokering) y el 11 añade un cliente máquina a
máquina (`servicio-conciliacion`, Client Credentials) que no tiene interfaz
ni usuario y se demuestra íntegramente con `curl` y los scripts de
`laboratorio-11-maquina-a-maquina/scripts/`. Todo son ajustes del realm en
Keycloak. Es justo la lección: la aplicación delega la autenticación, así
que ni reforzarla, ni añadir proveedores externos, ni dar de alta otros
clientes del realm la tocan.

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
