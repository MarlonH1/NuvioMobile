package com.nuvio.app.features.tvremote

internal expect object TvRemoteStorage {
    fun loadEnabled(): Boolean?
    fun saveEnabled(enabled: Boolean)
    fun loadPairedTvsJson(): String?
    fun savePairedTvsJson(json: String)
    fun loadActiveTvId(): String?
    fun saveActiveTvId(id: String?)
    fun loadClientId(): String?
    fun saveClientId(id: String)
}
