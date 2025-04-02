package com.example.apollokotlinpaginationsample

import android.content.Context

class Application : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        Companion.applicationContext = this.applicationContext
    }

    companion object {
        lateinit var applicationContext: Context
    }
}
