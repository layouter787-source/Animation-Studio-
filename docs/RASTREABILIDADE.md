# Rastreabilidade de requisitos — Animation Studio

Estados: `PENDENTE`, `EM IMPLEMENTAÇÃO`, `IMPLEMENTADO`, `TESTADO`, `BLOQUEADO`.

**Regra:** `TESTADO` só com evidência executada (job de CI verde ou teste em dispositivo). Nesta data **nenhum item está `TESTADO`**: o código desta fase foi escrito sem acesso a um ambiente que compile Gradle, por isso a primeira evidência será o job do GitHub Actions da PR `claude/fase-2-nucleo`. Até lá, "IMPLEMENTADO" significa apenas que o código existe.

## Bloqueio conhecido

O acesso GitHub usado nesta sessão não tem permissão para alterar `.github/workflows/`. O passo de testes unitários tem de ser adicionado manualmente ao workflow `android-debug-apk.yml`, antes de `assembleDebug`:

```yaml
      - name: Unit tests
        run: gradle --no-daemon testDebugUnitTest
```

Sem este passo, os testes de `app/src/test` não correm no CI (o `assembleDebug` não os executa).

## Inventário inicial (branch `main`, commit d320c64)

- Base Android com Kotlin/Compose/Material 3, CMake/NDK mínimo (`animation_engine`) e workflow que compila o APK de debug.
- `MainActivity.kt` tinha: lista de projetos **fictícia**, traços guardados em coordenadas de ecrã numa lista única partilhada por todos os frames (mudar de frame não separava desenhos), zoom/pan que não afetavam os traços, barras fixas que reservavam espaço, botão de reprodução sem efeito, sem testes.
- Documentos lidos: `README.md`, `docs/ESPECIFICACAO_CANVAS_ZOOM_PAN_ROTACAO_CAMERA.md`, `docs/SUPERINSTRUCAO_IMPLEMENTACAO_CLAUDE.md`, workflow.

## Estado desta fase

| ID | Requisito | Estado | Ficheiros | Notas / limitações |
|---|---|---|---|---|
| REQ-CANVAS-01 | Folha branca sobre espaço escuro, com limite | IMPLEMENTADO | `ui/AnimationEditor.kt` | Traços cortados à folha |
| REQ-CANVAS-02 | Transformação única projeto↔ecrã com inversa | IMPLEMENTADO | `domain/ViewTransform.kt`, `ViewTransformTest.kt` | Testes escritos, não executados |
| REQ-CANVAS-03 | Zoom focal com limites | IMPLEMENTADO | idem | Pinça de 2 dedos; botão Ajustar à folha |
| REQ-CANVAS-04 | Pan só da vista | IMPLEMENTADO | idem | 2 dedos ou ferramenta Mão; não entra no histórico |
| REQ-CANVAS-05 | Rotação visual | IMPLEMENTADO | idem | Gesto de 2 dedos; reposição pelo botão; sem indicador de ângulo |
| REQ-CANVAS-06 | Câmara (enquadramento/saída) | PENDENTE | — | |
| REQ-BARS-01 | Barras flutuantes, arrastáveis, ocultáveis | EM IMPLEMENTAÇÃO | `ui/AnimationEditor.kt` | Sem persistência de posições, sem acoplar em cima/baixo com o canvas a redimensionar |
| REQ-DRAW-01 | Pincel | EM IMPLEMENTAÇÃO | idem | Traço suavizado; sem pressão, presets, opacidade, textura |
| REQ-DRAW-02 | Borracha | IMPLEMENTADO | idem | BlendMode.Clear em camada offscreen |
| REQ-DRAW-03 | Balde, arrastar cor, laço | PENDENTE | — | |
| REQ-COLOR-01 | Seletor de cor | EM IMPLEMENTAÇÃO | idem | Só RGB com OK/Cancelar; faltam HSB, paleta, recentes |
| REQ-FRAME-01 | Frames independentes | IMPLEMENTADO | `domain/Timeline.kt`, `TimelineTest.kt` | Testes escritos, não executados |
| REQ-FRAME-02 | Novo/duplicar/apagar frame | IMPLEMENTADO | idem | Faltam copiar/colar esq./dir., multi-seleção, mover |
| REQ-TL-01 | Timeline | EM IMPLEMENTAÇÃO | `ui/AnimationEditor.kt` | Células numeradas; sem miniaturas, sem expandir camadas |
| REQ-UNDO-01 | Desfazer/refazer | IMPLEMENTADO | `domain/Timeline.kt` | Traços e operações de frame; camadas pendentes |
| REQ-PLAY-01 | Reprodução ONCE/LOOP/PING_PONG | IMPLEMENTADO | idem | Lógica testada em `TimelineTest`; temporização por `delay`, não medida |
| REQ-PROJ-01 | Persistência, autosave, recuperação, lista real de projetos | PENDENTE | — | Home mostra estado vazio honesto |
| REQ-ONION-01 | Onion skin | PENDENTE | — | |
| REQ-LAYER-01 | Camadas (renomear, opacidade %) | PENDENTE | — | |
| REQ-SCENE-01 | Cenário fixo editável + "separar a partir deste frame" | PENDENTE | — | Decisão de design abaixo |
| REQ-VIEWER-01 | Visualizador de frames / Flipbook | PENDENTE | — | |
| REQ-IO-01 | Importar imagem/vídeo; exportar MP4/PNG/sequência/ZIP | PENDENTE | — | |
| REQ-KEY-01 | Keyframes, biblioteca de substituições | PENDENTE | — | |
| REQ-BRUSH-01 | Biblioteca de pincéis (libmypaint) | PENDENTE | — | Ver licenças |

## Fluxo descrito pelo autor (a implementar)

- Menu → popup: importar imagem/vídeo; exportar vídeo MP4, PNG do frame atual/selecionado, sequência PNG, ZIP; adicionar camada.
- Camada/cenário: toque longo abre popup com renomear e opacidade em %.
- Frame: toque vai ao frame; toque longo abre copiar, inserir em branco à esquerda/direita, colar à esquerda/direita.
- Botão de expandir a timeline arrasta-se para mostrar todas as camadas e cenários; fica no lado oposto da barra superior.
- Cor: toque na amostra abre popup (paleta, RGB, HSB); arrastar a amostra para uma região fechada preenche-a.
- Barra superior e de ferramentas acopláveis em qualquer lado, adaptando-se; com a barra em baixo o canvas sobe.
- Pincel/borracha: toque prolongado abre popup com pincéis por categoria e recentes. Laço: popup de transformação.
- FPS: campo editável com setas (stepper já existe); Frame Viewer ao estilo do FlipaClip antigo.

## Decisões de design

1. **Cenário** = camada fixa (um desenho para todos os frames) editável a partir de qualquer frame; a edição vale para todos por desenho. "Separar a partir deste frame" converte-o em animado dali em diante, sem apagar nada. (Padrão equivalente ao de camada de imagem vs. camada de animação noutros programas; não foi possível confirmar a documentação do RoughAnimator.)
2. **Barras**: a especificação original pede overlays; o autor pede também acoplar com o canvas a ajustar-se. Implementar os dois modos (flutuante/acoplado) com opção do utilizador.

## Licenças e dependências externas

| Componente | Licença | Estado |
|---|---|---|
| JUnit 4.13.2 | EPL-1.0 | Em uso (só testes) |
| libmypaint | ISC (confirmar no ficheiro COPYING do commit fixado) | Não integrado |
| mypaint-brushes (ficheiros `.myb`) | CC0 para os pincéis; scripts GPL-2+ não copiar | Não integrado |
| OpenToonz | Modified BSD na maioria; terceiros/pincéis à parte | Só referência de comportamento, sem código copiado |

## Níveis de evidência

1. Código escrito → 2. Compila no CI → 3. Testes unitários verdes no CI → 4. Validado em emulador/dispositivo. Nada passou do nível 1 até esta data.
