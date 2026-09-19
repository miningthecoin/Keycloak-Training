# Laboratorio 13 · Endurecimiento (opcional)

**Tipo:** opcional · **Estado:** no desarrollado · **Continúa en:** `laboratorio-14-cierre-authz-passkeys`

## Por qué este laboratorio es opcional

Todo el curso ha corrido en modo de desarrollo: `start-dev`, HTTP sin TLS,
base de datos H2 embebida, `admin`/`admin` como administrador de arranque y
secretos escritos en variables de una terminal. Eso es correcto para
aprender y es exactamente lo que **no** se despliega.

El paso de esa configuración a una de producción es un trabajo real, pero
es **trabajo de plataforma, no de desarrollo seguro de aplicaciones**: lo
hace quien opera Keycloak, normalmente una vez, y casi nada de ello cambia
una línea de la aplicación Spring. Por eso el curso lo deja como material
de consulta y salta del laboratorio 12 al 14.

**Si vienes del laboratorio 12, ve directamente al laboratorio 14.** No te
falta nada: el laboratorio 14 parte del realm tal como quedó en el 12 y no
supone ninguna configuración de endurecimiento previa.

## Qué cubriría

Los temas están tratados en la **lámina 43 de la presentación del curso** y
desarrollados, con los valores concretos y sus advertencias, en la sección
**"Mitigating security threats"** de la guía de administración:

<https://www.keycloak.org/docs/latest/server_admin/index.html#mitigating_security_threats>

De un vistazo:

| Tema | Qué se cambia | Dónde mirar |
|---|---|---|
| **HTTPS** | Salir de `start-dev`, terminar TLS y exigir `https` en el realm (*Require SSL*) | Guía del servidor, *Configuring TLS* |
| **Redirect URIs estrictas** | Nada de comodines: la URI exacta, como ya hace el laboratorio 04 | *Mitigating security threats*, "Unspecific redirect URIs" |
| **Políticas de cliente (OAuth 2.1)** | Exigir PKCE S256, prohibir flujos heredados, forzar autenticación de cliente fuerte | Guía de administración, *Client policies* |
| **Rotación de secretos** | Pasar de *Client Id and Secret* a *Signed JWT* o mTLS, o rotar con periodicidad | Laboratorio 11, 2.7 |
| **Administrador permanente** | Sustituir el `admin`/`admin` de `KC_BOOTSTRAP_ADMIN_*` por una cuenta real con MFA | Guía del servidor, *Configuring the admin user* |
| **Base de datos** | H2 fuera; base de datos externa con copias de seguridad | Guía del servidor, *Configuring the database* |
| **Cabeceras y CSP** | *Security defenses* del realm | *Mitigating security threats*, "Security headers" |
| **Retención de registros** | El almacén de eventos no es el archivo de largo plazo (laboratorio 12, 2.8) | PCI DSS 10.5.1 |

## Qué NO hace este laboratorio

No modifica el realm `curso` ni `keycloak/docker-compose.yml`. No existe
`keycloak/curso-realm.json` ni `aplicacion_base_lab-13/` en esta carpeta, a
propósito: el punto de control del laboratorio 12 sigue siendo válido y es
el que usa el laboratorio 14.

## Siguiente laboratorio

`laboratorio-14-cierre-authz-passkeys`: la demostración de cierre, con
passkeys, autorización fina y acceso privilegiado temporal.
