package com.frzterr.app.data.local

import android.content.Context

object ProfileLocalStore {

    private const val PREF_NAME = "profile_cache"

    fun save(
        context: Context,
        name: String?,
        avatarUrl: String?,
        username: String?
    ) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("name", name)
            .putString("avatar", avatarUrl)
            .putString("username", username)
            .apply()
    }

    fun load(context: Context): Triple<String?, String?, String?> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return Triple(
            prefs.getString("name", null),
            prefs.getString("avatar", null),
            prefs.getString("username", null)
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    fun saveLocalAvatarPath(context: Context, path: String?) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("avatar_path", path)
            .apply()
    }

    fun loadLocalAvatarPath(context: Context): String? {
        return context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString("avatar_path", null)
    }

}