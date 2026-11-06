package com.animationstudio.data

import com.animationstudio.domain.Timing
import org.json.JSONArray
import org.json.JSONObject

/** Durações de frame num ficheiro à parte (não altera o formato do projeto). */
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
        return root.toString()
    }

    fun decode(text: String): Timing? = try {
        val ha = JSONObject(text).getJSONArray("holds")
        val holds = LinkedHashMap<Long, Int>()
        for (i in 0 until ha.length()) {
            val a = ha.getJSONArray(i)
            holds[a.getLong(0)] = a.getInt(1)
        }
        Timing(holds)
    } catch (e: Exception) {
        null
    }
}
