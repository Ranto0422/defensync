package com.example.defensync

import android.app.Application
import com.example.defensync.core.ServiceLocator

class DefenSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
