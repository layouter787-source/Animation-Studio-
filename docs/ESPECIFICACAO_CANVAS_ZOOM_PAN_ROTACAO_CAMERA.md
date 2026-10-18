# Animation Studio — Especificação técnica de Canvas, Zoom, Pan, Rotação e Câmara

**Tipo:** Especificação técnica complementar de produção  
**Produto:** Animation Studio para Android  
**Base planeada:** Kotlin, Jetpack Compose, C++/NDK, JNI e backend gráfico com fallback  
**Referência de estudo:** OpenToonz, repositório oficial  
**Estado:** Análise e requisitos; não significa que estas funções já estejam implementadas.

---

## 1. Objetivo e limites

Este documento aprofunda o comportamento da folha/canvas, zoom, pan, rotação da vista, câmara de saída e transformação de coordenadas. Foi preparado a partir da documentação pública do OpenToonz e de pesquisas direcionadas ao código-fonte, incluindo SceneViewer, eventos do viewer, contratos de zoom/pan e definições da câmara.

**Limite importante:** OpenToonz é um projeto grande. Esta análise não afirma que todos os seus ficheiros tenham sido lidos integralmente. O foco foi procurar os componentes relevantes para visualização, navegação e câmara. Antes de integrar código, é necessária uma auditoria específica dos ficheiros e dependências que se pretende reutilizar.

Não copiar o OpenToonz inteiro para o Animation Studio nem tratar código desktop baseado em Qt como código Android pronto. O objetivo é converter os conceitos em requisitos próprios, adequados a Android, toque, stylus e barras flutuantes.

## 2. Referências examinadas

### Código-fonte oficial
- Repositório: https://github.com/opentoonz/opentoonz
- SceneViewer — declaração: https://github.com/opentoonz/opentoonz/blob/master/toonz/sources/toonz/sceneviewer.h
- SceneViewer — implementação: https://github.com/opentoonz/opentoonz/blob/master/toonz/sources/toonz/sceneviewer.cpp
- Eventos e gestos do viewer: https://github.com/opentoonz/opentoonz/blob/master/toonz/sources/toonz/sceneviewerevents.cpp
- Contratos de pan/zoom das ferramentas: https://github.com/opentoonz/opentoonz/blob/master/toonz/sources/include/tools/tool.h
- Widget de definições da câmara: https://github.com/opentoonz/opentoonz/blob/master/toonz/sources/toonzqt/camerasettingswidget.cpp
- Cabeçalho de definições da câmara: https://github.com/opentoonz/opentoonz/blob/master/toonz/sources/toonzqt/camerasettingswidget.h
- Objetos de cena/câmara: https://github.com/opentoonz/opentoonz/blob/master/toonz/sources/include/toonz/tstageobject.h

### Documentação oficial
- Viewer e Timeline/Xsheet: https://opentoonz.readthedocs.io/en/latest/working_in_xsheet.html
- Níveis de desenho, zoom, pan e rotação: https://opentoonz.readthedocs.io/en/latest/drawing_animation_levels.html
- Configuração de cena e câmara: https://opentoonz.readthedocs.io/en/latest/setting_up_a_scene.html
- Licença e estrutura do projeto: https://github.com/opentoonz/opentoonz

As ligações para master podem mudar. Antes de reutilizar código, fixar um commit e registar a revisão analisada.

## 3. Cinco sistemas que não devem ser confundidos

1. **Folha de desenho:** superfície branca, delimitada e com dimensões próprias.
2. **Vista do editor:** como o utilizador vê a folha; inclui zoom, pan e rotação visual.
3. **Conteúdo artístico:** traços, imagens, camadas, fundos e frames guardados em coordenadas do projeto.
4. **Câmara de saída:** enquadramento, proporção e resolução usados na exportação.
5. **Interface flutuante:** barras, menus e painéis sobrepostos, que não fazem parte da imagem final.

A vista pode mudar sem alterar a arte. A câmara pode mudar sem deslocar a folha no ecrã. Mover uma barra não pode alterar nenhum destes sistemas.

### 3.1 Folha e espaços de coordenadas

- A área exterior à folha é escura; a folha é branca por padrão.
- A folha tem largura e altura próprias em píxeis do projeto.
- A folha pode ser maior que o ecrã e parcialmente visível.
- Zoom, pan e rotação visual não alteram a resolução nem os dados artísticos.
- A vista usa coordenadas do ecrã; o motor usa coordenadas estáveis do projeto.
- A conversão entre píxeis de ecrã, unidades dp e píxeis do projeto deve ser explícita.
- Pincel, borracha, laço, balde, conta-gotas e câmara devem usar convenções de coordenadas coerentes.

## 4. Zoom

Zoom é uma escala de visualização. Ampliar faz a folha parecer maior e mostra uma região menor; reduzir mostra uma área maior. A resolução real do documento não muda.

### 4.1 Zoom centrado no foco

Quando o utilizador faz zoom sobre um ponto, esse ponto deve permanecer aproximadamente sob o mesmo ponto do ecrã. Para pinça, o foco natural é o ponto médio entre os dedos. Um controlo de zoom pode usar o centro da folha ou o ponto selecionado.

Modelo simplificado sem rotação:

    screen = scale × project + translation

Se a escala mudar de s para s2 e o foco f tiver de permanecer fixo no ecrã, a nova translação é:

    translation2 = f - (s2 / s) × (f - translation)

Com rotação, aplicar o mesmo princípio à transformação afim completa: determinar o ponto do projeto sob o foco, atualizar escala/rotação e recalcular a translação para manter esse ponto estável.

### 4.2 Controlos e invariantes

- Pinça com dois dedos para ampliar/reduzir.
- Controlos explícitos de zoom para precisão e acessibilidade.
- Comando Ajustar à folha.
- Comando Repor vista.
- Indicador de escala/zoom.
- Limites mínimo e máximo definidos e testados.
- Não iniciar pincel ou borracha durante zoom.
- Não alterar o enquadramento de exportação.
- Abrir/fechar painéis não deve mudar a escala sem ação explícita.
- Evitar arredondar coordenadas do projeto em cada movimento.

### 4.3 Ajustar à folha

Ajustar à folha calcula a maior escala que permite mostrar toda a folha na área útil do editor, considerando a orientação e uma margem visual. Barras flutuantes são overlays: não devem reservar espaço no layout. Se cobrirem a folha, o utilizador pode movê-las ou ocultá-las. O centro da folha deve alinhar-se com o centro visual escolhido pelo produto.

## 5. Pan

Pan desloca a vista para revelar outra parte da folha. Não move desenhos, camadas, câmara nem keyframes. A documentação OpenToonz descreve a ferramenta Hand para deslocar o conteúdo visual do viewer; no Animation Studio, o princípio deve ser adaptado ao toque.

### 5.1 Gestos

- Dois dedos para deslocar a vista, quando o reconhecedor identificar navegação.
- Ferramenta de mão para pan explícito com um dedo.
- Um dedo não deve iniciar pan por acidente quando a ferramenta de desenho estiver ativa.
- A folha pode deslocar-se parcialmente para fora do ecrã; disponibilizar Ajustar à folha.
- Pan não cria uma entrada de desfazer artístico.

### 5.2 Invariantes

- As coordenadas do documento não mudam.
- Zoom e rotação permanecem constantes durante pan puro.
- A câmara não muda.
- Mover uma barra não é pan.
- Interrupção do gesto pelo sistema deixa a vista num estado válido.

## 6. Rotação do canvas

É obrigatório distinguir **rotação da vista** de **rotação da arte**. A rotação da vista faz a folha parecer rodada para facilitar o traço; as coordenadas do documento continuam iguais. A rotação da arte transforma efetivamente uma seleção ou camada e pertence à ferramenta de transformação, com histórico e pré-visualização.

### 6.1 Comportamento

- Rodar em torno de um ponto de foco definido.
- Preservar o foco durante a rotação.
- Combinar corretamente com zoom e pan.
- Mostrar ângulo atual quando útil.
- Disponibilizar rotação a zero e Repor vista.
- Não alterar dimensões, traços, câmara ou exportação.
- A folha deve receber o traço na posição correta mesmo inclinada.

A documentação OpenToonz descreve uma ferramenta de rotação do viewer, uma referência visual de horizonte e um comando para repor a vista. Estes são padrões úteis; a interação Android pode ser diferente.

### 6.2 Transformação e inversa

A vista deve ter uma única transformação composta de projeto para ecrã, incluindo escala, rotação e translação, com ordem bem definida. O caminho inverso para entrada de desenho usa a matriz inversa:

    screenPoint = ViewTransform × projectPoint

    projectPoint = inverse(ViewTransform) × screenPoint

Não converter coordenadas apenas dividindo por zoom quando existe rotação. Isso causa traços deslocados. UI, motor, seleção e conta-gotas não devem manter matrizes independentes com convenções diferentes.

## 7. Prioridade de gestos e barras flutuantes

Como as barras flutuam sobre a folha, o sistema tem de distinguir controlos, movimento de barras, navegação e desenho.

### 7.1 Prioridade recomendada

1. Botão ou controlo tocado.
2. Pega de arrasto de barra flutuante.
3. Manipuladores de seleção/transformação.
4. Gestos multi-toque de navegação.
5. Ferramenta ativa, se o gesto começar numa zona de desenho válida.

### 7.2 Estados conceptuais

- Idle: nenhum gesto ativo.
- PossibleDraw: possível traço ainda cancelável.
- Drawing: traço confirmado.
- Panning: deslocamento da vista.
- Zooming: alteração de escala.
- RotatingView: rotação visual.
- MovingToolbar: arrasto da pega da barra.
- TransformingSelection: transformação de seleção.
- Cancelled: entrada interrompida; libertar recursos e regressar a estado válido.

Testar pinça durante início de traço, arrasto de pega, toque em ícone, desenho junto a barra, zoom e rotação combinados, perda de foco, mudança de orientação e uso de stylus.

## 8. Câmara do projeto

A câmara define a região do projeto usada no resultado final. Não é o zoom do editor. OpenToonz documenta tamanho da câmara, proporção e resolução em píxeis. No Animation Studio, as dimensões de saída em píxeis devem ser explícitas e autoritativas.

### 8.1 Propriedades

- Identificador/nome.
- Largura e altura de saída em píxeis.
- Proporção calculada a partir da largura e altura.
- Transformação e enquadramento no espaço do projeto.
- Rotação, se suportada.
- Câmara ativa, se existirem várias.
- FPS de saída ou referência explícita ao FPS do projeto.
- Fundo/transparência suportados pelo formato.
- Keyframes, quando a animação da câmara estiver implementada.

### 8.2 Independência da câmara

- A folha pode ser maior que a câmara.
- A câmara pode enquadrar só uma parte da folha.
- Mover a câmara não move os desenhos.
- Zoom/pan/rotação do editor não alteram a câmara.
- Mover barras não altera a câmara.
- Ajustar à folha e Ver enquadramento da câmara são comandos diferentes.
- A moldura da câmara é uma sobreposição, não conteúdo exportado.

### 8.3 Modos de vista

- Vista da folha: mostra a área de trabalho.
- Vista da câmara: destaca o enquadramento final.
- Sobreposição da câmara: mostra o retângulo de saída sobre a folha.
- Pré-visualização de saída: mostra a composição final sem UI, seleção ou onion skin.

### 8.4 Alterar a câmara

Ao mudar a resolução ou proporção, validar limites, atualizar a moldura e manter os desenhos intactos. Não esticar nem recortar automaticamente a arte. Ajustar a câmara ao conteúdo deve ser uma ação explícita. A exportação usa exatamente a resolução confirmada.

### 8.5 Câmara animada

Se forem implementados keyframes de câmara, guardar transformações por tempo/frame, definir interpolação e comportamento fora dos keyframes, e usar a mesma transformação na pré-visualização e exportação. Não declarar a função concluída até testar edição, persistência, reprodução e exportação.

## 9. Pipeline de renderização e exportação

1. Resolver o frame e o tempo a exportar.
2. Obter camadas e cenários visíveis segundo a ordem definida.
3. Compor a imagem no espaço do projeto.
4. Aplicar o enquadramento da câmara à resolução de saída.
5. Aplicar as regras de fundo/transparência do formato.
6. Codificar o frame ou enviá-lo ao encoder de vídeo.
7. Libertar buffers temporários de forma segura.

Não exportar uma captura de ecrã da interface. A exportação deve usar os dados do projeto e a câmara, não a matriz visual do editor; assim, barras, painéis, seleção, zoom, rotação visual e onion skin não entram no resultado.

## 10. Arquitetura Android

### Kotlin e Jetpack Compose

Responsáveis por workspace, barras flutuantes, estado de gestos, painéis, indicadores, acessibilidade, ciclo de vida e encaminhamento de entrada.

### Motor de desenho

Responsável por transformações de coordenadas, interpolação de traços, composição de camadas, operações raster, renderização do projeto e saída da câmara, buffers e cache.

### Contrato de vista

Manter uma única fonte de verdade para escala, ângulo, translação, ponto de foco, transformação projeto→ecrã, transformação inversa e limites. Alterar escala/rotação deve recalcular translação para preservar o foco.

### GPU

Avaliar OpenGL ES como backend inicial e manter o motor desacoplado da API gráfica. Vulkan só deve ser adicionado se medições em dispositivos reais justificarem a complexidade. Não portar diretamente SceneViewer: depende de Qt e de componentes desktop.

## 11. Critérios de aceitação

### Zoom
- [ ] Pinça altera apenas a escala da vista.
- [ ] O foco permanece estável.
- [ ] A folha mantém dimensões e conteúdo.
- [ ] Ajustar à folha mostra a folha inteira.
- [ ] Zoom repetido não causa deriva progressiva.

### Pan
- [ ] Pan move a vista, não a arte.
- [ ] Pan não cria histórico artístico.
- [ ] A folha pode ser recuperada com Ajustar à folha.

### Rotação
- [ ] Rotação visual não transforma permanentemente os desenhos.
- [ ] Traços coincidem com o cursor/stylus depois de rodar.
- [ ] Rotação combina com zoom e pan.
- [ ] Repor vista restaura ângulo neutro.
- [ ] Rotação visual não afeta exportação.

### Câmara
- [ ] Alterar câmara não desloca a folha nem os desenhos.
- [ ] Alterar vista não altera a câmara.
- [ ] A moldura corresponde à proporção de saída.
- [ ] O vídeo usa resolução e enquadramento definidos.
- [ ] A saída não contém interface, seleção ou onion skin.
- [ ] Pré-visualização e exportação correspondem.

### Barras e gestos
- [ ] Arrastar barras não inicia traço.
- [ ] Barras não reservam espaço permanente.
- [ ] Pinça perto das barras não move uma barra acidentalmente.
- [ ] Existe forma acessível de recuperar barras ocultas.

## 12. Plano de testes geométricos

1. Converter os quatro cantos da folha para coordenadas de ecrã.
2. Aplicar zoom e transformação inversa; verificar recuperação das coordenadas.
3. Aplicar rotação e inversa; verificar erro dentro de tolerância definida.
4. Combinar escala, rotação e translação.
5. Simular traços conhecidos com a vista rodada.
6. Mudar orientação e confirmar que só muda a apresentação.
7. Confirmar que a câmara não muda com transformações da vista.
8. Confirmar que a exportação não muda quando só se altera a vista.
9. Testar folhas pequenas, panorâmicas e de alta resolução.
10. Testar valores extremos e operações próximas dos limites.

Definir e documentar tolerâncias de ponto flutuante; não exigir igualdade exata em todos os cálculos.

## 13. Licença e reutilização de OpenToonz

O repositório oficial declara que a maioria dos ficheiros fora de thirdparty e stuff/library/mypaint brushes usa Modified BSD. As áreas de terceiros e os pincéis podem ter licenças próprias. A licença deve ser verificada para cada ficheiro e dependência.

Antes de copiar código: fixar o commit exato; identificar ficheiros; ler cabeçalhos e licenças; verificar dependências; preservar avisos exigidos; adaptar para Android em vez de copiar componentes Qt; registar a origem; e realizar revisão técnica/licenciamento antes da distribuição.

Este documento não incorpora blocos extensos de código-fonte OpenToonz. Descreve comportamentos e referencia ficheiros para permitir uma implementação Android própria. Se forem necessários excertos reutilizados, devem ficar num ficheiro de terceiros identificado, com avisos de licença e registo de alterações.

## 14. Decisões finais

1. A folha é branca e tem dimensões próprias.
2. A vista tem escala, rotação e translação independentes.
3. A conversão usa a transformação composta e a sua inversa.
4. Zoom preserva o ponto de foco.
5. Pan desloca a vista, não a arte.
6. Rotação do canvas significa rotação visual, salvo quando a ferramenta de transformação de conteúdo está explicitamente ativa.
7. A câmara é independente da vista e controla a saída.
8. A exportação é renderizada a partir dos dados do projeto, nunca de uma captura da interface.
9. Barras flutuantes são overlays e não alteram a geometria da folha.
10. Código OpenToonz só pode ser reutilizado após auditoria de ficheiro, dependências e licença.

**Referência principal:** OpenToonz, software de animação 2D de código aberto. A documentação e o código servem como estudo técnico; o Animation Studio precisa de uma implementação Android própria, validada em dispositivos reais.