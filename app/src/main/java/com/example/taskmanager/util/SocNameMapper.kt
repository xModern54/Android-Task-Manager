package com.example.taskmanager.util

import android.content.Context
import com.example.taskmanager.R
import org.json.JSONObject

object SocNameMapper {
    @Volatile
    private var cache: Map<String, String>? = null

    fun resolve(context: Context, socId: String): String? {
        val id = socId.trim()
        if (id.isEmpty()) return null
        val map = cache ?: load(context)
        return map[id] ?: map[id.uppercase()] ?: map[id.lowercase()]
    }

    private fun load(context: Context): Map<String, String> {
        synchronized(this) {
            cache?.let { return it }
            val map = HashMap<String, String>()
            val text = context.resources.openRawResource(R.raw.socs_mapping)
                .bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.optJSONObject(key) ?: continue
                val name = obj.optString("NAME", "").trim()
                val cpu = obj.optString("CPU", "").trim()
                val value = when {
                    name.isNotBlank() -> name
                    cpu.isNotBlank() -> cpu
                    else -> null
                }
                if (value != null) {
                    map[key] = value
                }
            }
            cache = map
            return map
        }
    }
}
