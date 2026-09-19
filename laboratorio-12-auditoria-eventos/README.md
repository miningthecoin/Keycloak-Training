# Laboratorio 12 · Auditoría de eventos

**Tipo:** práctico · **Duración:** 45 min · **Funcionalidad de Keycloak:** eventos de usuario, eventos de administración y *event listeners*

## 1. Objetivo

Que quede rastro de lo que ocurre, y que ese rastro se pueda consultar. Al
terminar:

- Keycloak guarda los **eventos de usuario** (quién entró, quién falló la
  contraseña, quién cerró sesión, qué cliente pidió un token) y los muestra
  en **Events → User events**, con filtros por usuario y por tipo.
- Keycloak guarda los **eventos de administración** (quién regeneró un
  secreto, quién cambió un rol) con la **representación** del cambio, en
  **Events → Admin events**.
- El *event listener* `jboss-logging` escribe también los eventos de éxito
  en el log del contenedor, no solo los errores.
- La aplicación escribe su **propio registro de auditoría** con el usuario,
  el `sub`, el `sid` y la ruta de cada acceso denegado, y ese `sid` es el
  mismo que aparece en los eventos de Keycloak: los dos registros se
  **correlacionan**.
- Los eventos se pueden extraer por API para alimentar un SIEM.

### Beneficios de implementarlo

- **Se puede responder a la pregunta del auditor.** "¿Quién vio los datos
  de clientes el martes?" deja de ser una conjetura y pasa a ser una
  consulta con filtros.
- **Los ataques se ven.** Tres `LOGIN_ERROR` seguidos del mismo usuario y
  la misma IP son un patrón. Sin eventos, son tres líneas que nadie guardó.
- **Los cambios de configuración tienen autor.** Regenerar un secreto o
  tocar un rol deja constancia de qué administrador, desde qué IP y qué
  envió exactamente.
- **El rastro cruza sistemas.** El `sid` une lo que hizo Keycloak (emitir
  la sesión) con lo que hizo la aplicación (servir o denegar datos).
- **Se integra con lo que ya haya.** La API de eventos y el listener de log
  son dos formas estándar de llevar todo esto a un SIEM sin escribir un
  *provider* propio.

### Un ejemplo de las amenazas que evita

Un lunes, la Cooperativa Andina recibe un aviso: un listado con datos de
clientes ha aparecido donde no debía. La dirección pregunta lo evidente:
quién accedió a la sección de clientes la semana pasada, desde dónde, y si
alguien tocó alguna credencial de servicio en esas fechas.

Sin auditoría, la respuesta honesta es "no lo sabemos". La base de datos
de Keycloak no guarda ningún inicio de sesión; el log del contenedor solo
tiene los errores, y solo mientras Docker no rote el archivo; la aplicación
escribe trazas técnicas de arranque y nada más. No se puede distinguir un
acceso legítimo de uno ilegítimo, porque no hay ninguno registrado. Tampoco
se puede saber si el secreto de `servicio-conciliacion` se regeneró el
martes a las tres de la tarde, ni quién lo hizo.

Peor aún: si alguien hubiera abusado de una cuenta, no habría forma de
acotar el alcance (¿una sesión o cincuenta?), ni de avisar a los afectados,
ni de demostrar ante un auditor que la brecha se limitó a lo que se dice.
La falta de registro no solo impide detectar: impide **cerrar** el
incidente.

Tras este laboratorio, esa misma pregunta se responde en tres consultas:
*User events* filtrado por usuario, *Admin events* filtrado por tipo de
recurso, y el log de la aplicación filtrado por `sid`.

Fuentes oficiales (Server Administration Guide):

- *Configuring auditing to track events*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#configuring-auditing-to-track-events>
- *Auditing user events* (activación, expiración, *Add saved types*):
  <https://www.keycloak.org/docs/latest/server_admin/index.html#auditing-user-events>
- *Auditing admin events* (*Include representation* y su coste):
  <https://www.keycloak.org/docs/latest/server_admin/index.html#auditing-admin-events>
- *Event listener*, *The logging event listener* y *The Email Event
  Listener*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#event-listener>,
  <https://www.keycloak.org/docs/latest/server_admin/index.html#the-logging-event-listener>,
  <https://www.keycloak.org/docs/latest/server_admin/index.html#the-email-event-listener>

Y de las guías del servidor:

- *Configuring providers*, formato de las opciones de SPI:
  <https://www.keycloak.org/server/configuration-provider#_configuration_option_format>
- *Configuring Keycloak*, fuentes de configuración y su prioridad:
  <https://www.keycloak.org/server/configuration#_configuring_sources_for_keycloak>
- *Configuring logging*, niveles por categoría:
  <https://www.keycloak.org/server/logging#_configuring_category_specific_log_levels>

## 2. Conceptos

### 2.1 Dos registros distintos, y ninguno es el otro

Keycloak distingue dos familias de eventos, y la guía les dedica dos
secciones separadas:

| | **User events** | **Admin events** |
|---|---|---|
| Qué registran | Lo que hacen los **usuarios** en el realm: iniciar sesión, fallar la contraseña, cerrar sesión, canjear un código por un token | Lo que hacen los **administradores**: cada llamada a la API de administración, que es lo que ejecuta la Admin Console por debajo |
| Ejemplos de tipo | `LOGIN`, `LOGIN_ERROR`, `LOGOUT`, `CODE_TO_TOKEN`, `CLIENT_LOGIN`, `INTROSPECT_TOKEN_ERROR` | `CREATE`, `UPDATE`, `DELETE`, `ACTION` sobre recursos `CLIENT`, `REALM_ROLE`, `USER`, `REALM` |
| Campo clave | `userId`, `sessionId`, `ipAddress`, `error` | `authDetails` (qué administrador, desde qué IP), `resourcePath`, `representation` |
| PCI DSS | 10.2.1.1, 10.2.1.4, 10.2.1.5 | 10.2.1.2 |

La guía lo resume así para los de administración: *la Admin Console
ejecuta las acciones administrativas invocando la interfaz REST de
Keycloak, y Keycloak audita esas invocaciones*. Por eso el `resourcePath`
de un evento de administración es una ruta de API
(`clients/<id>/client-secret`), no un texto descriptivo.

Y hay un tercer registro que **Keycloak no ve**: el de la propia
aplicación. Keycloak sabe que `luis` inició sesión, pero no sabe que luego
pidió `/privada` y se le denegó, porque esa decisión la toma Spring
Security en el laboratorio 06. Ese registro lo añade la aplicación
(sección 6).

### 2.2 Por defecto no se guarda nada

La guía es explícita: *por defecto, Keycloak no almacena ni muestra los
eventos en la Admin Console; solo los eventos de error se registran en la
Admin Console y en el archivo de log del servidor*.

Eso es exactamente lo que se comprueba en el ejercicio ANTES: la pantalla
Events está vacía, y en `docker compose logs` solo hay líneas `WARN` de
`LOGIN_ERROR`. Los inicios de sesión correctos no dejan rastro en ninguna
parte.

### 2.3 Saved types: qué se guarda y qué no

Al activar *Save events* hay una lista de **tipos guardados** (*Add saved
types*). Por defecto trae 103 tipos, que incluyen todos los que interesan
en este curso: `LOGIN`, `LOGIN_ERROR`, `LOGOUT`, `CODE_TO_TOKEN`,
`CLIENT_LOGIN`, `UPDATE_CREDENTIAL`, `USER_DISABLED_BY_TEMPORARY_LOCKOUT`…

Pero **hay tipos que existen y no están en esa lista por defecto**. En
Keycloak 26.7.3 son estos:

```
client_info · identity_provider_response · identity_provider_retrieve_token
introspect_token · invalid_signature · pushed_authorization_request
refresh_token · register_node · unregister_node · user_info_request
user_session_deleted
```

Mira la lista y verás el criterio: son eventos de **alta frecuencia y bajo
valor individual**. Un `REFRESH_TOKEN` o un `USER_INFO_REQUEST` ocurren en
cada ciclo de vida de cada sesión de cada usuario; un `INTROSPECT_TOKEN`
ocurriría en cada petición que una API validara contra Keycloak
(laboratorio 11, 2.4). Guardarlos todos llenaría la base de datos de ruido
y dejaría los eventos importantes sepultados. El valor por defecto prioriza
lo que tiene significado de seguridad.

Nosotros **añadimos uno**: `INTROSPECT_TOKEN_ERROR`. La versión de éxito
seguiría siendo ruido, pero el **error** es una señal: significa que
alguien presentó a la API un token que no era válido, que es justo el
ataque que el laboratorio 11 demostró con el token manipulado.

Elegir los tipos guardados es, por tanto, una decisión con dos filos:

- Guardar de más: base de datos enorme, consultas lentas, y el evento que
  importa perdido entre miles que no importan.
- Guardar de menos: el evento que el auditor pide no existe, y no se puede
  recuperar hacia atrás. **Lo que no se guardó no se puede investigar
  después.**

Una regla práctica: guardar todo lo relacionado con autenticación,
credenciales y permisos, y dejar fuera lo que sea tráfico de protocolo
rutinario, salvo su versión `_ERROR`.

### 2.4 El listener y el almacén son cosas distintas

Este punto confunde a mucha gente. Hay **dos destinos** independientes:

1. **El almacén de eventos** (la base de datos). Lo controlan *Save events*
   y los *Saved types*. Es lo que se ve en la pantalla Events y lo que
   devuelve la API.
2. **Los event listeners**. Reciben **todos** los eventos, filtren lo que
   filtren los *Saved types*. `jboss-logging` los escribe en el log del
   servidor, en la categoría `org.keycloak.events`.

Se comprueba en el ejercicio 7.3: en el log del contenedor aparecen líneas
`USER_INFO_REQUEST` que **no** están en la pantalla Events, porque ese tipo
no está entre los guardados. El listener lo vio; el almacén no lo guardó.

La guía describe dos listeners incorporados:

- **Logging Event Listener** (`jboss-logging`), el que usamos.
- **Email Event Listener**, que envía un correo al usuario ante ciertos
  eventos (error de login, cambio de contraseña, cambio o retirada de
  OTP…). Requiere *Realm's email settings configured* y que el usuario
  tenga correo verificado. **No se configura en este curso** porque el
  realm no tiene SMTP.

### 2.5 Los niveles del listener: por qué el log solo muestra errores

La guía dice: *cuando el Logging Event Listener está habilitado, este
listener escribe en un archivo de log cuando ocurre un evento de error*, y
que *Keycloak no incluye los eventos de log de depuración en los logs del
servidor por defecto*. Traducido: los eventos de **éxito** se escriben a
nivel `DEBUG`, que no se muestra, y los de **error** a `WARN`, que sí.

Por eso, en el ANTES, `docker compose logs` enseña los `LOGIN_ERROR` de
`luis` pero no el `LOGIN` de `ana`.

La guía da la solución exacta:

```
bin/kc.[sh|bat] start --spi-events-listener--jboss-logging--success-level=info --spi-events-listener--jboss-logging--error-level=error
```

y aclara que *los valores válidos para los niveles de log son debug, info,
warn, error y fatal*. Ese formato
`spi-<spi-id>--<provider-id>--<propiedad>` es el que documenta la guía
*Configuring providers*. Nosotros aplicamos solo la primera mitad
(`success-level=info`) en `keycloak/docker-compose.yml`, que es la única
pieza compartida del curso que este laboratorio modifica.

Existe una alternativa que la guía también menciona: subir el nivel de la
**categoría** `org.keycloak.events` a `DEBUG` con `--log-level`. Es peor
para nuestro caso: mostraría los eventos de éxito, sí, pero mezclados con
toda la depuración interna de esa categoría. Cambiar el nivel del listener
es más quirúrgico.

### 2.6 La representación: el "qué cambió", y su coste

En los eventos de administración, *Include representation* incluye, según
la guía, *los documentos JSON enviados a través de la API REST de
administración, para que puedas ver las acciones de los administradores*.

Sin representación, un evento dice "el administrador `admin` hizo `UPDATE`
sobre `roles-by-id/1148a8a9…` a las 03:52". Con representación, dice
además **qué mandó**: el JSON completo del rol, con su nueva descripción.

Dos matices importantes:

- **Keycloak guarda lo enviado, no un "antes y después".** La
  representación es el estado **nuevo**. Para saber qué había antes hay que
  compararlo con el punto de control anterior (el `curso-realm.json` de
  cada laboratorio) o con un evento previo sobre el mismo recurso. No
  esperes un *diff*.
- **Puede contener datos sensibles.** Es un volcado de lo que viajó por la
  API: atributos de usuario, configuración de clientes, correos. La propia
  guía avisa del volumen (*puede llevar a almacenar mucha información en la
  base de datos*) y ofrece limitar el tamaño con
  `--spi-events-store--jpa--max-field-length`. Por eso el registro de
  auditoría es, él mismo, un activo a proteger: es PCI DSS 10.3.

En el ejercicio 7.4 se ve un caso tranquilizador y uno a vigilar: al
regenerar el secreto, Keycloak guarda `{"type":"secret","value":"**********"}`
(enmascarado), pero al cambiar un rol guarda el objeto entero.

### 2.7 Correlación: el `sid` como hilo conductor

El ID Token que la aplicación recibe lleva un claim `sid`, el
identificador de la sesión SSO. Los eventos `LOGIN`, `LOGOUT` y
`CODE_TO_TOKEN` de Keycloak llevan ese mismo valor en su campo
`sessionId`.

Si la aplicación escribe el `sid` en su propio registro, los dos sistemas
quedan cosidos:

```
Keycloak  LOGIN    usuario=luis  sessionId=089Nm3E2EwVykzErr5EbLukb  ip=172.18.0.1
   aplicación      LOGIN         sid=089Nm3E2EwVykzErr5EbLukb
   aplicación      DENEGADO      sid=089Nm3E2EwVykzErr5EbLukb  ruta=/privada
```

Con una sola cadena de búsqueda se reconstruye la historia completa: desde
qué IP se autenticó alguien, con qué cliente, y qué pidió después en la
aplicación. Eso es lo que convierte dos registros sueltos en una **traza de
auditoría**.

### 2.8 Retención: 30 días no son 12 meses

El campo *Expiration* de *User events settings* borra los eventos pasado
ese tiempo. En este laboratorio se fija en **30 días**, un valor didáctico
que permite ver el campo en acción sin llenar la base de datos H2 de
desarrollo.

**PCI DSS 10.5.1 exige conservar el historial de los registros de
auditoría durante al menos 12 meses, con los 3 meses más recientes
inmediatamente disponibles para análisis.** Treinta días no cumplen ese
requisito, y subir el número tampoco sería la solución correcta: la base de
datos de Keycloak **no es un almacén de registros de largo plazo**. Es la
base de datos operativa de un servidor de identidad; crecerla sin límite
degrada el propio servicio y mezcla dos responsabilidades.

El diseño correcto es el que la arquitectura de Keycloak ya insinúa: el
almacén interno guarda una ventana corta y **consultable en caliente**,
mientras que un *event listener* (o una extracción periódica por API, como
la de 7.6) envía una copia a un sistema externo de gestión de registros,
que es el que cumple la retención, la protección contra modificación (PCI
DSS 10.3) y la revisión periódica (PCI DSS 10.4).

## 3. Relación con OWASP, ASVS y PCI DSS

- **OWASP A09:2021 Fallos de registro y monitorización de seguridad.** Es
  literalmente esta categoría: eventos auditables (inicios de sesión,
  fallos, acciones de alto valor) que no se registran, registros que solo
  viven en local, y ausencia de alertas. El ANTES es el caso de manual.
- **OWASP ASVS V7.1 (contenido del log).** El registro debe incluir los
  eventos de seguridad relevantes con suficiente contexto (quién, qué,
  cuándo, desde dónde) y **no** debe contener credenciales ni datos
  sensibles. Por eso `AuditoriaDeAcceso` escribe identificadores y nunca
  tokens (sección 6).
- **OWASP ASVS V7.2 (procesamiento del log).** Los registros deben poder
  procesarse y consultarse. La API de eventos (7.6) es lo que hace posible
  llevarlos a un SIEM.
- **OWASP ASVS V7.3 (protección del log).** Los registros deben protegerse
  contra acceso y modificación no autorizados. Aquí entra el aviso de 2.6:
  la representación puede contener datos sensibles, y la pantalla Events
  solo debe ser accesible a quien tenga el rol adecuado.
- **PCI DSS 10.2.** Los registros de auditoría deben capturar, entre otros:
  **10.2.1.1** todo acceso individual a datos de titulares de tarjeta (lo
  registra la aplicación, no Keycloak); **10.2.1.2** todas las acciones
  realizadas por personas con acceso administrativo (los *Admin events*);
  **10.2.1.4** los intentos de acceso lógico inválidos (los `LOGIN_ERROR`);
  **10.2.1.5** los cambios en credenciales, incluida su creación y
  elevación de privilegios (la regeneración del secreto y los cambios de
  rol).
- **PCI DSS 10.3.** Los registros de auditoría se protegen frente a
  destrucción y modificación no autorizada.
- **PCI DSS 10.4.** Los registros se revisan, y esa revisión debe poder
  hacerse de forma eficiente: los filtros de la pantalla Events y las
  consultas por API son el mecanismo.
- **PCI DSS 10.5.1.** Retención de 12 meses con 3 inmediatamente
  disponibles (2.8).

## 4. Ejercicio ANTES: ocurren cosas y no queda constancia

**Propósito.** Provocar tres hechos que cualquier auditor querría poder
revisar, y comprobar que ninguno deja rastro consultable.

Arranca la aplicación tal como quedó en el laboratorio 11:

```bash
cd ~/keycloak-curso/laboratorio-11-maquina-a-maquina/aplicacion_base_lab-11
export KEYCLOAK_CLIENT_SECRET='el-secreto-de-tu-cliente'
mvn spring-boot:run
```

### 4.1 Los tres hechos

1. **Un acceso legítimo y uno denegado.** Ventana de incógnito →
   <http://localhost:8081> → **Clientes** → entra como `ana`
   (`Andina*Segura2026` + código TOTP del laboratorio 09) → verás la tabla
   → **Cerrar sesión**. Después, en la misma ventana, **Clientes** → entra
   como `luis` / `luis123` → recibes *Acceso denegado* → **Cerrar sesión**.
2. **Tres intentos fallidos.** Ventana de incógnito nueva → **Clientes** →
   escribe `luis` con una contraseña incorrecta **tres veces**, separando
   los intentos un par de segundos. Son menos de los 5 que bloquean la
   cuenta (laboratorio 08), así que no salta la protección: es
   exactamente el tipo de tanteo que hay que poder detectar.
3. **Un cambio de credencial.** Admin Console → **Clients** →
   `servicio-conciliacion` → **Credentials** → **Regenerate**.

### 4.2 Dónde no está la respuesta

**En la Admin Console:** menú **Events**, pestañas *User events* y *Admin
events*.

Por API, que es lo mismo sin capturas de pantalla:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -d client_id=admin-cli -d username=admin -d password=admin -d grant_type=password | jq -r .access_token)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/admin/realms/curso/events | jq -c .
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/admin/realms/curso/admin-events | jq -c .
```

Salida real:

```
[]
[]
```

**En el log del contenedor:**

```bash
cd ~/keycloak-curso/keycloak && docker compose logs --since 30m | grep org.keycloak.events
```

Salida real (recortada a las líneas del realm `curso`):

```
2026-09-17 03:37:47,486 WARN [org.keycloak.events] (executor-thread-50) type="LOGIN_ERROR", realmName="curso", clientId="aplicacion-base", userId="03e6b8df-bf8b-452e-a9c3-4d5bd6711321", ipAddress="172.18.0.1", error="invalid_user_credentials", auth_method="openid-connect", auth_type="code", redirect_uri="http://localhost:8081/login/oauth2/code/keycloak", code_id="JEMG-IUzs-cPexI-sQ6t8cF3", username="luis"
2026-09-17 03:37:50,592 WARN [org.keycloak.events] (executor-thread-57) type="LOGIN_ERROR", ... username="luis"
2026-09-17 03:37:53,370 WARN [org.keycloak.events] (executor-thread-56) type="LOGIN_ERROR", ... username="luis"
```

**En el log de la aplicación:** 22 líneas, todas de arranque de Spring
Boot. Ni una palabra sobre `ana`, `luis` o `/privada`.

### 4.3 La pregunta del auditor

> **¿Quién accedió a los datos de clientes el martes, y quién cambió esa
> credencial de servicio?**

Repasa lo que hay:

| Hecho ocurrido | ¿Dónde consta? |
|---|---|
| `ana` entró y vio la tabla de clientes | **En ningún sitio.** Los eventos de éxito no se guardan (2.2) y se escriben a nivel DEBUG, que no se muestra (2.5). |
| `luis` entró y se le denegó `/privada` | **En ningún sitio.** Su `LOGIN` tampoco se guarda, y la denegación ocurre en la aplicación, que no registra nada. |
| Tres intentos fallidos de `luis` | Tres líneas `WARN` en el log del contenedor, sin más contexto que la IP, y que desaparecen cuando Docker rote el log. No son consultables ni filtrables. |
| Alguien regeneró el secreto | **En ningún sitio.** Ni qué administrador, ni cuándo, ni desde dónde. |

Tres de los cuatro hechos son invisibles, y el cuarto solo sobrevive como
texto suelto. Esto incumple PCI DSS 10.2.1.1, 10.2.1.2, 10.2.1.4 y
10.2.1.5 de golpe, y es el escenario que describe OWASP A09.

Detén la aplicación con `Ctrl+C`.

## 5. Configuración de Keycloak, paso a paso

Con la Admin Console en el realm **`curso`**: menú **Realm settings** →
pestaña **Events**.

### 5.1 Eventos de usuario

1. Subpestaña **User events settings**.
2. **Save events**: **On**.
3. **Expiration**: `30` y unidad **Days**. Lee 2.8 antes de dar este valor
   por bueno en producción.
4. **Save**.

Junto a estos ajustes está el botón **Clear user events**, que vacía lo ya
guardado. No lo pulses ahora.

### 5.2 Comprobar y ampliar los tipos guardados

1. En la misma subpestaña, despliega la lista de tipos (**Add saved
   types** muestra los que se pueden añadir).
2. Comprueba que los que usaremos ya están: `LOGIN`, `LOGIN_ERROR`,
   `LOGOUT`, `CODE_TO_TOKEN`, `CLIENT_LOGIN`.
3. **Añade `INTROSPECT_TOKEN_ERROR`**, que no viene por defecto. Es el
   evento que deja constancia de que alguien presentó a la API un token
   inválido (laboratorio 11, 7.8).
4. **Add** y **Save**.

El porqué de que no venga por defecto, y qué implica elegir la lista, está
en 2.3. La lista pasa de 103 a 104 tipos.

### 5.3 Eventos de administración

1. Subpestaña **Admin events settings**.
2. **Save events**: **On**. Al activarlo aparece el interruptor siguiente.
3. **Include representation**: **On**. Qué aporta y qué cuesta, en 2.6.
4. **Save**.

### 5.4 El listener y su nivel de log

1. Subpestaña **Event listeners**. Comprueba que está `jboss-logging`. No
   añadas `email`: el realm del curso no tiene SMTP configurado (2.4).
2. Con eso, los errores ya salen en el log del contenedor, pero los
   aciertos no (2.5). Para verlos, edita `keycloak/docker-compose.yml` y
   sustituye la línea `command:` por:

   ```yaml
       # LABORATORIO 12: el event listener "jboss-logging" escribe los eventos
       # de ÉXITO (LOGIN, LOGOUT, CLIENT_LOGIN...) a nivel DEBUG y los de ERROR
       # a nivel WARN, así que con la configuración por defecto en
       # "docker compose logs" solo se ven los errores. La opción de abajo sube
       # los de éxito a INFO, tal como documenta la guía de administración en
       # "The logging event listener" (formato spi-<spi>--<provider>--<propiedad>
       # de la guía "Configuring providers"). Cambiar esta línea exige recrear
       # el contenedor: "docker compose up -d" (los datos del volumen se
       # conservan; NO hace falta "down -v").
       command: start-dev --import-realm --spi-events-listener--jboss-logging--success-level=info
   ```

3. Aplica el cambio recreando el contenedor:

   ```bash
   cd ~/keycloak-curso/keycloak
   docker compose up -d
   ```

   Salida real:

   ```
    Container keycloak  Recreated
    Container keycloak  Starting
    Container keycloak  Started
   ```

   Comprueba con qué argumentos quedó:

   ```bash
   docker inspect keycloak --format '{{join .Args " "}}'
   ```

   ```
   start-dev --import-realm --spi-events-listener--jboss-logging--success-level=info
   ```

> **Este es el único archivo compartido del curso que toca este
> laboratorio.** Recrear el contenedor **no** borra nada: la configuración
> de eventos que acabas de guardar vive en la base de datos del volumen
> `keycloak_datos` y sigue ahí al arrancar. Lo que sí se pierde son las
> sesiones abiertas: tendrás que volver a entrar en la Admin Console.

Compruébalo tú mismo tras el reinicio:

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/admin/realms/curso/events/config \
  | jq -c '{eventsEnabled, eventsExpiration, adminEventsEnabled, adminEventsDetailsEnabled, eventsListeners}'
```

```json
{"eventsEnabled":true,"eventsExpiration":2592000,"adminEventsEnabled":true,"adminEventsDetailsEnabled":true,"eventsListeners":["jboss-logging"]}
```

## 6. Cambios en la aplicación

Trabaja sobre `~/keycloak-curso/aplicacion_base`. El resultado completo
está en `aplicacion_base_lab-12/`. Son **un archivo nuevo y un bean**.

### 6.1 `seguridad/AuditoriaDeAcceso.java` (nuevo)

Keycloak registra lo que pasa en Keycloak. Quién vio la tabla de clientes
lo decide la aplicación, y solo la aplicación puede registrarlo (PCI DSS
10.2.1.1). Crea el archivo:

```java
package com.curso.keycloak.seguridad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * LABORATORIO 12: registro de auditoría de la aplicación.
 *
 * Keycloak audita lo que ocurre en Keycloak (quién inició sesión, quién
 * falló la contraseña, qué cambió un administrador). Pero la decisión de
 * "quién vio los datos de clientes" la toma ESTA aplicación, y Keycloak no
 * la ve. PCI DSS 10.2.1.1 pide rastro del acceso a los datos de titulares
 * de tarjeta: eso solo puede registrarlo quien los sirve.
 *
 * Este componente escucha los eventos que Spring Security publica en el
 * ApplicationContext y escribe una línea por cada uno en el logger
 * "auditoria", separado del log técnico para que se pueda enviar a un
 * destino distinto (archivo propio, SIEM):
 *
 *   - AuthenticationSuccessEvent: alguien completó el inicio de sesión OIDC.
 *   - LogoutSuccessEvent:         alguien cerró sesión (POST /logout).
 *   - AuthorizationDeniedEvent:   alguien pidió una ruta para la que no
 *                                 tiene permiso (por ejemplo /privada sin
 *                                 el rol gestor-clientes).
 *
 * CORRELACIÓN. Cada línea lleva el claim "sid" (session id) del ID Token.
 * Es el MISMO identificador de sesión que Keycloak escribe en sus eventos
 * LOGIN y LOGOUT (campo "Session"). Con él, un incidente se sigue de un
 * sistema al otro: el evento LOGIN de Keycloak dice desde qué IP y con qué
 * cliente entró alguien; la línea de esta aplicación dice qué hizo después.
 *
 * QUÉ NO SE REGISTRA, A PROPÓSITO: ni el ID Token, ni el access token, ni
 * ningún secreto. Un token en un log es una credencial en un log
 * (OWASP ASVS V7.1: el registro no debe contener credenciales ni datos
 * sensibles). Se escriben solo identificadores: usuario, "sub", "sid",
 * "azp" y la ruta.
 *
 * Para que Spring publique AuthorizationDeniedEvent hace falta declarar un
 * AuthorizationEventPublisher (ver SeguridadConfig). Los otros dos eventos
 * los publica Spring Security por sí solo.
 *
 * No hay ninguna clase de Keycloak aquí: solo eventos estándar de Spring
 * Security y SLF4J.
 */
@Component
public class AuditoriaDeAcceso {

    /**
     * Logger con nombre propio, "auditoria", en lugar del nombre de la
     * clase. Así se puede enrutar aparte en la configuración de logging
     * sin tocar el código.
     */
    private static final Logger AUDITORIA = LoggerFactory.getLogger("auditoria");

    /**
     * Inicio de sesión completado. El principal es el OidcUser que Spring
     * construyó a partir del ID Token validado (laboratorio 04).
     */
    @EventListener
    public void inicioDeSesion(AuthenticationSuccessEvent evento) {
        if (evento.getAuthentication().getPrincipal() instanceof OidcUser usuario) {
            AUDITORIA.info("LOGIN usuario={} sub={} sid={} azp={}",
                    usuario.getPreferredUsername(),
                    usuario.getSubject(),
                    usuario.getIdToken().getClaimAsString("sid"),
                    usuario.getIdToken().getClaimAsString("azp"));
        }
    }

    /**
     * Cierre de sesión local (antes de redirigir a Keycloak, laboratorio 05).
     */
    @EventListener
    public void cierreDeSesion(LogoutSuccessEvent evento) {
        Authentication autenticacion = evento.getAuthentication();
        AUDITORIA.info("LOGOUT usuario={} sid={}", nombre(autenticacion), sid(autenticacion));
    }

    /**
     * Acceso denegado por las reglas de SeguridadConfig.
     *
     * Dos casos distintos, con distinto nivel:
     *   - Un usuario AUTENTICADO sin el rol necesario: es el 403 real
     *     (laboratorio 06) y se registra como WARN, con su usuario y sid.
     *   - Un visitante ANÓNIMO: Spring también publica el evento, pero acto
     *     seguido lo envía al login. No es un incidente; se registra como
     *     INFO para que la traza esté completa sin generar alarmas.
     */
    @EventListener
    public void accesoDenegado(AuthorizationDeniedEvent<?> evento) {
        Authentication autenticacion = evento.getAuthentication().get();
        String ruta = ruta(evento.getObject());

        if (autenticacion != null && autenticacion.getPrincipal() instanceof OidcUser) {
            AUDITORIA.warn("DENEGADO usuario={} sid={} ruta={}",
                    nombre(autenticacion), sid(autenticacion), ruta);
        } else {
            AUDITORIA.info("SIN_SESION ruta={} (se redirige al inicio de sesión)", ruta);
        }
    }

    // ---- Utilidades: extraen identificadores, nunca tokens ---------------

    /**
     * El "objeto" del evento es lo que se estaba autorizando. Para las
     * reglas de authorizeHttpRequests, Spring Security 6.5 publica la
     * propia petición HTTP (HttpServletRequest); en otras versiones o en
     * autorización de métodos puede ser un RequestAuthorizationContext u
     * otro objeto. Se contemplan los dos primeros y, si no, se escribe la
     * clase, para no perder la traza.
     */
    private static String ruta(Object objeto) {
        if (objeto instanceof HttpServletRequest peticion) {
            return peticion.getRequestURI();
        }
        if (objeto instanceof RequestAuthorizationContext contexto) {
            return contexto.getRequest().getRequestURI();
        }
        return objeto == null ? "-" : objeto.getClass().getSimpleName();
    }

    private static String nombre(Authentication autenticacion) {
        if (autenticacion != null && autenticacion.getPrincipal() instanceof OidcUser usuario) {
            return usuario.getPreferredUsername();
        }
        return "anónimo";
    }

    private static String sid(Authentication autenticacion) {
        if (autenticacion != null && autenticacion.getPrincipal() instanceof OidcUser usuario) {
            return usuario.getIdToken().getClaimAsString("sid");
        }
        return "-";
    }
}
```

### 6.2 `SeguridadConfig.java`: publicar las denegaciones

Spring Security decide "permitido / denegado" en silencio: no publica nada
cuando rechaza una petición. Hay que pedirlo. Añade los imports:

```java
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
```

y este bean dentro de la clase:

```java
    /**
     * LABORATORIO 12: publicador de eventos de autorización.
     *
     * Por defecto Spring Security decide "permitido / denegado" en silencio:
     * no publica ningún evento cuando rechaza una petición. Al declarar este
     * bean, el filtro de autorización (authorizeHttpRequests) publica un
     * AuthorizationDeniedEvent por cada denegación, con el Authentication
     * del solicitante y el contexto de la petición (la ruta). Es lo que
     * AuditoriaDeAcceso convierte en una línea de auditoría.
     *
     * SpringAuthorizationEventPublisher es la implementación estándar; por
     * defecto solo publica las denegaciones (las concesiones serían
     * demasiado ruido: una por petición).
     */
    @Bean
    AuthorizationEventPublisher publicadorDeAutorizacion(ApplicationEventPublisher publicador) {
        return new SpringAuthorizationEventPublisher(publicador);
    }
```

Añade también la referencia al laboratorio 12 en el comentario de cabecera
de la clase. No hay más cambios: ni plantillas, ni `application.yml`, ni
controlador.

### 6.3 Arrancar

```bash
cd ~/keycloak-curso/aplicacion_base
export KEYCLOAK_CLIENT_SECRET='el-secreto-de-tu-cliente'
mvn spring-boot:run
```

## 7. Ejercicio DESPUÉS: los mismos hechos, ahora con rastro

Repite **exactamente** los tres hechos del ANTES (4.1) y añade dos del
laboratorio 11: un `client_credentials` y una introspección de un token
manipulado.

```bash
cd ~/keycloak-curso/laboratorio-11-maquina-a-maquina
TOKEN_URL=http://localhost:8080/realms/curso/protocol/openid-connect/token
TOKEN=$(curl -s -X POST "$TOKEN_URL" -d grant_type=client_credentials \
  -d client_id=servicio-conciliacion -d "client_secret=$SECRETO_CONCILIACION" | jq -r .access_token)
# token manipulado del laboratorio 11, 7.8
curl -s -X POST http://localhost:8080/realms/curso/protocol/openid-connect/token/introspect \
  -u "api-conciliacion:$SECRETO_API" -d "token=$FALSO"
```

### 7.1 User events: la lista

**Events** en el menú → pestaña **User events**. Por API, lo mismo:

```bash
curl -s -H "Authorization: Bearer $TOKEN_ADMIN" 'http://localhost:8080/admin/realms/curso/events' \
  | jq -r '.[] | [(.time/1000|todate), .type, .clientId, .ipAddress, (.error//"-"), (.details.username//"-")] | @tsv'
```

Salida real:

```
2026-09-17T03:51:08Z   LOGIN_ERROR              aplicacion-base         172.18.0.1   invalid_user_credentials   luis
2026-09-17T03:51:04Z   LOGIN_ERROR              aplicacion-base         172.18.0.1   invalid_user_credentials   luis
2026-09-17T03:51:00Z   LOGIN_ERROR              aplicacion-base         172.18.0.1   invalid_user_credentials   luis
2026-09-17T03:50:44Z   LOGOUT                   aplicacion-base         172.18.0.1   -                          -
2026-09-17T03:50:35Z   CODE_TO_TOKEN            aplicacion-base         172.18.0.1   -                          -
2026-09-17T03:50:35Z   LOGIN                    aplicacion-base         172.18.0.1   -                          luis
2026-09-17T03:50:23Z   LOGOUT                   aplicacion-base         172.18.0.1   -                          -
2026-09-17T03:50:17Z   CODE_TO_TOKEN            aplicacion-base         172.18.0.1   -                          -
2026-09-17T03:50:17Z   LOGIN                    aplicacion-base         172.18.0.1   -                          ana
2026-09-17T03:46:56Z   INTROSPECT_TOKEN_ERROR   api-conciliacion        172.18.0.1   invalid_token              -
2026-09-17T03:46:56Z   CLIENT_LOGIN             servicio-conciliacion   172.18.0.1   -                          service-account-servicio-conciliacion
```

**Resultado esperado.** Once eventos donde antes había cero. Están los
seis tipos que buscábamos: el `LOGIN` de `ana`, los tres `LOGIN_ERROR` de
`luis`, los `LOGOUT`, los `CODE_TO_TOKEN`, el `CLIENT_LOGIN` del proceso
nocturno y el `INTROSPECT_TOKEN_ERROR` de la API.

Fíjate en el `CLIENT_LOGIN`: el "usuario" es
`service-account-servicio-conciliacion`, la cuenta de servicio del
laboratorio 11. Máquina a máquina también se audita, y con nombre propio.

### 7.2 Filtros y detalle de un evento

En la consola, **Search user event** abre el formulario de filtros. Por
API son parámetros:

```bash
# por usuario (el id de ana, no su nombre)
curl -s -H "Authorization: Bearer $TOKEN_ADMIN" \
  "http://localhost:8080/admin/realms/curso/events?user=b7182d02-65e7-4a19-a314-c6f1b50436cb" \
  | jq -r '.[] | [(.time/1000|todate), .type, .clientId, .sessionId] | @tsv'
```

```
2026-09-17T03:50:23Z   LOGOUT          aplicacion-base   36tp-YMgznu8Ass5ENb-2ERA
2026-09-17T03:50:17Z   CODE_TO_TOKEN   aplicacion-base   36tp-YMgznu8Ass5ENb-2ERA
2026-09-17T03:50:17Z   LOGIN           aplicacion-base   36tp-YMgznu8Ass5ENb-2ERA
```

Los tres eventos de la sesión de `ana`, con el mismo `sessionId`. Ese valor
es la clave de 7.5.

Ahora el **detalle** del `LOGIN`, que en la consola se abre pulsando la
fila:

```json
{
  "id": "1b15068b-7dbc-495c-8ae3-8e73e4695ae5",
  "time": 1789617017313,
  "type": "LOGIN",
  "realmId": "daf9793f-a6ee-4807-ad14-8db066001c77",
  "clientId": "aplicacion-base",
  "userId": "b7182d02-65e7-4a19-a314-c6f1b50436cb",
  "sessionId": "36tp-YMgznu8Ass5ENb-2ERA",
  "ipAddress": "172.18.0.1",
  "details": {
    "auth_method": "openid-connect",
    "auth_type": "code",
    "response_type": "code",
    "redirect_uri": "http://localhost:8081/login/oauth2/code/keycloak",
    "consent": "no_consent_required",
    "code_id": "36tp-YMgznu8Ass5ENb-2ERA",
    "username": "ana",
    "response_mode": "query"
  }
}
```

Y el de un `LOGIN_ERROR`, con el motivo:

```json
{
  "type": "LOGIN_ERROR",
  "clientId": "aplicacion-base",
  "userId": "03e6b8df-bf8b-452e-a9c3-4d5bd6711321",
  "ipAddress": "172.18.0.1",
  "error": "invalid_user_credentials",
  "details": {
    "auth_method": "openid-connect",
    "auth_type": "code",
    "redirect_uri": "http://localhost:8081/login/oauth2/code/keycloak",
    "code_id": "bM47kvkacNNbhs337onpZG_H",
    "username": "luis"
  }
}
```

**Resultado esperado.** El evento dice **quién** (`userId` y
`details.username`), **desde dónde** (`ipAddress`), **con qué cliente**,
**cuándo** y, en el error, **por qué**: `invalid_user_credentials`. Es
PCI DSS 10.2.1.4 cubierto con datos, no con intenciones.

Un detalle importante: el `LOGIN_ERROR` **no tiene `sessionId`**. No llegó
a crearse sesión, porque no hubo autenticación. Solo los eventos de una
sesión real lo llevan.

### 7.3 El log del contenedor, ahora con los aciertos

```bash
cd ~/keycloak-curso/keycloak && docker compose logs --since 10m | grep org.keycloak.events
```

Salida real (recortada al realm `curso`, y el `realmId` eliminado para que
quepa):

```
2026-09-17 03:50:17,316 INFO  [org.keycloak.events] (executor-thread-1) type="LOGIN", realmName="curso", clientId="aplicacion-base", userId="b7182d02-65e7-4a19-a314-c6f1b50436cb", sessionId="36tp-YMgznu8Ass5ENb-2ERA", ipAddress="172.18.0.1", auth_method="openid-connect", auth_type="code", response_type="code", redirect_uri="http://localhost:8081/login/oauth2/code/keycloak", consent="no_consent_required", code_id="36tp-YMgznu8Ass5ENb-2ERA", username="ana", response_mode="query"
2026-09-17 03:50:17,397 INFO  [org.keycloak.events] (executor-thread-1) type="CODE_TO_TOKEN", realmName="curso", clientId="aplicacion-base", userId="b7182d02-...", sessionId="36tp-YMgznu8Ass5ENb-2ERA", grant_type="authorization_code", scope="openid profile", client_auth_method="client-secret"
2026-09-17 03:50:17,541 INFO  [org.keycloak.events] (executor-thread-1) type="USER_INFO_REQUEST", realmName="curso", clientId="aplicacion-base", userId="b7182d02-...", sessionId="36tp-YMgznu8Ass5ENb-2ERA", auth_method="validate_access_token", username="ana"
2026-09-17 03:50:23,541 INFO  [org.keycloak.events] (executor-thread-1) type="LOGOUT", realmName="curso", clientId="aplicacion-base", userId="b7182d02-...", sessionId="36tp-YMgznu8Ass5ENb-2ERA", redirect_uri="http://localhost:8081/"
2026-09-17 03:51:00,864 WARN  [org.keycloak.events] (executor-thread-2) type="LOGIN_ERROR", realmName="curso", clientId="aplicacion-base", userId="03e6b8df-...", ipAddress="172.18.0.1", error="invalid_user_credentials", username="luis"
```

**Resultado esperado.** Tres cosas que mirar:

1. El `LOGIN` de `ana` **aparece**, y a nivel `INFO`. En el ANTES no
   estaba: es el efecto de `success-level=info` (5.4).
2. Los errores siguen a `WARN`. Los dos niveles conviven, que es justo lo
   que permite alertar solo sobre los segundos.
3. Hay una línea `USER_INFO_REQUEST` que **no aparece en la pantalla
   Events** de 7.1. Es la demostración de 2.4: el listener recibe todos los
   eventos, mientras que el almacén guarda solo los tipos seleccionados.

### 7.4 Admin events: quién cambió qué

**Events** → pestaña **Admin events**:

```bash
curl -s -H "Authorization: Bearer $TOKEN_ADMIN" 'http://localhost:8080/admin/realms/curso/admin-events' \
  | jq -r '.[] | [(.time/1000|todate), .operationType, .resourceType, .resourcePath, .authDetails.ipAddress] | @tsv'
```

Salida real:

```
2026-09-17T03:52:55Z   UPDATE   REALM_ROLE   roles-by-id/1148a8a9-b694-4586-a936-26b22580e4ad         172.18.0.1
2026-09-17T03:52:06Z   ACTION   CLIENT       clients/6cd2613f-e729-4884-990e-ce5e24fe502a/client-secret   172.18.0.1
2026-09-17T03:39:46Z   UPDATE   REALM        events/config                                            172.18.0.1
```

Los tres son reconocibles: la regeneración del secreto (`ACTION` sobre
`client-secret`), el cambio de descripción del rol, y —el más divertido—
**la propia activación de la auditoría**, que quedó registrada como el
primer evento de administración del realm.

**La regeneración del secreto**, con su representación:

```json
{
  "time": 1789617126544,
  "authDetails": {
    "realmId": "64547a38-ea8b-48ae-a6e5-a18af61a79b8",
    "clientId": "95e73526-5178-40a7-bb05-40b8d153b389",
    "userId": "f6c696a2-c809-4460-a85b-af0848874ff6",
    "ipAddress": "172.18.0.1"
  },
  "operationType": "ACTION",
  "resourceType": "CLIENT",
  "resourcePath": "clients/6cd2613f-e729-4884-990e-ce5e24fe502a/client-secret",
  "representation": "{\"type\":\"secret\",\"value\":\"**********\"}"
}
```

Dos lecturas:

- **Quién.** `authDetails.userId` es el administrador y su `realmId` es el
  del realm `master`; `clientId` es el cliente de la Admin Console. Se
  resuelven a nombres con una consulta más:

  ```bash
  curl -s -H "Authorization: Bearer $TOKEN_ADMIN" \
    http://localhost:8080/admin/realms/master/users/f6c696a2-c809-4460-a85b-af0848874ff6 | jq -c '{username}'
  ```

  ```json
  {"username":"admin"}
  ```

  Y el cliente resulta ser `security-admin-console`. Es decir: el usuario
  `admin`, desde la consola web, desde `172.18.0.1`. PCI DSS 10.2.1.2.

- **El secreto no viaja al registro.** La representación trae
  `"value":"**********"`. Keycloak enmascara ese campo. Es la excepción
  tranquilizadora del aviso de 2.6, no la regla: comprueba siempre qué
  acaba en tus registros antes de dar por hecho que están limpios.

**El cambio del rol**, donde la representación sí es el objeto entero:

```json
{
  "operationType": "UPDATE",
  "resourceType": "REALM_ROLE",
  "resourcePath": "roles-by-id/1148a8a9-b694-4586-a936-26b22580e4ad",
  "representation": {
    "id": "1148a8a9-b694-4586-a936-26b22580e4ad",
    "name": "lector-conciliacion",
    "description": "Puede leer los datos de conciliación (proceso nocturno, sin usuario). Revisado en el laboratorio 12",
    "composite": false,
    "clientRole": false,
    "containerId": "daf9793f-a6ee-4807-ad14-8db066001c77",
    "attributes": {}
  }
}
```

**Resultado esperado.** Se ve el estado **nuevo** del rol, con la
descripción cambiada. El estado **anterior** no está: Keycloak guarda lo
que se envió, no un *diff* (2.6). Para reconstruirlo, compara con el
`curso-realm.json` del laboratorio 11, donde ese rol aún se describía sin
el añadido "Revisado en el laboratorio 12".

Los filtros también existen aquí. Por ejemplo, solo las modificaciones de
roles de realm:

```bash
curl -s -H "Authorization: Bearer $TOKEN_ADMIN" \
  'http://localhost:8080/admin/realms/curso/admin-events?resourceTypes=REALM_ROLE&operationTypes=UPDATE' \
  | jq -c '.[] | {time: (.time/1000|todate), resourcePath}'
```

```json
{"time":"2026-09-17T03:52:55Z","resourcePath":"roles-by-id/1148a8a9-b694-4586-a936-26b22580e4ad"}
```

### 7.5 El registro de la aplicación y la correlación

En la terminal donde corre la aplicación, las líneas del logger
`auditoria`. Salida real de la sesión de `ana`:

```
2026-09-16T23:50:17.547-04:00  INFO 7510 --- [aplicacion-base] [nio-8081-exec-7] auditoria : LOGIN usuario=ana sub=b7182d02-65e7-4a19-a314-c6f1b50436cb sid=36tp-YMgznu8Ass5ENb-2ERA azp=aplicacion-base
2026-09-16T23:50:23.480-04:00  INFO 7510 --- [aplicacion-base] [nio-8081-exec-2] auditoria : LOGOUT usuario=ana sid=36tp-YMgznu8Ass5ENb-2ERA
```

Y la de `luis`, con la denegación:

```
2026-09-17T00:00:23.651-04:00  INFO 7983 --- [aplicacion-base] [nio-8081-exec-3] auditoria : SIN_SESION ruta=/privada (se redirige al inicio de sesión)
2026-09-17T00:00:23.915-04:00  INFO 7983 --- [aplicacion-base] [nio-8081-exec-5] auditoria : LOGIN usuario=luis sub=03e6b8df-bf8b-452e-a9c3-4d5bd6711321 sid=089Nm3E2EwVykzErr5EbLukb azp=aplicacion-base
2026-09-17T00:00:23.928-04:00  WARN 7983 --- [aplicacion-base] [nio-8081-exec-5] auditoria : DENEGADO usuario=luis sid=089Nm3E2EwVykzErr5EbLukb ruta=/privada
2026-09-17T00:00:23.952-04:00  INFO 7983 --- [aplicacion-base] [nio-8081-exec-1] auditoria : LOGOUT usuario=luis sid=089Nm3E2EwVykzErr5EbLukb
```

Se lee la historia completa: alguien sin sesión pidió `/privada` y fue al
login; volvió como `luis`; pidió `/privada` y se le **denegó** (WARN);
cerró sesión. Ese `DENEGADO` es el hecho que Keycloak **no puede**
registrar, porque la decisión es de la aplicación.

**La correlación.** El `sid` de `ana` en la aplicación es
`36tp-YMgznu8Ass5ENb-2ERA`; el `sessionId` de su evento `LOGIN` en
Keycloak (7.2) es `36tp-YMgznu8Ass5ENb-2ERA`. El mismo valor. Igual con
`luis` y `089Nm3E2EwVykzErr5EbLukb`, que es el `sessionId` de su `LOGIN`:

```bash
curl -s -H "Authorization: Bearer $TOKEN_ADMIN" \
  "http://localhost:8080/admin/realms/curso/events?user=03e6b8df-bf8b-452e-a9c3-4d5bd6711321&max=2" \
  | jq -r '.[] | [(.time/1000|todate), .type, .sessionId] | @tsv'
```

```
2026-09-17T04:00:23Z   CODE_TO_TOKEN   089Nm3E2EwVykzErr5EbLukb
2026-09-17T04:00:23Z   LOGIN           089Nm3E2EwVykzErr5EbLukb
```

Con ese identificador, una búsqueda en los dos registros reconstruye el
incidente entero: Keycloak aporta la IP, el cliente y el método de
autenticación; la aplicación aporta qué se pidió y si se concedió.

### 7.6 Sacar los eventos por API: hacia el SIEM

La pantalla Events sirve para investigar a mano. Para vigilar de forma
continua hace falta llevarse los eventos a un sistema de gestión de
registros. La API de administración es la vía directa.

Primero, un token de administrador. Se obtiene con el cliente `admin-cli`
del realm `master`, usando el usuario `admin`:

```bash
TOKEN_ADMIN=$(curl -s -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -d client_id=admin-cli -d username=admin -d password=admin -d grant_type=password | jq -r .access_token)
```

Salida real de la respuesta completa (el token, truncado):

```json
{
  "token_type": "Bearer",
  "expires_in": 60,
  "scope": "profile email",
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5c…"
}
```

> **Este token solo vive en tu terminal.** Es una variable de entorno de la
> sesión de shell: no se escribe en ningún archivo, no se sube al
> repositorio y caduca en 60 segundos (el *Access Token Lifespan* del
> laboratorio 05). En un script largo hay que volver a pedirlo. Y nunca se
> pega en un README, un ticket o un chat.
>
> **En producción esto no se hace así.** Usar el `admin` del realm
> `master` con su contraseña, y con el grant *password* que el curso
> descartó desde el laboratorio 01, es aceptable en un laboratorio y
> **solo** en un laboratorio: esa cuenta puede hacer cualquier cosa en
> cualquier realm. Lo correcto es exactamente lo del laboratorio 11: un
> cliente confidencial con **service account**, autenticado por
> `client_credentials`, al que se le asigna únicamente el rol
> **`view-events`** del cliente `realm-management` del realm `curso`. Ese
> rol existe ya:
>
> ```bash
> curl -s -H "Authorization: Bearer $TOKEN_ADMIN" \
>   "http://localhost:8080/admin/realms/curso/clients/$ID_REALM_MANAGEMENT/roles/view-events" | jq -c '{name}'
> ```
>
> ```json
> {"name":"view-events"}
> ```
>
> Con `view-events` el recolector puede **leer** los eventos y nada más:
> no puede crear usuarios, ni cambiar clientes, ni borrar el propio
> registro que está recogiendo. Es mínimo privilegio aplicado a la
> auditoría, y evita la paradoja de que quien recoge los registros tenga
> permiso para alterarlos (PCI DSS 10.3).

Los intentos fallidos, que es lo que un SIEM querría correlacionar por IP:

```bash
curl -s -H "Authorization: Bearer $TOKEN_ADMIN" \
  'http://localhost:8080/admin/realms/curso/events?type=LOGIN_ERROR&max=3' \
  | jq '.[] | {time: (.time/1000|todate), type, clientId, ipAddress, error, username: .details.username}'
```

Salida real:

```json
{
  "time": "2026-09-17T03:51:08Z",
  "type": "LOGIN_ERROR",
  "clientId": "aplicacion-base",
  "ipAddress": "172.18.0.1",
  "error": "invalid_user_credentials",
  "username": "luis"
}
{
  "time": "2026-09-17T03:51:04Z",
  "type": "LOGIN_ERROR",
  "clientId": "aplicacion-base",
  "ipAddress": "172.18.0.1",
  "error": "invalid_user_credentials",
  "username": "luis"
}
{
  "time": "2026-09-17T03:51:00Z",
  "type": "LOGIN_ERROR",
  "clientId": "aplicacion-base",
  "ipAddress": "172.18.0.1",
  "error": "invalid_user_credentials",
  "username": "luis"
}
```

Y los de administración:

```bash
curl -s -H "Authorization: Bearer $TOKEN_ADMIN" \
  'http://localhost:8080/admin/realms/curso/admin-events?max=3' \
  | jq '.[] | {time: (.time/1000|todate), operationType, resourceType, resourcePath, admin: .authDetails.userId, ip: .authDetails.ipAddress}'
```

```json
{
  "time": "2026-09-17T03:52:55Z",
  "operationType": "UPDATE",
  "resourceType": "REALM_ROLE",
  "resourcePath": "roles-by-id/1148a8a9-b694-4586-a936-26b22580e4ad",
  "admin": "f6c696a2-c809-4460-a85b-af0848874ff6",
  "ip": "172.18.0.1"
}
{
  "time": "2026-09-17T03:52:06Z",
  "operationType": "ACTION",
  "resourceType": "CLIENT",
  "resourcePath": "clients/6cd2613f-e729-4884-990e-ce5e24fe502a/client-secret",
  "admin": "f6c696a2-c809-4460-a85b-af0848874ff6",
  "ip": "172.18.0.1"
}
{
  "time": "2026-09-17T03:39:46Z",
  "operationType": "UPDATE",
  "resourceType": "REALM",
  "resourcePath": "events/config",
  "admin": "f6c696a2-c809-4460-a85b-af0848874ff6",
  "ip": "172.18.0.1"
}
```

Tres `LOGIN_ERROR` del mismo usuario y la misma IP en ocho segundos es,
para un SIEM, una regla de correlación de una línea. Ese es el salto: de
"no lo sabemos" a "tenemos una alerta".

### 7.7 La pregunta del auditor, respondida

> **¿Quién accedió a los datos de clientes el martes, y quién cambió esa
> credencial de servicio?**

| Pregunta | Respuesta | De dónde sale |
|---|---|---|
| ¿Quién entró? | `ana`, a las 03:50:17 UTC, desde `172.18.0.1`, con el cliente `aplicacion-base`, por OpenID Connect con flujo *code* | Evento `LOGIN`, 7.2 |
| ¿Vio los datos de clientes? | Sí. Su sesión `36tp-YMgznu8Ass5ENb-2ERA` no registró ninguna denegación | Log `auditoria` correlacionado por `sid`, 7.5 |
| ¿Alguien más lo intentó? | Sí. `luis` entró a las 03:50:35 y se le **denegó** `/privada` | Evento `LOGIN` + línea `DENEGADO`, 7.5 |
| ¿Hubo tanteos de contraseña? | Sí. Tres `LOGIN_ERROR` de `luis` desde `172.18.0.1` entre 03:51:00 y 03:51:08, motivo `invalid_user_credentials` | 7.2 y 7.6 |
| ¿Quién cambió la credencial? | El usuario `admin` del realm `master`, desde `security-admin-console` y desde `172.18.0.1`, a las 03:52:06, con una `ACTION` sobre `clients/6cd2613f…/client-secret` | Evento de administración, 7.4 |
| ¿Y el proceso automático? | `servicio-conciliacion` pidió un token a las 03:46:56 con su cuenta de servicio; alguien presentó a la API un token inválido un instante después | `CLIENT_LOGIN` e `INTROSPECT_TOKEN_ERROR`, 7.1 |

Lo que en el ANTES eran cuatro "no lo sabemos", ahora son seis respuestas
con hora, usuario, IP y cliente.

## 8. Lista de verificación

- [ ] **Realm settings → Events → User events settings**: *Save events* On y *Expiration* 30 días.
- [ ] En los tipos guardados está `INTROSPECT_TOKEN_ERROR`, añadido a mano, además de `LOGIN`, `LOGIN_ERROR`, `LOGOUT`, `CODE_TO_TOKEN` y `CLIENT_LOGIN`.
- [ ] **Admin events settings**: *Save events* On e *Include representation* On.
- [ ] **Event listeners** contiene `jboss-logging` y **no** contiene `email`.
- [ ] `keycloak/docker-compose.yml` lleva `--spi-events-listener--jboss-logging--success-level=info` y el contenedor se recreó con `docker compose up -d`.
- [ ] `docker inspect keycloak --format '{{join .Args " "}}'` muestra esa opción.
- [ ] Tras el reinicio, `events/config` sigue con la auditoría activada (la configuración vive en el volumen).
- [ ] **Events → User events** muestra el `LOGIN` de `ana`, tres `LOGIN_ERROR` de `luis`, los `LOGOUT`, los `CODE_TO_TOKEN`, el `CLIENT_LOGIN` y el `INTROSPECT_TOKEN_ERROR`.
- [ ] Los filtros por usuario y por tipo funcionan, y el detalle de un `LOGIN_ERROR` muestra `error: invalid_user_credentials`.
- [ ] **Events → Admin events** muestra la regeneración del secreto y el cambio de descripción del rol, ambos con administrador e IP.
- [ ] La representación del secreto aparece enmascarada (`"value":"**********"`).
- [ ] `docker compose logs | grep org.keycloak.events` muestra ahora `INFO` para los `LOGIN` y `WARN` para los `LOGIN_ERROR`.
- [ ] En el log del contenedor hay tipos (`USER_INFO_REQUEST`) que no están en la pantalla Events.
- [ ] El log de la aplicación tiene líneas `auditoria` con `LOGIN`, `LOGOUT` y `DENEGADO ... ruta=/privada`.
- [ ] El `sid` de la aplicación coincide con el `sessionId` del evento `LOGIN` de Keycloak.
- [ ] Ninguna línea del log de la aplicación contiene un token ni un secreto.
- [ ] `aplicacion_base` solo ha cambiado en `AuditoriaDeAcceso.java` y el bean de `SeguridadConfig`.

## 9. Punto de control

`keycloak/curso-realm.json` parte del punto de control del laboratorio 11
y añade el bloque de auditoría:

```json
  "eventsEnabled": true,
  "eventsExpiration": 2592000,
  "enabledEventTypes": [ … 104 tipos, con INTROSPECT_TOKEN_ERROR … ],
  "adminEventsEnabled": true,
  "adminEventsDetailsEnabled": true,
  "eventsListeners": [ "jboss-logging" ],
```

`eventsExpiration` va en **segundos**: 2 592 000 son los 30 días de 5.1.
También se actualiza la descripción del rol `lector-conciliacion` para que
el JSON refleje el cambio hecho en 7.4.

Lo que el JSON **no** puede llevar es la opción del listener
(`success-level=info`): no es configuración del realm, sino del servidor,
y vive en `keycloak/docker-compose.yml` (5.4). Si restauras el realm en
una instalación nueva, acuérdate de aplicar también el compose de este
laboratorio.

Importación verificada en un contenedor temporal: tras importar, la
configuración llega intacta (`eventsEnabled`, expiración de 2 592 000 s,
104 tipos con `INTROSPECT_TOKEN_ERROR`, eventos de administración con
representación) y un inicio de sesión fallido seguido de uno correcto se
guardan en Events y se ven en el log con sus dos niveles:

```
2026-09-17T03:56:34Z   LOGIN         aplicacion-base   172.17.0.1   -                          luis
2026-09-17T03:56:32Z   LOGIN_ERROR   aplicacion-base   172.17.0.1   invalid_user_credentials   luis
```

```
2026-09-17 03:56:32,754 WARN  [org.keycloak.events] type="LOGIN_ERROR", realmName="curso", clientId="aplicacion-base", ...
2026-09-17 03:56:34,973 INFO  [org.keycloak.events] type="LOGIN", realmName="curso", clientId="aplicacion-base", ...
```

## 10. Problemas frecuentes

**La pantalla Events sigue vacía después de activarla**
Los eventos se guardan a partir del momento de activarla: no hay efecto
retroactivo. Repite los hechos del ejercicio. Si aun así no aparece nada,
comprueba que pulsaste **Save** en *User events settings* y que estás
mirando el realm `curso`, no `master`.

**Un tipo de evento no aparece aunque ocurra**
No está entre los *Saved types* (2.3). Es lo que pasa con
`INTROSPECT_TOKEN_ERROR` si te saltaste 5.2, y lo que pasa siempre con
`USER_INFO_REQUEST` o `REFRESH_TOKEN`. Compruébalo en el log del
contenedor: si ahí sale y en Events no, es exactamente este caso.

**En `docker compose logs` no veo los LOGIN correctos**
Falta la opción del listener, o el contenedor no se recreó. Verifica con
`docker inspect keycloak --format '{{join .Args " "}}'`. Un
`docker compose restart` **no** basta: hay que hacer `docker compose up -d`
para que Docker aplique el nuevo `command`.

**Tras recrear el contenedor he perdido la sesión de la Admin Console**
Es lo esperado. Los datos persisten en el volumen; las sesiones, no. Vuelve
a entrar con `admin` / `admin`.

**Los eventos de administración no muestran qué se cambió**
*Include representation* está en Off (5.3). Recuerda que solo afecta a los
eventos **posteriores**: los ya guardados no la tendrán.

**La representación no me dice cómo estaba antes**
No lo hace ni lo hará: Keycloak guarda el documento JSON **enviado**, que
es el estado nuevo (2.6). Compara con el punto de control del laboratorio
anterior.

**El log de la aplicación no muestra la línea DENEGADO**
Falta el bean `AuthorizationEventPublisher` de 6.2. Sin él, Spring
Security deniega en silencio y `AuditoriaDeAcceso` nunca se entera.

**Veo muchas líneas `SIN_SESION`**
Es normal: cada petición anónima a una ruta protegida publica un evento de
denegación antes de redirigir al login, y el navegador pide varios recursos
por página. Por eso se registran a nivel INFO y las denegaciones reales de
un usuario autenticado a WARN (6.1).

**`curl` a la API de eventos devuelve 401**
El token de administrador caduca a los 60 segundos (laboratorio 05).
Vuelve a pedirlo. En scripts largos, refréscalo en cada llamada.

**La base de datos crece mucho**
Es el aviso de 2.6 y 2.8: representación activada y muchos tipos
guardados. Baja la expiración, reduce los tipos, limita el tamaño de la
representación con `--spi-events-store--jpa--max-field-length` y exporta a
un sistema externo, que es donde debe vivir el historial largo.

## 11. Siguiente laboratorio

`laboratorio-13-endurecimiento`: hasta aquí todo ha corrido en `start-dev`,
por HTTP, con `admin`/`admin` y secretos escritos en archivos de
laboratorio. El siguiente laboratorio repasa lo que hay que cambiar antes
de que esto se parezca a producción: HTTPS, redirect URIs estrictas,
políticas de cliente de OAuth 2.1, rotación de secretos y el administrador
permanente.
