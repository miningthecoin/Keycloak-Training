# Laboratorio 09 · Segundo factor con TOTP

**Tipo:** práctico · **Duración:** 60 min · **Funcionalidad de Keycloak:** OTP Policy, acción requerida *Configure OTP* y sub-flujo *Browser - Conditional 2FA*

## 1. Objetivo

Que la contraseña deje de ser lo único que separa a un atacante de los
datos de tarjeta. Al terminar:

- `ana`, la cuenta con acceso a la sección de clientes, necesita **dos
  factores** para entrar: su contraseña y un código de un solo uso que
  genera una app de autenticación en su teléfono.
- La primera vez que entre, Keycloak la obliga a registrar el
  autenticador escaneando un código QR.
- A partir de ahí, cada inicio de sesión pide la contraseña y un código
  de seis dígitos. Sin el código no hay acceso, aunque la contraseña sea
  correcta.
- `luis` sigue con un solo factor, como contraste.
- **La aplicación no cambia ni una línea.**

### Beneficios de implementarlo

- **Una contraseña filtrada ya no basta.** Phishing, reutilización, una
  fuga de otra web: da igual cómo se pierda la contraseña, sin el segundo
  factor no abre la cuenta.
- **El segundo factor no viaja por la red como un secreto reutilizable.**
  El código lo genera el teléfono a partir de un secreto compartido una
  sola vez, cambia cada 30 segundos y no sirve dos veces.
- **Cero coste para la aplicación.** No hay que integrar ningún SDK de
  SMS, ni gestionar teléfonos, ni almacenar secretos. Lo hace Keycloak con
  el estándar TOTP (RFC 6238), que entienden FreeOTP, Google
  Authenticator y cualquier app compatible.
- **Se aplica donde hace falta.** Se puede exigir solo a las cuentas con
  acceso a datos sensibles, o a todas. En este laboratorio se exige a la
  cuenta que ve tarjetas.
- **Es un requisito de cumplimiento directo.** PCI DSS 8.4 exige factor
  múltiple para el acceso al entorno de datos de tarjeta.

### Un ejemplo de las amenazas que evita

`ana` reutiliza su contraseña del trabajo en una tienda online cualquiera.
Esa tienda sufre una brecha y su base de datos, con correos y contraseñas,
acaba a la venta. Un atacante prueba esas parejas correo/contraseña en
cientos de servicios: es *credential stuffing*, el ataque más rentable que
existe porque no adivina nada, solo reutiliza lo que ya se filtró. Llega
al portal de la Cooperativa Andina, prueba el correo de `ana` con la
contraseña filtrada, y como es la correcta, entra. La política de
contraseñas del laboratorio 08 no lo detuvo: la contraseña era fuerte, el
problema es que estaba en manos ajenas. La detección de fuerza bruta
tampoco: acertó al primer intento.

Con el segundo factor, esa misma contraseña correcta lleva a `ana` a una
pantalla que pide un código de seis dígitos que solo está en su teléfono.
El atacante no lo tiene. El acceso se detiene ahí, y `ana` se entera de
que alguien conoce su contraseña porque le llega, sin haberlo pedido, la
pantalla de segundo factor.

Fuentes oficiales (Server Administration Guide):

- *One-time password (OTP) policies*, con TOTP frente a HOTP y las
  opciones de configuración:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#one-time-password-otp-policies>
- *Authentication flows*, el flujo *Browser* y su sub-flujo condicional de
  segundo factor:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_authentication-flows>
- *Required actions* y *Configure OTP*:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#con-required-actions_server_administration_guide>
- *Two-factor authentication with OTP* (registro desde la consola de
  cuenta):
  <https://www.keycloak.org/docs/latest/server_admin/index.html#two-factor-authentication-with-otp>

## 2. Conceptos

### 2.1 Factores de autenticación

Un factor es una prueba de identidad de una categoría:

| Categoría | Ejemplo |
|---|---|
| Algo que **sabes** | una contraseña |
| Algo que **tienes** | un teléfono con una app de autenticación |
| Algo que **eres** | una huella, la cara |

Hasta el laboratorio 08 la cuenta se protegía con un solo factor, el que
sabes. El problema de ese factor es que se puede copiar sin que te des
cuenta: quien ve tu contraseña la tiene igual que tú. Añadir un segundo
factor de otra categoría significa que robar uno no basta.

### 2.2 TOTP: contraseñas de un solo uso basadas en tiempo

La guía describe dos algoritmos de OTP:

- **TOTP** (*Time-based*): el generador combina un secreto compartido con
  la hora actual y produce un código. El servidor calcula lo mismo y
  compara. El código vale solo una ventana corta de tiempo. Es el que
  usaremos.
- **HOTP** (*Counter-based*): usa un contador en vez de la hora. Es más
  cómodo (no caduca) pero menos seguro (el código sirve un tiempo
  indeterminado) y obliga a escribir en la base de datos en cada login.

La guía es explícita: TOTP es más seguro que HOTP porque el código válido
existe solo durante una ventana breve.

El registro se hace con un **código QR** que Keycloak genera a partir de
la *OTP Policy*. La app del teléfono (FreeOTP, Google Authenticator) lo
escanea, guarda el secreto, y desde entonces genera un código nuevo cada
30 segundos.

### 2.3 La OTP Policy del realm

Está en **Authentication → Policy → OTP Policy**. Sus valores por defecto,
que son los que usaremos y conviene entender:

| Opción | Valor por defecto | Qué significa |
|---|---|---|
| OTP Type | TOTP | basado en tiempo |
| OTP Hash Algorithm | SHA1 | el que entienden todas las apps; SHA256/512 son más fuertes pero menos compatibles |
| Number of Digits | 6 | longitud del código |
| Look Ahead Window | 1 | cuántos intervalos vecinos acepta, por si los relojes no están perfectamente sincronizados |
| OTP Token Period | 30 | segundos que dura cada código |
| Reusable code | Off | un mismo código no se puede usar dos veces |

Dos consecuencias prácticas de esta tabla:

- **El reloj importa.** Si el reloj del teléfono y el del servidor se
  separan más de lo que cubre la ventana, los códigos no coinciden. En
  WSL, comprueba que la hora del contenedor y la de tu máquina están
  sincronizadas si algo falla.
- **Un código no se reutiliza.** Con *Reusable code* desactivado, el
  código que usaste para registrar el autenticador no vale para el primer
  login si lo intentas en los mismos 30 segundos: espera al siguiente.

### 2.4 El flujo Browser ya sabe pedir el segundo factor

Esta es la parte elegante y la razón de que no haya que tocar ningún
flujo. El flujo **Browser** que Keycloak trae de fábrica contiene, después
del formulario de usuario y contraseña, un sub-flujo llamado **Browser -
Conditional 2FA**. La guía explica que es *condicional*: dentro tiene una
comprobación *Condition - User Configured* que hace que el *OTP Form* se
ejecute **solo si el usuario tiene una credencial OTP configurada**.

De ahí sale el comportamiento que buscamos sin programar nada:

- `ana`, que tendrá OTP configurado, verá el formulario de código en cada
  login.
- `luis`, que no lo tiene, no verá ningún segundo paso.

No modificamos el flujo. Si en algún momento quisieras **obligar** a todos
los usuarios a tener 2FA, se haría marcando *Configure OTP* como acción
requerida por defecto del realm (*Authentication → Required actions →
Configure OTP → Set as default action*), de modo que cada usuario nuevo
tenga que registrarlo. En este laboratorio lo exigimos solo a `ana`.

### 2.5 La acción requerida "Configure OTP"

¿Cómo consigue el laboratorio que `ana` registre su autenticador? Con una
**acción requerida**, el mismo mecanismo que en el laboratorio 08 obligó a
`ana` a cambiar la contraseña. Se le asigna *Configure OTP* y, en su
siguiente inicio de sesión, Keycloak interrumpe el flujo y muestra la
pantalla del QR antes de dejarla continuar. Una vez registrado, la acción
desaparece y queda la credencial OTP.

### 2.6 Por qué la aplicación no cambia

Como en el laboratorio 08, no se toca `aplicacion_base`. El segundo factor
ocurre dentro de la pantalla de login de Keycloak, en el flujo Browser. La
aplicación recibe exactamente el mismo ID Token que antes, emitido solo
cuando el usuario ha superado **todos** los pasos del flujo. Que hayan
sido uno o dos factores es invisible para ella.

## 3. Relación con OWASP, ASVS y PCI DSS

- **OWASP A07:2021 Fallos de identificación y autenticación.** La
  categoría cita "ausencia o ineficacia de la autenticación multifactor".
  Una cuenta con acceso a datos sensibles protegida por un único factor es
  el caso de libro.
- **OWASP ASVS V2.8 Autenticadores de un solo uso.** Cubre los
  generadores TOTP: secreto de longitud suficiente, algoritmo estándar,
  ventana de tiempo acotada. Keycloak los cumple con la OTP Policy.
- **PCI DSS 8.4.2.** Exige autenticación multifactor para **todo** acceso
  al entorno de datos del titular de la tarjeta. `ana` accede a
  referencias de tarjeta, así que entra de lleno en el requisito.
- **PCI DSS 8.5.** Fija cómo deben comportarse los sistemas MFA: los
  factores son independientes, y el acceso solo se concede tras presentar
  todos. El flujo Browser lo garantiza: sin el código, no hay token.

## 4. Ejercicio ANTES: un solo factor

**Propósito.** Comprobar que, en el estado del laboratorio 08, la
contraseña es lo único que hace falta.

1. Ventana de incógnito → <http://localhost:8081> → **Clientes**.
2. Entra como `ana` / `Andina*Segura2026`.

**Resultado esperado.** `ana` pasa directamente a `/privada` en cuanto
acierta la contraseña. No hay ningún segundo paso. Quien tenga esa
contraseña, la tenga como la tenga, es `ana` a todos los efectos.

Compruébalo también en la Admin Console: **Users → ana → Credentials**
muestra solo una credencial de tipo *Password*. No hay ningún
autenticador.

Cierra la sesión.

## 5. Configuración de Keycloak, paso a paso

Con la Admin Console en el realm **`curso`**:

### 5.1 Revisar la OTP Policy

1. Menú **Authentication** → pestaña **Policy** → subpestaña **OTP
   Policy**.
2. Confirma los valores por defecto de la tabla de 2.3: *OTP Type* TOTP,
   *SHA1*, 6 dígitos, periodo 30, *Look Ahead Window* 1.
3. No cambies nada. El objetivo es que sepas qué hay ahí.

### 5.2 Confirmar que "Configure OTP" está disponible

1. Menú **Authentication** → pestaña **Required actions**.
2. Verifica que **Configure OTP** está *Enabled*. Es lo normal por
   defecto.

No lo marques como *Set as default action*: eso lo exigiría a todos. En
este laboratorio lo asignamos solo a `ana`.

### 5.3 Exigir el segundo factor a `ana`

1. **Users** → `ana` → pestaña **Details**.
2. En **Required user actions**, añade **Configure OTP**.
3. **Save**.

No toques a `luis`: su cuenta se queda con un solo factor para el
contraste.

## 6. Cambios en la aplicación

**Ninguno.** Igual que en el laboratorio 08, esta sección existe para
dejarlo por escrito: el laboratorio 09 no toca `aplicacion_base`. La
carpeta `aplicacion_base_lab-09/` es idéntica a `aplicacion_base_lab-08/`.

El segundo factor vive por completo en la pantalla de login de Keycloak.
Si tienes la aplicación arrancada del laboratorio anterior, no necesitas
ni reiniciarla.

## 7. Ejercicio DESPUÉS: dos factores

### 7.1 `ana` registra su autenticador

Necesitas una app de autenticación en el teléfono: **FreeOTP** o **Google
Authenticator**, las dos que nombra la guía. Cualquiera de un móvil vale.

1. Ventana de incógnito → <http://localhost:8081> → **Clientes**.
2. Entra como `ana` / `Andina*Segura2026`.

**Resultado esperado.** En lugar de llegar a `/privada`, aparece la
pantalla **Mobile Authenticator Setup** con un código QR.

3. Abre la app del teléfono, elige "escanear código QR" y apunta a la
   pantalla.
4. La app empieza a mostrar un código de seis dígitos que cambia cada 30
   segundos. Escribe el código actual en el campo, ponle un nombre al
   dispositivo si te lo pide, y confirma.

**Resultado esperado.** `ana` entra en `/privada` con su cabecera de
siempre, `Sesión: ana [gestor-clientes]`. En **Users → ana →
Credentials** hay ahora dos credenciales: *Password* y *OTP*.

### 7.2 A partir de ahora, cada login pide el código

1. Cierra la sesión (botón **Cerrar sesión**).
2. Vuelve a **Clientes** y entra como `ana` / `Andina*Segura2026`.

**Resultado esperado.** Tras aceptar la contraseña, Keycloak muestra una
pantalla que pide el **código de un solo uso**. Escribe el que muestre la
app en ese momento y entra.

3. Cierra sesión otra vez, vuelve a entrar y, en la pantalla del código,
   escribe seis dígitos cualquiera, por ejemplo `000000`.

**Resultado esperado.** Keycloak rechaza el acceso con el mensaje
*Invalid authenticator code* y no deja pasar, aunque la contraseña fuera
correcta. Ese es el segundo factor haciendo su trabajo.

> Si escribes un código correcto y aun así lo rechaza, casi siempre es una
> de dos cosas: reutilizaste el mismo código en menos de 30 segundos
> (espera al siguiente, ver 2.3), o el reloj del teléfono está
> desincronizado. Actívale la hora automática al teléfono.

### 7.3 `luis` sigue con un solo factor

1. Cierra sesión. Entra como `luis` / `luis123`.

**Resultado esperado.** `luis` entra con solo la contraseña, sin pantalla
de código, y como no tiene el rol `gestor-clientes`, recibe **Acceso
denegado** (laboratorio 06). El sub-flujo condicional no le pide OTP
porque no tiene credencial OTP configurada (2.4).

### 7.4 Registrar OTP desde la consola de cuenta (alternativa)

La guía describe también el registro voluntario, sin acción requerida,
desde la consola de cuenta. Es como lo haría un usuario que quiere activar
el 2FA por su cuenta:

1. <http://localhost:8080/realms/curso/account> → entra como el usuario.
2. **Account security → Signing in → Set up Authenticator application**.
3. Escanea el QR y confirma con un código.

## 8. Lista de verificación

- [ ] **Authentication → Policy → OTP Policy** está en TOTP, SHA1, 6 dígitos, periodo 30, ventana 1.
- [ ] **Configure OTP** está *Enabled* en Required actions y **no** como acción por defecto.
- [ ] `ana` tenía *Configure OTP* como acción requerida y ya tiene una credencial OTP registrada.
- [ ] Al entrar, `ana` pasa por la pantalla del código además de la contraseña.
- [ ] Un código incorrecto es rechazado con *Invalid authenticator code*.
- [ ] `luis` entra con un solo factor (y recibe *Acceso denegado* por no tener el rol).
- [ ] En **Users → ana → Credentials** aparecen *Password* y *OTP*.
- [ ] `aplicacion_base` no ha cambiado en todo el laboratorio.

## 9. Punto de control

`keycloak/curso-realm.json` parte del punto de control del laboratorio 08
(política de contraseñas, fuerza bruta, credenciales hasheadas) y añade a
`ana` la acción requerida **`CONFIGURE_TOTP`**.

**Una decisión de seguridad deliberada: el archivo NO contiene ningún
secreto TOTP.** El secreto de un autenticador es, precisamente, el segundo
factor. Meterlo en un repositorio, aunque fuera hasheado, equivaldría a
repartir el segundo factor a todo el que clone el curso, que es justo lo
que el 2FA trata de evitar. Por eso el punto de control deja a `ana`
**pendiente de registrar** su autenticador: quien restaure el realm y
entre como `ana` verá el QR y enrolará su propio dispositivo. Es, además,
el flujo real: cada persona registra su propio teléfono.

La OTP Policy no aparece en el archivo porque usamos los valores por
defecto del realm; Keycloak los aplica sin necesidad de declararlos.

## 10. Problemas frecuentes

**`ana` no ve la pantalla del QR**
La acción requerida *Configure OTP* no se guardó (5.3), o `ana` ya tenía
una sesión abierta. Cierra sesión y vuelve a entrar.

**El código correcto es rechazado**
Dos causas habituales, ambas en 2.3: reutilizaste el mismo código dentro
de su ventana de 30 segundos (espera al siguiente) o el reloj del teléfono
está desincronizado. Si pasa siempre, compara la hora del contenedor
(`docker exec keycloak date`) con la de tu equipo.

**Después de varios intentos, ni el código correcto entra**
Se activó la detección de fuerza bruta del laboratorio 08: los fallos de
OTP también cuentan. Espera a que pase el bloqueo o desbloquea a `ana` en
**Users → ana**.

**`luis` no ve la pantalla de código**
Es lo correcto: no tiene credencial OTP, así que el sub-flujo condicional
no se la pide (2.4). Solo verás 2FA en `luis` si le asignas *Configure
OTP*.

**Perdí el teléfono / borré el autenticador**
Como administrador, en **Users → ana → Credentials** puedes borrar la
credencial OTP y volver a asignar la acción requerida *Configure OTP* para
que `ana` registre uno nuevo. Para un usuario final, es el caso de uso de
*Forgot password* / recuperación que la guía describe aparte.

**Al importar el realm, `ana` entra sin pedir OTP**
El punto de control deja a `ana` pendiente de registrar el autenticador
(sección 9), no con uno ya hecho. Hasta que no complete el registro en su
primer login, no hay segundo factor. Es intencionado.

## 11. Siguiente laboratorio

`laboratorio-10-login-federado-github`: hasta ahora todas las identidades
viven en el realm `curso`. El siguiente laboratorio delega el inicio de
sesión en un proveedor externo (GitHub), para que los usuarios entren con
una cuenta que la Cooperativa no tiene que custodiar.
