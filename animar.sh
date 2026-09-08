#!/usr/bin/env bash
# Exporta a animação (GIF ou MP4) descrita pelas propriedades anim.* .
#
#   ./animar.sh                                    usa trianglify.properties
#   ./animar.sh --anim.format=mp4 --anim.duration=20
#   ./animar.sh --anim.speed=2 --anim.file=saida/rapido.gif
#   ./animar.sh --seed=praia --anim.seedCount=8 --anim.scale=0.5
#
# O MP4 precisa do ffmpeg no PATH; o GIF usa o ffmpeg se ele estiver instalado
# (a paleta sai melhor) e, se não estiver, o codificador do próprio JDK.
#
# Equivale a: ./run.sh --animar "$@"
set -euo pipefail
cd "$(dirname "$0")"

./gradlew -q --console=plain installDist
exec build/install/trianglify/bin/trianglify --animar "$@"
