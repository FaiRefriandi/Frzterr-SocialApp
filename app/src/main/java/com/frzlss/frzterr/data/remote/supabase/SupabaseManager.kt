package com.frzlss.frzterr.data.remote.supabase

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseManager {

    private const val SUPABASE_URL = "https://wulshdvhdlcwlhsozkzl.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Ind1bHNoZHZoZGxjd2xoc296a3psIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjUzMTMxNjMsImV4cCI6MjA4MDg4OTE2M30.LLoCyRJ8SMZfCBNw9z4qIXzQB43bAU_2ajnAJecpX38"

    lateinit var client: SupabaseClient
        private set

    fun initialize(context: Context) {
        client = createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }
}
