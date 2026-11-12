package com.martinikaws.myspoti.shared

import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataMap

/**
 * Everything the phone and watch say to each other goes through here, so the two sides
 * cannot drift apart.
 *
 * - State (phone -> watch) is a DataItem at [PATH_NOW_PLAYING]. The Data Layer keeps the last
 *   value and syncs it, so the watch gets the current track even if it connects late.
 * - Commands (watch -> phone) are fire-and-forget Messages under [PATH_COMMAND_PREFIX].
 */
object Protocol {
    /** Declared by the phone app in res/values/wear.xml; the watch looks for nodes with it. */
    const val CAPABILITY_PHONE = "myspoti_phone_bridge"

    const val PATH_NOW_PLAYING = "/myspoti/now_playing"
    const val PATH_COMMAND_PREFIX = "/myspoti/cmd/"

    /** Sent by the watch when it opens, so the phone re-publishes state immediately. */
    const val PATH_REQUEST_STATE = "/myspoti/request_state"
}

enum class Command(val path: String) {
    PLAY_PAUSE(Protocol.PATH_COMMAND_PREFIX + "play_pause"),
    NEXT(Protocol.PATH_COMMAND_PREFIX + "next"),
    PREVIOUS(Protocol.PATH_COMMAND_PREFIX + "previous"),

    /** Payload: one byte, the absolute volume step (0..maxVolume). */
    SET_VOLUME(Protocol.PATH_COMMAND_PREFIX + "set_volume"),

    /** Payload: 8 bytes, big-endian position in ms. */
    SEEK(Protocol.PATH_COMMAND_PREFIX + "seek");

    companion object {
        fun fromPath(path: String): Command? = entries.firstOrNull { it.path == path }
    }
}

data class NowPlaying(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** SystemClock.elapsedRealtime() on the phone is useless on the watch, so we send wall time. */
    val positionUpdatedAtEpochMs: Long = 0,
    val playbackSpeed: Float = 1f,
    val volume: Int = 0,
    val maxVolume: Int = 15,
    /** Package of the player on the phone, e.g. com.spotify.music, or your patched package. */
    val playerPackage: String = "",
    val hasSession: Boolean = false,
) {
    /** Position extrapolated to [nowEpochMs], so the progress bar moves without new messages. */
    fun positionAt(nowEpochMs: Long): Long {
        if (!isPlaying || positionUpdatedAtEpochMs == 0L) return positionMs
        val elapsed = ((nowEpochMs - positionUpdatedAtEpochMs) * playbackSpeed).toLong()
        val p = positionMs + elapsed
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }

    fun writeTo(map: DataMap) {
        map.putString(K_TITLE, title)
        map.putString(K_ARTIST, artist)
        map.putString(K_ALBUM, album)
        map.putBoolean(K_PLAYING, isPlaying)
        map.putLong(K_POS, positionMs)
        map.putLong(K_DUR, durationMs)
        map.putLong(K_POS_AT, positionUpdatedAtEpochMs)
        map.putFloat(K_SPEED, playbackSpeed)
        map.putInt(K_VOL, volume)
        map.putInt(K_VOL_MAX, maxVolume)
        map.putString(K_PKG, playerPackage)
        map.putBoolean(K_HAS_SESSION, hasSession)
    }

    companion object {
        const val K_ARTWORK = "artwork"
        const val K_ARTWORK_KEY = "artwork_key"
        private const val K_TITLE = "title"
        private const val K_ARTIST = "artist"
        private const val K_ALBUM = "album"
        private const val K_PLAYING = "playing"
        private const val K_POS = "pos"
        private const val K_DUR = "dur"
        private const val K_POS_AT = "pos_at"
        private const val K_SPEED = "speed"
        private const val K_VOL = "vol"
        private const val K_VOL_MAX = "vol_max"
        private const val K_PKG = "pkg"
        private const val K_HAS_SESSION = "has_session"

        fun from(map: DataMap) = NowPlaying(
            title = map.getString(K_TITLE, ""),
            artist = map.getString(K_ARTIST, ""),
            album = map.getString(K_ALBUM, ""),
            isPlaying = map.getBoolean(K_PLAYING),
            positionMs = map.getLong(K_POS),
            durationMs = map.getLong(K_DUR),
            positionUpdatedAtEpochMs = map.getLong(K_POS_AT),
            playbackSpeed = map.getFloat(K_SPEED, 1f),
            volume = map.getInt(K_VOL),
            maxVolume = map.getInt(K_VOL_MAX, 15),
            playerPackage = map.getString(K_PKG, ""),
            hasSession = map.getBoolean(K_HAS_SESSION),
        )

        fun artwork(map: DataMap): Asset? = map.getAsset(K_ARTWORK)
    }
}

fun Long.toBytes(): ByteArray = ByteArray(8) { i -> (this shr (56 - 8 * i)).toByte() }
fun ByteArray.toLong(): Long = fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
