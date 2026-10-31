package com.animationstudio.ui

import android.content.Context

/** Preferências do estúdio (SharedPreferences): caneta, rejeição de palma, posição das barras, estabilizador, suavização, pontas, pixelização e tinta do balde. */
internal class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("studio", Context.MODE_PRIVATE)

    init {
        StudioRender.taperEnds = sp.getBoolean("taperEnds", false)
        StudioRender.pixelated = sp.getBoolean("pixelated", false)
        StudioRender.inkPercent = sp.getInt("inkPercent", 35).coerceIn(0, 100)
    }

    var penOnly: Boolean
        get() = sp.getBoolean("penOnly", false)
        set(v) { sp.edit().putBoolean("penOnly", v).apply() }

    var palmReject: Boolean
        get() = sp.getBoolean("palmReject", true)
        set(v) { sp.edit().putBoolean("palmReject", v).apply() }

    var timelineBottom: Boolean
        get() = sp.getBoolean("timelineBottom", false)
        set(v) { sp.edit().putBoolean("timelineBottom", v).apply() }

    var toolsRight: Boolean
        get() = sp.getBoolean("toolsRight", false)
        set(v) { sp.edit().putBoolean("toolsRight", v).apply() }

    var stabilizer: Float
        get() = sp.getFloat("stabilizer", 0.35f)
        set(v) { sp.edit().putFloat("stabilizer", v).apply() }

    /** Suavização do traço em percentagem (0..1). */
    var smoothing: Float
        get() = sp.getFloat("smoothing", 0.4f)
        set(v) { sp.edit().putFloat("smoothing", v).apply() }

    /** Pontas afinadas nos novos traços (por defeito desligado: traço uniforme). */
    var taperEnds: Boolean
        get() = sp.getBoolean("taperEnds", false)
        set(v) { sp.edit().putBoolean("taperEnds", v).apply(); StudioRender.taperEnds = v }

    /** Traço pixelado, sem anti-aliasing (por defeito desligado). */
    var pixelated: Boolean
        get() = sp.getBoolean("pixelated", false)
        set(v) { sp.edit().putBoolean("pixelated", v).apply(); StudioRender.pixelated = v }

    /** Opacidade (0–100%) a partir da qual o balde trata um pixel como tinta. */
    var inkPercent: Int
        get() = sp.getInt("inkPercent", 35).coerceIn(0, 100)
        set(v) { val c = v.coerceIn(0, 100); sp.edit().putInt("inkPercent", c).apply(); StudioRender.inkPercent = c }
}
