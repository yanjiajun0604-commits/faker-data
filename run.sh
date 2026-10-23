#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ ! -f target/faker-data-0.1.0.jar ]; then
  ./mvnw -B -DskipTests package
fi
exec java -jar target/faker-data-0.1.0.jar "$@"
