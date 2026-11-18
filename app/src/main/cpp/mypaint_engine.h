#ifndef ANIMSTUDIO_MYPAINT_ENGINE_H
#define ANIMSTUDIO_MYPAINT_ENGINE_H

#include <string>
#include <vector>

// Um mapeamento de entrada -> definição (curva por pontos x,y) de um pincel libmypaint.
struct MpMapping {
    int settingIndex = -1;       // índice em MpDabRequest::settingNames
    std::string input;           // cname da entrada: "pressure", "speed1", "random", ...
    std::vector<float> points;   // x0,y0,x1,y1,...
};

struct MpDabRequest {
    std::vector<std::string> settingNames;  // cnames: "opaque", "radius_logarithmic", "color_h", ...
    std::vector<float> baseValues;          // mesmo tamanho que settingNames
    std::vector<MpMapping> mappings;
    std::vector<float> points;              // x, y, pressão por ponto do traço
    float dtime = 0.008f;                   // segundos entre pontos consecutivos
};

// Calcula os toques (11 floats cada): x, y, raio, r, g, b, opaco, dureza, alpha_eraser, aspeto, ângulo.
// Devolve false se o pedido for inválido.
bool mpComputeDabs(const MpDabRequest& request, std::vector<float>& out);

#endif  // ANIMSTUDIO_MYPAINT_ENGINE_H
