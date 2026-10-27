package com.samvaads.widget.internal

import android.content.Context
import android.content.SharedPreferences

/** [KeyValueStore] over the SDK's own SharedPreferences file. */
internal class PrefsStore(context: Context) : KeyValueStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun getBoolean(key: String): Boolean = prefs.getBoolean(key, false)

    override fun getInt(key: String): Int = prefs.getInt(key, 0)

    override fun edit(changes: Map<String, Any?>) {
        val e = prefs.edit()
        for ((k, v) in changes) {
            when (v) {
                null -> e.remove(k)
                is String -> e.putString(k, v)
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                else -> e.putString(k, v.toString())
            }
        }
        e.apply()
    }

    private companion object {
        const val FILE = "com.samvaads.widget"
    }
}
