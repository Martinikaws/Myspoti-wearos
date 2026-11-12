package com.martinikaws.myspoti.wear

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.martinikaws.myspoti.shared.Command
import com.martinikaws.myspoti.shared.NowPlaying
import com.martinikaws.myspoti.shared.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** The watch's side of the Data Layer: receives state, sends commands. */
class PhoneLink(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val dataClient = Wearable.getDataClient(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)
    private val capabilityClient = Wearable.getCapabilityClient(appContext)

    private val _nowPlaying = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying

    private val _artwork = MutableStateFlow<ImageBitmap?>(null)
    val artwork: StateFlow<ImageBitmap?> = _artwork

    private val _phoneConnected = MutableStateFlow<Boolean?>(null)
    /** null = still checking. */
    val phoneConnected: StateFlow<Boolean?> = _phoneConnected

    private var artworkKey: String? = null

    private val dataListener = DataClient.OnDataChangedListener { events ->
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == Protocol.PATH_NOW_PLAYING
            ) {
                onState(DataMapItem.fromDataItem(event.dataItem).dataMap)
            }
        }
    }

    fun start() {
        dataClient.addListener(dataListener)
        scope.launch {
            loadLastKnownState()
            send(Protocol.PATH_REQUEST_STATE)
        }
    }

    fun stop() {
        dataClient.removeListener(dataListener)
    }

    fun send(command: Command, payload: ByteArray = ByteArray(0)) {
        scope.launch { send(command.path, payload) }
    }

    private suspend fun send(path: String, payload: ByteArray = ByteArray(0)) {
        val nodeId = phoneNodeId()
        _phoneConnected.value = nodeId != null
        nodeId ?: return
        runCatching { messageClient.sendMessage(nodeId, path, payload).await() }
            .onFailure { Log.w(TAG, "sendMessage $path failed", it) }
    }

    private suspend fun phoneNodeId(): String? = runCatching {
        capabilityClient
            .getCapability(Protocol.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
            .let { nodes -> nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() }
            ?.id
    }.getOrNull()

    /** The Data Layer keeps the last item, so the UI is filled in before the phone answers. */
    private suspend fun loadLastKnownState() {
        val uri = Uri.Builder()
            .scheme(PutDataRequest.WEAR_URI_SCHEME)
            .path(Protocol.PATH_NOW_PLAYING)
            .build()
        runCatching {
            val buffer = dataClient.getDataItems(uri).await()
            try {
                buffer.firstOrNull()?.let { onState(DataMapItem.fromDataItem(it).dataMap) }
            } finally {
                buffer.release()
            }
        }
    }

    private fun onState(map: DataMap) {
        _nowPlaying.value = NowPlaying.from(map)
        val key = map.getString(NowPlaying.K_ARTWORK_KEY)
        val asset = NowPlaying.artwork(map)
        if (asset == null) {
            artworkKey = null
            _artwork.value = null
        } else if (key != artworkKey) {
            artworkKey = key
            scope.launch {
                _artwork.value = withContext(Dispatchers.IO) {
                    runCatching {
                        dataClient.getFdForAsset(asset).await().inputStream.use {
                            BitmapFactory.decodeStream(it)?.asImageBitmap()
                        }
                    }.getOrNull()
                }
            }
        }
    }

    private companion object {
        const val TAG = "MyspotiLink"
    }
}
