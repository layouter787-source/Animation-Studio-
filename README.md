# Animation Studio — Android

Estúdio de animação 2D para Android, inspirado no fluxo de trabalho de animação frame a frame do RoughAnimator, com implementação e identidade próprias.

## Stack
- Kotlin, Jetpack Compose e Material 3 para a interface.
- C++ com Android NDK e CMake para o motor nativo.
- JNI para a ligação entre Kotlin e C++.
- OpenGL ES como primeiro backend gráfico; Vulkan poderá ser avaliado depois, com fallback por dispositivo.
- GitHub Actions para compilar um APK de depuração na nuvem.

## Regras de interface
- Área de trabalho cinzenta-escura.
- A folha de desenho do projeto é branca e tem dimensões próprias.
- Zoom e deslocamento da vista não alteram a resolução da folha.
- A estrutura visual segue as referências fornecidas pelo autor: lista de projetos, timeline no topo, ferramentas à esquerda e folha branca no centro.
- O código e os recursos serão implementados para este projeto; não copiamos código proprietário do RoughAnimator.

## Estado atual
Esta é a fundação compilável do projeto: aplicação Android Compose, ponte JNI mínima e workflow de APK debug. O editor de desenho real, timeline funcional, armazenamento de projetos, ferramentas de pintura e exportação serão desenvolvidos e testados em etapas.

## Compilar
Abra o repositório no Android Studio ou num ambiente compatível com Gradle e Android SDK. Também pode iniciar o workflow **Android Debug APK** no separador Actions.

O APK de debug não é uma versão de produção assinada.
