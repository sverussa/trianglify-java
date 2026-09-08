#!/usr/bin/env bash
# Abre a interface gráfica.
#
#   ./gui.sh
#
# Na primeira execução o Gradle baixa o JavaFX e o AtlantaFX; depois disso a
# janela abre direto. Equivale a: ./gradlew runGui
set -euo pipefail
cd "$(dirname "$0")"

exec ./gradlew -q --console=plain runGui
