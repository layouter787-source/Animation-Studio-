# MISSÃO PRINCIPAL — ANIMATION STUDIO
## Superinstrução para implementação integral de um estúdio profissional de animação 2D nativo para Android

**Repositório autorizado:** https://github.com/layouter787-source/Animation-Studio-  
**Destinatário:** Claude com acesso ao GitHub.  
**Objetivo:** implementar no repositório um aplicativo Android real, funcional, testável e compilável, respeitando a especificação existente e produzindo um APK verificável.

---

## 1. Ordem principal

Não entregues apenas um plano, protótipo, maquete, MVP, estrutura vazia ou interface demonstrativa. Trabalha diretamente no repositório autorizado: inspeciona o código, implementa funcionalidades reais, executa testes, compila o APK e corrige os problemas que encontrares, dentro das capacidades reais do ambiente.

Não declares uma funcionalidade concluída só porque existe um botão, uma classe, uma tela, um modelo de dados ou um método vazio. Uma funcionalidade deve ter comportamento real e evidência de validação.

Divide o trabalho em alterações coerentes quando necessário, mas continua para além do plano inicial. Não substituas a implementação por instruções para o utilizador fazer manualmente.

## 2. Limites do repositório e preservação

Trabalha **exclusivamente** em `layouter787-source/Animation-Studio-`. Não alteres outros repositórios, projetos ou contas.

Antes de editar:
1. Inspeciona a estrutura, branch, commits recentes e estado atual.
2. Lê o `README.md` e integralmente `docs/ESPECIFICACAO_CANVAS_ZOOM_PAN_ROTACAO_CAMERA.md`.
3. Lê os restantes documentos de requisitos, arquitetura, design, testes e workflows.
4. Inspeciona código, Gradle, dependências, recursos, testes e GitHub Actions.
5. Identifica o que está implementado, incompleto, ausente ou defeituoso.
6. Preserva o trabalho válido e não substituas ficheiros sem compreender a sua finalidade.
7. Não exponhas tokens, credenciais, chaves privadas ou segredos em código, logs ou commits.

Cria um inventário inicial e usa-o para orientar o trabalho. Se encontrares contradições nos requisitos, investiga-as, preserva a intenção funcional e documenta as decisões.

## 3. Rastreabilidade dos requisitos

Cria ou atualiza um documento de rastreabilidade com identificador, comportamento esperado, componentes/ficheiros envolvidos, testes, evidências, limitações e estado real. Usa os estados `PENDENTE`, `EM IMPLEMENTAÇÃO`, `IMPLEMENTADO`, `TESTADO` e `BLOQUEADO`.

Só usa `TESTADO` quando houver evidência apropriada. Um botão visível não prova que uma funcionalidade funciona. Não escondas tarefas por concluir em classes vazias, `TODO`, ações sem efeito ou dados simulados.

## 4. Objetivo do produto

Criar um estúdio de animação 2D profissional para Android, orientado a desenho e animação quadro a quadro. Deve permitir criar, editar, organizar, guardar, reproduzir, importar e exportar projetos reais. Não é apenas uma aplicação de desenho com timeline decorativa.

A experiência deve ser coerente, adaptada a toque, estável e utilizável em dispositivos Android reais, incluindo hardware com recursos limitados. Não prometas desempenho, compatibilidade ou funcionalidades que não tenham sido verificadas.

## 5. Tecnologias e arquitetura

Inspeciona a configuração existente e preserva escolhas válidas. Como base, usa Kotlin, Jetpack Compose, Material 3 e ferramentas Android compatíveis. Mantém `minSdk 28` se for compatível com os requisitos e dependências reais.

Separa responsabilidades: interface, domínio, modelos de projeto, canvas/viewport, motor de desenho, timeline, reprodução, armazenamento, importação/exportação e testes. A estrutura de módulos deve refletir código real; não cries módulos vazios só para corresponder a uma lista.

Avalia C++/Android NDK para rasterização, composição, traços, preenchimento ou renderização intensiva apenas se a vantagem justificar a complexidade. Se usares JNI, agrupa chamadas, limita cópias de buffers, define propriedade/ciclo de vida da memória e trata erros corretamente. Não deixes exceções atravessarem JNI sem tratamento.

Avalia OpenGL ES se fizer sentido. Usa Vulkan apenas com justificação técnica, compatibilidade e medições; não presumas que Vulkan ou C++ são sempre mais rápidos. Mede antes de otimizar.

## 6. Ecrã inicial e projetos

Implementa o ecrã inicial conforme os documentos e referências: projetos com miniaturas, nomes e informação útil, mais uma área clara para criar projeto. Adapta a ecrãs pequenos/grandes e orientações vertical/horizontal.

Implementa criar, abrir, renomear, duplicar e eliminar projetos (com confirmação); atualizar miniaturas; recuperar após encerramento inesperado; tratar ficheiros inválidos/corrompidos com mensagens claras; e garantir que eliminar um projeto não apaga recursos de outros projetos.

O diálogo de novo projeto deve incluir nome, FPS, dimensões da câmara, proporção, margens/enquadramento quando aplicáveis e pré-visualização da área. Valida valores e limites de memória. A resolução de saída não pode depender do zoom do editor.

## 7. Editor e linguagem visual

Preserva a disposição e os desenhos de referência existentes. O espaço de trabalho deve ser cinzento-escuro/azul-acinzentado e a folha de desenho **branca**, delimitada contra o espaço exterior escuro. Não substituas a folha por um canvas preto nem redesenhes radicalmente a interface sem necessidade.

Integra canvas, ferramentas, seletor de cor, controlos de pincel, timeline, camadas, fundos/cenários, câmara, frames, onion skin, reprodução, undo/redo, menu, importação/exportação e indicador do frame atual/total.

Cada controlo visível deve funcionar de verdade ou estar claramente indisponível por uma limitação real. Não uses emojis como substitutos de ícones profissionais. Evita néon, gradientes intensos, desfoques decorativos e controlos que desperdicem espaço.

## 8. Canvas: coordenadas, zoom, pan, rotação e câmara

Lê primeiro a especificação técnica `docs/ESPECIFICACAO_CANVAS_ZOOM_PAN_ROTACAO_CAMERA.md`. Implementa um modelo de viewport consistente, não transformações improvisadas de elementos Compose.

Distingue coordenadas do projeto, folha, ecrã, câmara e motor gráfico. Implementa transformações diretas e inversas testáveis para que o ponto tocado no ecrã corresponda ao ponto certo no projeto após zoom, pan, rotação, escala e mudança de orientação.

### Zoom
- Pinça para ampliar/reduzir.
- Zoom em torno do ponto focal.
- Limites e comportamento previsível nos extremos.
- Redesenho sem deslocamento indevido dos traços.
- Não altera a resolução real nem a saída exportada.

### Pan
- Deslocamento independente do conteúdo artístico.
- Respeita as zonas de ferramentas e barras.
- Não pode ser confundido com desenho nem criar traços acidentais.

### Rotação
Distingue rotação visual da vista, rotação de objeto, rotação de camada (se suportada) e rotação da câmara. Rodar a vista não pode modificar os dados artísticos nem a orientação final exportada. Implementa reposição do enquadramento/rotação conforme a especificação.

### Câmara
A câmara define o enquadramento e a resolução de saída. Implementa os controlos previstos, incluindo X/Y, rotação e escala quando aplicáveis. Mantém a câmara independente do zoom/pan/rotação visual. Renderiza a exportação a partir dos dados do projeto e da câmara, nunca de uma captura de ecrã do editor.

### Gestos
Define prioridades claras entre desenho, borracha, pinça, pan com dois dedos, rotação, seleção, arrastamento de barras, botões e edição de frames. Gestos iniciados em controlos não podem desenhar no canvas. Testa diferentes densidades, orientações, zooms e rotações.

## 9. Barras flutuantes

As barras devem maximizar a área útil de desenho. Permite arrastar, reposicionar, recolher e ocultar; quando especificado, posicionar em cima, em baixo, à esquerda ou à direita.

Arrastar uma barra não pode desenhar nem mover a folha. Os botões devem continuar utilizáveis. Não reserves permanentemente grandes áreas vazias. Persiste as posições/preferências, adapta-as à orientação e impede que os controlos fiquem fora do ecrã. Quando as barras forem ocultadas, deve existir uma forma clara de as recuperar. Testa a interação com desenho, zoom, pan e rotação.

## 10. Motor de desenho

Implementa um motor real: os traços pertencem ao frame e à camada apropriados, persistem e não desaparecem ao mudar de frame. Suporta início/atualização/fim do traço, coordenadas corretas, suavização, largura, opacidade, fluxo, espaçamento, rotação aleatória, textura quando suportada, undo/redo, cancelamento de traço incompleto e atualização da composição.

Suporta pressão da caneta apenas quando o hardware/API fornece essa informação; oferece fallback por toque. Não bloqueies a thread principal com operações pesadas.

Define abstrações para motor e presets de pincéis. Investiga Google Ink e libmypaint se relevantes, mas verifica compatibilidade Android, dependências, limitações e licença de cada componente/recurso. Não assumes que uma biblioteca de traços seja um motor de pintura completo.

## 11. Ferramentas

Implementa as ferramentas previstas: pincel, borracha, preenchimento, seleção por laço, undo/redo, navegação e criação de frames, onion skin e ferramentas auxiliares especificadas.

### Pincéis
Seleção, categorias, recentes e definições reais de tamanho, opacidade, fluxo, espaçamento, suavização, pressão, rotação aleatória e textura/forma da ponta quando suportadas. Nenhum controlo pode ser decorativo sem efeito.

### Borracha
Modifica conteúdo real, respeita frame/camada ativos e permite undo/redo. Não apaga conteúdo de outras camadas ou frames silenciosamente.

### Preenchimento
Motor real com tolerância configurável, limites da folha, regiões fechadas, tratamento definido para contornos imperfeitos, pré-visualização quando viável, cancelamento, undo/redo e processamento seguro de imagens grandes. Arrastar uma cor para uma região fechada pode preencher conforme a especificação; o balde usa lógica compatível.

### Laço
Seleciona efetivamente a região. Se permitir transformar conteúdo, respeita limites, frame/camada e undo/redo. Uma linha tracejada sem seleção real não conta como implementação.

## 12. Cor e paletas

Implementa modos Paleta, RGB e HSB, confirmar/cancelar, cor atual, favoritos, histórico, cores recentes e persistência de paletas. Valida valores e garante que a cor escolhida afeta pincéis, preenchimento e ferramentas compatíveis.

## 13. Frames e timeline

A timeline é central. Implementa uma sequência editável e persistente, sincronizada com canvas, camadas, reprodução, visualizador e projeto. Cada frame tem identidade e conteúdo; não uses uma única variável global para todos.

Implementa frame atual, anterior/seguinte, frame vazio, duplicar, copiar/colar, inserir à esquerda/direita, seleção simples/múltipla, remover, reordenar, duração/temporização, miniaturas e indicador de frame atual/total.

O menu de pressão prolongada inclui copiar, inserir frame vazio à esquerda/direita e colar à esquerda/direita. Inserir desloca os frames posteriores; nunca substitui silenciosamente um frame existente. Define o comportamento quando não há frame copiado. Operações devem ser reversíveis quando apropriado.

## 14. Camadas

Cada camada deve ter nome, ordem, visibilidade, opacidade, conteúdo, estado ativo, associação a frames e miniatura conforme aplicável. Implementa criar, renomear, selecionar, mostrar/ocultar, ajustar opacidade, reordenar e eliminar com confirmação. Define corretamente a relação entre frames, camadas, fundos e cenas; mudar de frame não pode destruir conteúdo.

## 15. Fundos e cenários

Mantém fundos/cenários separados das camadas de animação quando necessário. Suporta fundos partilhados por vários frames e fundos que variam ao longo da animação. Editar um fundo partilhado deve atualizar todos os frames que o referenciam sem apagar os desenhos da animação. Implementa visibilidade, edição, organização e persistência.

## 16. Onion skin, Flipbook e visualizador de frames

### Onion skin
Ativar/desativar, número de frames anteriores/posteriores, opacidade, diferenciação visual e atualização ao navegar. Onion skin é referência não editável e nunca entra na exportação final.

### Flipbook
Modo de desenho focado com frame atual destacado, anterior/seguinte, gesto de deslizar, adicionar, copiar, duplicar, limpar, onion skin e expandir/recolher. Usa o mesmo modelo de dados da timeline e do visualizador.

### Visualizador e lote
Grelha/lista de miniaturas, seleção simples/múltipla, copiar, colar, duplicar, mover, eliminar, navegar e pré-visualizar. Mantém todos os modos sincronizados e testa operações em lotes e inserções que alterem índices.

## 17. Biblioteca de substituições

Implementa a biblioteca prevista para variantes de bocas, olhos, expressões, poses e outros desenhos. Organiza elementos, mostra miniaturas, permite selecionar variantes e associá-las a frames/intervalos. Preserva originais e evita substituição irreversível sem autorização.

## 18. Câmara, keyframes e interpolação

Implementa as propriedades previstas: X/Y, escala X/Y, rotação, opacidade, câmara e outras especificadas. Define valores iniciais, ordenação temporal, seleção, edição e interpolação determinística e testável. Não mistures a transformação da vista com as transformações de objetos/câmara na saída.

## 19. Reprodução

Implementa reprodução real nos modos `ONCE`, `LOOP` e `PING_PONG`, respeitando FPS e duração. A reprodução avança frames corretamente, termina ou repete conforme o modo, inicia/para pelo utilizador, atualiza a vista, evita tarefas/temporizadores acumulados, liberta recursos ao sair do editor e não bloqueia a interface. Se o dispositivo não sustentar a velocidade, trata a situação sem corromper o projeto.

## 20. Importação

Implementa importação real dos formatos previstos: imagens, áudio, vídeo, projetos, referências visuais e recursos de fundo/cenário. Define o destino de cada recurso. Para vídeo de referência, avalia extração de frames e armazenamento. Valida formatos, dimensões, permissões e ficheiros inválidos. Não guardes apenas o nome do ficheiro fingindo que o importaste. Não carregues ficheiros arbitrariamente grandes todos de uma vez na memória.

## 21. Exportação

Implementa, conforme os requisitos, MP4, PNG de um frame, sequência de PNG numerados e ZIP com frames selecionados/todos.

A exportação deve usar dimensões da câmara, FPS, composição e ordem corretas; excluir interface, barras, seleções e onion skin; não usar captura de ecrã; gerir progresso, cancelamento, erros e recursos; e validar o ficheiro final.

Para MP4, verifica o pipeline real de codificação, incluindo MediaCodec quando apropriado. Não declares MP4 funcional sem produzir um ficheiro e verificar que pode ser aberto. Não inventes resultados. A arquitetura deve permitir exportar sem depender de uma captura da interface.

## 22. Armazenamento, autosave e recuperação

Define um formato de projeto versionado e documentado, contendo metadados, dimensões, FPS, frames, camadas, traços/imagens, cores, fundos, câmara, keyframes, referências importadas e preferências necessárias.

Implementa guardar, autosave, recuperação após encerramento inesperado, migração de versões, deteção de ficheiros inválidos, proteção contra escrita parcial, gestão de espaço e limpeza segura de temporários. O autosave não pode bloquear o desenho nem criar cópias ilimitadas. Testa fechar/reabrir e recuperação após falha.

## 23. Desempenho e memória

Mede antes de otimizar. Considera miniaturas reduzidas, carregamento progressivo, renderização incremental, cache de composição, libertação de recursos, reutilização de buffers, limites de memória, operações pesadas fora da thread principal, cancelamento, exportação em blocos e redução de cópias/recomposições. Define limites e mensagens úteis para projetos demasiado grandes. Não prometas desempenho sem medições.

## 24. UX, acessibilidade e robustez

Adapta a orientação, densidade e tamanho do ecrã; evita controlos cortados/sobrepostos; preserva o estado ao abrir/fechar painéis; oferece mensagens de erro úteis, confirmação para operações destrutivas, áreas táteis e contraste adequados. A interface deve priorizar o espaço de desenho.

## 25. Undo/redo

Implementa histórico coerente para traços, borracha, preenchimento, frames, camadas, transformações, conteúdo e operações em lote quando aplicável. Define operações reversíveis e ciclo de vida dos recursos. Undo/redo não pode afetar elementos alheios à operação. Testa várias operações encadeadas.

## 26. Segurança e integridade

Trata ficheiros importados como entradas não confiáveis. Valida formatos, tamanhos, dimensões e estruturas. Evita permissões excessivas, caminhos inseguros e dependências desnecessárias. Não envies projetos privados a serviços externos sem funcionalidade explicitamente autorizada. Uma falha de importação/exportação/gravação não pode corromper o projeto.

## 27. OpenToonz e licenças

OpenToonz é uma referência técnica, não autorização para copiar indiscriminadamente. Consulta os ficheiros relevantes de SceneViewer, eventos, ferramentas, câmara e objetos de cena quando necessário. Antes de reutilizar código ou recursos:
1. Identifica ficheiro e commit de origem.
2. Lê a licença aplicável ao ficheiro.
3. Verifica dependências e licenças de terceiros.
4. Confirma compatibilidade com Android.
5. Preserva avisos/notificações obrigatórios.
6. Regista origem e licença no projeto.
7. Prefere uma implementação independente do comportamento quando apropriado.

Não assumes que código Qt pode ser integrado diretamente em Kotlin/Compose Android. Aplica o mesmo cuidado a bibliotecas de pincéis, texturas, ícones e fontes.

## 28. Testes obrigatórios

Cria e executa testes reais.

### Unitários
Modelos, criação/cópia/inserção de frames, camadas, persistência/migração, conversão de coordenadas, zoom focal, pan, rotação, transformações inversas, câmara, interpolação, temporização, undo/redo e recursos.

### Integração
Criar/guardar/fechar/reabrir projeto; desenhar e mudar frame; editar camadas sem perder conteúdo; alterar fundo partilhado; onion skin; reprodução; importação; exportação; recuperação após falha simulada; sincronização timeline/Flipbook.

### Interface
Criar projeto, desenhar, alterar cor, apagar, adicionar frames, criar camadas, mover/ocultar barras, zoom/pan/rotação, reproduzir, guardar/reabrir e exportar.

### Motor gráfico
Traços contínuos, coordenadas corretas, ausência de desenho acidental ao mover barras, sem artefactos após transformações, composição correta, exportação independente da interface, libertação de recursos e comportamento dentro dos limites definidos.

Regista comandos executados, resultados e falhas. Não inventes resultados nem declares que testes de dispositivo foram executados se não o foram.

## 29. GitHub Actions e APK

Inspeciona os workflows existentes e configura/corrige CI para preparar JDK, Android SDK/NDK quando necessário, resolver dependências, executar verificações e testes, compilar APK de debug e publicar o APK como artefacto. Executa testes instrumentados quando houver emulador disponível.

Não guardes segredos no workflow. Não declares sucesso sem verificar o job real. Se não puderes testar num dispositivo físico ou executar testes instrumentados, indica isso claramente. Não afirmes que o APK está pronto para publicação na Play Store sem cumprir os requisitos de release.

## 30. Processo de trabalho

Segue continuamente:
**Inspecionar → Priorizar → Implementar → Testar → Compilar → Corrigir → Verificar.**

Sequência sugerida, adaptável ao estado real:
1. Auditoria e rastreabilidade.
2. Build e base da aplicação.
3. Modelos e persistência.
4. Canvas e transformações.
5. Motor de desenho.
6. Frames, camadas e timeline.
7. Undo/redo.
8. Ferramentas e pincéis.
9. Fundos e onion skin.
10. Reprodução.
11. Câmara e keyframes.
12. Importação/exportação.
13. Gestão de projetos.
14. Desempenho e robustez.
15. Testes e CI/APK.

Esta sequência não autoriza abandonar as funcionalidades posteriores. Continua até concluir os requisitos ou identificar um bloqueio genuíno. Se uma funcionalidade for grande, divide-a em partes funcionais e verificáveis.

## 31. Commits e alterações

Agrupa alterações relacionadas em commits lógicos quando possível. Não apagues histórico, não faças force-push e não substituas dependências/configurações sem avaliar o impacto. Verifica o resultado de cada alteração no repositório. Se uma permissão impedir uma operação, informa o impedimento exato e continua o trabalho possível.

## 32. Critérios de conclusão

Só considera o trabalho concluído quando houver evidência de que:
- O aplicativo compila.
- O APK foi produzido e está acessível.
- A aplicação abre num ambiente Android compatível.
- É possível criar, desenhar, guardar e reabrir um projeto.
- Frames e camadas preservam o conteúdo.
- Canvas implementa corretamente as transformações declaradas.
- Barras não geram desenhos acidentais.
- Reprodução respeita a temporização.
- Exportações declaradas como suportadas produzem ficheiros válidos.
- Testes executados têm resultados documentados.
- Funcionalidades pendentes estão explicitamente identificadas.
- A documentação reflete a implementação real.

Compilar não prova que todas as funcionalidades estão implementadas. Testes unitários não substituem validação visual nem testes em hardware. Distingue sempre estes níveis de evidência.

## 33. Relatório final obrigatório

Apresenta:
1. Resumo do trabalho realizado.
2. Ficheiros criados/modificados.
3. Componentes e funcionalidades realmente implementados.
4. Testes executados e resultados.
5. Estado de compilação.
6. Link do workflow do GitHub Actions.
7. Link do APK, apenas se existir e estiver acessível.
8. Limitações técnicas.
9. Requisitos pendentes.
10. Dependências externas/licenças.
11. Próximos passos necessários.

Não inventes links, commits, testes ou funcionalidades. Se algo estiver bloqueado, explica o bloqueio, o impacto e o que foi feito, e continua nas áreas independentes.

## 34. Instrução final

Começa por inspecionar o repositório e ler integralmente os documentos de especificação. Depois implementa diretamente no repositório autorizado. Não respondas apenas com um plano, exemplos de código ou uma interface superficial.

O objetivo é um estúdio Android de animação 2D real, com o máximo de requisitos efetivamente implementados, arquitetura sustentável, projetos editáveis, testes e APK verificável.

**Prioridade máxima: funcionalidade real, fidelidade à especificação, preservação do trabalho existente, testes, build reproduzível e transparência absoluta sobre o que foi ou não concluído.**
