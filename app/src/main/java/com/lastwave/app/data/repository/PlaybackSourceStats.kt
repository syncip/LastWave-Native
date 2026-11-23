package com.lastwave.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where songs came from (TIDAL, YouTube, ...), at what quality, and how much
 * data the app moved. All-time totals since [Snapshot.sinceMillis].
 */
@Singleton
class PlaybackSourceStats @Inject constructor(
    @ApplicationContext context: Context,
) {
    enum class Quality { HI_RES, LOSSLESS, LOSSY }

    data class Snapshot(
        val sinceMillis: Long,
        val playsBySource: Map<String, Int>,
        val playsByQuality: Map<Quality, Int>,
        val wifiBytes: Long,
        val meteredBytes: Long,
    ) {
        val plays: Int get() = playsBySource.values.sum()
    }

    private val prefs = context.getSharedPreferences("playback_source_stats", Context.MODE_PRIVATE)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val uid = Process.myUid()
    private val _snapshot = MutableStateFlow(read())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    @Synchronized
    fun recordPlay(source: String, quality: Quality) {
        val edit = prefs.edit()
        addTraffic(edit)
        edit.putInt(SOURCE + source, prefs.getInt(SOURCE + source, 0) + 1)
        edit.putInt(QUALITY + quality.name, prefs.getInt(QUALITY + quality.name, 0) + 1)
        edit.apply()
        _snapshot.value = read()
    }

    @Synchronized
    fun sampleTraffic() {
        val edit = prefs.edit()
        addTraffic(edit)
        edit.apply()
        _snapshot.value = read()
    }

    /**
     * Adds the app's bytes since the last sample to the network active now.
     * Per-uid counters restart at boot, so a smaller reading is all new.
     */
    private fun addTraffic(edit: android.content.SharedPreferences.Editor) {
        val rx = TrafficStats.getUidRxBytes(uid)
        val tx = TrafficStats.getUidTxBytes(uid)
        if (rx < 0 || tx < 0) return
        val now = rx + tx
        val last = prefs.getLong(LAST_COUNTER, -1L)
        edit.putLong(LAST_COUNTER, now)
        if (!prefs.contains(SINCE)) edit.putLong(SINCE, System.currentTimeMillis())
        if (last < 0) return
        val delta = if (now >= last) now - last else now
        val key = if (connectivity?.isActiveNetworkMetered == true) METERED else WIFI
        edit.putLong(key, prefs.getLong(key, 0L) + delta)
    }

    private fun read(): Snapshot {
        val all = prefs.all
        return Snapshot(
            sinceMillis = prefs.getLong(SINCE, System.currentTimeMillis()),
            playsBySource = all.filterKeys { it.startsWith(SOURCE) }
                .mapNotNull { (k, v) -> (v as? Int)?.let { k.removePrefix(SOURCE) to it } }.toMap(),
            playsByQuality = Quality.entries.associateWith { prefs.getInt(QUALITY + it.name, 0) },
            wifiBytes = prefs.getLong(WIFI, 0L),
            meteredBytes = prefs.getLong(METERED, 0L),
        )
    }

    private companion object {
        const val SOURCE = "src:"
        const val QUALITY = "q:"
        const val SINCE = "since"
        const val LAST_COUNTER = "last_counter"
        const val WIFI = "bytes_wifi"
        const val METERED = "bytes_metered"
    }
}
