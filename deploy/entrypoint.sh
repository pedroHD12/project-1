#!/bin/sh
set -eu

if [ "${SPRING_PROFILES_ACTIVE:-}" = "cloud" ]; then
  : "${DB_SSL_ROOT_CERT_BASE64:?DB_SSL_ROOT_CERT_BASE64 é obrigatório no perfil cloud.}"
  certificate_directory="$HOME/.postgresql"
  certificate_file="$certificate_directory/root.crt"
  temporary_certificate="$certificate_file.tmp"

  mkdir -p "$certificate_directory"
  umask 077
  printf '%s' "$DB_SSL_ROOT_CERT_BASE64" | base64 -d > "$temporary_certificate"
  test -s "$temporary_certificate"
  mv "$temporary_certificate" "$certificate_file"
fi

exec java -jar /app/app.jar
