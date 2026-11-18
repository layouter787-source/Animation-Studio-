package com.animationstudio.ui

/**
 * Ponte JNI para o libmypaint (código em app/src/main/cpp/mypaint_engine.cpp).
 *
 * O libmypaint calcula os "toques" (dabs) de um traço: posição, raio, opacidade, dureza, aspeto, ângulo e cor.
 * O desenho dos toques faz-se em Kotlin ([drawMyPaintDabs]). Se a biblioteca nativa não carregar (por exemplo num
 * emulador sem a ABI), [available] devolve false e os pincéis libmypaint ficam fora da biblioteca.
 */
internal object MyPaintNative {
    @Volatile
    private var state = 0 // 0 = por tentar, 1 = pronta, -1 = indisponível

    fun available(): Boolean {
        val s = state
        if (s != 0) return s > 0
        synchronized(this) {
            if (state == 0) {
                state = try {
                    System.loadLibrary("animation_engine")
                    1
                } catch (e: Throwable) {
                    -1
                }
            }
            return state > 0
        }
    }

    /**
     * Calcula os toques de um traço. [points] tem x, y e pressão (0..1) por ponto. Devolve 11 valores por toque:
     * x, y, raio, r, g, b, opaco, dureza, alpha_eraser, aspeto, ângulo (graus); ou null se falhar.
     * O nome do método não pode mudar (liga-se ao símbolo JNI Java_com_animationstudio_ui_MyPaintNative_computeDabs).
     */
    @JvmStatic
    external fun computeDabs(
        names: Array<String>,
        base: FloatArray,
        mapSetting: IntArray,
        mapInput: Array<String>,
        mapPoints: Array<FloatArray>,
        points: FloatArray,
        dtime: Float,
    ): FloatArray?
}
