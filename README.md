# Desarrollo seguro con Keycloak

Curso práctico de 16 horas para desarrolladores junior y junior avanzados.
Integra paso a paso las funcionalidades de Keycloak en una aplicación web
Java, enmarcado en las prácticas de desarrollo seguro de **OWASP** y en los
requisitos de **PCI DSS** relativos a control de acceso, autenticación y
trazabilidad.

Toda la parte de Keycloak se apoya exclusivamente en la documentación
oficial: <https://www.keycloak.org/guides> y
<https://www.keycloak.org/documentation> (versión de referencia: 26.7.3).

## Cómo se trabaja

1. Clona este repositorio en tu máquina virtual **WSL con Ubuntu 24.04**.
2. Sigue el `laboratorio-00-entorno` para instalar Docker, JDK 21 y Maven.
3. Trabaja **siempre** sobre la carpeta `aplicacion_base/`. Es tu aplicación.
4. Cada laboratorio tiene un `README.md` con la teoría, un ejercicio que
   demuestra la debilidad actual, los pasos de integración con el código
   completo, y un ejercicio que demuestra la corrección.
5. Si te quedas atrás, cada laboratorio incluye
   `aplicacion_base_lab-XX/` con todo lo implementado hasta ese punto
   (y nada de los laboratorios posteriores).

## Estructura del repositorio

```
keycloak-curso/
├── README.md                      Este documento
├── aplicacion_base/               Tu aplicación de trabajo
├── keycloak/                      docker-compose.yml de Keycloak (todo el curso)
├── laboratorio-00-entorno/
├── laboratorio-01-fundamentos/     Solo teoría
├── laboratorio-02-estado-del-arte/ Solo teoría
├── laboratorio-13-endurecimiento/  Opcional: solo README, sin realm ni aplicación
├── laboratorio-03-.../
│   ├── README.md                  Guion completo del laboratorio
│   ├── keycloak/                  Export del realm al terminar el laboratorio
│   └── aplicacion_base_lab-03/    Aplicación con lo implementado hasta aquí
└── ...
```

## Plan del curso

| # | Laboratorio | Tipo | Funcionalidad de Keycloak | Debilidad antes → corrección después | OWASP / PCI DSS |
|---|---|---|---|---|---|
| 00 | Preparación del entorno | Práctico | — | — | — |
| 01 | Fundamentos: OAuth 2.0, OpenID Connect, SAML, JWT y flujos | Teórico | — | — | Top 10 A01, A07; ASVS V2, V3, V4; PCI 7, 8 |
| 02 | Estado del arte: OAuth 2.1, PKCE, DPoP, FAPI, passkeys, step-up, M2M, zero trust | Teórico | — | — | — |
| 03 | Keycloak: arquitectura, conceptos e instalación | Práctico | Realm, usuarios, consolas Admin y Account | — | — |
| 04 | Autenticación con Authorization Code + PKCE | Práctico | Cliente OIDC confidencial, Standard flow | `/privada` visible sin identidad → exige inicio de sesión | A07; PCI 8.2 |
| 05 | Logout y gestión de sesión | Práctico | RP-initiated logout, timeouts de sesión y token | "Salir" no cierra la sesión SSO → cierre real y expiraciones | A07; PCI 8.2.8 |
| 06 | Autorización basada en roles (RBAC) | Práctico | Roles de realm/cliente, grupos, roles en el token | Cualquier autenticado ve clientes → solo el rol autorizado | A01; PCI 7.2 |
| 07 | Minimización de claims y audiencia | Práctico | Client scopes, protocol mappers, `aud` | Token con datos de más y válido en cualquier sitio → mínimo necesario | A01, A04; PCI 7.2 |
| 08 | Políticas de contraseña y protección contra fuerza bruta | Práctico | Password policies, brute force detection | Contraseña `123456` e intentos ilimitados → política y bloqueo | A07; PCI 8.3.4, 8.3.6 |
| 09 | Segundo factor con TOTP | Práctico | OTP policy, flujo Browser – Conditional OTP | Contraseña robada = cuenta robada → 2FA | A07; PCI 8.4 |
| 10 | Inicio de sesión federado con GitHub | Práctico | Identity brokering, first-login flow, mappers | Otra base de credenciales que custodiar → delegación | A07 |
| 11 | Máquina a máquina | Práctico | Service account, client credentials, validación con JWKS (curl) | Secreto estático compartido → tokens de corta vida | A02, A07; PCI 8.6 |
| 12 | Auditoría de eventos | Práctico | Eventos de usuario y de administración | Sin rastro de accesos → registro consultable | A09; PCI 10 |
| 13 | Endurecimiento | **Opcional** | HTTPS, redirect URIs estrictas, client policies OAuth 2.1, rotación de secreto | Configuración de laboratorio → lista para producción | A05; PCI 2, 4 |
| 14 | Cierre: passkeys, autorización fina y acceso temporal | Demostración | WebAuthn Passwordless y passkeys, Authorization Services, Admin REST API | — | A01, A07; ASVS V2.2, V2.8, V4.1; PCI 7.2, 8.4, 10.2.1.2 |

> **El laboratorio 13 es opcional y no está desarrollado.** Reúne tareas de
> plataforma, no de desarrollo seguro de aplicaciones, y casi ninguna toca
> la aplicación Spring. Su carpeta contiene un resumen de una página con los
> enlaces oficiales. **El laboratorio 14 parte directamente del punto de
> control del laboratorio 12** y no supone ningún endurecimiento previo.

## Agenda de 16 horas (4 sesiones de 4 h)

| Sesión | Contenido | Horas |
|---|---|---|
| 1 | Lab 00 (0,5 h) · Lab 01 (1,5 h) · Lab 02 (1 h) · Lab 03 (1 h) | 4 |
| 2 | Lab 04 (1,5 h) · Lab 05 (1 h) · Lab 06 (1,5 h) | 4 |
| 3 | Lab 07 (1 h) · Lab 08 (1 h) · Lab 09 (1 h) · Lab 10 (1 h) | 4 |
| 4 | Lab 11 (1 h) · Lab 12 (1 h) · Lab 14 (1 h) · Repaso, dudas y cierre (1 h) | 4 |

El laboratorio 13 queda como lectura opcional fuera de sesión.

## Decisiones de diseño

- **Aplicación**: Spring Boot + Thymeleaf, lo más simple posible, con
  comentarios en español en cada sección de código.
- **Sin Keycloak Client Adapters**. La guía oficial de planificación indica
  usar primero el soporte OIDC del ecosistema y dejar los adaptadores como
  último recurso. Usamos el soporte nativo de Spring Security
  (`spring-boot-starter-oauth2-client`).
- **Flujos**: Authorization Code con PKCE para usuarios; Client Credentials
  para máquina a máquina. No se usan Implicit ni Password (desaconsejados
  por RFC 9700 y eliminados de OAuth 2.1).
- **Keycloak** se ejecuta en Docker dentro de WSL con `start-dev`, tal como
  describe la guía "Getting started – Docker".
- **Puertos**: Keycloak 8080, aplicación 8081.

## Plantilla de cada laboratorio práctico

```
1. Objetivo
2. Conceptos (con enlace a la sección oficial de keycloak.org)
3. Relación con OWASP y PCI DSS
4. Ejercicio ANTES: cómo evidenciar la debilidad, paso a paso, y resultado esperado
5. Configuración en Keycloak, paso a paso
6. Cambios en la aplicación, con el código completo
7. Ejercicio DESPUÉS: cómo comprobar la corrección, paso a paso, y resultado esperado
8. Lista de verificación
9. Problemas frecuentes
```

## Referencias oficiales

- Guías: <https://www.keycloak.org/guides>
- Documentación: <https://www.keycloak.org/documentation>
- Planificación de la securización de aplicaciones: <https://www.keycloak.org/securing-apps/overview>
- OpenID Connect en Keycloak: <https://www.keycloak.org/securing-apps/oidc-layers>
- Getting started con Docker: <https://www.keycloak.org/getting-started/getting-started-docker>
- Guía de administración del servidor: <https://www.keycloak.org/docs/latest/server_admin/index.html>
