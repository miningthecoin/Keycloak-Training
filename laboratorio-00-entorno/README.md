# Laboratorio 00 · Preparación del entorno

**Tipo:** práctico · **Duración:** 30 min · **Funcionalidad de Keycloak:** ninguna

## 1. Objetivo

Dejar tu máquina virtual WSL con Ubuntu 24.04 lista para todo el curso:

- Docker funcionando (para ejecutar Keycloak).
- JDK 21 y Maven (para ejecutar la aplicación base).
- Herramientas de línea de comandos (`curl`, `jq`, `git`).
- El repositorio clonado **dentro del sistema de archivos de Linux**.
- La aplicación base arrancando en <http://localhost:8081>.

Al terminar ejecutarás `verificar-entorno.sh` y todo debe salir en verde.

## 2. Conceptos

**WSL y los dos sistemas de archivos.** Desde Ubuntu en WSL puedes ver tu
disco de Windows en `/mnt/c/...`. Trabajar ahí es mala idea para este curso:

- Maven y Docker acceden a miles de archivos pequeños; sobre `/mnt/c` es
  entre 5 y 20 veces más lento.
- Si la carpeta está en OneDrive, cada compilación (`target/`) se sincroniza
  a la nube.
- Los permisos POSIX no se conservan; los scripts pueden fallar.

Por eso el repositorio se clona en tu carpeta personal de Linux, `~/`
(equivale a `/home/<tu-usuario>`). Puedes abrirla desde Windows escribiendo
`\\wsl$\Ubuntu-24.04\home\<tu-usuario>` en el Explorador, o con VS Code y
la extensión *WSL*.

**Puertos.** Keycloak escuchará en el **8080** y la aplicación base en el
**8081**. WSL reenvía ambos a Windows automáticamente, así que los abrirás
en tu navegador habitual con `http://localhost:...`.

**Docker en WSL.** Hay dos opciones válidas. Elige una:

| Opción | Cuándo usarla |
|---|---|
| A. Docker Desktop para Windows con integración WSL | Ya lo tienes instalado o tu empresa lo permite. |
| B. Docker Engine instalado dentro de Ubuntu (`docker.io`) | No tienes Docker Desktop. Es la opción más simple y no requiere licencia. |

Este laboratorio detalla la opción B. Si usas la A, salta el paso 4.2 y
verifica que `docker run hello-world` funciona desde Ubuntu.

## 3. Relación con OWASP y PCI DSS

Ninguna directa. Solo una nota de higiene: no ejecutes nada de este curso
como `root` ni con `sudo` salvo donde se indique. Aplicar mínimo privilegio
también a tu entorno de desarrollo es parte de la cultura de desarrollo
seguro (PCI DSS req. 7, principio de necesidad de conocer).

## 4. Pasos

### 4.1 Comprobar la versión de Ubuntu

```bash
lsb_release -a
```

Resultado esperado: `Description: Ubuntu 24.04.x LTS`.

Si tienes varias distribuciones WSL, comprueba desde PowerShell cuál es la
predeterminada con `wsl -l -v` y que aparece como versión 2.

### 4.2 Instalar Docker Engine (opción B)

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-v2
sudo usermod -aG docker $USER
```

Cierra la terminal de Ubuntu y ábrela de nuevo para que el grupo `docker`
se aplique. Luego:

```bash
docker run --rm hello-world
```

Resultado esperado: un mensaje que empieza por `Hello from Docker!`.

Si obtienes `Cannot connect to the Docker daemon`, arranca el servicio:

```bash
sudo service docker start
```

En Ubuntu 24.04 con WSL2 el servicio suele arrancar solo gracias a
`systemd`. Si no es tu caso, comprueba que `/etc/wsl.conf` contiene:

```ini
[boot]
systemd=true
```

y reinicia WSL desde PowerShell con `wsl --shutdown`.

### 4.3 Instalar JDK 21, Maven y utilidades

```bash
sudo apt install -y openjdk-21-jdk maven git curl jq unzip
```

Comprueba:

```bash
java -version    # openjdk version "21..."
mvn -version     # Apache Maven 3.8 o superior, Java version: 21
git --version
jq --version
```

### 4.4 Clonar el repositorio en Linux

```bash
cd ~
git clone <URL-DEL-REPOSITORIO-DEL-CURSO> keycloak-curso
cd keycloak-curso
ls
```

Resultado esperado: ves `aplicacion_base/` y las carpetas
`laboratorio-00-entorno/` a `laboratorio-14-...`.

> Si ya habías clonado el repositorio en `/mnt/c/...`, no lo uses. Clona de
> nuevo en `~/` y trabaja siempre desde ahí.

### 4.5 Descargar la imagen de Keycloak

Keycloak se instalará en el laboratorio 03, pero la descarga de la imagen
tarda unos minutos. Adelántala ahora:

```bash
docker pull quay.io/keycloak/keycloak:26.7.3
```

La imagen y la versión son las que indica la guía oficial
*Getting started – Docker*: <https://www.keycloak.org/getting-started/getting-started-docker>.

### 4.6 Arrancar la aplicación base por primera vez

```bash
cd ~/keycloak-curso/aplicacion_base
mvn spring-boot:run
```

La primera vez Maven descarga dependencias (2–5 min). Cuando veas en el
log una línea similar a:

```
Tomcat started on port 8081 (http)
Started AplicacionBase in x.xxx seconds
```

abre en el navegador de Windows:

- <http://localhost:8081> → portada pública.
- <http://localhost:8081/privada> → tabla de clientes.

Observa la cabecera: `Sesión: anónimo`. Y observa que `/privada` se abre
sin pedir nada. Esa es la situación de partida del curso.

Detén la aplicación con `Ctrl+C`.

### 4.7 Ejecutar el verificador

```bash
cd ~/keycloak-curso/laboratorio-00-entorno
chmod +x verificar-entorno.sh
./verificar-entorno.sh
```

Resultado esperado: todas las comprobaciones en `OK`.

## 5. Lista de verificación

- [ ] `lsb_release -a` muestra Ubuntu 24.04.
- [ ] `docker run --rm hello-world` funciona **sin** `sudo`.
- [ ] `java -version` muestra 21.
- [ ] `mvn -version` muestra Maven 3.8+ usando Java 21.
- [ ] El repositorio está en `~/keycloak-curso`, no en `/mnt/c`.
- [ ] `docker images` lista `quay.io/keycloak/keycloak` con tag `26.7.3`.
- [ ] La aplicación base responde en <http://localhost:8081> y <http://localhost:8081/privada>.
- [ ] `verificar-entorno.sh` termina sin fallos.

## 6. Problemas frecuentes

**`permission denied while trying to connect to the Docker daemon socket`**
No has reabierto la terminal tras `usermod -aG docker`. Ciérrala, ábrela y
repite. Comprueba con `groups` que aparece `docker`.

**`mvn spring-boot:run` falla con `release version 21 not supported`**
Maven está usando otro JDK. Ejecuta `sudo update-alternatives --config java`
y elige la ruta de `java-21-openjdk`. Confirma con `mvn -version`.

**Puerto 8081 ocupado**
Otra aplicación lo usa. Averigua cuál con `sudo ss -ltnp | grep 8081`, o
cambia el puerto en `application.yml` (y recuerda ese cambio en los
laboratorios siguientes).

**Windows no abre `localhost:8081`**
Comprueba que la app arrancó sin error en la terminal de WSL y que estás en
WSL2 (`wsl -l -v` en PowerShell). Con WSL1 el reenvío de puertos no es
automático.

**Maven no descarga dependencias (proxy corporativo)**
Configura el proxy en `~/.m2/settings.xml`. Pide los datos a tu equipo de
red.

## 7. Siguiente laboratorio

`laboratorio-01-fundamentos` (teórico): protocolos OAuth 2.0, OpenID
Connect, SAML y tokens JWT.
