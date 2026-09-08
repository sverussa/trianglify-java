#!/usr/bin/env bash
# Atalho para gerar a imagem sem digitar a linha completa do Gradle.
#
#   ./run.sh                          usa trianglify.properties
#   ./run.sh --seed=praia --width=1920
#
# Equivale a: ./gradlew -q installDist && build/install/trianglify/bin/trianglify "$@"
set -euo pipefail
cd "$(dirname "$0")"

./gradlew -q --console=plain installDist
exec build/install/trianglify/bin/trianglify "$@"
