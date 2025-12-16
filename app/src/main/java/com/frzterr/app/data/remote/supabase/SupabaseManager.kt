package com.frzterr.app.data.remote.supabase

import android.content.Context
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseManager {

    private const val SUPABASE_URL =
        "https://wulshdvhdlcwlhsozkzl.supabase.co"

    // PUB KEY aman untuk client
    private const val SUPABASE_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Ind1bHNoZHZoZGxjd2xoc296a3psIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjUzMTMxNjMsImV4cCI6MjA4MDg4OTE2M30.LLoCyRJ8SMZfCBNw9z4qIXzQB43bAU_2ajnAJecpX38"

    val ANON_KEY: String
        get() = SUPABASE_KEY

    lateinit var client: SupabaseClient
        private set

    private var initialized = false

    fun initialize(context: Context) {

        if (initialized) {
            Log.e("SUPABASE_INIT", "SupabaseManager already initialized, skipping")
            return
        }

        Log.e("SUPABASE_INIT", "Initializing SupabaseManager...")

        client = createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Auth) {
                // Enable automatic token refresh
                autoSaveToStorage = true
                autoLoadFromStorage = true
                alwaysAutoRefresh = true
                
                // Configure session management
                scheme = "app"
                host = "supabase"
            }
            install(Postgrest)
            install(Storage)
        }

        initialized = true

        Log.e("SUPABASE_INIT", "SupabaseClient ready with session persistence")
    }
}
