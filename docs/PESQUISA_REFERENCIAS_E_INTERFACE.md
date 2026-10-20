# Pesquisa de referências e especificação da interface

## 1. RoughAnimator (referência principal) — o que foi confirmado

Fonte: guia oficial do utilizador (roughanimator.com/userguide). A página carrega por JavaScript; o conteúdo abaixo vem dos excertos devolvidos pela pesquisa, não de uma leitura integral.

- Um projeto novo abre com **uma camada Background que não pode ser apagada** e uma "Layer 1" com um desenho; há sliders de opacidade por camada e botões para ordenar/apagar.
- **Adicionar camada** oferece: *camada vazia*, *duplicar camada* (cópia completa com conteúdo) e *duplicar a temporização para desenhos em branco* (útil para colorir por baixo da linha). As duas primeiras correspondem ao popup que o autor pediu no "+" (nova / duplicar).
- Menu de camada: modo de mistura, máscara para a camada de baixo, bloquear, juntar com a de baixo, apagar, opacidade, reordenar arrastando.
- Duração do desenho: modos *Independent* (só a camada atual) e *Sync layers* (todas).
- Existe uma **ferramenta de câmara para animar pans/zooms**.

### Não confirmado

Não consegui ler a descrição detalhada da câmara nem do comportamento do Background do RoughAnimator (keyframes, interpolação, o que acontece ao editar o fundo). Não vou inventar. As propostas abaixo são **desenho próprio**, apoiado noutras apps, e devem ser validadas pelo autor.

## 2. Decisões para o Animation Studio

**Cenário (diferente do RoughAnimator, como pedido):** camada em que todos os frames partilham o mesmo desenho; editável a partir de qualquer frame (o que se desenha vale para todos). "Separar a partir deste frame" cria uma cópia para o frame atual e seguintes, sem apagar o que veio antes. Implementado no domínio (`Project.separateSceneryFromCurrent`). Novos frames continuam o cenário do frame atual; duplicar frame partilha o cenário e copia os desenhos das camadas normais.

**Botão "+" de camada/cenário:** popup com *Nova* e *Duplicar* (`addLayer`, `duplicateLayer`).

**Câmara (proposta):** retângulo de saída com posição, escala e rotação por keyframe e interpolação entre eles, independente da vista do editor. Referências de comportamento de outras apps (não do RoughAnimator): reduzir o enquadramento dá zoom-in e movê-lo faz pan; repor câmara; copiar/colar a câmara noutro frame; interpolar entre dois ou mais keyframes (documentação do Pivot Animator). O OpenToonz permite animar a câmara na própria vista com a ferramenta de edição.

**Canvas/folha:** zoom, pan, rotação e reposição conforme `docs/ESPECIFICACAO_CANVAS_ZOOM_PAN_ROTACAO_CAMERA.md`. A ferramenta Mão **não** existe no desenho do autor: o deslocamento é com dois dedos.

## 3. Interface (esboços do autor)

Barra superior (móvel, pode ir para baixo; o canvas ajusta-se):
- Menu (esquerda).
- Camadas e cenários: linhas "Camada 1" e "Cenário 1", cada uma com o seu "+" à esquerda e a sua fila de frames à direita (timeline).
- Aba central "Expandir a timeline" (arrastar para mostrar todas as camadas/cenários; fica no lado oposto da barra).
- Câmara (ícone) à direita.
- Painel de cor à direita: amostra da cor selecionada + métodos Paleta, RGB, HSB.

Barra de ferramentas (móvel, grelha de 2 colunas):
1. Pincel | Borracha
2. Balde de tinta | Laço
3. Desfazer | Refazer
4. FPS do projeto (campo com setas) 
5. "+" adicionar frame
6. Navegar entre frames (anterior | seguinte), como pequena barra separada

Folha branca (canvas) com "FRAME x DE N" por baixo, sempre visível.

Estado: a UI atual ainda **não** segue este esboço (ver `RASTREABILIDADE.md`, REQ-UI-01).
