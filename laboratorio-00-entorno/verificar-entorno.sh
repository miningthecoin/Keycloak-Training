#!/usr/bin/env bash
#
# verificar-entorno.sh
# Comprueba que la máquina WSL cumple los requisitos del curso.
# Uso:  ./verificar-entorno.sh
#
# No modifica nada; solo informa. Termina con código 1 si alguna
# comprobación obligatoria falla.

set -u

ok=0
fallos=0

verde()  { printf '  \033[32mOK\033[0m    %s\n' "$1"; ok=$((ok+1)); }
rojo()   { printf '  \033[31mFALLO\033[0m %s\n' "$1"; fallos=$((fallos+1)); }
aviso()  { printf '  \033[33mAVISO\033[0m %s\n' "$1"; }

echo "Verificación del entorno · Curso Keycloak"
echo

# ---------------------------------------------------------------
# 1. Sistema operativo
# ---------------------------------------------------------------
echo "Sistema"
if grep -qi microsoft /proc/version 2>/dev/null; then
    verde "Se ejecuta dentro de WSL"
else
    aviso "No parece WSL (válido si usas Linux nativo)"
fi

if lsb_release -d 2>/dev/null | grep -q "Ubuntu 24.04"; then
    verde "Ubuntu 24.04"
else
    aviso "No es Ubuntu 24.04: $(lsb_release -d 2>/dev/null | cut -f2)"
fi

# ---------------------------------------------------------------
# 2. Ubicación del repositorio (evitar /mnt/c y OneDrive)
# ---------------------------------------------------------------
echo "Ubicación"
ruta="$(pwd)"
if [[ "$ruta" == /mnt/* ]]; then
    rojo "Estás en $ruta. Clona el repositorio en ~/ (sistema de archivos Linux)."
else
    verde "Repositorio en el sistema de archivos Linux ($ruta)"
fi

# ---------------------------------------------------------------
# 3. Docker
# ---------------------------------------------------------------
echo "Docker"
if command -v docker >/dev/null 2>&1; then
    verde "docker instalado ($(docker --version | cut -d, -f1))"
    if docker info >/dev/null 2>&1; then
        verde "El daemon de Docker responde sin sudo"
    else
        rojo "Docker no responde. ¿Reabriste la terminal tras usermod -aG docker? ¿sudo service docker start?"
    fi
    if docker image inspect quay.io/keycloak/keycloak:26.7.3 >/dev/null 2>&1; then
        verde "Imagen quay.io/keycloak/keycloak:26.7.3 descargada"
    else
        aviso "Imagen de Keycloak no descargada aún: docker pull quay.io/keycloak/keycloak:26.7.3"
    fi
else
    rojo "docker no está instalado (sudo apt install -y docker.io docker-compose-v2)"
fi

# ---------------------------------------------------------------
# 4. Java y Maven
# ---------------------------------------------------------------
echo "Java"
if command -v java >/dev/null 2>&1; then
    ver="$(java -version 2>&1 | head -1)"
    if echo "$ver" | grep -q '"21'; then
        verde "JDK 21 ($ver)"
    else
        rojo "Se requiere JDK 21. Encontrado: $ver"
    fi
else
    rojo "java no está instalado (sudo apt install -y openjdk-21-jdk)"
fi

if command -v mvn >/dev/null 2>&1; then
    mver="$(mvn -version 2>/dev/null | head -1)"
    verde "Maven ($mver)"
    if mvn -version 2>/dev/null | grep -q "Java version: 21"; then
        verde "Maven usa Java 21"
    else
        rojo "Maven no usa Java 21. Revisa: sudo update-alternatives --config java"
    fi
else
    rojo "mvn no está instalado (sudo apt install -y maven)"
fi

# ---------------------------------------------------------------
# 5. Utilidades
# ---------------------------------------------------------------
echo "Utilidades"
for h in git curl jq; do
    if command -v "$h" >/dev/null 2>&1; then
        verde "$h"
    else
        rojo "$h no está instalado (sudo apt install -y $h)"
    fi
done

# ---------------------------------------------------------------
# 6. Puertos libres
# ---------------------------------------------------------------
echo "Puertos"
for p in 8080 8081; do
    if ss -ltn 2>/dev/null | grep -q ":$p "; then
        aviso "Puerto $p en uso (bien si Keycloak o la app ya están corriendo)"
    else
        verde "Puerto $p libre"
    fi
done

# ---------------------------------------------------------------
# Resumen
# ---------------------------------------------------------------
echo
echo "Resumen: $ok comprobaciones correctas, $fallos fallos."
if [ "$fallos" -gt 0 ]; then
    echo "Corrige los fallos antes de continuar con el laboratorio 03."
    exit 1
fi
echo "Entorno listo."
