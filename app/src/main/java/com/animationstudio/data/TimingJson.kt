package com.animationstudio.data

import com.animationstudio.domain.CameraKey
import com.animationstudio.domain.Ease
import com.animationstudio.domain.Timing
import org.json.JSONArray
import org.json.JSONObject

/** Durações de frame e keyframes da câmara, num ficheiro à parte (não altera o formato do projeto). */
object TimingJson {
    fun encode(t: Timing): String {
        val root = JSONObject()
        val holds = JSONArray()
        for ((id, ticks) in t.holds) {
            val a = JSONArray()
            a.put(id)
            a.put(ticks)
            holds.put(a)
        }
        root.put("holds", holds)
        val cam = JSONArray()
        for ((id, k) in t.camera) {
            val o = JSONObject()
            o.put("f", id)
            o.put("x", k.cx.toDouble())
            o.put("y", k.cy.toDouble())
            o.put("z", k.zoom.toDouble())
            o.put("r", k.rotation.toDouble())
            o.put("e", if (k.ease == Ease.LINEAR) 0 else 1)
            cam.put(o)
        }
        root.put("cam", cam)
        return root.toString()
    }

    fun decode(text: String): Timing? = try {
        val r = JSONObject(text)
        val ha = r.getJSONArray("holds")
        val holds = LinkedHashMap<Long, Int>()
        for (i in 0 until ha.length()) {
            val a = ha.getJSONArray(i)
            holds[a.getLong(0)] = a.getInt(1)
        }
        val ca = r.getJSONArray("cam")
        val cam = LinkedHashMap<Long, CameraKey>()
        for (i in 0 until ca.length()) {
            val o = ca.getJSONObject(i)
            cam[o.getLong("f")] = CameraKey(
                o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("z").toFloat(),
                o.getDouble("r").toFloat(), if (o.getInt("e") == 0) Ease.LINEAR else Ease.SMOOTH,
            )
        }
        Timing(holds, cam)
    } catch (e: Exception) {
        null
    }
}
