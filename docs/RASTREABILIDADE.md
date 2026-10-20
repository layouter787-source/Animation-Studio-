# Rastreabilidade de requisitos — Animation Studio

Estados: `PENDENTE`, `EM IMPLEMENTAÇÃO`, `IMPLEMENTADO`, `TESTADO`, `BLOQUEADO`.

**Regra:** `TESTADO` só com evidência executada (job de CI verde ou teste em dispositivo).

## Evidência atual

- PR #1 (`claude/fase-2-nucleo` → `main`, merge `a1b3454`): job "Compile, test and package debug APK" **verde** (compila, corre `testDebugUnitTest` e gera o APK de debug), em 2026-10-20.
- Por isso os itens cobertos por testes unitários estão `TESTADO` (nível 3). Os itens de UI compilam mas **não foram validados em emulador nem dispositivo** (nível 2): ficam `IMPLEMENTADO`.
- Erro encontrado e corrigido no caminho: `PointerInputChange.historical` é API experimental; autorizada em `app/build.gradle.kts`.

## Inventário inicial (commit d320c64)

- Base Android com Kotlin/Compose/Material 3, CMake/NDK mínimo (`animation_engine`) e workflow que compila o APK de debug.
- `MainActivity.kt` tinha: lista de projetos **fictícia**, traços guardados em coordenadas de ecrã numa lista única partilhada por todos os frames, zoom/pan que não afetavam os traços, barras fixas que reservavam espaço, botão de reprodução sem efeito, sem testes.
- Documentos: `README.md`, `docs/ESPECIFICACAO_CANVAS_ZOOM_PAN_ROTACAO_CAMERA.md`, `docs/SUPERINSTRUCAO_IMPLEMENTACAO_CLAUDE.md`, `docs/PESQUISA_REFERENCIAS_E_INTERFACE.md`.

## Estado

| ID | Requisito | Estado | Ficheiros | Notas / limitações |
|---|---|---|---|---|
| REQ-CANVAS-01 | Folha branca sobre espaço escuro, com limite | IMPLEMENTADO | `ui/AnimationEditor.kt` | Traços cortados à folha; sem teste em dispositivo |
| REQ-CANVAS-02 | Transformação única projeto↔ecrã com inversa | TESTADO | `domain/ViewTransform.kt`, `ViewTransformTest.kt` | Testes unitários verdes no CI |
| REQ-CANVAS-03 | Zoom focal com limites | TESTADO (lógica) | idem | Gesto de pinça não validado em dispositivo |
| REQ-CANVAS-04 | Pan só da vista | TESTADO (lógica) | idem | A UI ainda tem a ferramenta Mão, que o autor não quer (remover em REQ-UI-01) |
| REQ-CANVAS-05 | Rotação visual | TESTADO (lógica) | idem | Gesto não validado em dispositivo; sem indicador de ângulo |
| REQ-CANVAS-06 | Câmara (enquadramento/saída) | PENDENTE | — | Proposta em `PESQUISA_REFERENCIAS_E_INTERFACE.md` |
| REQ-UI-01 | Interface conforme esboços (barra superior com camadas/cenário/timeline, grelha de ferramentas, painel de cor, câmara) | PENDENTE | — | UI atual é anterior aos esboços; migração de `Timeline` para `Project` necessária |
| REQ-BARS-01 | Barras flutuantes, arrastáveis, ocultáveis | EM IMPLEMENTAÇÃO | `ui/AnimationEditor.kt` | Sem persistência de posições, sem acoplar em cima/baixo com o canvas a redimensionar |
| REQ-DRAW-01 | Pincel | EM IMPLEMENTAÇÃO | idem | Traço suavizado; sem pressão, presets, opacidade, textura |
| REQ-DRAW-02 | Borracha | IMPLEMENTADO | idem | BlendMode.Clear em camada offscreen; não validada em dispositivo |
| REQ-DRAW-03 | Balde, arrastar cor, laço | PENDENTE | — | |
| REQ-COLOR-01 | Seletor de cor | EM IMPLEMENTAÇÃO | idem | Só RGB com OK/Cancelar; faltam HSB, paleta, recentes |
| REQ-FRAME-01 | Frames independentes | TESTADO | `domain/Timeline.kt`, `TimelineTest.kt` | |
| REQ-FRAME-02 | Novo/duplicar/apagar frame | TESTADO (lógica) | idem | Faltam copiar/colar esq./dir., multi-seleção, mover |
| REQ-TL-01 | Timeline | EM IMPLEMENTAÇÃO | `ui/AnimationEditor.kt` | Células numeradas; sem miniaturas, sem expandir camadas |
| REQ-UNDO-01 | Desfazer/refazer | TESTADO (lógica) | `domain/Timeline.kt` | Traços e operações de frame; camadas pendentes |
| REQ-PLAY-01 | Reprodução ONCE/LOOP/PING_PONG | TESTADO (lógica) | idem | Temporização por `delay` não medida em dispositivo |
| REQ-LAYER-01 | Camadas: nova, duplicar, renomear, opacidade %, visibilidade, apagar | TESTADO (só domínio) | `domain/Project.kt`, `ProjectTest.kt` | Sem UI; popup do "+" (nova/duplicar) e popup de camada (renomear/opacidade) pendentes |
| REQ-SCENE-01 | Cenário partilhado editável de qualquer frame + "separar a partir deste frame" | TESTADO (só domínio) | idem | Sem UI |
| REQ-PROJ-01 | Persistência, autosave, recuperação, lista real de projetos | PENDENTE | — | Home mostra estado vazio honesto |
| REQ-ONION-01 | Onion skin | PENDENTE | — | |
| REQ-VIEWER-01 | Visualizador de frames / Flipbook | PENDENTE | — | |
| REQ-IO-01 | Importar imagem/vídeo; exportar MP4/PNG/sequência/ZIP | PENDENTE | — | |
| REQ-KEY-01 | Keyframes, biblioteca de substituições | PENDENTE | — | |
| REQ-BRUSH-01 | Biblioteca de pincéis (libmypaint) | PENDENTE | — | Ver licenças |

## Fluxo descrito pelo autor (a implementar)

- Menu → popup: importar imagem/vídeo; exportar vídeo MP4, PNG do frame atual/selecionado, sequência PNG, ZIP; adicionar camada.
- "+" de camada e de cenário: popup com *Nova* e *Duplicar*.
- Camada/cenário: toque longo abre popup com renomear e opacidade em %.
- Frame: toque vai ao frame; toque longo abre copiar, inserir em branco à esquerda/direita, colar à esquerda/direita.
- Botão de expandir a timeline arrasta-se para mostrar todas as camadas e cenários; fica no lado oposto da barra superior.
- Cor: toque na amostra abre popup (paleta, RGB, HSB); arrastar a amostra para uma região fechada preenche-a.
- Barra superior e de ferramentas acopláveis em qualquer lado, adaptando-se; com a barra em baixo o canvas sobe.
- Pincel/borracha: toque prolongado abre popup com pincéis por categoria e recentes. Laço: popup de transformação.
- FPS: campo editável com setas; Frame Viewer ao estilo do FlipaClip antigo.

## Licenças e dependências externas

| Componente | Licença | Estado |
|---|---|---|
| JUnit 4.13.2 | EPL-1.0 | Em uso (só testes) |
| libmypaint | ISC (confirmar no ficheiro COPYING do commit fixado) | Não integrado |
| mypaint-brushes (ficheiros `.myb`) | CC0 para os pincéis; scripts GPL-2+ não copiar | Não integrado |
| OpenToonz | Modified BSD na maioria; terceiros/pincéis à parte | Só referência de comportamento, sem código copiado |

## Níveis de evidência

1. Código escrito → 2. Compila no CI → 3. Testes unitários verdes no CI → 4. Validado em emulador/dispositivo. Os itens de domínio estão no nível 3; a UI está no nível 2.
