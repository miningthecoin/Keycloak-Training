# Aplicación base

Aplicación web mínima en Spring Boot sobre la que se realizan todos los
laboratorios del curso. **Trabaja siempre sobre esta carpeta.** Si te
pierdes, cada laboratorio incluye una copia con todo lo implementado hasta
ese punto en `laboratorio-XX/aplicacion_base_lab-XX/`.

## Qué contiene

| Ruta       | Sección  | Estado inicial                                  |
|------------|----------|-------------------------------------------------|
| `/`        | Pública  | Portada informativa. Abierta a todo el mundo.   |
| `/privada` | Privada  | Datos de clientes. **También abierta a todos.** |

No hay autenticación, autorización, sesión ni roles. Ese es el punto de
partida: los laboratorios cierran una debilidad cada vez.

## Estructura

```
aplicacion_base/
├── pom.xml                                   Dependencias Maven
└── src/main/
    ├── java/com/curso/keycloak/
    │   ├── AplicacionBase.java               Arranque de Spring Boot
    │   └── web/
    │       ├── PortalControlador.java        Rutas / y /privada
    │       └── Cliente.java                  Modelo de datos ficticio
    └── resources/
        ├── application.yml                   Puerto 8081 y ajustes
        ├── templates/                        Vistas Thymeleaf
        │   ├── fragmentos.html               Cabecera y pie comunes
        │   ├── publica.html
        │   └── privada.html
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

```bash
cd aplicacion_base
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
