package com.martinikaws.myspoti.mobile

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.martinikaws.myspoti.shared.Command
import com.martinikaws.myspoti.shared.NowPlaying
import com.martinikaws.myspoti.shared.Protocol
import com.martinikaws.myspoti.shared.toLong
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Bridges whatever media player is active on the phone (Spotify, or a patched Spotify with a
 * different package name) to the watch.
 *
 * Why MediaSession and not the Spotify App Remote SDK: the SDK needs a registered client ID,
 * talks only to the official com.spotify.music package, and breaks if a patch changes the
 * package name. Every player publishes a MediaSession, so this works with any of them.
 */
class MediaBridgeService : NotificationListenerService(), MessageClient.OnMessageReceivedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionManager by lazy { getSystemService(MediaSessionManager::class.java) }
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val dataClient by lazy { Wearable.getDataClient(this) }

    private var controller: MediaController? = null
    private var lastArtworkKey: String? = null
    private var lastArtworkAsset: Asset? = null
    private var publishJob: Job? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { sessions -> pickController(sessions) }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = schedulePublish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = schedulePublish()
        override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) = schedulePublish()
        override fun onSessionDestroyed() {
            pickController(sessionManager.getActiveSessions(componentName()))
        }
    }

    override fun onListenerConnected() {
        val component = componentName()
        sessionManager.addOnActiveSessionsChangedListener(sessionsListener, component)
        pickController(sessionManager.getActiveSessions(component))
        Wearable.getMessageClient(this).addListener(this)
        isConnected = true
    }

    override fun onListenerDisconnected() {
        sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
        Wearable.getMessageClient(this).removeListener(this)
        controller?.unregisterCallback(controllerCallback)
        controller = null
        isConnected = false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun componentName() = ComponentName(this, MediaBridgeService::class.java)

    /** Prefer Spotify-like packages, then whatever is playing, then the most recent session. */
    private fun pickController(sessions: List<MediaController>?) {
        val list = sessions.orEmpty()
        val best = list.firstOrNull { it.packageName.contains("spotify") && it.isPlaying() }
            ?: list.firstOrNull { it.isPlaying() }
            ?: list.firstOrNull { it.packageName.contains("spotify") }
            ?: list.firstOrNull()

        if (best?.sessionToken == controller?.sessionToken) return
        controller?.unregisterCallback(controllerCallback)
        controller = best
        best?.registerCallback(controllerCallback)
        Log.d(TAG, "Bridging session: ${best?.packageName}")
        schedulePublish()
    }

    private fun MediaController.isPlaying() =
        playbackState?.state == PlaybackState.STATE_PLAYING

    // ---------------------------------------------------------------- phone -> watch

    /** Coalesce bursts of callbacks (Spotify fires several per track change). */
    private fun schedulePublish() {
        publishJob?.cancel()
        publishJob = scope.launch {
            delay(150)
            publish()
        }
    }

    private suspend fun publish() {
        val c = controller
        val meta = c?.metadata
        val state = c?.playbackState
        val (volume, maxVolume) = currentVolume(c)

        val nowPlaying = NowPlaying(
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            isPlaying = state?.state == PlaybackState.STATE_PLAYING,
            positionMs = state?.currentPositionNow() ?: 0,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
            positionUpdatedAtEpochMs = System.currentTimeMillis(),
            playbackSpeed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            volume = volume,
            maxVolume = maxVolume,
            playerPackage = c?.packageName.orEmpty(),
            hasSession = c != null,
        )

        val request = PutDataMapRequest.create(Protocol.PATH_NOW_PLAYING).apply {
            nowPlaying.writeTo(dataMap)
            artworkFor(meta)?.let { (key, asset) ->
                dataMap.putAsset(NowPlaying.K_ARTWORK, asset)
                dataMap.putString(NowPlaying.K_ARTWORK_KEY, key)
            }
        }.asPutDataRequest().setUrgent()

        runCatching { dataClient.putDataItem(request).await() }
            .onFailure { Log.w(TAG, "putDataItem failed", it) }
    }

    /** PlaybackState.position is a snapshot; project it to "now" before sending. */
    private fun PlaybackState.currentPositionNow(): Long {
        if (state != PlaybackState.STATE_PLAYING) return position
        val elapsed = android.os.SystemClock.elapsedRealtime() - lastPositionUpdateTime
        return position + (elapsed * playbackSpeed).toLong()
    }

    /**
     * Album art is re-encoded small (watch screens are ~450px) and cached so we only ship it
     * when the album actually changes.
     */
    private fun artworkFor(meta: MediaMetadata?): Pair<String, Asset>? {
        meta ?: return null
        val key = meta.getString(MediaMetadata.METADATA_KEY_ALBUM) + "|" +
            meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
        if (key == lastArtworkKey) return lastArtworkAsset?.let { key to it }

        val bitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: return null
        val scaled = Bitmap.createScaledBitmap(bitmap, ART_SIZE, ART_SIZE, true)
        val bytes = ByteArrayOutputStream().use {
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, it)
            it.toByteArray()
        }
        lastArtworkKey = key
        lastArtworkAsset = Asset.createFromBytes(bytes)
        return key to lastArtworkAsset!!
    }

    // ---------------------------------------------------------------- watch -> phone

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == Protocol.PATH_REQUEST_STATE) {
            lastArtworkKey = null // force artwork resend for a freshly opened watch app
            schedulePublish()
            return
        }
        val command = Command.fromPath(event.path) ?: return
        val c = controller
        val controls = c?.transportControls
        when (command) {
            Command.PLAY_PAUSE ->
                if (c?.isPlaying() == true) controls?.pause() else controls?.play()
            Command.NEXT -> controls?.skipToNext()
            Command.PREVIOUS -> controls?.skipToPrevious()
            Command.SEEK -> controls?.seekTo(event.data.toLong())
            Command.SET_VOLUME -> setVolume(c, event.data.firstOrNull()?.toInt() ?: return)
        }
        // Volume changes on the local stream do not fire a session callback, so publish anyway.
        schedulePublish()
    }

    /**
     * When Spotify plays on this phone, volume is the phone's music stream. When it plays
     * through Spotify Connect (speaker, PC), the session is REMOTE and owns its own volume.
     */
    private fun currentVolume(c: MediaController?): Pair<Int, Int> {
        val info = c?.playbackInfo
        return if (info?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
            info.currentVolume to info.maxVolume
        } else {
            audio.getStreamVolume(AudioManager.STREAM_MUSIC) to
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        }
    }

    private fun setVolume(c: MediaController?, value: Int) {
        val info = c?.playbackInfo
        if (info?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
            c!!.setVolumeTo(value.coerceIn(0, info.maxVolume), 0)
        } else {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            // Flag 0 = no system volume panel popping up on the phone.
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, value.coerceIn(0, max), 0)
        }
    }

    companion object {
        private const val TAG = "MyspotiBridge"
        private const val ART_SIZE = 320

        /** Read by the phone UI to show whether notification access is granted and bound. */
        @Volatile
        var isConnected = false
            private set
    }
}
