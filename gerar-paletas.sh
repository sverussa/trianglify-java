#!/usr/bin/env bash
#
# Gera uma imagem para cada paleta de cores disponivel, todas com a mesma
# semente e as mesmas dimensoes, para que possam ser comparadas lado a lado.
#
# A lista de paletas vem do proprio gerador (--listar-paletas), entao o script
# nao precisa manter uma copia da lista.
#
set -euo pipefail
cd "$(dirname "$0")"

SEED="paletas"
WIDTH=480
HEIGHT=320
CELL=48
FORMAT=png
OUT=saida/paletas
INDICE=1
extra=()

usage() {
  cat <<'AJUDA'
Gera uma imagem para cada paleta de cores disponivel.

uso:
  ./gerar-paletas.sh [opcoes] [--chave=valor ...]

opcoes:
  --seed=TEXTO     semente usada em todas as imagens   (padrao: paletas)
  --width=N        largura em pixels                   (padrao: 480)
  --height=N       altura em pixels                    (padrao: 320)
  --cellSize=N     tamanho da celula                   (padrao: 48)
  --format=FMT     png, svg ou both                    (padrao: png)
  --out=DIR        diretorio de saida                  (padrao: saida/paletas)
  --sem-indice     nao gera o index.html de visualizacao
  -h, --help       mostra esta ajuda

Qualquer outra --chave=valor e repassada ao gerador, o que permite variar
tambem a aparencia. Exemplos:

  ./gerar-paletas.sh
  ./gerar-paletas.sh --seed=praia --width=800 --height=600
  ./gerar-paletas.sh --colorFunction=shadows --colorFunction.arg=0.9
  ./gerar-paletas.sh --format=svg --out=/tmp/paletas
AJUDA
}

while [ $# -gt 0 ]; do
  case "$1" in
    --seed=*)     SEED=${1#*=} ;;
    --width=*)    WIDTH=${1#*=} ;;
    --height=*)   HEIGHT=${1#*=} ;;
    --cellSize=*) CELL=${1#*=} ;;
    --format=*)   FORMAT=${1#*=} ;;
    --out=*)      OUT=${1#*=} ;;
    --sem-indice) INDICE=0 ;;
    -h|--help)    usage; exit 0 ;;
    --*=*)        extra+=("$1") ;;   # repassado ao gerador
    *)            echo "erro: argumento nao reconhecido: $1 (use --help)" >&2; exit 2 ;;
  esac
  shift
done

case "$FORMAT" in
  png|svg|both) ;;
  *) echo "erro: --format deve ser png, svg ou both (recebido: $FORMAT)" >&2; exit 2 ;;
esac

# Com 'both' o gerador escreve os dois arquivos; a extensao do caminho so
# define o nome base.
EXT=$FORMAT
[ "$FORMAT" = both ] && EXT=png

echo "compilando..."
./gradlew -q --console=plain installDist
BIN=build/install/trianglify/bin/trianglify

PALETAS=()
while IFS= read -r p; do
  [ -n "$p" ] && PALETAS+=("$p")
done < <("$BIN" --listar-paletas)

TOTAL=${#PALETAS[@]}
if [ "$TOTAL" -eq 0 ]; then
  echo "erro: nenhuma paleta retornada pelo gerador" >&2
  exit 1
fi

mkdir -p "$OUT"
echo "gerando $TOTAL paletas em $OUT/ (${WIDTH}x${HEIGHT}, seed=$SEED)"
echo

i=0
for p in "${PALETAS[@]}"; do
  i=$((i + 1))
  printf '  [%2d/%d] %-10s' "$i" "$TOTAL" "$p"
  "$BIN" -q \
    --seed="$SEED" \
    --width="$WIDTH" --height="$HEIGHT" --cellSize="$CELL" \
    --xColors="$p" --yColors=match \
    --output.format="$FORMAT" --output.file="$OUT/$p.$EXT" \
    ${extra[@]+"${extra[@]}"}
  echo " -> $p.$EXT"
done

if [ "$INDICE" -eq 1 ] && [ "$FORMAT" != svg ]; then
  {
    echo "<!doctype html><meta charset='utf-8'><title>Paletas do Trianglify</title>"
    echo "<style>"
    echo "  body{font:14px system-ui,sans-serif;margin:24px;background:#111;color:#eee}"
    echo "  h1{font-size:18px;font-weight:600;margin:0 0 4px}"
    echo "  p{color:#999;margin:0 0 24px}"
    echo "  ul{list-style:none;padding:0;margin:0;display:grid;gap:16px;"
    echo "     grid-template-columns:repeat(auto-fill,minmax(240px,1fr))}"
    echo "  img{width:100%;height:auto;display:block;border-radius:6px}"
    echo "  span{display:block;margin-top:6px;font-family:ui-monospace,monospace;font-size:12px}"
    echo "</style>"
    echo "<h1>Paletas do Trianglify</h1>"
    echo "<p>$TOTAL paletas &middot; seed <code>$SEED</code> &middot; ${WIDTH}&times;${HEIGHT}</p>"
    echo "<ul>"
    for p in "${PALETAS[@]}"; do
      echo "  <li><img src=\"$p.$EXT\" alt=\"$p\" loading=\"lazy\"><span>$p</span></li>"
    done
    echo "</ul>"
  } > "$OUT/index.html"
  echo
  echo "indice: $OUT/index.html"
fi

echo
echo "pronto: $TOTAL imagens em $OUT/"
