# Pontos de execução do Trianglify, num lugar só.
#
#   make                        lista os alvos
#   make imagem ARGS="--seed=praia --width=1920"
#   make gif ARGS="--anim.speed=2 --anim.duration=20"
#
# Cada alvo chama o script equivalente, que continua funcionando sozinho.
# Sem ARGS, tudo sai de trianglify.properties.

ARGS ?=
GRADLE := ./gradlew -q --console=plain

.DEFAULT_GOAL := ajuda
.PHONY: ajuda imagem anim gif mp4 gui paletas compilar bin limpar

ajuda: ## mostra esta lista
	@grep -hE '^[a-z0-9-]+:.*##' $(MAKEFILE_LIST) \
		| awk -F':.*## ' '{printf "  make %-9s %s\n", $$1, $$2}'
	@echo
	@echo '  passe opções com ARGS="--chave=valor ..."'

imagem: ## gera a imagem parada (PNG e/ou SVG)
	./run.sh $(ARGS)

anim: ## exporta a animação no formato de anim.format
	./animar.sh $(ARGS)

gif: ## exporta a animação em GIF
	./animar.sh --anim.format=gif $(ARGS)

mp4: ## exporta a animação em MP4 (precisa do ffmpeg)
	./animar.sh --anim.format=mp4 $(ARGS)

gui: ## abre a interface gráfica
	./gui.sh

paletas: ## gera uma imagem por paleta, com index.html
	./gerar-paletas.sh $(ARGS)

compilar: ## compila o núcleo, a CLI e a GUI
	$(GRADLE) build compileGuiJava

bin: ## instala o executável em build/install/trianglify/bin
	$(GRADLE) installDist

limpar: ## apaga build/ e saida/
	$(GRADLE) clean
	rm -rf saida
