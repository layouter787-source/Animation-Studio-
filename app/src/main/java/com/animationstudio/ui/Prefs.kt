package com.animationstudio.ui

import android.content.Context
import com.animationstudio.domain.RecentItems

/** Preferências do estúdio (SharedPreferences): caneta, rejeição de palma, posição das barras, estabilizador, suavização, pontas, pixelização, tinta do balde e pincéis recentes. */
internal class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("studio", Context.MODE_PRIVATE)

    init {
        StudioRender.taperEnds = sp.getBoolean("taperEnds", false)
        StudioRender.pixelated = sp.getBoolean("pixelated", false)
        // Chave nova: o limiar antigo (35%) deixava passar o balde por linhas finas e leves.
        StudioRender.inkPercent = sp.getInt("inkPercent2", 8).coerceIn(0, 100)
        // Gera as texturas dos pincéis fora da thread principal, para o primeiro traço com textura não travar.
        StudioRender.warmUp()
        // Pincéis que o utilizador importou de imagens (uma vez por arranque).
        CustomBrushes.load(context)
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

    /** Smoothing por corda (0–1 = 0–40 px). Por defeito curto (6 px): a corda comprida deixava o traço muito atrasado. */
    var stabilizer: Float
        get() = sp.getFloat("stabilizer2", 0.15f)
        set(v) { sp.edit().putFloat("stabilizer2", v).apply() }

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

    /** Opacidade (0–100%) a partir da qual o balde trata um pixel como tinta (parede). */
    var inkPercent: Int
        get() = sp.getInt("inkPercent2", 8).coerceIn(0, 100)
        set(v) { val c = v.coerceIn(0, 100); sp.edit().putInt("inkPercent2", c).apply(); StudioRender.inkPercent = c }

    /** Pincéis e borrachas usados há pouco (ids), o mais recente primeiro; no máximo [RecentItems.DEFAULT_MAX]. */
    var recentBrushes: List<String>
        get() = RecentItems.decode(sp.getString("recentBrushes", ""))
        set(v) { sp.edit().putString("recentBrushes", RecentItems.encode(v.take(RecentItems.DEFAULT_MAX))).apply() }

    /** Regista [id] como o pincel mais recente e devolve a lista atualizada. */
    fun pushRecentBrush(id: String): List<String> {
        val next = RecentItems.push(recentBrushes, id)
        recentBrushes = next
        return next
    }
}
