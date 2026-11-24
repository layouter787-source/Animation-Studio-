// Ponte entre a app e a biblioteca de pincéis AnimBrush (third_party/AnimBrush).
// Sem JNI aqui: o ficheiro animbrush_jni.cpp faz a ligação ao Kotlin. Sem a pasta third_party/AnimBrush o build
// compila na mesma, mas available() devolve false e a app só mostra os pincéis incluídos.
#pragma once
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace abridge {

/** true se a AnimBrush foi compilada nesta build. */
bool available();

/** Carrega as texturas (stamps.abpack) da memória; [data] pode ser nulo (só máscaras procedurais). */
bool init(const uint8_t* data, size_t size);

/** Catálogo em JSON UTF-8 (UI: id, nome, categoria, tamanho por defeito, ...). */
std::string catalog();

/** Traço já rasterizado: pixéis ARGB direitos (não pré-multiplicados), origem (x, y) na folha e escala de pixéis. */
struct Image {
    int w = 0, h = 0;
    float x = 0, y = 0;      // posição (unidades da folha) do canto superior esquerdo
    float scale = 1;         // pixéis da imagem por unidade da folha
    std::vector<uint32_t> argb;
};

/**
 * Rasteriza um traço com o pincel [id]. [pts] tem 3 valores por ponto (x, y, pressão 0..1) em unidades da folha;
 * [radius] é o raio do pincel (unidades da folha). Borrachas: a imagem é a quantidade apagada (alfa), em preto.
 */
bool render(const char* id, const float* pts, int count, float radius, float r, float g, float b, uint32_t seed,
            bool eraser, Image& out);

/**
 * Traço ao vivo: o mesmo motor, alimentado amostra a amostra com a pressão e o tempo reais da caneta.
 * Há uma sessão de cada vez. Cada chamada devolve em [dirty] só a área que mudou (false = nada mudou).
 * A superfície cobre a folha inteira ([sheetW] x [sheetH] unidades; escala 1, ou menor se passar o limite de memória).
 * Borrachas: a imagem é a quantidade apagada, como em render().
 */
bool liveBegin(const char* id, float sheetW, float sheetH, float radius, float r, float g, float b, uint32_t seed,
               bool eraser, float x, float y, float pressure, Image& dirty);
bool liveMove(float x, float y, float pressure, float dtSeconds, Image& dirty);
bool liveEnd(Image& dirty);
void liveCancel();

}  // namespace abridge
