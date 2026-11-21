package com.animationstudio.data

import com.animationstudio.domain.CameraKey
import com.animationstudio.domain.CameraTrack
import org.json.JSONArray
import org.json.JSONObject

/** Keyframes da câmara num ficheiro à parte (não altera o formato do projeto). Ficheiros antigos com o campo "e" ainda abrem. */
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
                o.getDouble("r").toFloat(),
            )
        }
        CameraTrack(keys)
    } catch (e: Exception) {
        null
    }
}
