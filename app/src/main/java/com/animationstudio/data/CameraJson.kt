package com.animationstudio.data

import com.animationstudio.domain.CameraKey
import com.animationstudio.domain.CameraTrack
import com.animationstudio.domain.Ease
import org.json.JSONArray
import org.json.JSONObject

/** Keyframes da câmara num ficheiro à parte (não altera o formato do projeto). */
object CameraJson {
    fun encode(t: CameraTrack): String {
        val root = JSONObject()
        val cam = JSONArray()
        for ((id, k) in t.keys) {
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

    fun decode(text: String): CameraTrack? = try {
        val ca = JSONObject(text).getJSONArray("cam")
        val keys = LinkedHashMap<Long, CameraKey>()
        for (i in 0 until ca.length()) {
            val o = ca.getJSONObject(i)
            keys[o.getLong("f")] = CameraKey(
                o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("z").toFloat(),
                o.getDouble("r").toFloat(), if (o.getInt("e") == 0) Ease.LINEAR else Ease.SMOOTH,
            )
        }
        CameraTrack(keys)
    } catch (e: Exception) {
        null
    }
}
