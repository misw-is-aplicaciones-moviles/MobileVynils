#!/usr/bin/env bash
set -euo pipefail

# Requiere el BackVynils del curso en Docker Compose y un dispositivo Android conectado.
# E2E_DB_CONTAINER permite indicar la base si no se usa el puerto 3000 habitual.
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(dirname -- "$script_dir")
db_container=${E2E_DB_CONTAINER:-}

if [[ -z "$db_container" ]]; then
    mapfile -t backends < <(docker ps --filter "publish=${E2E_BACKEND_PORT:-3000}" --format '{{.ID}}')
    if [[ ${#backends[@]} -ne 1 ]]; then
        echo 'No se encontró un único backend. Indica E2E_DB_CONTAINER con su contenedor PostgreSQL.' >&2
        exit 1
    fi
    project=$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "${backends[0]}")
    if [[ -z "$project" || "$project" == '<no value>' ]]; then
        echo 'El backend no pertenece a Docker Compose. Indica E2E_DB_CONTAINER.' >&2
        exit 1
    fi
    mapfile -t databases < <(docker ps \
        --filter "label=com.docker.compose.project=$project" \
        --filter 'label=com.docker.compose.service=db' --format '{{.ID}}')
    if [[ ${#databases[@]} -ne 1 ]]; then
        echo 'No se encontró una única base del backend. Indica E2E_DB_CONTAINER.' >&2
        exit 1
    fi
    db_container=${databases[0]}
fi

echo 'Preparando los IDs automáticos del backend de pruebas, sin borrar datos...'
docker exec -i "$db_container" sh -c \
    'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "${POSTGRES_DB:-$POSTGRES_USER}"' \
    < "$script_dir/preparar_backend_e2e.sql"

cd -- "$repo_dir"
exec ./gradlew connectedDebugAndroidTest "$@"
