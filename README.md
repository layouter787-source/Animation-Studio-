# Animation Studio — Especificação integral do produto

**Documento:** Especificação de produto, experiência, comportamento, arquitetura, qualidade e aceitação  
**Plataforma principal:** Android (telemóveis e tablets)  
**Estado do documento:** Requisitos-alvo para produto completo de produção  
**Idioma-base da especificação:** Português  
**Repositório:** https://github.com/layouter787-source/Animation-Studio-

> **Importante — estado e verdade do produto:** este documento define o que o Animation Studio deverá oferecer quando o produto estiver completo. Não afirma que todas as funcionalidades já estejam implementadas. Cada requisito só poderá ser marcado como concluído depois de existir código integrado, testes e validação em dispositivos. A existência de um APK compilável não equivale a um estúdio de animação completo.

---

# 1. Visão, missão e posicionamento

O **Animation Studio** é um estúdio de desenho e animação 2D, prioritariamente para Android, com fluxo de trabalho centrado em animação frame a frame, desenho direto sobre uma folha branca, gestão temporal por timeline e uma interface que se adapta à forma como cada artista trabalha.

A experiência de edição inspira-se em fluxos de trabalho de aplicações como RoughAnimator e FlipaClip, mas o Animation Studio deve ter implementação, identidade visual, código, arquitetura e evolução próprios. As aplicações de referência servem para estudar padrões de interação, não para copiar código, marcas, recursos gráficos ou componentes proprietários.

## 1.1 Missão

Permitir que um artista consiga criar, organizar, rever, animar e exportar projetos 2D completos diretamente num dispositivo Android, com controlos que possam ser reorganizados para deixar o máximo de espaço possível para desenhar.

## 1.2 Princípios que não podem ser quebrados

1. **A folha é branca por padrão.** O espaço exterior à folha é escuro. A folha é um objeto com dimensões e limites próprios, não apenas uma região escura ou o fundo inteiro da aplicação.
2. **O canvas não perde espaço para barras fixas.** A barra superior e a barra de ferramentas flutuam sobre a área de trabalho, podem ser movidas e ocultadas e não reservam permanentemente faixas ou colunas.
3. **A navegação visual não altera a arte.** Zoom, pan e rotação da vista não alteram a resolução, as coordenadas internas, os frames nem o enquadramento de exportação.
4. **Os dados são reais e persistentes.** Frames, camadas, cenários, cores, tempos e projetos não podem ser apenas elementos decorativos de interface ou dados fictícios.
5. **O editor funciona offline.** Desenhar, guardar, abrir e reproduzir projetos locais não deve exigir conta ou ligação à Internet.
6. **A interface não pode bloquear durante operações longas.** Importação, composição, geração de miniaturas e exportação devem usar trabalho assíncrono e informar o estado.
7. **A edição deve ser recuperável.** Autosave, recuperação após falha e desfazer/refazer são partes essenciais da experiência.
8. **A performance deve ser medida.** Não afirmar que uma API gráfica ou linguagem é sempre mais rápida; medir em dispositivos e projetos reais.
9. **A interface respeita os esboços fornecidos.** Melhorar a clareza e a qualidade visual não autoriza alterar silenciosamente a estrutura central escolhida pelo autor.
10. **Uma função não é considerada concluída só porque existe um botão.** Cada comando deve executar uma operação real, atualizar o estado persistente e ter tratamento de erro e testes.

## 1.3 Público-alvo

- Animadores 2D iniciantes e experientes.
- Ilustradores que criam animação frame a frame.
- Estudantes de desenho, movimento e princípios de animação.
- Artistas que trabalham em telemóveis ou tablets Android, com toque ou stylus.
- Criadores que precisam de exportar sequências de imagens ou vídeos para outras ferramentas.

## 1.4 O que o produto não deve ser

- Não deve ser apenas um protótipo de interface.
- Não deve ser uma galeria de ecrãs sem ferramentas funcionais.
- Não deve simular a gravação, a reprodução ou a exportação.
- Não deve depender de um serviço remoto para tarefas básicas de desenho.
- Não deve declarar suporte universal para stylus, GPU, formatos ou resoluções sem testes.
- Não deve incluir funcionalidades futuras como se já estivessem prontas.

---

# 2. Plataforma, compatibilidade e experiência do dispositivo

## 2.1 Plataforma

- Aplicação Android nativa.
- Kotlin para a camada de aplicação e interface.
- Jetpack Compose e Material 3 para a interface.
- C++/Android NDK para componentes de motor que beneficiem de processamento nativo.
- JNI para uma fronteira explícita entre Kotlin e C++.
- CMake para configurar e compilar o código C++.
- GitHub Actions para compilação automatizada e artefactos de teste.
- Armazenamento local como caminho principal dos projetos.

A versão mínima de Android e as arquiteturas ABI suportadas devem ser definidas no Gradle e testadas no processo de lançamento; não podem ficar implícitas.

## 2.2 Formatos de dispositivo

O editor deve suportar:
- Telemóveis em orientação vertical.
- Telemóveis em orientação horizontal.
- Tablets, incluindo áreas de trabalho amplas.
- Mudança de orientação sem perder o projeto ou a ferramenta ativa.
- Recortes de ecrã, barras de sistema e áreas seguras.
- Dispositivos apenas táteis e dispositivos com stylus, quando disponível.

A interface pode ajustar a posição inicial das barras à orientação, mas o utilizador continua a poder movê-las. A adaptação automática nunca deve reintroduzir uma coluna fixa ou uma faixa permanente que reduza a folha.

## 2.3 Toque, stylus e entrada

- Aceitar entrada de dedo.
- Usar pressão e propriedades de stylus apenas quando o dispositivo e a API as disponibilizarem.
- Não assumir que todos os dispositivos têm pressão, inclinação, botão de stylus ou rejeição de palma.
- Distinguir entrada de desenho de gestos de navegação e de interação com a interface.
- Prevenir que um toque em botão, pega ou painel inicie um traço acidental.
- Disponibilizar alvos de toque suficientemente grandes e opção de aumentar a escala da interface.

---

# 3. Modelo da área de trabalho e folha de desenho

## 3.1 Conceitos visuais distintos

A implementação deve distinguir claramente:

- **Área de trabalho (workspace):** superfície visual do editor, com fundo escuro.
- **Folha/canvas:** retângulo branco por padrão, com largura e altura definidas no projeto.
- **Conteúdo artístico:** traços, imagens, formas, fundos e outros dados pertencentes ao projeto.
- **Câmara:** enquadramento usado para pré-visualização e exportação; não é a navegação da vista.
- **Interface flutuante:** barras, painéis, indicadores e menus desenhados por cima da área de trabalho.

A folha pode ocupar uma parte ou ultrapassar a área visível conforme o zoom. O espaço exterior à folha continua visualmente distinto.

## 3.2 Transformação de coordenadas

O motor deve manter coordenadas internas do projeto independentes das coordenadas do ecrã. A conversão entre espaço do ecrã e espaço do projeto deve ter em conta:
- Escala/zoom da vista.
- Deslocamento/pan.
- Rotação da vista, se ativada.
- Origem e dimensões da folha.
- Densidade e escala da interface.

A conversão deve ser centralizada numa implementação testável. Ferramentas de desenho, laço, balde, seleção, câmara e gestos não devem criar sistemas incompatíveis de coordenadas.

## 3.3 Zoom, pan e rotação da vista

- Pinça com dois dedos para aproximar e afastar.
- Gesto de dois dedos para deslocar a vista, sem alterar os desenhos.
- Ferramenta de mão ou comando equivalente para navegação precisa.
- Comando “Ajustar à folha” para enquadrar a folha visível.
- Indicador de zoom e, quando aplicável, rotação da vista.
- Limites de zoom definidos para evitar perda de orientação ou instabilidade numérica.
- Possibilidade de centrar a folha.
- A folha pode ficar parcialmente fora do ecrã quando ampliada.
- A vista pode ser movida mesmo quando a folha é maior que o ecrã.
- Os gestos de vista não podem ser confundidos com o desenho de um traço.

A rotação da vista, caso disponibilizada, é apenas uma transformação de visualização. A rotação da câmara é uma propriedade de enquadramento distinta.

## 3.4 Dimensões do projeto

No diálogo de criação, o utilizador deve poder definir:
- Nome.
- Largura e altura em píxeis.
- FPS inicial.
- Predefinição de proporção/tamanho, se desejado.
- Opções de câmara/enquadramento inicial, quando aplicáveis.

O sistema deve validar valores mínimos e máximos, explicar limites de memória quando pertinente e evitar alocações enormes sem confirmação. Alterar as dimensões posteriormente deve ser uma operação explícita, com pré-visualização/confirmar/cancelar e política definida para conteúdo existente.

## 3.5 Desenhar junto ou sob as barras

As barras flutuantes podem sobrepor a folha. A política de entrada deve ser determinística:
- Um gesto iniciado sobre um controlo interativo pertence ao controlo.
- Um gesto iniciado na pega da barra move a barra.
- Um gesto iniciado numa zona de desenho livre inicia o desenho, se a ferramenta ativa desenhar.
- Um gesto de navegação reconhecido como gesto de dois dedos navega a vista.
- A aplicação não pode transformar o arrasto de uma barra num traço.
- A aplicação não pode transformar um traço iniciado na folha num arrasto de barra apenas porque a barra se moveu por cima de outra área.
- Se os controlos estiverem a cobrir uma zona, o utilizador pode mover ou ocultar a barra para desenhar nessa zona sem obstáculos.

---

# 4. Barras flutuantes — requisito crítico de produto

## 4.1 Regra de layout

**A área de trabalho ocupa todo o espaço disponível do editor. As barras são desenhadas por cima dela.** A presença de uma barra não deve diminuir a largura ou altura do canvas, empurrar a folha, alterar a escala do projeto ou criar uma faixa vazia depois de a barra ser movida.

Não implementar a barra de ferramentas como uma coluna fixa de layout nem a barra superior como um cabeçalho que reserva sempre espaço dentro do canvas. A interface do sistema Android pode continuar a ter as suas áreas seguras; dentro da área da aplicação, as barras do editor são overlays.

## 4.2 Barra superior

Pode disponibilizar, conforme o modo e o espaço:
- Menu do projeto e ações de ficheiro.
- Nome do projeto.
- FPS de reprodução.
- Indicador de frame atual e total.
- Duração/intervalo de desenho e controlos temporais.
- Câmara/enquadramento.
- Cor atual.
- Acesso à timeline, camadas e cenários.
- Opções de ferramenta e definições.

O conjunto visível pode adaptar-se ao tamanho do ecrã, mas nenhuma ação importante deve desaparecer sem alternativa acessível.

## 4.3 Barra de ferramentas

Ferramentas base:
- Pincel.
- Borracha.
- Balde de preenchimento.
- Laço/seleção.
- Desfazer.
- Refazer.
- Frame anterior.
- Frame seguinte.
- Adicionar frame.
- Reprodução.
- Onion skin.
- Acesso às opções da ferramenta.

A barra deve ter ícones consistentes, estado ativo inequívoco, descrição acessível e feedback de toque. O conjunto de ferramentas pode crescer, sem exigir que todos os ícones estejam sempre visíveis.

## 4.4 Movimento e posicionamento

- A barra deve ter uma pega/área de arrasto distinta dos botões.
- Arrastar a pega desloca a barra, sem desenhar.
- As posições válidas devem manter a barra alcançável e dentro das margens seguras.
- O utilizador pode posicioná-la junto às margens ou numa posição flutuante útil.
- Ao mudar de orientação, a aplicação deve recuperar uma posição válida para a orientação atual.
- Se uma posição guardada ficar fora do ecrã devido a alteração de resolução/orientação, reposicionar a barra de forma segura, sem a perder.
- A posição pode ser guardada globalmente e/ou por projeto, com comportamento consistente e documentado.
- Deve existir uma ação para restaurar a posição padrão.

## 4.5 Ocultar e reabrir

- Cada barra pode ser ocultada independentemente.
- Ocultar a barra não desativa a ferramenta selecionada nem modifica o desenho.
- Deve existir um mecanismo visível/discreto e documentado para reabrir a barra.
- A interface não pode depender exclusivamente de um gesto secreto.
- O estado visível/oculto deve ser persistido conforme a preferência escolhida.
- Ao reabrir, a barra regressa a uma posição acessível.
- Ocultar barras liberta a vista visual; não muda as dimensões reais da folha nem o enquadramento da câmara.

## 4.6 Reorganização e acessibilidade

- Premir prolongadamente uma ferramenta pode abrir opções, categorias ou personalização quando essa ação fizer sentido.
- A reorganização de ícones deve ser explícita, para não confundir um toque normal com uma tentativa de mover a barra.
- A ferramenta selecionada deve continuar evidente.
- Menus e painéis devem poder ser fechados sem perder a edição.
- Os controlos devem respeitar áreas seguras e não ficar atrás de recortes, teclado ou barras do sistema.
- Não cobrir permanentemente o indicador de frame, a câmara ou ações críticas.

## 4.7 Testes obrigatórios de barras

1. Mostrar/ocultar cada barra não altera a área nem a escala da folha.
2. Mover a barra não cria traço, apaga conteúdo ou muda de frame.
3. Tocar num botão executa apenas a ação desse botão.
4. Uma barra movida pode ser reaberta e recuperada após reiniciar.
5. A mudança de retrato para paisagem não deixa uma barra inacessível.
6. A área que fica livre após mover uma barra não contém uma coluna/faixa reservada.
7. Se o utilizador mover a barra para longe da folha, a folha mantém o mesmo centro e zoom.
8. O mecanismo para reabrir barras continua acessível com ambas ocultas.

## 4.8 Referências de interação

- FlipaClip: https://play.google.com/store/apps/details?id=com.vblast.flipaclip
- FlipaClip — suporte: https://support.flipaclip.com/
- Autodesk Sketchbook — toolbars: https://help.autodesk.com/cloudhelp/ENU/SKETPRO-Help/files/sb-basics/SKETPRO_Help_sb_basics_toolbars_html.html

Estas fontes são referências para padrões de utilização, não para copiar recursos proprietários.

---

# 5. Ecrã inicial e gestão da biblioteca de projetos

## 5.1 Estrutura visual

Seguir o esboço fornecido pelo autor:
- Lista vertical de projetos do lado esquerdo, com miniatura e nome.
- Área ampla do lado direito dedicada à criação de um novo projeto.
- Botão de criação claramente visível.
- Ações de ordenação por nome e data, quando disponíveis.
- Interface escura, sóbria e organizada.

Em ecrãs pequenos, a mesma função pode adaptar-se a uma disposição vertical sem perder as ações principais. A adaptação não deve introduzir um feed fictício ou conteúdo de demonstração apresentado como trabalho real.

## 5.2 Operações de projeto

- Criar projeto.
- Abrir projeto.
- Renomear projeto.
- Duplicar projeto.
- Apagar projeto, sempre com confirmação.
- Ordenar por nome e data da última alteração.
- Mostrar miniatura e metadados básicos.
- Identificar projetos com erro de recuperação ou migração.
- Recuperar um projeto interrompido quando existir uma cópia recuperável.

## 5.3 Diálogo de novo projeto

Campos e comportamentos:
- Nome obrigatório e validado.
- Largura/altura ou predefinição.
- FPS inicial.
- Resumo da dimensão escolhida.
- Opções de câmara/margens quando relevantes.
- Botões claros de criar e cancelar.
- Validação antes de criar ficheiros.
- Estimativa ou aviso de memória para dimensões excessivas, quando possível.

Ao criar o projeto, inicializar dados reais: folha branca, estrutura temporal inicial, camada inicial, cenário/fundo inicial e câmara/enquadramento inicial coerente.

## 5.4 Estado e armazenamento

- O armazenamento local é o padrão.
- O nome e os metadados devem sobreviver ao fecho da aplicação.
- A miniatura deve representar conteúdo real do projeto.
- O estado “a guardar/guardado/erro” deve ser comunicado sem interromper desnecessariamente o desenho.
- Projetos corrompidos não devem ser sobrescritos automaticamente sem tentar recuperação.
- A remoção deve explicar que o projeto será apagado e pedir confirmação.
- Não pedir permissões de armazenamento amplas se APIs modernas do Android permitirem acesso escolhido pelo utilizador.

---

# 6. Editor: estrutura e painéis

## 6.1 Organização base

A estrutura visual de referência inclui:
- Barra superior flutuante.
- Timeline no topo do editor, quando visível.
- Faixas de camadas e cenários associadas à timeline.
- Folha branca no espaço de trabalho escuro.
- Barra de ferramentas flutuante.
- Indicador de frame atual e total.
- Controlos de zoom/rotação da vista.
- Painéis contextuais de ferramenta, cor, camadas, cenário e câmara.

A timeline pode ser recolhida/expandida por um controlo claro. A barra superior e a barra de ferramentas continuam móveis e sobrepostas ao espaço de trabalho.

## 6.2 Painéis contextuais

- Abrir um painel não deve modificar conteúdo por si só.
- Os painéis devem ter um botão de fechar evidente.
- A edição em painel deve atualizar o estado real.
- Fechar o painel mantém alterações já confirmadas.
- Cancelar um diálogo descarta apenas alterações não confirmadas.
- O painel deve adaptar-se a telemóvel/tablet e não impedir acesso ao canvas sem necessidade.
- Quando um painel cobre uma região, o utilizador pode fechá-lo ou movê-lo conforme o modelo de interação.

## 6.3 Indicador de frame

O indicador “Frame X de Y” deve permanecer disponível mesmo com a timeline recolhida. Deve atualizar-se ao navegar, inserir ou remover frames, sem contagens obsoletas.

---

# 7. Ferramentas de desenho e edição

## 7.1 Pincel e traços

O motor de traços deve suportar:
- Traços contínuos com dedo e stylus.
- Interpolação entre amostras para evitar segmentos partidos.
- Tamanho configurável.
- Opacidade configurável.
- Cor selecionada.
- Suavização/estabilização ajustável.
- Sensibilidade à pressão quando disponível.
- Pré-visualização do tamanho quando viável.
- Cancelamento seguro do traço em caso de interrupção de entrada.
- Gravação como conteúdo editável do frame/camada, não como decoração temporária da UI.

O sistema de pincéis deve ser extensível por uma interface comum de motor e presets. Cada preset pode definir tamanho, opacidade, espaçamento, suavização, dinâmica de pressão e parâmetros específicos suportados. Parâmetros não suportados pelo motor não devem ser apresentados como funcionais.

## 7.2 Biblioteca de pincéis

- Categorias e lista de pincéis.
- Pincéis recentes.
- Preset ativo claramente identificado.
- Ajustes de tamanho e opacidade.
- Espaçamento, suavização e outros parâmetros quando suportados.
- Possibilidade futura de adicionar presets e texturas próprias.
- Metadados versionados para que um projeto preserve a identificação do preset utilizado.
- Se um preset faltar numa versão futura, apresentar fallback e aviso em vez de falhar ao abrir o projeto.

Bibliotecas externas só podem ser integradas depois de confirmar licença, dependências, compatibilidade com Android e licença de cada textura/preset. A licença do motor não garante direitos sobre conteúdos de terceiros.

## 7.3 Borracha

- Apagar conteúdo na camada/frame ativo.
- Tamanho e opacidade quando aplicáveis.
- Respeitar o tipo de conteúdo e a camada ativa.
- Desfazer/refazer.
- Não apagar a folha nem outras camadas por engano.
- Comportamento consistente durante zoom e pan.

## 7.4 Balde de preenchimento

- Preencher uma região de cor semelhante, com tolerância ajustável.
- Definir se o preenchimento considera a camada ativa ou uma composição de referência, quando implementado.
- Respeitar transparência e limites de imagem.
- Executar em tarefa de fundo quando a área for grande.
- Permitir cancelar uma operação longa quando seguro.
- Não bloquear o desenho por tempo indefinido.
- Uma falha ou região inválida não altera o conteúdo.
- O resultado deve poder ser desfeito.

## 7.5 Arrastar cor para preencher

- Arrastar a amostra da cor selecionada até uma região fechada.
- Usar o mesmo motor de preenchimento do balde.
- Mostrar feedback da região-alvo.
- Permitir cancelar antes da aplicação quando tecnicamente viável.
- Se não for detetada uma região válida, não alterar o projeto.
- Evitar conflito com traço, pan, seleção e movimento de barras.
- O resultado deve ser persistido e incluído no histórico de desfazer/refazer.

## 7.6 Laço e seleção

- Selecionar uma região ou conteúdo.
- Indicar visualmente a seleção ativa.
- Mover e transformar a seleção quando suportado.
- Copiar, cortar, colar e apagar conteúdo selecionado.
- Cancelar a seleção sem modificar a arte.
- Respeitar o frame e a camada ativos.
- Converter corretamente coordenadas com zoom/pan.
- Evitar que o arrasto da seleção seja interpretado como desenho.

## 7.7 Cor, RGB, HSB e paletas

- Amostra da cor ativa.
- Seletor com modos RGB e HSB.
- Confirmar/cancelar seleção de cor.
- Paletas do projeto.
- Cores favoritas e recentes.
- Persistência das paletas no projeto.
- Cor consistente entre pincel, preenchimento e ferramentas compatíveis.
- Contraste e apresentação acessíveis, sem depender exclusivamente da cor para comunicar estado.

## 7.8 Desfazer/refazer

- Histórico coerente por projeto.
- Suportar desenho, borracha, preenchimento, transformações e operações estruturais quando implementadas.
- Não criar entradas inúteis para toques sem alteração.
- Agrupar operações de gesto de forma natural, para um traço não exigir dezenas de ações de desfazer.
- Operações em lote devem ser atómicas: completar integralmente ou falhar sem deixar estado parcial.
- Atualizar os botões de acordo com a disponibilidade real.
- Limitar memória do histórico de forma segura e comunicar limitações se necessário.
- Nunca perder o projeto inteiro devido a uma falha no histórico.

## 7.9 Formas e ferramentas adicionais

O sistema deve permitir acrescentar novas ferramentas sem reescrever o editor inteiro. Formas geométricas, conta-gotas, simetria e ferramentas adicionais podem ser adicionadas quando definidas, implementadas e testadas. Não devem aparecer como botões decorativos sem comportamento.

---

# 8. Frames, exposição e modelo temporal

## 8.1 Semântica de frame

Cada frame é uma unidade temporal com conteúdo editável associado às camadas. A navegação não pode apagar, fundir ou substituir desenhos silenciosamente. A timeline e o Frame Viewer são representações diferentes do mesmo estado de projeto.

O modelo temporal deve distinguir:
- Índice/ordem do frame.
- Conteúdo do frame por camada.
- Duração/exposição do desenho quando o modelo a suportar.
- FPS de reprodução/exportação.
- Relações com cenários partilhados ou animados.

## 8.2 Operações básicas

- Adicionar frame em branco.
- Duplicar frame.
- Copiar frame.
- Colar à esquerda/direita como inserção.
- Mover frame.
- Apagar frame.
- Selecionar frame e navegar para ele.
- Ajustar duração/exposição quando suportado.
- Desfazer/refazer as operações.
- Mostrar miniaturas atualizadas.

Colar como inserção deve deslocar os frames seguintes. Não pode sobrescrever frames existentes silenciosamente. Se futuramente existir uma operação de substituição, ela deve ser explicitamente diferente de inserir.

## 8.3 Operações em lote

- Selecionar vários frames.
- Copiar, duplicar, mover e apagar a seleção.
- Inverter ordem de uma sequência quando implementado.
- Preservar alinhamento temporal entre camadas e cenários.
- Validar o resultado antes de confirmar.
- Oferecer desfazer para o conjunto da operação.
- Evitar que a remoção de frames deixe referências inválidas.

## 8.4 FPS e duração

FPS é a taxa de reprodução/exportação, não o total de frames. Os controlos de FPS devem permitir alterar o valor com entrada numérica e controlos incrementais. O sistema deve validar valores suportados e calcular a duração a partir do modelo temporal efetivo.

Se houver exposições de duração variável, a timeline deve comunicar essa diferença sem confundir o número de desenhos com a duração total.

## 8.5 Modos de reprodução

- **ONCE:** tocar do início ao fim e parar.
- **LOOP:** repetir continuamente.
- **PING_PONG:** avançar e retroceder sem duplicar desnecessariamente os extremos.
- Reproduzir a sequência real do projeto, incluindo durações/exposições aplicáveis.
- Respeitar FPS definido.
- Interromper e retomar de forma previsível.
- Não incluir onion skin nem controlos do editor no resultado renderizado.
- Mostrar estado de reprodução e permitir parar rapidamente.

## 8.6 Frame Viewer

Vista alternativa para gerir a sequência:
- Grelha/lista de miniaturas.
- Seleção individual e múltipla.
- Copiar, duplicar, mover e apagar.
- Navegar para o frame.
- Pré-visualizar a sequência.
- Indicar o frame ativo.
- Reordenar com gestos explícitos, com feedback visual.

Alterações no Frame Viewer devem atualizar a timeline e vice-versa. Não pode existir uma segunda cópia independente dos dados.

## 8.7 Flipbook

Modo focado em desenhar frame a frame:
- Mostrar o frame atual em destaque.
- Navegar para anterior/seguinte.
- Adicionar, duplicar, copiar ou limpar frame.
- Ativar onion skin.
- Recolher/expandir a vista conforme o ecrã.
- Manter ligação ao mesmo projeto e à mesma timeline.

O Flipbook é um modo de trabalho, não uma exportação separada nem um projeto duplicado.

---

# 9. Onion skin

- Mostrar frames anteriores e/ou seguintes como referência não editável.
- Configurar quantidade de frames anteriores e seguintes.
- Ajustar opacidade.
- Distinguir visualmente anteriores e seguintes.
- Atualizar ao mudar de frame.
- Ativar/desativar rapidamente.
- Nunca misturar a referência com o conteúdo editável do frame atual.
- Nunca incluir onion skin na exportação final.
- Não persistir as imagens de onion skin como se fossem traços do frame atual.
- A composição deve ser eficiente e usar cache sem comprometer a correção.

---

# 10. Camadas e cenários/fundos

## 10.1 Camadas

- Criar e apagar.
- Renomear.
- Reordenar.
- Alterar visibilidade.
- Alterar opacidade.
- Identificar camada ativa.
- Bloquear edição quando o bloqueio estiver disponível.
- Mostrar miniaturas ou indicadores úteis.
- Integrar com a timeline e com o histórico.
- Preservar as relações entre frames e camadas ao inserir, mover ou remover frames.

## 10.2 Cenários/fundos

Cenários são conceptualmente separados das camadas de animação.

### Fundo partilhado
O mesmo cenário pode ser referenciado por vários frames. Editar o cenário partilhado atualiza as referências que apontam para essa fonte, sem apagar a animação principal.

### Fundo animado
O conteúdo do fundo pode mudar frame a frame.

A interface deve deixar claro o modo utilizado. Alterar visibilidade ou opacidade do fundo não pode afetar por engano a camada principal. Os cenários devem ter nome, visibilidade, opacidade e edição coerentes com o modelo do projeto.

## 10.3 Sincronização de faixas

A inserção, duplicação, deslocação ou remoção de frames deve preservar a consistência temporal entre as faixas de camadas e cenários. A política para faixas que não contêm desenho num determinado frame deve ser explícita e testada.

---

# 11. Biblioteca de substituições

Biblioteca de elementos reutilizáveis para animação, por exemplo:
- Bocas e formas de boca para diálogo.
- Olhos e expressões.
- Poses.
- Partes de personagens.
- Variações de desenho.

Requisitos:
- Miniaturas, nomes e categorias.
- Inserir ou atribuir uma variante a frame/intervalo selecionado.
- Preservar alternativas na biblioteca.
- Evitar substituição silenciosa fora do intervalo escolhido.
- Permitir remover uma atribuição sem apagar a fonte da biblioteca.
- Integrar com a persistência do projeto e o histórico quando aplicável.
- Se o recurso de origem faltar, mostrar um aviso recuperável.

---

# 12. Câmara, enquadramento e saída

## 12.1 Câmara do projeto

A câmara define o enquadramento final e é independente do zoom/pan da interface. Deve suportar:
- Retângulo de enquadramento sobre a folha.
- Dimensões e proporção de saída.
- Posição.
- Escala/zoom.
- Rotação quando suportada.
- Pré-visualização do enquadramento.
- Reposição da câmara.
- Valores numéricos quando aplicáveis.

O zoom de trabalho não pode alterar o enquadramento exportado. A exportação usa o estado da câmara e as definições de saída do projeto, não a posição dos painéis.

## 12.2 Keyframes de câmara

A animação da câmara por keyframes é prevista na arquitetura, mas só pode ser anunciada como funcional quando houver criação/edição, interpolação, pré-visualização e exportação validadas.

---

# 13. Animação por keyframes e transformações

O produto completo deve prever animação de propriedades ao longo do tempo:
- Posição X/Y.
- Escala X/Y.
- Rotação.
- Opacidade.
- Propriedades de câmara e filtros quando implementados.

Comportamentos esperados:
- Criar, selecionar, mover, copiar e apagar keyframes.
- Interpolar entre valores.
- Suportar modos de easing quando disponíveis.
- Pré-visualizar antes de exportar.
- Distinguir keyframes de transformação da animação desenhada frame a frame.
- Guardar as curvas e os valores no projeto.
- Permitir desfazer/refazer.
- Definir comportamento de início/fim e ausência de keyframe de forma consistente.
- Não destruir a arte raster original ao alterar apenas uma transformação.

---

# 14. Importação

## 14.1 Imagens

Permitir importar imagem como:
- Referência visual.
- Conteúdo de desenho/camada.
- Recurso de cenário/fundo.

O fluxo deve permitir escolher o destino, confirmar escala/posicionamento quando aplicável e cancelar sem alterar o projeto. A imagem deve ser tratada com limites de dimensão e memória.

## 14.2 Vídeo e rotoscopia

O produto completo deve prever:
- Importar vídeo como referência de rotoscopia.
- Selecionar intervalo.
- Extrair frames com frequência/intervalo configurável.
- Pré-visualizar a extração.
- Avisar sobre espaço e memória.
- Cancelar tarefas longas quando seguro.
- Não carregar o vídeo inteiro na memória se o processamento por partes for possível.
- Tratar codecs e formatos não suportados com uma mensagem útil.

A disponibilidade real depende dos codecs e APIs Android suportados. Não prometer compatibilidade universal.

## 14.3 Projetos

- Importar projeto no formato nativo versionado.
- Validar estrutura antes de abrir.
- Detetar versões futuras ou incompatíveis.
- Não sobrescrever um projeto existente sem confirmação.
- Se a migração falhar, preservar o ficheiro de origem e explicar o erro.

---

# 15. Exportação

## 15.1 Formatos-alvo

- MP4.
- PNG do frame atual.
- PNG de frames selecionados.
- Sequência de PNG numerados.
- ZIP com a sequência exportada.
- GIF, caso a solução técnica escolhida permita qualidade e desempenho aceitáveis.

Cada formato só pode ser considerado suportado após teste real com saída válida e verificável.

## 15.2 Opções de exportação

- Intervalo de frames.
- FPS de saída.
- Resolução.
- Câmara/enquadramento.
- Fundo/transparência conforme o formato e o motor.
- Local de destino escolhido pelo utilizador.
- Nome do ficheiro.
- Opções de qualidade suportadas pelo encoder.

## 15.3 Fluxo e erros

- Pré-visualizar as opções.
- Mostrar progresso e estado.
- Permitir cancelar quando seguro.
- Não bloquear a interface.
- Tratar falta de espaço, permissão recusada, encoder indisponível e falhas de escrita.
- Não deixar ficheiros finais incompletos apresentados como válidos.
- Limpar temporários após sucesso ou cancelamento seguro.
- Confirmar o destino final e permitir abrir/partilhar o ficheiro através das APIs Android.
- Não exportar barras, painéis, indicadores, seleção ou onion skin.
- Não exportar conteúdo oculto se as regras do projeto definirem que camadas ocultas não aparecem na saída.
- O resultado deve corresponder à pré-visualização de saída.

## 15.4 Áudio

Importação, sincronização e exportação de áudio são requisitos de expansão do produto, mas precisam de uma especificação e testes próprios para faixa temporal, sincronização, controlo de volume, codecs e mistura. Não marcar como concluído antes de uma implementação integral.

---

# 16. Persistência, autosave e recuperação

## 16.1 Modelo de armazenamento

O formato do projeto deve ser versionado e guardar, no mínimo:
- Identificador e nome do projeto.
- Versão do formato.
- Dimensões da folha.
- FPS e modelo temporal.
- Frames e ordem.
- Conteúdo por frame/camada.
- Camadas, nomes, ordem, visibilidade e opacidade.
- Cenários e referências partilhadas.
- Câmara e enquadramento.
- Paletas e cores.
- Metadados de pincéis/presets usados.
- Keyframes e biblioteca de substituições, quando disponíveis.
- Preferências relevantes do projeto.

## 16.2 Consistência

- Guardar em transações/etapas consistentes quando possível.
- Evitar substituir o último estado válido por um ficheiro incompleto.
- Validar os dados antes de os carregar.
- Migrar versões antigas através de passos explícitos.
- Preservar uma via de recuperação se uma migração falhar.
- Não depender de caminhos temporários de cache como fonte única dos desenhos.

## 16.3 Autosave

- Guardar alterações automaticamente.
- Agrupar gravações frequentes para não prejudicar a fluidez.
- Mostrar estado guardado, a guardar ou erro.
- Guardar alterações estruturais com prioridade suficiente para evitar inconsistência.
- Tentar guardar antes de sair quando o ciclo de vida Android permitir.
- Não depender apenas do evento de encerramento, que pode não ocorrer.

## 16.4 Recuperação

- Recuperar o estado mais recente consistente após encerramento inesperado.
- Detetar recuperação pendente e informar o utilizador.
- Não substituir automaticamente um projeto saudável por uma cópia de recuperação sem validação.
- Permitir descartar a recuperação se o utilizador preferir o último estado guardado.
- Registar erros técnicos sem incluir o conteúdo artístico privado em telemetria.

---

# 17. Arquitetura técnica

## 17.1 Camada de aplicação

**Kotlin + Jetpack Compose + Material 3**
- Ecrã inicial e gestão de projetos.
- Navegação e ciclo de vida Android.
- Estado da interface e painéis.
- Barras flutuantes e persistência das suas preferências.
- Diálogos e validação.
- Acessibilidade e integração com APIs Android.
- Coordenação das operações de projeto e exportação.

A interface não deve conter a implementação de todos os algoritmos de desenho. Separar apresentação, modelo de projeto, operações e motor.

## 17.2 Motor nativo

**C++ + Android NDK + CMake**
- Processamento de traços.
- Composição de camadas.
- Operações raster e preenchimento.
- Transformações e renderização.
- Geração de miniaturas.
- Processamento de imagem que beneficie de código nativo.
- Componentes de renderização e exportação quando apropriado.

O uso de C++ deve ser justificado por necessidades medidas. Não duplicar lógica em Kotlin e C++ sem um contrato claro.

## 17.3 JNI

- Fronteira JNI documentada e pequena.
- Agrupar dados de um traço em lotes, evitando chamadas por ponto quando não necessário.
- Validar referências, tamanhos e buffers.
- Tratar erros de forma recuperável.
- Evitar cópias repetidas de imagens grandes entre Kotlin e C++.
- Definir propriedade e duração de vida de buffers.
- Não permitir que exceções nativas atravessem a fronteira sem tratamento.
- Testar integração em dispositivos/ABIs suportadas.

## 17.4 GPU e backend gráfico

- Avaliar OpenGL ES como backend inicial.
- Avaliar Vulkan apenas com caso de uso e medições que justifiquem o custo.
- Deteção de capacidades e fallback para dispositivos sem suporte adequado.
- Manter uma estratégia de compatibilidade e evitar caminhos que só funcionem num modelo específico.
- Não afirmar que Vulkan é sempre mais rápido, mais eficiente ou consome menos bateria.
- Medir latência de traço, composição, reprodução, memória e temperatura.
- Evitar uso desnecessário de GPU quando o ecrã está parado.

## 17.5 Separação de módulos sugerida

A organização concreta pode evoluir, mas deve manter responsabilidades separadas:
- **app:** ponto de entrada, ciclo de vida e navegação.
- **ui/home:** biblioteca e criação de projetos.
- **ui/editor:** workspace, overlays, painéis e gestos.
- **domain/project:** entidades e regras do projeto.
- **domain/timeline:** frames, duração, FPS e operações temporais.
- **data/project:** serialização, armazenamento, migração e recuperação.
- **engine/api:** contrato entre aplicação e motor.
- **engine/native:** C++, renderização e algoritmos.
- **engine/brushes:** presets e abstrações de pincel.
- **media/import:** importação de imagem/vídeo.
- **media/export:** composição e exportação.
- **tests:** testes unitários, integração e instrumentação.

Esta é uma separação lógica, não uma exigência de criar um módulo Gradle por pasta se isso tornar o projeto desnecessariamente complexo.

---

# 18. Gestos: prioridade, arbitragem e conflitos

A implementação deve definir uma política central para decidir que elemento recebe cada gesto.

## 18.1 Prioridade de interação

1. Controlos explicitamente tocados.
2. Pega de movimento da barra.
3. Manipulação de seleção/transformação ativa.
4. Gestos de navegação de dois dedos.
5. Ferramenta ativa de desenho, quando o gesto começa numa área de desenho disponível.

A ordem deve ser adaptada quando uma ferramenta exigir outro comportamento, mas não pode existir ambiguidade silenciosa.

## 18.2 Casos que devem ser testados

- Arrastar a pega da barra superior.
- Arrastar a pega da barra de ferramentas.
- Tocar num ícone enquanto há um traço anteriormente iniciado.
- Desenhar perto de uma barra.
- Pinçar com dois dedos sobre a folha.
- Deslocar a folha sem desenhar.
- Mover uma seleção.
- Arrastar uma cor para preencher.
- Usar stylus enquanto a outra mão navega, quando o dispositivo permitir.
- Rodar o dispositivo durante uma operação.
- Interromper a entrada por notificação, suspensão ou perda de foco.

## 18.3 Cancelamento de gesto

Quando o Android interromper a sequência de entrada, o motor deve finalizar ou cancelar o gesto de forma consistente. Não deixar traços presos, seleções em estado inválido ou barras a saltar para coordenadas incorretas.

---

# 19. Interface visual e design system

## 19.1 Direção visual

- Tema escuro para a área exterior da folha.
- Folha de desenho branca por padrão.
- Cinzentos escuros e tons azul-acinzentados discretos.
- Acentos moderados para seleção e estado ativo.
- Sem gradientes neon ou efeitos decorativos que prejudiquem a leitura.
- Ícones vetoriais consistentes, sem usar emojis como substitutos de ícones profissionais.
- Contraste legível e hierarquia clara.
- Feedback visual para ações ativas, seleção, gravação, reprodução e erro.

## 19.2 Consistência

- Mesmo símbolo e semântica para a mesma ação.
- Ações destrutivas distinguíveis de ações normais.
- Confirmação para apagar projetos e operações de alto impacto.
- Menus contextuais com ações relevantes ao objeto selecionado.
- Estados vazios que explicam como começar, sem inventar dados de utilizador.
- Mensagens de erro com causa provável e ação recuperável quando possível.

## 19.3 Acessibilidade

- Descrições acessíveis para ícones.
- Alvos de toque adequados.
- Escala de texto compatível com as definições do Android sempre que possível.
- Não depender exclusivamente da cor para indicar seleção.
- Foco e navegação coerentes para entradas alternativas.
- Anúncio acessível de estados importantes, como erro de gravação e conclusão de exportação.
- Opções para aumentar controlos sem quebrar a área de trabalho.

---

# 20. Desempenho, memória, bateria e estabilidade

Os alvos concretos devem ser definidos por testes em dispositivos de referência. Os seguintes princípios são obrigatórios:

- O desenho deve apresentar baixa latência percetível.
- A composição da interface não deve reconstruir desnecessariamente o projeto inteiro a cada ponto de traço.
- A geração de miniaturas deve ocorrer fora do caminho crítico de entrada.
- A reprodução não deve bloquear os controlos do editor.
- A exportação deve ser executada em segundo plano com progresso.
- A memória deve ser limitada por políticas de cache e carregamento sob demanda.
- Projetos grandes não devem obrigar a manter todos os bitmaps de todos os frames na memória.
- O motor deve libertar recursos nativos quando projetos e ecrãs são fechados.
- A aplicação deve reduzir trabalho quando não está visível.
- A renderização não deve continuar intensamente quando o projeto está parado.
- Monitorizar ANR, falhas nativas, exceções, consumo de memória e erros de exportação.
- Testar comportamento térmico em sessões prolongadas; não prometer ausência de aquecimento.
- Evitar tarefas em ciclo contínuo sem necessidade.
- Operações pesadas devem poder ser canceladas quando for seguro.

Antes do lançamento, criar um conjunto de projetos de referência: pequeno, médio, grande, muitos frames, muitas camadas, traços densos e imagens importadas. Registar dispositivo, versão Android, dimensões, FPS e resultados dos testes.

---

# 21. Segurança, privacidade e permissões

- Projetos locais por padrão.
- Não exigir conta para desenhar, guardar ou exportar localmente.
- Não carregar desenhos para um servidor sem uma funcionalidade explícita e consentimento.
- Pedir acesso a ficheiros através de mecanismos Android apropriados e apenas quando necessário.
- Validar ficheiros importados e limitar dimensões/tamanhos.
- Tratar projetos como entrada não confiável até validação.
- Não executar conteúdo embutido em projetos.
- Não guardar tokens ou segredos no repositório.
- Não incluir imagens, nomes de ficheiros ou conteúdo artístico privado em registos de diagnóstico sem consentimento.
- Se forem adicionados serviços remotos no futuro, documentar recolha, finalidade, retenção, eliminação e consentimento antes do lançamento.

---

# 22. Erros e recuperação de operações

Todas as operações relevantes devem ter resultados claros de sucesso, falha ou cancelamento.

Casos obrigatórios:
- Falha ao guardar.
- Espaço insuficiente.
- Projeto inválido ou corrompido.
- Versão de projeto não suportada.
- Memória insuficiente durante importação/composição.
- Formato de vídeo não suportado.
- Falha de encoder.
- Permissão de destino recusada.
- Cancelamento pelo utilizador.
- Perda de foco ou interrupção do sistema.
- Falha de inicialização do motor nativo ou backend gráfico.

O produto deve evitar mensagens técnicas incompreensíveis como única explicação. Detalhes técnicos podem ficar num diagnóstico opcional. Se uma operação falhar, o estado anterior deve continuar válido sempre que possível.

---

# 23. Testes e garantia de qualidade

## 23.1 Testes unitários

- Conversão de coordenadas com zoom/pan/rotação.
- Operações de frame e reordenação.
- Cálculo de FPS e duração.
- Inserção sem sobrescrita.
- Opacidade, visibilidade e ordem de camadas.
- Referências a cenários partilhados.
- Serialização e desserialização.
- Migração de formato.
- Histórico de desfazer/refazer.
- Validação de dimensões e limites.
- Composição e regras de exportação.

## 23.2 Testes de integração

- Kotlin ↔ JNI ↔ C++.
- Criação de projeto, desenho, gravação, fecho e reabertura.
- Frames independentes.
- Persistência de camadas e cenários.
- Renderização após zoom/pan.
- Miniaturas e cache.
- Importação e exportação.
- Recuperação após encerramento inesperado.
- Compatibilidade com todas as ABIs declaradas como suportadas.

## 23.3 Testes de interface

- Criar e abrir projetos.
- Arrastar, ocultar e reabrir barras.
- Alternar orientação.
- Selecionar ferramentas.
- Alterar cor.
- Criar e navegar frames.
- Expandir/recolher timeline.
- Abrir e fechar painéis.
- Exportar e lidar com falhas.
- Acessibilidade básica dos controlos.

## 23.4 Testes manuais em dispositivos

Testar, no mínimo, os tipos de dispositivo disponíveis:
- Telemóvel de gama baixa ou limitada.
- Telemóvel de gama média.
- Telemóvel de gama alta, se disponível.
- Tablet, se disponível.
- Dispositivo com stylus, se o suporte a stylus for anunciado.

Se um tipo de dispositivo não estiver disponível para teste, documentar essa limitação em vez de afirmar que foi validado.

## 23.5 Critérios de bloqueio de lançamento

Não lançar como versão de produção se:
- Há perda de desenhos em operações normais.
- O autosave pode corromper o último projeto válido.
- Barras podem desaparecer sem possibilidade de recuperação.
- A exportação apresenta sucesso sem gerar um ficheiro válido.
- A timeline mostra frames que não correspondem aos dados guardados.
- Há falhas nativas frequentes ou ANRs reprodutíveis.
- Permissões ou erros impedem o utilizador de recuperar o próprio trabalho sem explicação.

---

# 24. Compilação, integração contínua e distribuição

## 24.1 Ambiente de build

- Gradle/Android Gradle Plugin e JDK compatíveis, com versões fixadas e documentadas.
- Kotlin, Compose, NDK e CMake em versões explicitamente definidas.
- Dependências declaradas; evitar versões dinâmicas não reprodutíveis.
- Segredos fora do código e dos logs.
- Compilação local e em GitHub Actions com o mesmo conjunto de tarefas essenciais.

## 24.2 GitHub Actions

O pipeline deve:
1. Obter o código.
2. Preparar JDK e Android SDK/NDK/CMake requeridos.
3. Validar o projeto.
4. Executar testes unitários.
5. Compilar as variantes pretendidas.
6. Publicar artefactos de build com identificação clara.
7. Falhar de forma explícita se uma etapa obrigatória falhar.

APK de depuração serve para testes. A versão de produção deve ter assinatura de lançamento protegida, versionamento, notas de lançamento e verificações adequadas. Não guardar keystores ou palavras-passe no repositório.

## 24.3 Versionamento

- Código de versão monotónico.
- Nome de versão legível.
- Notas com funções adicionadas, problemas conhecidos e compatibilidade.
- Identificar se o artefacto é debug, teste interno ou release.
- Guardar ligação ao commit que produziu o artefacto.
- Não chamar “produção” a uma compilação de debug.

---

# 25. Critérios de aceitação de produto completo

Os requisitos seguintes são verificáveis. Cada item deve ter código, testes e evidência de execução antes de ser marcado como concluído.

## 25.1 Projetos
- [ ] Criar, abrir, renomear, duplicar e apagar projetos reais.
- [ ] Mostrar miniaturas reais e metadados persistentes.
- [ ] Guardar e recuperar projetos após fechar/reabrir a aplicação.
- [ ] Tratar erros de armazenamento sem destruir o último estado válido.

## 25.2 Folha e área de trabalho
- [ ] A folha é branca por padrão e tem limites visíveis.
- [ ] O espaço exterior é escuro.
- [ ] Zoom/pan não alteram resolução nem conteúdo interno.
- [ ] Coordenadas do traço continuam corretas depois de zoom, pan e mudança de orientação.
- [ ] Existe comando para ajustar a folha à vista.

## 25.3 Barras flutuantes
- [ ] A barra superior flutua por cima da área de trabalho.
- [ ] A barra de ferramentas flutua por cima da área de trabalho.
- [ ] Nenhuma delas reserva permanentemente uma faixa ou coluna.
- [ ] Ambas podem ser movidas, ocultadas e reabertas.
- [ ] A posição é restaurada ou corrigida após mudança de orientação.
- [ ] Mover a barra não desenha nem altera o projeto.
- [ ] A forma de reabrir permanece acessível mesmo com ambas ocultas.

## 25.4 Desenho
- [ ] Pincel cria traços reais e persistentes.
- [ ] Borracha altera apenas o conteúdo esperado.
- [ ] Cor e tamanho afetam o resultado real.
- [ ] Desfazer/refazer funciona de forma coerente.
- [ ] O motor trata interrupções de entrada sem deixar estado inválido.

## 25.5 Timeline e frames
- [ ] Frames independentes preservam os seus desenhos.
- [ ] Adicionar, duplicar, mover, inserir e apagar atualizam o estado persistente.
- [ ] Colar como inserção não sobrescreve frames existentes.
- [ ] FPS é distinto do número total de frames.
- [ ] Timeline e Frame Viewer, quando disponíveis, refletem o mesmo projeto.
- [ ] Reprodução respeita ordem, FPS e durações configuradas.

## 25.6 Camadas, cenários e onion skin
- [ ] Camadas podem ser selecionadas, renomeadas, reordenadas e ter visibilidade/opacidade alteradas.
- [ ] Cenários partilhados e animados têm comportamento distinto e previsível.
- [ ] Onion skin é apenas visual, não editável e não exportada.
- [ ] Operações estruturais preservam a sincronização temporal.

## 25.7 Importação e exportação
- [ ] Importação valida formatos e mostra erros recuperáveis.
- [ ] Exportação produz ficheiros válidos nos formatos anunciados.
- [ ] Progresso, cancelamento e falta de espaço são tratados.
- [ ] O vídeo exportado respeita FPS, intervalo, dimensões e câmara.
- [ ] A saída não contém interface, seleção ou onion skin.

## 25.8 Produção
- [ ] Testes automatizados passam.
- [ ] Build CI é reproduzível e produz artefactos identificados.
- [ ] Assinatura de release é protegida.
- [ ] Política de privacidade/permissões corresponde ao comportamento real.
- [ ] Falhas críticas e perda de dados não têm problemas conhecidos sem mitigação.
- [ ] A compatibilidade declarada corresponde aos dispositivos realmente testados.

---

# 26. Fases de implementação e gestão do trabalho

Esta secção organiza o desenvolvimento; **não reduz o produto a um MVP**. O objetivo continua a ser a especificação integral acima. A ordem é para reduzir risco técnico e permitir testar as fundações antes de adicionar dependências.

## Fase A — Fundação de produção
- Estrutura Android, Kotlin/Compose, NDK/CMake e JNI.
- Build reprodutível.
- Modelo de projeto versionado.
- Armazenamento e recuperação iniciais.
- Testes automatizados básicos.

## Fase B — Workspace e gestos
- Área de trabalho de ecrã inteiro.
- Folha branca com coordenadas próprias.
- Zoom/pan.
- Barras flutuantes, móveis, ocultáveis e persistentes.
- Arbitragem central de gestos.
- Testes de orientação e acessibilidade.

## Fase C — Motor de desenho
- Traços, pincel, borracha, cor.
- Conversão de coordenadas.
- Histórico.
- Persistência e reabertura de arte real.
- Perfil de desempenho.

## Fase D — Modelo de animação
- Frames independentes.
- Timeline e miniaturas.
- Camadas e cenários.
- FPS, durações e reprodução.
- Onion skin.

## Fase E — Ferramentas de produção
- Preenchimento, laço, paletas e biblioteca de pincéis.
- Operações em lote e Frame Viewer.
- Flipbook.
- Câmara e enquadramento.

## Fase F — Animação avançada
- Keyframes e interpolação.
- Biblioteca de substituições.
- Ferramentas adicionais e fluxos avançados.

## Fase G — Media e entrega
- Importação de imagem e vídeo.
- Exportação de imagens, sequência, ZIP e vídeo.
- Áudio, se aprovado e especificado em detalhe.
- Otimização, compatibilidade e testes de carga.

## Fase H — Preparação de lançamento
- Auditoria de dados e recuperação.
- Testes em dispositivos.
- Testes de acessibilidade e permissões.
- Verificação de builds de release.
- Documentação de utilizador, notas de lançamento e lista de limitações conhecidas.

A conclusão de uma fase não deve ser inferida apenas pela existência de classes ou botões. Os critérios de aceitação devem ser executados.

---

# 27. Regras de implementação e manutenção do repositório

- O repositório dedicado é `layouter787-source/Animation-Studio-`.
- Não modificar outros repositórios do utilizador.
- Não adicionar segredos, credenciais, keystores ou dados privados ao Git.
- Evitar alterações destrutivas e preservar o histórico.
- Cada alteração deve ter mensagem de commit que descreva o comportamento implementado.
- Atualizar este documento quando requisitos forem alterados, sem marcar funcionalidades não implementadas como concluídas.
- Manter um registo honesto do que foi implementado, testado, parcialmente suportado ou ainda planeado.
- Não remover funcionalidades existentes sem avaliar impacto e sem autorização explícita.
- Dependências externas devem ser revistas quanto a licença, manutenção, compatibilidade e tamanho.
- Nunca incluir código ou assets de terceiros sem cumprir a respetiva licença.

---

# 28. Referências técnicas

- Repositório do produto: https://github.com/layouter787-source/Animation-Studio-
- FlipaClip — Google Play: https://play.google.com/store/apps/details?id=com.vblast.flipaclip
- FlipaClip — suporte: https://support.flipaclip.com/
- Jetpack Compose: https://developer.android.com/compose
- Android NDK: https://developer.android.com/ndk
- JNI e desempenho: https://developer.android.com/training/articles/perf-jni
- AndroidX Ink: https://developer.android.com/jetpack/androidx/releases/ink
- Google Ink — código e licença: https://github.com/google/ink
- libmypaint — código e licença: https://github.com/mypaint/libmypaint

As bibliotecas são opções a avaliar, não dependências automaticamente aprovadas. Confirmar APIs atuais, licença, dependências e compatibilidade Android antes de integrar.

---

# 29. Regra final do produto

**O Animation Studio deve ser um estúdio de animação 2D completo, não um protótipo.** A folha branca é o espaço de criação; o espaço exterior é escuro; as barras superior e de ferramentas são móveis, flutuantes e ocultáveis. Elas nunca devem tornar-se molduras fixas que roubem permanentemente área ao canvas. Os frames, camadas, cenários, ferramentas, reprodução, gravação e exportação devem operar sobre dados reais e persistentes. Uma funcionalidade só pode ser declarada pronta depois de implementada, testada e validada.


## Especificação técnica aprofundada do canvas e da câmara

Foi criado um documento complementar com os requisitos e a análise de referência para zoom, pan, rotação visual do canvas, transformação de coordenadas, arbitragem de gestos, câmara e pipeline de exportação, incluindo ligações ao código e à documentação oficial do OpenToonz:

- [Especificação técnica — Canvas, Zoom, Pan, Rotação e Câmara](docs/ESPECIFICACAO_CANVAS_ZOOM_PAN_ROTACAO_CAMERA.md)

Este documento é uma análise técnica de referência e não significa que estas funcionalidades já estejam implementadas. Qualquer reutilização de código OpenToonz exige auditoria do ficheiro, dependências e licença aplicável.
