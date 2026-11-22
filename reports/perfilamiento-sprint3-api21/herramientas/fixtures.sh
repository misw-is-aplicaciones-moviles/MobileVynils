#!/bin/bash
# Reinicia BackVynils con la semilla y crea los datos de prueba API21 (equivalentes a los del P20).
set -e
cd /c/WSL/uniandes/mobile-apps/BackVynils
docker compose down -v >/dev/null 2>&1
docker compose up -d >/dev/null 2>&1
for i in $(seq 1 60); do curl -sf http://localhost:3000/albums >/dev/null && break; sleep 2; done
J='-H Content-Type:application/json'
post() { curl -sf -X POST $J "http://localhost:3000/$1" -d "$2"; echo; }
post collectors '{"name":"API21 Perfilamiento","telephone":"3001234567","email":"api21@example.com"}'
post bands '{"name":"API21 Perfilamiento","image":"https://example.com/api21-banda.jpg","description":"Banda de perfilamiento API 21","creationDate":"2000-01-01T00:00:00.000Z"}'
post musicians '{"name":"API21 Musico","image":"https://example.com/api21-musico.jpg","description":"Musico libre de perfilamiento API 21","birthDate":"1980-01-01T00:00:00.000Z"}'
post albums '{"name":"API21 Album","cover":"https://example.com/api21-album.jpg","releaseDate":"2000-01-01T00:00:00.000Z","description":"Album de perfilamiento API 21","genre":"Rock","recordLabel":"EMI"}'
post prizes '{"organization":"API21 Org","name":"API21 Premio","description":"Premio de perfilamiento API 21"}'
