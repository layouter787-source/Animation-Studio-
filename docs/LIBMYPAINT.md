# libmypaint na Animation Studio

**Origem:** https://github.com/mypaint/libmypaint, commit `d5a88fbe6649d5ec776bc42ec8c1f4bb29d7fd7f` (descarregado no build por `FetchContent`; não está copiado no repositório). **Licença:** ISC.
**Pincéis:** `app/src/main/assets/mypaint_brushes.txt` (25 pincéis extraídos dos `.myb` do projeto mypaint-brushes, licença CC0).

## Como funciona
- `app/src/main/cpp/CMakeLists.txt` compila o libmypaint como biblioteca estática e junta-a a `libanimation_engine.so`.
- `mypaint_engine.cpp` usa a API `mypaint_brush_*` com uma superfície que só **regista** os toques (`draw_dab`). Devolve 11 valores por toque: x, y, raio, r, g, b, opaco, dureza, alpha_eraser, aspeto, ângulo.
- `MyPaintBrushes.kt` lê o catálogo e calcula os toques de um traço; `MyPaintRender.kt` desenha-os com o perfil de dureza do libmypaint.
- Os pincéis aparecem na categoria "libmypaint". Sem a biblioteca nativa, a categoria não aparece.

## Limitações conhecidas
- O traço guardado não tem pressão: é sintetizada (sobe no início e desce no fim).
- Sem *smudge* (a superfície não lê a tela); `config.h` e `json.h` são simplificados (sem glib nem json-c).
- O build precisa de `git`, `python3` e rede (para o FetchContent).
- Testado só no computador (compilação do núcleo C++ e pincéis reais); o Kotlin e o comportamento no telemóvel não foram compilados nem testados aqui.
