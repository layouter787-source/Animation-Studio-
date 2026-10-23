package com.animationstudio.ui

import android.content.Context

/** Preferências do estúdio (SharedPreferences): caneta, rejeição de palma, posição das barras, estabilizador. */
internal class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("studio", Context.MODE_PRIVATE)

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
}
