package com.nuvio.app.features.tvremote

import android.content.Context

/** Application context for the Android-only parts of the TV remote (discovery, notification). */
internal object TvRemoteAndroid {
    @Volatile
    var appContext: Context? = null
        private set

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        TvRemoteNowPlaying.install(context.applicationContext)
    }
}
