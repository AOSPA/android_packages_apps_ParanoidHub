package co.aospa.hub

import android.app.Application

class HubApp: Application() {

    override fun onCreate() {
        super.onCreate()
        app = this
    }

    companion object {
        private const val TAG = "HubApp"
        var app: HubApp? = null
    }
}