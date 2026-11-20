# AnimBrush na app

AnimBrush é a biblioteca de pincéis e borrachas do projeto (C++17, sem dependências, ~500 pincéis). Substitui o libmypaint,
que foi removido.

## Onde fica

`third_party/AnimBrush/` com, no mínimo:

- `include/` (cabeçalhos) e `src/` (incluindo `src/brushes/`): compilados pelo CMake da app (`app/src/main/cpp/CMakeLists.txt`);
- `assets/stamps.abpack`: texturas (as 165 máscaras do zip). O Gradle copia só este ficheiro para os assets da app.

Se a pasta não existir, o build compila na mesma, sem os pincéis AnimBrush (a app usa só os pincéis incluídos).

## Como a app a usa

1. `animbrush_core.cpp` (sem JNI) cria o pincel pelo id, alimenta-o com as amostras do traço e rasteriza-o na
   `RasterSurface` de referência da AnimBrush, numa imagem justa à volta do traço (o dobro da resolução em traços
   pequenos, para ficar nítido ao ampliar).
2. `animbrush_jni.cpp` expõe isto ao Kotlin (`AnimBrushNative`): disponibilidade, carregar texturas, catálogo JSON e
   rasterizar um traço.
3. `AnimBrushes.kt` lê o catálogo no arranque (numa thread própria), junta os pincéis à biblioteca com o prefixo `ab:` e
   categorias "AB · …", e converte o resultado numa imagem que o Compose desenha (`drawAnimBrush`).
4. `Render.kt` guarda o resultado na cache do traço (via `MyPaintBrushes.kt` e `MyPaintRender.kt`, nomes antigos que
   ficaram para o Render.kt não mudar). A imagem calcula-se ao desenhar; durante o traço mostra-se uma linha simples
   entre cálculos, para não travar, e o traço final é sempre calculado ao levantar o dedo.

## Limitações conhecidas

- O traço guardado só tem posições: a pressão é sintetizada (sobe no início e desce no fim) e não há tempos (8 ms por amostra).
- Borrachas: partem de preto opaco e a quantidade retirada é o alfa; as que dependem da cor por baixo (cor parecida,
  só linhas escuras, só bordas) comportam-se como borracha normal.
- Os sliders do catálogo (`params`) ainda não aparecem na interface; usam-se os valores por defeito.
- Pincéis que dependem de ler a tela (esborratar) só veem uma superfície vazia.
- Pincéis animados (relâmpago, fogo, árvore ao vento) são desenhados com `frame = 0`.
- Cada traço fica em cache como imagem: projetos com muitos traços grandes ocupam memória.
- Testado aqui: compilação do CMake com e sem a pasta, e rasterização de lápis, tinta, aguarela, óleo, relâmpago, árvore, fogo
  e uma borracha no PC. Não foi testado em dispositivo.
