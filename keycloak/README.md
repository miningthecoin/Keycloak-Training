# Keycloak del curso

Esta carpeta contiene el `docker-compose.yml` con el que se ejecuta
Keycloak durante **todo el curso**. Se instala en el laboratorio 03 y se
mantiene arrancado en los siguientes.

```bash
cd ~/keycloak-curso/keycloak
docker compose up -d
```

- Consola de administración: <http://localhost:8080> (admin / admin)
- Consola de cuenta del realm del curso: <http://localhost:8080/realms/curso/account>

## Carpeta `import/`

Vacía por defecto. Si quieres restaurar el estado de Keycloak de un
laboratorio concreto (por ejemplo porque rompiste la configuración), copia
aquí el archivo `laboratorio-XX/keycloak/curso-realm.json` y arranca de cero:

```bash
docker compose down -v                                   # borra los datos
cp ../laboratorio-05-logout-sesiones/keycloak/curso-realm.json import/
docker compose up -d
```

La opción `--import-realm` solo importa realms que **no existen**; por eso
hace falta `down -v` antes. Referencia:
<https://www.keycloak.org/server/importExport>
