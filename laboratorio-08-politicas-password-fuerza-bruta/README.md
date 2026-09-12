# Laboratorio 08 · Políticas de contraseña y protección contra fuerza bruta

**Tipo:** práctico · **Duración:** 60 min · **Funcionalidad de Keycloak:** password policies y brute force detection

## 1. Objetivo

Que las contraseñas del realm dejen de ser de juguete y que adivinarlas a
base de intentos deje de ser gratis. Al terminar:

- El realm rechaza contraseñas como `123456`, `ana123` o el propio nombre
  de usuario: exige 12 caracteres con mayúscula, minúscula, dígito y
  carácter especial, y no permite reutilizar las tres últimas.
- `ana` está obligada a cambiar su contraseña la próxima vez que entre, y
  pasa a usar `Andina*Segura2026`.
- Tras 5 intentos fallidos, Keycloak bloquea temporalmente la cuenta. La
  contraseña correcta deja de funcionar durante el bloqueo.
- El mensaje de error es el mismo para "contraseña incorrecta" y para
  "cuenta bloqueada", de modo que un atacante no sabe si acertó el usuario.
- **La aplicación no cambia ni una línea.**

### Beneficios de implementarlo

- **El ataque más común deja de ser rentable.** Probar contraseñas es la
  técnica más barata que existe. Con 5 intentos y espera creciente, un
  diccionario de un millón de palabras pasa de minutos a años.
- **Nada de esto se programa.** Es configuración del servidor de
  identidad. Ninguna aplicación del realm tiene que implementar contadores
  de intentos, ni bloqueos, ni reglas de complejidad, ni arriesgarse a
  hacerlo mal.
- **Coherencia en todo el realm.** La política vale igual para la
  aplicación web, para la consola de cuenta y para cualquier aplicación
  que se añada mañana.
- **Un requisito de cumplimiento que se marca con dos pantallas.** PCI DSS
  8.3.6 y 8.3.4 exigen exactamente esto, y queda configurado y auditable
  en la Admin Console.
- **No revela información.** El mensaje uniforme evita que el atacante
  distinga entre usuario inexistente, contraseña mala y cuenta bloqueada.

### Un ejemplo de las amenazas que evita

La Cooperativa Andina publica su portal en internet. Un atacante no
necesita vulnerabilidad ninguna: se descarga una lista de las diez mil
contraseñas más usadas y un script que prueba una por segundo contra el
formulario de login. Sabe que `luis.herrera@ejemplo.test` trabaja allí
porque lo vio en LinkedIn, así que prueba `luis`, `luis123`, `Luis2024`,
`cooperativa`, `andina123`. En el estado actual del curso, el tercer
intento acierta. Nadie se entera: para Keycloak son cinco peticiones
normales, y la que acierta es indistinguible de que Luis llegue a la
oficina y entre. El atacante tiene ahora una sesión válida, y si Luis
tuviera el rol `gestor-clientes`, también los datos de tarjeta.

Segunda escena, más silenciosa. El mismo atacante no ataca a un usuario
con mil contraseñas, sino a mil usuarios con la misma contraseña:
`Verano2026`. Es lo que se llama *password spraying*. Estadísticamente,
en una plantilla de mil personas alguien la usa. Sin límite de intentos,
el ataque completo son mil peticiones, apenas quince minutos, y no
levanta ninguna alarma porque cada cuenta recibe un solo intento fallido.

Tras este laboratorio, la primera escena muere en el quinto intento, con
esperas que crecen a cada bloqueo. Y `Verano2026` ni siquiera puede
existir como contraseña: la política exige carácter especial y doce
posiciones. El atacante necesita cambiar de oficio.

Fuentes oficiales (Server Administration Guide):

- *Password policies* y *Password policy types* (longitud, dígitos,
  mayúsculas, caracteres especiales, *Not username*, *Not email*, *Not
  recently used*, *Expire password*, *Password blacklist*, algoritmos de
  hash):
  <https://www.keycloak.org/docs/latest/server_admin/index.html#_password-policies>
  y <https://www.keycloak.org/docs/latest/server_admin/index.html#password-policy-types>
- *Brute force attacks*, con los tres modos de bloqueo y sus algoritmos:
  <https://www.keycloak.org/docs/latest/server_admin/index.html#password-guess-brute-force-attacks>
  y <https://www.keycloak.org/docs/latest/server_admin/index.html#lockout-temporarily>

## 2. Conceptos

### 2.1 Un realm nace sin política de contraseñas

La guía lo dice con todas las letras: cuando Keycloak crea un realm, no
le asocia ninguna política, y se puede poner una contraseña simple sin
restricción de longitud, seguridad ni complejidad; añade que eso es
inaceptable en producción. Es exactamente el estado en el que `ana` y
`luis` llevan desde el laboratorio 03 con `ana123` y `luis123`.

Las políticas se acumulan: se añaden una a una y **todas** deben
cumplirse. Las que usaremos:

| Política | Valor | Qué exige |
|---|---|---|
| Minimum length | 12 | doce caracteres como mínimo |
| Digits | 1 | al menos un dígito |
| Lowercase characters | 1 | al menos una minúscula |
| Uppercase characters | 1 | al menos una mayúscula |
| Special characters | 1 | al menos un carácter especial |
| Not username | — | la contraseña no puede ser el nombre de usuario |
| Not email | — | ni la dirección de correo |
| Not recently used | 3 | no reutilizar las tres últimas |

Otras dos que la guía describe y no activamos, pero conviene conocer:
**Expire password** (caduca a los N días) y **Password blacklist**, que
compara contra un archivo de contraseñas prohibidas, por ejemplo las cien
mil más filtradas.

Sobre el **hash**: Keycloak nunca guarda contraseñas en claro. Usa Argon2
por defecto y la guía recomienda mantenerlo. No lo tocamos; aparece en la
misma pantalla y el alumno debe saber que está ahí.

### 2.2 La trampa: la política no afecta a los usuarios existentes

Este es el punto que más sorprende. La guía avisa: *la nueva política no
será efectiva para los usuarios existentes*; hay que asignarles la acción
"Update password" o usar "Expire password" para forzar el cambio.

Tiene sentido: Keycloak solo guarda el hash, no puede saber si la
contraseña antigua cumpliría la política nueva. La política se comprueba
cuando **se establece** una contraseña, no cuando se usa.

Por eso en este laboratorio hacemos dos cosas distintas:

- A **`ana`** le asignamos la acción requerida *Update password*. En su
  próximo inicio de sesión, Keycloak la obliga a elegir una contraseña
  que cumpla la política.
- A **`luis`** lo dejamos con `luis123` a propósito. Sigue entrando con
  ella, y eso demuestra sobre el terreno que "poner la política" no es
  lo mismo que "las contraseñas ya son buenas". Además nos sirve de
  víctima para el ejercicio de fuerza bruta.

### 2.3 Detección de fuerza bruta

También viene **desactivada** por defecto. La guía explica que protege
solo los mecanismos susceptibles: contraseña, OTP y códigos de
recuperación. Se activa en *Realm settings → Security defenses → Brute
force detection*, eligiendo uno de tres modos:

| Modo | Qué hace |
|---|---|
| **Lockout temporarily** | Deshabilita la cuenta un tiempo, que crece con cada bloqueo. Es el que usaremos. |
| Lockout permanently | Deshabilita la cuenta hasta que un administrador la reactive. |
| Lockout permanently after temporary lockout | Mixto: bloquea temporalmente N veces y después, para siempre. |

Parámetros del modo temporal y lo que ponemos en el laboratorio:

| Parámetro | Por defecto | En el lab | Por qué |
|---|---|---|---|
| **Max login failures** | 30 | **5** | observable en clase; PCI DSS 8.3.4 exige 10 o menos |
| **Wait increment** | 1 min | 1 min | el bloqueo empieza en 1 minuto y crece |
| **Max wait** | 15 min | **5 min** | para no dejar la clase esperando |
| **Failure reset time** | 12 h | 12 h | cuándo se olvida el contador |
| **Quick login check** | 1000 ms | 1000 ms | ver el aviso de abajo |
| **Minimum quick login wait** | 1 min | 1 min | ídem |

El algoritmo de la guía, con la estrategia *Multiple*: la espera se
incrementa cuando el número de fallos es múltiplo de *Max login failures*.
Con 5 fallos y 1 minuto de incremento, los cuatro primeros fallos no
bloquean, el quinto bloquea 1 minuto, y así sucesivamente hasta el
máximo.

**Aviso importante sobre "Quick login check".** Hay una segunda regla,
independiente del contador: si dos intentos llegan separados por menos de
*Quick login check* (1 segundo), Keycloak bloquea la cuenta durante
*Minimum quick login wait* sin esperar a los 5 fallos. Es una defensa
contra scripts automáticos. Si haces el ejercicio pulsando muy rápido o
con un bucle sin pausa, verás el bloqueo **antes** del quinto intento y
el contador marcará 2. No es un error del laboratorio: es esta regla
actuando. Para ver el umbral de 5, deja más de un segundo entre intentos.

### 2.4 El mensaje de error es deliberadamente vago

La guía lo subraya: cuando una cuenta está bloqueada y el usuario intenta
entrar, Keycloak muestra el mismo *Invalid username or password* que para
una contraseña inválida, **para que el atacante no sepa que la cuenta
está deshabilitada**. Lo comprobaremos: mismo texto para "me equivoqué de
contraseña" y para "acerté pero estoy bloqueado".

### 2.5 El inconveniente que la propia guía reconoce

La documentación incluye una sección titulada *Downside of Keycloak brute
force detection*: el servidor queda expuesto a una denegación de
servicio. Un atacante que conozca nombres de usuario puede fallar a
propósito hasta bloquear las cuentas de toda la plantilla. La guía
recomienda complementar con software de prevención de intrusiones (IPS)
alimentado por los registros de Keycloak, que anota cada fallo con la IP
de origen. Es un buen recordatorio: ningún control es gratis, y elegir el
modo permanente sin pensarlo puede ser peor que el ataque.

### 2.6 Por qué la aplicación no cambia

En todo el laboratorio no se toca `aplicacion_base`. No es una
casualidad, es la consecuencia de la decisión del laboratorio 04: la
aplicación nunca ve contraseñas, no tiene formulario de login y no guarda
credenciales. Endurecer las contraseñas es asunto exclusivo de Keycloak.
Una aplicación con login propio habría necesitado tocar código, pruebas y
despliegue para conseguir lo mismo.

## 3. Relación con OWASP, ASVS y PCI DSS

- **OWASP A07:2021 Fallos de identificación y autenticación.** La
  categoría cita explícitamente "permitir contraseñas por defecto, débiles
  o muy conocidas" y "permitir ataques automatizados como el relleno de
  credenciales o la fuerza bruta". Las dos mitades de este laboratorio.
- **OWASP ASVS V2.1 Seguridad de contraseñas.** Exige una longitud mínima
  de 12 caracteres (V2.1.1) y comprobar las contraseñas nuevas contra
  listas de filtradas (V2.1.7). Conviene una precisión honesta: ASVS
  **desaconseja** las reglas de composición obligatoria, porque empujan a
  patrones predecibles del tipo `Password1!`, y prefiere longitud más
  lista negra. Usamos composición porque PCI DSS la exige y porque es lo
  que el alumno verá en la mayoría de las organizaciones, pero la lista
  negra de 2.1 es la opción técnicamente superior.
- **OWASP ASVS V2.2.1.** Controles anti-automatización: limitar los
  intentos fallidos. Es la detección de fuerza bruta.
- **PCI DSS 8.3.6.** Longitud mínima de 12 caracteres, con números y
  letras. Nuestra política lo cumple y añade mayúscula y especial.
- **PCI DSS 8.3.4.** Bloquear el identificador tras **no más de 10**
  intentos fallidos, y mantenerlo bloqueado al menos 30 minutos o hasta
  que un administrador lo restablezca. Nuestros 5 intentos cumplen el
  límite; los 5 minutos de espera máxima son un valor de clase, por
  debajo de lo que exige la norma. En un entorno real habría que subirlo
  a 30 minutos o usar el modo de bloqueo permanente.

## 4. Ejercicio ANTES: contraseñas de juguete e intentos ilimitados

**Propósito.** Comprobar que el realm acepta cualquier contraseña y que
fallar mil veces no tiene consecuencias.

### 4.1 Keycloak acepta `123456`

1. Abre la **consola de cuenta** del realm en una ventana de incógnito:
   <http://localhost:8080/realms/curso/account>
2. Entra como `ana` / `ana123`.
3. Ve a **Account security → Signing in → Password → Update**.
4. Contraseña actual `ana123`, nueva contraseña `123456` dos veces.
   **Submit**.

**Resultado esperado.** Keycloak la acepta sin una sola objeción. La
cuenta que da acceso a datos de titulares de tarjeta está protegida por
la contraseña más usada del mundo.

Vuelve a dejarla en `ana123` con el mismo procedimiento, para partir de
un estado conocido.

### 4.2 Los intentos fallidos no cuestan nada

1. Ventana de incógnito → <http://localhost:8081> → **Clientes**.
2. En la pantalla de Keycloak, entra como `luis` con una contraseña
   inventada. Repítelo **diez veces**, equivocándote a propósito.
3. Al undécimo intento, escribe la contraseña correcta `luis123`.

**Resultado esperado.**
- Los diez intentos fallan con *Invalid username or password*.
- El undécimo **entra sin ningún problema**. No hubo bloqueo, ni espera,
  ni aviso.
- En **Realm settings → Security defenses → Brute force detection** verás
  que la protección está desactivada.

Un script haría esos diez intentos en dos segundos, y cien mil en una
tarde. Cierra la sesión de `luis`.

## 5. Configuración de Keycloak, paso a paso

Con la Admin Console en el realm **`curso`**:

### 5.1 Política de contraseñas

1. Menú **Authentication** → pestaña **Policies** → subpestaña **Password
   policy**.
2. En el desplegable **Add policy**, añade una a una estas ocho, fijando
   el valor cuando lo pida:

   | Política | Valor |
   |---|---|
   | Minimum Length | `12` |
   | Digits | `1` |
   | Lowercase Characters | `1` |
   | Uppercase Characters | `1` |
   | Special Characters | `1` |
   | Not Username | — |
   | Not Email | — |
   | Not Recently Used | `3` |

3. **Save**.

La pantalla mostrará la política resultante. En el archivo del realm se
guarda como una sola cadena:

```
length(12) and digits(1) and lowerCase(1) and upperCase(1) and specialChars(1)
  and notUsername(undefined) and notEmail(undefined) and passwordHistory(3)
```

### 5.2 Obligar a `ana` a cambiar la contraseña

Recuerda 2.2: la política no toca a quien ya existe.

1. **Users** → `ana` → pestaña **Details**.
2. En **Required user actions**, añade **Update Password**.
3. **Save**.

No toques a `luis`: su `luis123` debe seguir funcionando.

### 5.3 Detección de fuerza bruta

1. **Realm settings** → pestaña **Security defenses** → subpestaña
   **Brute force detection**.
2. *Brute force mode*: **Lockout temporarily**.
3. Ajusta:
   - *Max login failures*: `5`
   - *Wait increment*: `1` **Minutes**
   - *Max wait*: `5` **Minutes**
   - Deja el resto con sus valores por defecto (*Failure reset time* 12
     horas, *Quick login check* 1000 ms, *Minimum quick login wait* 1
     minuto).
4. **Save**.

## 6. Cambios en la aplicación

**Ninguno.** Esta sección existe para dejarlo por escrito: el laboratorio
08 no toca `aplicacion_base`. La carpeta `aplicacion_base_lab-08/` es
idéntica a `aplicacion_base_lab-07/` y se incluye solo para mantener la
estructura del curso.

Si vienes del laboratorio 07 con tu aplicación funcionando, no la pares
siquiera. Los cambios de este laboratorio son del servidor de identidad y
tienen efecto inmediato sobre ella.

Es la demostración práctica de 2.6: delegar la autenticación significa
que endurecerla no cuesta un despliegue.

## 7. Ejercicio DESPUÉS: contraseñas fuertes y ataques que se apagan solos

### 7.1 `ana` está obligada a cambiar su contraseña

1. Ventana de incógnito → <http://localhost:8081> → **Clientes**.
2. Entra como `ana` / `ana123`.

**Resultado esperado.** En lugar de volver a la aplicación, Keycloak
muestra la pantalla **Update password**. La sesión no continúa hasta que
la cambie.

3. Prueba a poner `ana123` otra vez. Y `ana`. Y `Andina2026` sin
   carácter especial.

**Resultado esperado.** Cada intento es rechazado con un mensaje
específico: contraseña demasiado corta, debe contener al menos un
carácter especial, no puede ser igual al nombre de usuario, etcétera.

4. Escribe **`Andina*Segura2026`** dos veces y confirma.

**Resultado esperado.** Keycloak la acepta, y `ana` llega a `/privada`
con su cabecera `Sesión: ana [gestor-clientes]`, igual que en el
laboratorio 07. Su contraseña para el resto del curso es
`Andina*Segura2026`.

### 7.2 `luis` sigue con su contraseña débil

Entra como `luis` / `luis123`. Funciona.

No es un fallo: es 2.2 en acción. Keycloak no puede evaluar una
contraseña que solo conoce en forma de hash. Solo cuando `luis` la cambie
tendrá que cumplir la política nueva. Compruébalo al revés: en **Users →
luis → Credentials → Reset password**, intenta ponerle `hola` y verás el
rechazo.

### 7.3 Fuerza bruta contra `luis`

1. Ventana de incógnito → <http://localhost:8081> → **Clientes**.
2. Intenta entrar como `luis` con contraseñas inventadas, **esperando un
   par de segundos entre intentos** (ver el aviso de 2.3).
3. Al **quinto** intento fallido, prueba con la contraseña correcta
   `luis123`.

**Resultado esperado.**
- El quinto intento bloquea la cuenta.
- La contraseña correcta **también falla**, con el mismo mensaje.
- En **Users → luis**, la Admin Console marca al usuario como bloqueado
  temporalmente.
- Pasado un minuto, o pulsando **Unlock user** en la Admin Console,
  `luis123` vuelve a funcionar.

### 7.4 El mensaje no delata nada

Compara con atención el texto de dos situaciones:

- `luis` con una contraseña incorrecta, sin estar bloqueado.
- `luis` con la contraseña **correcta**, estando bloqueado.

**Resultado esperado.** Los dos dicen exactamente
`Invalid username or password.` Un atacante no puede distinguir si ha
encontrado la contraseña buena y ha topado con el bloqueo, o si
simplemente falló. Es 2.4 funcionando.

### 7.5 Ver el estado de bloqueo como administrador

En **Users → luis**, mientras esté bloqueado, aparece el aviso
correspondiente y el botón para desbloquearlo. Keycloak registra además
la IP desde la que llegaron los fallos, que es lo que la guía sugiere
alimentar a un IPS (2.5).

## 8. Lista de verificación

- [ ] **Authentication → Policies → Password policy** muestra las ocho políticas de 5.1.
- [ ] `ana` tenía *Update Password* como acción requerida y ya usa `Andina*Segura2026`.
- [ ] Intentar poner `123456`, `ana` o `Andina2026` como contraseña es rechazado con un motivo concreto.
- [ ] `luis` sigue entrando con `luis123` (los existentes no se ven afectados).
- [ ] **Realm settings → Security defenses → Brute force detection** está en *Lockout temporarily* con 5 fallos, incremento de 1 minuto y espera máxima de 5 minutos.
- [ ] Al quinto fallo la cuenta se bloquea y la contraseña correcta deja de funcionar.
- [ ] El mensaje de error es idéntico en los dos casos.
- [ ] **Unlock user** en la Admin Console restaura el acceso inmediatamente.
- [ ] `aplicacion_base` no ha cambiado en todo el laboratorio.

## 9. Punto de control

`keycloak/curso-realm.json` parte del punto de control del laboratorio 07
y añade `passwordPolicy` y los parámetros de fuerza bruta
(`bruteForceProtected`, `failureFactor`, `waitIncrementSeconds`,
`maxFailureWaitSeconds`…).

**Atención a un detalle que cambia respecto a los laboratorios
anteriores.** Hasta ahora las contraseñas venían en claro en el archivo
(`"value": "ana123"`). Eso **ya no funciona** aquí: al importar, Keycloak
valida esas contraseñas contra la política del propio archivo, y como
`luis123` no la cumple, **la importación entera falla** con el error
`invalidPasswordMinSpecialCharsMessage` y el realm no se crea.

Por eso este archivo lleva las credenciales **ya cifradas**, tal como las
exporta Keycloak (`secretData` y `credentialData`, con Argon2). Las
contraseñas siguen siendo `Andina*Segura2026` para `ana` y `luis123` para
`luis`; simplemente viajan como hash, que es también lo correcto desde el
punto de vista de seguridad: el repositorio deja de contener contraseñas
legibles.

Si alguna vez necesitas regenerar este archivo, expórtalo desde un
Keycloak ya configurado en lugar de escribirlo a mano.

## 10. Problemas frecuentes

**La cuenta se bloquea al segundo o tercer intento, no al quinto**
Es la regla *Quick login check* de 2.3: los intentos llegaron separados
por menos de un segundo. Deja un par de segundos entre ellos. En el panel
de bloqueo verás el contador de fallos en 2 en lugar de 5.

**`ana` no ve la pantalla de cambio de contraseña**
La acción requerida *Update Password* no se guardó (5.2) o `ana` tenía ya
una sesión abierta. Cierra sesión y vuelve a entrar.

**No consigo desbloquear a `luis`**
En **Users → luis** busca el botón de desbloqueo. Si prefieres limpiar
todos los bloqueos del realm, en **Realm settings → Security defenses →
Brute force detection** existe la opción de restablecer. También se
desbloquea solo al cumplirse la espera.

**La importación del realm falla con `invalidPassword...Message`**
Estás importando un archivo con contraseñas en claro que no cumplen la
política incluida en ese mismo archivo. Ver la sección 9.

**La política no parece aplicarse a nadie**
Se aplica solo al **establecer** contraseñas nuevas. Las existentes
siguen valiendo hasta que se cambian (2.2). Para forzarlo en masa, la
guía sugiere la política *Expire password*.

**Un usuario se ha quedado bloqueado y no era un ataque**
Es el inconveniente que documenta la guía (2.5). Desbloquéalo a mano y
valora si 5 intentos es un umbral demasiado agresivo para tu caso; el
valor de este laboratorio está elegido para verse en clase.

## 11. Siguiente laboratorio

`laboratorio-09-2fa-totp`: una contraseña fuerte sigue siendo un solo
factor, y si se filtra, la cuenta cae. El siguiente laboratorio añade un
segundo factor con códigos temporales (TOTP) y una aplicación de
autenticación.
