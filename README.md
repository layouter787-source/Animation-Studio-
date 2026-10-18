# Animation Studio para Android
## Especificação funcional e técnica completa

> **Documento principal de requisitos do produto.** Esta especificação define a visão, os comportamentos esperados, a arquitetura e os critérios de aceitação do Animation Studio. As funcionalidades descritas são requisitos do produto; não significa que já estejam todas implementadas. O estado real de cada funcionalidade deve ser atualizado à medida que for construída e testada.

---

## 1. Visão do produto

O **Animation Studio** é um estúdio de desenho e animação 2D frame a frame, pensado prioritariamente para telemóveis e tablets Android. O fluxo de trabalho inspira-se em aplicações como RoughAnimator e FlipaClip, mas o produto terá implementação, identidade visual e evolução próprias.

A prioridade central é permitir que o artista tenha **o máximo de espaço útil para desenhar**, com controlos acessíveis e personalizáveis que não ocupem permanentemente uma faixa da área de trabalho.

### Princípios fundamentais

1. O espaço de trabalho pertence ao artista: as barras são móveis, flutuantes e ocultáveis.
2. A folha de desenho é **branca por padrão**, enquanto o espaço à volta dela é escuro.
3. Zoom, deslocamento e rotação da vista não alteram as dimensões nem a resolução real do projeto.
4. Os desenhos de cada frame são independentes e mantêm-se sincronizados com as camadas e a timeline.
5. Operações destrutivas devem poder ser desfeitas quando tecnicamente aplicável.
6. O projeto deve guardar e recuperar o trabalho sem depender de uma ligação à Internet.
7. A interface deve adaptar-se a telemóveis, tablets, orientação horizontal/vertical e diferentes tamanhos de ecrã.
8. A experiência deve ser fluida, sem prometer desempenho que não tenha sido medido em dispositivos reais.

---

## 2. A regra mais importante da interface: barras flutuantes

### 2.1 Área de trabalho de ecrã inteiro

A área do editor deve ocupar todo o espaço disponível da aplicação. A folha branca, as barras e os painéis são elementos apresentados **sobre** essa área de trabalho.

**Não reservar permanentemente uma coluna para a barra de ferramentas nem uma faixa para a barra superior.** Quando uma barra está flutuante, a área de desenho continua por baixo dela. O utilizador pode desenhar na parte visível da folha sem a interface reduzir o espaço do canvas.

Este comportamento é essencial: as barras não devem empurrar o canvas para o lado nem encolher a folha só por estarem visíveis.

### 2.2 Barra superior móvel

A barra superior contém os controlos principais do projeto, incluindo, conforme o modo e o espaço disponível:
- Menu do projeto.
- Duração/intervalo de desenho e controlos de frame.
- FPS de reprodução.
- Acesso à timeline, camadas e cenários.
- Controlos de câmara.
- Cor atual e acesso ao seletor de cores.
- Acesso às opções da ferramenta ou do projeto.

Comportamento obrigatório:
- Flutua sobre a área de trabalho.
- Pode ser arrastada para uma posição permitida pelo utilizador.
- Pode ser ocultada e chamada novamente.
- Não deve reduzir a área do canvas quando se move ou aparece.
- Deve evitar ficar permanentemente por cima de controlos importantes ou de uma zona onde o artista esteja a desenhar.
- A posição e o estado visível/oculto devem ser guardados nas preferências de interface.
- Deve respeitar margens seguras do Android e não ficar inacessível atrás de recortes do ecrã.

### 2.3 Barra de ferramentas móvel

A barra de ferramentas contém os instrumentos de desenho e edição, incluindo:
- Pincel.
- Borracha.
- Balde de tinta/preenchimento.
- Laço/seleção.
- Desfazer e refazer.
- Navegação entre frames.
- Adicionar frame.
- Reprodução e acesso à onion skin.
- Outros instrumentos à medida que forem implementados.

Comportamento obrigatório:
- É uma barra flutuante sobre a área de trabalho, não uma coluna fixa que rouba espaço ao canvas.
- Pode ser arrastada livremente entre posições permitidas no ecrã.
- Pode ser posicionada junto a qualquer margem ou numa zona conveniente do espaço de trabalho.
- Pode ser ocultada para deixar a área visual limpa e reaberta por um controlo/gesto previsível.
- Os ícones podem ser reorganizados pelo utilizador quando essa função estiver implementada.
- A seleção de um instrumento deve continuar ativa mesmo quando a barra é ocultada.
- A barra e os seus menus não devem capturar gestos destinados ao desenho quando o gesto começa fora dos seus limites.

### 2.4 Gestos e interação com barras

- **Arrastar a partir da pega da barra:** move a barra, sem desenhar.
- **Tocar num ícone:** seleciona/ativa a ferramenta ou abre o respetivo painel.
- **Premir prolongadamente um ícone:** abre opções adicionais ou um seletor de ferramentas, quando aplicável.
- **Ocultar:** recolhe a barra e deixa a área de trabalho livre.
- **Mostrar novamente:** através de um botão discreto ou gesto de reabertura documentado nas definições.
- O gesto de mover a barra deve ser distinguível do gesto de desenhar; não exigir arrastar um ícone de ferramenta para mover toda a barra.
- Não depender apenas de gestos escondidos: disponibilizar sempre uma forma descobrível de mostrar novamente as barras.

### 2.5 Modos de posicionamento e personalização

A implementação deve suportar:
- Barra superior flutuante, movível e ocultável.
- Barra de ferramentas flutuante, movível e ocultável.
- Reposição para a posição inicial.
- Posições guardadas separadamente para orientação horizontal e vertical, se necessário.
- Opção para reorganizar ferramentas e, quando possível, escolher quais aparecem diretamente e quais ficam num menu.
- Tamanhos de barra/ícones apropriados para toque e acessibilidade.

A posição inicial deve seguir os esboços fornecidos pelo autor. A personalização não deve alterar a organização base sem ação explícita do utilizador.

### 2.6 Referência de comportamento: FlipaClip

A referência funcional é a ideia de mover a barra de ferramentas pelo palco/área de desenho para libertar espaço e poder reorganizar os ícones. A página de apresentação da aplicação FlipaClip descreve mover a barra de ferramentas e reorganizar os ícones.

Referências:
- FlipaClip na Google Play: https://play.google.com/store/apps/details?id=com.vblast.flipaclip
- FlipaClip na App Store (descrição de ferramentas móveis): https://apps.apple.com/us/app/flipa-clip-draw-2d-animation/id1101848914
- Centro de suporte: https://support.flipaclip.com/

Estas referências servem para compreender padrões de interação. Não são autorização para copiar código, imagens, ícones proprietários ou a implementação interna de outra aplicação.

---

## 3. Folha branca, espaço de trabalho, zoom e navegação

### 3.1 Separação visual

- **Espaço de trabalho:** fundo cinzento-escuro/quase preto.
- **Folha/canvas do projeto:** branca por padrão, retangular e delimitada pelas dimensões do projeto.
- **Conteúdo artístico:** traços, imagens, fundos, camadas e outros elementos que pertençam ao projeto.
- A folha branca não deve ser confundida com todo o espaço de trabalho.

### 3.2 Zoom e deslocamento

- Zoom com gesto de pinça de dois dedos.
- Deslocamento da vista com gesto de dois dedos, ou ferramenta de mão dedicada.
- O zoom deve ampliar a folha visualmente; não deve aumentar a resolução real do projeto.
- O utilizador deve poder afastar a vista para ver a folha inteira e aproximar-se para desenhar detalhes.
- A navegação da vista não pode mover os desenhos dentro do projeto.
- Disponibilizar um comando para ajustar a folha à área visível.
- Mostrar opcionalmente o nível de zoom e a rotação da vista.
- A folha pode ultrapassar visualmente os limites do ecrã quando ampliada; isso é esperado.
- A área de desenho deve permanecer utilizável por baixo das barras flutuantes.

### 3.3 Resolução e formato

Ao criar um projeto, permitir definir:
- Nome.
- Largura e altura da folha em píxeis.
- FPS.
- Opcionalmente, uma predefinição de proporção (por exemplo, 16:9, 4:3, 1:1) que preencha as dimensões correspondentes.
- Margens de câmara, se forem utilizadas pelo modo de câmara.

A resolução do projeto é independente do zoom da interface. Alterar as dimensões da folha deve ser uma operação própria, com confirmação e tratamento cuidadoso do conteúdo existente.

---

## 4. Ecrã inicial e gestão de projetos

O ecrã inicial segue a referência fornecida:
- Lista de projetos à esquerda, com miniatura e nome.
- Área ampla à direita para criar um projeto novo.
- Ações para abrir projeto e criar novo projeto.
- Ordenação por nome e por data, quando disponível.

### Criar projeto

O diálogo de criação deve permitir:
- Nome do projeto.
- FPS inicial.
- Largura e altura da folha.
- Predefinições de tamanho/proporção.
- Confirmar ou cancelar.

Ao criar, iniciar com uma folha branca, uma camada de desenho inicial e um cenário/fundo inicial conforme o modelo do projeto.

### Persistência

- Guardar os projetos no armazenamento privado da aplicação.
- Guardar alterações automaticamente, sem exigir que o utilizador carregue sempre em Guardar.
- Mostrar um estado de gravação e apresentar erros de armazenamento de forma clara.
- Permitir abrir, renomear, duplicar e apagar projetos com confirmação para ações destrutivas.
- Recuperar alterações quando a aplicação fecha inesperadamente, sempre que possível.
- Não apresentar projetos fictícios como se fossem trabalho real do utilizador. Projetos de exemplo, se existirem, devem estar identificados como exemplos.

---

## 5. Editor e disposição inicial

A disposição inicial deve respeitar os esboços do autor:
- Barra superior flutuante.
- Barra de ferramentas flutuante.
- Timeline no topo do editor quando ativada.
- Camadas e cenários associados às respetivas faixas.
- Folha branca no centro do espaço de trabalho.
- Indicador persistente do frame atual e total de frames.
- Painéis contextuais de opções de ferramenta, cor, câmara e camadas, que podem ser abertos e fechados.

A disposição não deve ficar rígida: o utilizador deve poder mover/ocultar as barras. Os painéis contextuais podem sobrepor a área de trabalho, mas devem ter forma simples de fechar, e não devem cobrir permanentemente o centro da folha sem necessidade.

---

## 6. Ferramentas de desenho

### 6.1 Pincel

Requisitos:
- Desenho contínuo por toque ou stylus.
- Traços suaves e interpolação de pontos.
- Tamanho e opacidade configuráveis.
- Cor atual configurável.
- Suporte à pressão da caneta quando o dispositivo a disponibilizar.
- Suavização/estabilização configurável.
- Pré-visualização do tamanho do pincel, se viável.
- Diferentes presets de pincel através de um sistema extensível.

### 6.2 Borracha

- Apaga apenas o conteúdo editável da camada/frame selecionados, de acordo com a semântica do tipo de camada.
- Tamanho e opacidade configuráveis quando suportados.
- Deve respeitar a camada ativa.
- Deve suportar desfazer/refazer.

### 6.3 Balde de preenchimento

- Preenche regiões de acordo com tolerância configurável.
- Deve limitar a operação à camada ou composição definida nas opções da ferramenta.
- Deve evitar bloquear a interface durante preenchimentos grandes.
- Deve permitir desfazer o resultado.
- Erros ou regiões inválidas não podem destruir o conteúdo existente.

### 6.4 Laço/seleção

- Selecionar uma área ou conteúdo.
- Mover, transformar, copiar, cortar ou apagar a seleção quando suportado.
- Mostrar controlos de transformação claros.
- Cancelar a seleção sem alterar o desenho.
- Respeitar a camada e o frame ativos.

### 6.5 Seletor de cor e paletas

- Seletor RGB e HSB.
- Confirmação/cancelamento da cor.
- Amostra da cor atual na interface.
- Histórico de cores recentes.
- Paletas do projeto, cores favoritas e capacidade de voltar a usar cores guardadas.
- A cor selecionada deve ser partilhada pelas ferramentas que dela dependem.

### 6.6 Arrastar a cor para preencher

Como interação adicional:
- O utilizador pode arrastar a amostra de cor para uma região fechada da folha para tentar preenchê-la.
- Esta interação utiliza o mesmo motor de preenchimento do balde.
- Deve haver feedback visual sobre a região-alvo.
- Deve ser possível cancelar antes de confirmar quando aplicável.
- Se não houver uma região válida, não alterar o desenho.
- O gesto não deve entrar em conflito com desenhar, mover a vista ou selecionar.

### 6.7 Desfazer e refazer

- Desfazer/refazer operações de desenho e edição.
- Quando possível, incluir operações de frame, camada, seleção e transformação.
- O histórico deve pertencer ao projeto e ser coerente com as operações feitas.
- Não mostrar botões ativos quando não há operação disponível para desfazer/refazer.

---

## 7. Frames e timeline de animação

### 7.1 Independência dos frames

Cada frame deve conservar o seu próprio conteúdo. Mudar de frame não pode apagar, substituir ou misturar silenciosamente o desenho de outro frame.

A representação interna deve ligar cada frame ao conteúdo das camadas e cenários aplicáveis. A timeline é uma vista e um controlador desse estado, não um conjunto de miniaturas desligadas dos dados reais.

### 7.2 Timeline

- A timeline pode ser mostrada/ocultada.
- A timeline deve permitir selecionar um frame.
- Deve mostrar a sequência e o frame ativo.
- Deve incluir faixas de camadas e cenários conforme o modelo do projeto.
- Deve permitir adicionar frames.
- Deve suportar deslocamento horizontal quando a sequência não cabe no ecrã.
- A interface deve adaptar a quantidade de células visíveis ao tamanho do dispositivo.
- O frame atual e a contagem total devem permanecer visíveis mesmo quando a timeline está recolhida.

### 7.3 Operações de frame

Planeadas:
- Adicionar frame em branco.
- Duplicar/copiar frame.
- Colar à esquerda ou à direita como inserção, deslocando os frames seguintes.
- Mover frame.
- Apagar frame com possibilidade de desfazer.
- Selecionar vários frames.
- Copiar/duplicar/mover/apagar em lote.
- Ajustar duração de desenho ou intervalo de frames, conforme o modelo temporal adotado.
- Inverter uma sequência de frames quando implementado.

As operações de inserção não devem sobrescrever frames existentes sem aviso. Operações em lote devem ser consistentes entre camadas e cenários.

### 7.4 Frame Viewer

Uma vista alternativa para gerir frames:
- Grelha ou lista de miniaturas.
- Seleção individual e múltipla.
- Copiar, duplicar, mover e apagar.
- Pré-visualização.
- Navegação para o frame selecionado.

O Frame Viewer e a timeline devem usar o mesmo estado de projeto; alterações feitas numa vista devem aparecer imediatamente na outra.

### 7.5 FPS e duração

- FPS define a velocidade de reprodução/exportação, não o número total de frames.
- Controlos numéricos permitem ajustar o FPS.
- Validar valores dentro de limites definidos pela aplicação.
- A duração final deriva da sequência, das durações dos desenhos e do FPS, segundo o modelo temporal implementado.

### 7.6 Modos de reprodução

Modos planeados:
- **Uma vez (ONCE):** reproduzir do início ao fim e parar.
- **Loop:** repetir do início após o fim.
- **Ping-pong:** reproduzir para a frente e depois para trás, sem duplicar desnecessariamente os frames dos extremos.

---

## 8. Onion skin

- Mostrar versões não editáveis dos frames anteriores e/ou seguintes enquanto o artista desenha no frame atual.
- Definir quantos frames anteriores/seguintes aparecem.
- Ajustar a opacidade.
- Diferenciar visualmente frames anteriores e seguintes.
- O onion skin é apenas uma ajuda visual: não altera os desenhos e não é exportado como parte da animação.
- A alteração de frame deve atualizar a visualização da onion skin.
- A onion skin deve poder ser ativada/desativada rapidamente.

---

## 9. Camadas e cenários

### 9.1 Camadas

- Criar, selecionar, renomear e apagar camadas.
- Alterar ordem, visibilidade e opacidade.
- Identificar claramente a camada ativa.
- Impedir edição acidental de camadas bloqueadas, caso exista bloqueio.
- Manter a relação correta entre camadas, frames e histórico.

### 9.2 Cenários/fundos

O cenário/fundo é conceptualmente separado das camadas de animação.

Modos pretendidos:
1. **Fundo partilhado:** o mesmo cenário é reutilizado em vários frames; editar a fonte partilhada atualiza todas as instâncias que a referenciam.
2. **Fundo animado:** o conteúdo de fundo pode mudar frame a frame.

A interface deve deixar claro qual modo está ativo. Editar o fundo não pode apagar acidentalmente a animação principal. A visibilidade e a opacidade devem ser controláveis.

---

## 10. Biblioteca de substituições

Biblioteca planeada para elementos reutilizáveis de animação, como:
- Bocas para diálogo.
- Olhos e expressões.
- Poses e partes de personagens.
- Variações de desenho.

Requisitos:
- Miniaturas e nomes.
- Inserir/substituir uma variante num frame ou intervalo.
- Preservar as alternativas da biblioteca.
- Não substituir silenciosamente desenhos fora do intervalo escolhido.
- Integrar com o estado do projeto e o histórico quando possível.

---

## 11. Câmara e enquadramento

A câmara define o enquadramento final da animação, independentemente da navegação do editor.

- Mostrar um retângulo de enquadramento.
- Definir dimensões/proporção de saída.
- Ajustar posição, escala e rotação quando suportado.
- Permitir pré-visualizar o resultado enquadrado.
- Exportar com base no enquadramento da câmara, não na posição atual da interface.
- Aumentar o zoom de trabalho não deve alterar o enquadramento exportado.
- Animação de propriedades da câmara por keyframes é uma funcionalidade futura.

---

## 12. Animação por keyframes

Funcionalidade planeada para animar propriedades ao longo do tempo:
- Posição X/Y.
- Escala X/Y.
- Rotação.
- Opacidade.
- Posteriormente, propriedades de câmara e filtros quando suportadas.

Requisitos:
- Criar, selecionar, copiar, mover e apagar keyframes.
- Interpolação entre valores.
- Modos de easing/interpolação quando implementados.
- Pré-visualização antes da exportação.
- Separar claramente keyframes de transformação da animação desenhada frame a frame.

---

## 13. Importação e exportação

### 13.1 Importação

Planeada:
- Importar imagem como referência, elemento de desenho ou conteúdo de cenário.
- Importar vídeo como referência para rotoscopia.
- Extrair frames de vídeo com intervalo/frequência configurável, sujeito às capacidades do dispositivo e aos limites de memória.
- Importar um projeto próprio quando o formato de projeto estiver definido.

A importação deve apresentar progresso e erros úteis, e não bloquear indefinidamente a interface.

### 13.2 Exportação

Formatos planeados:
- MP4.
- GIF, se a biblioteca e o desempenho permitirem.
- PNG do frame atual ou de frames selecionados.
- Sequência de PNG numerados.
- ZIP com frames exportados, quando implementado.

A exportação deve:
- Usar o FPS e o enquadramento selecionados.
- Mostrar progresso.
- Permitir cancelar quando tecnicamente seguro.
- Tratar falta de espaço e falhas de codificação.
- Guardar ficheiros em local escolhido/autorizado pelo utilizador.
- Não incluir barras, painéis, onion skin ou indicadores do editor no resultado final.
- Exportar apenas o conteúdo pretendido do projeto.

### 13.3 Áudio

Importação e sincronização de áudio são uma possibilidade futura e não devem ser consideradas implementadas até existirem faixa temporal, reprodução sincronizada e exportação testadas.

---

## 14. Arquitetura técnica

### Camada Android/UI
- Kotlin.
- Jetpack Compose.
- Material 3.
- Gestão de navegação, diálogos, painéis, preferências e acessibilidade.
- UI separada da lógica do motor de desenho.

### Motor nativo
- C++ com Android NDK e CMake para operações que beneficiem de implementação nativa.
- JNI para comunicação com Kotlin.
- Agrupar operações para evitar chamadas JNI excessivas por ponto de traço.
- Definir uma API clara e testável entre UI e motor.
- Não transferir grandes bitmaps entre Kotlin e C++ repetidamente sem necessidade.

### GPU
- OpenGL ES como primeiro backend gráfico, após validar os requisitos reais do motor.
- Vulkan pode ser avaliado posteriormente e deve ser usado apenas quando houver benefício demonstrado.
- Deteção de capacidades e fallback para dispositivos sem suporte adequado.
- Não presumir que Vulkan será sempre mais rápido ou consumirá menos bateria.
- CPU/GPU devem ter comportamento funcional equivalente para as operações essenciais.

### Persistência e memória
- Formato de projeto versionado.
- Guardar desenhos, frames, camadas, cenários, FPS, dimensões, paletas, opções de ferramenta e preferências de projeto.
- Não carregar todos os frames em memória simultaneamente quando isso for desnecessário.
- Criar miniaturas e usar cache com limites.
- Guardar de forma incremental quando possível.
- Recuperação após encerramento inesperado.

### Testes
- Testes unitários para operações de frames, dimensões, histórico e serialização.
- Testes de integração para o motor nativo e a ponte JNI.
- Testes de UI para criar/abrir projeto e navegar entre frames.
- Testes manuais em dispositivos Android reais, incluindo telemóvel e tablet, quando disponíveis.
- Medir desempenho e consumo de memória em projetos pequenos e grandes.

---

## 15. Requisitos não funcionais

- **Offline-first:** desenhar e animar sem conta nem Internet.
- **Responsividade:** não bloquear a interface durante operações pesadas.
- **Fiabilidade:** autosave, recuperação e validação dos dados do projeto.
- **Desempenho:** traços responsivos, zoom fluido e reprodução adequada ao dispositivo; medir em vez de prometer.
- **Memória:** suportar projetos grandes com carregamento por necessidade e cache limitada.
- **Acessibilidade:** áreas de toque suficientes, descrições dos ícones e contraste legível.
- **Orientação:** suportar retrato e paisagem, adaptando a posição inicial das barras sem retirar a opção de as mover.
- **Privacidade:** projetos locais por padrão; não enviar desenhos para servidores sem uma funcionalidade explícita e consentimento.
- **Compatibilidade:** definir e testar a versão mínima de Android e as arquiteturas suportadas na configuração do Gradle.
- **Identidade:** interface escura discreta, sem gradientes neon, respeitando os esboços fornecidos.

---

## 16. Fora do âmbito da primeira versão

Não considerar como concluído até existir implementação e validação:
- Motor de pincéis completo com texturas profissionais.
- Vulkan.
- Keyframes completos.
- Biblioteca de substituições.
- Importação avançada de vídeo.
- Exportação MP4/GIF robusta em todas as resoluções.
- Áudio sincronizado.
- Sincronização na nuvem.
- Colaboração em tempo real.

Estas funcionalidades podem ser desenvolvidas em fases, mas não devem ser representadas como prontas antes dos testes.

---

## 17. Fases de desenvolvimento

### Fase 1 — Base compilável
- Projeto Android, Compose, NDK/CMake e JNI.
- Ecrã inicial e criação de projeto.
- Editor de base com folha branca.
- Workflow de compilação na nuvem.

### Fase 2 — Área de trabalho e barras flutuantes
- Área de trabalho de ecrã inteiro.
- Barra superior e barra de ferramentas arrastáveis.
- Ocultar/mostrar sem reduzir a área do canvas.
- Guardar posições por orientação.
- Zoom e deslocamento da folha sem alterar a resolução.
- Resolver conflitos entre gestos de desenho, navegação e movimento das barras.

### Fase 3 — Desenho real
- Motor de traços.
- Pincel, borracha, cor e tamanho.
- Desfazer/refazer.
- Limites de desenho e conversão correta entre coordenadas do ecrã e coordenadas da folha.

### Fase 4 — Projeto e frames persistentes
- Guardar e reabrir projetos.
- Frames independentes.
- Camadas e cenários iniciais.
- Timeline sincronizada com os dados reais.

### Fase 5 — Animação
- Onion skin.
- Reprodução e FPS.
- Operações de frame e Frame Viewer.

### Fase 6 — Ferramentas avançadas
- Balde, laço, paletas, biblioteca de pincéis.
- Importação de imagem e referência de vídeo.
- Câmara e enquadramento.

### Fase 7 — Exportação e otimização
- Exportação de imagens e vídeo.
- Testes de projetos grandes.
- Perfil de CPU/GPU/memória e bateria.
- Avaliação fundamentada de OpenGL ES e, se justificado, Vulkan.

---

## 18. Critérios de aceitação prioritários

### Barras flutuantes
- [ ] O canvas ocupa toda a área de trabalho disponível.
- [ ] Mostrar a barra superior não encolhe nem desloca a folha.
- [ ] Mostrar a barra de ferramentas não encolhe nem desloca a folha.
- [ ] Ambas as barras podem ser arrastadas.
- [ ] Ambas podem ser ocultadas e reabertas sem perder a ferramenta ativa.
- [ ] As posições são preservadas ao reabrir o projeto, conforme as preferências.
- [ ] A interface adapta-se a retrato e paisagem.
- [ ] Mover uma barra não deixa uma área vazia reservada no layout.

### Folha e desenho
- [ ] A folha é branca por padrão e o espaço exterior é escuro.
- [ ] Pinçar para ampliar/reduzir não muda a resolução do projeto.
- [ ] O deslocamento move apenas a vista.
- [ ] O desenho aparece nas coordenadas corretas da folha após zoom e deslocamento.
- [ ] A barra flutuante não começa um traço por acidente.

### Frames e projetos
- [ ] Um frame conserva o desenho quando se navega para outro frame e se regressa.
- [ ] Cada frame tem conteúdo independente.
- [ ] Guardar e reabrir preserva frames, camadas, dimensões e FPS.
- [ ] A timeline e o Frame Viewer, quando disponíveis, mostram o mesmo estado.
- [ ] A exportação não inclui controlos da interface nem onion skin.

---

## 19. Estado real do repositório

O repositório contém uma base inicial Android com Kotlin/Compose, configuração nativa mínima e GitHub Actions para compilar um APK de depuração. A compilação inicial foi executada com sucesso.

**A compilação bem-sucedida não significa que o estúdio completo esteja pronto.** O editor funcional, o armazenamento real, os frames independentes, a gestão de gestos das barras flutuantes, o motor de desenho, a reprodução e a exportação devem ser implementados e testados. Esta especificação é o contrato de produto para orientar esse trabalho.

---

## 20. Referências úteis

- Repositório Animation Studio: https://github.com/layouter787-source/Animation-Studio-
- FlipaClip — Google Play: https://play.google.com/store/apps/details?id=com.vblast.flipaclip
- FlipaClip — suporte: https://support.flipaclip.com/
- Android NDK: https://developer.android.com/ndk
- JNI e desempenho: https://developer.android.com/training/articles/perf-jni
- Jetpack Compose: https://developer.android.com/compose

---

**Regra final:** dar prioridade à liberdade de desenho. A folha branca é o espaço de criação; as barras superior e de ferramentas são controlos flutuantes, móveis e ocultáveis, e nunca devem transformar-se em molduras fixas que roubem permanentemente área ao canvas.
