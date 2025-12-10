package com.frzlss.frzterr

import android.app.Application
import com.frzlss.frzterr.data.remote.supabase.SupabaseManager

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        SupabaseManager.initialize(this)
    }
}
