package com.martinikaws.myspoti.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.martinikaws.myspoti.shared.Command
import com.martinikaws.myspoti.shared.toBytes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val link = PhoneLink(app, viewModelScope).also { it.start() }

    val nowPlaying = link.nowPlaying
    val artwork = link.artwork
    val phoneConnected = link.phoneConnected

    /**
     * While the bezel is turning, the watch owns the volume value. Without this, each
     * phone echo (a step or two behind) would make the indicator jump backwards.
     */
    private val localVolume = MutableStateFlow<Int?>(null)
    private var releaseLocalVolume: Job? = null

    val volume: StateFlow<Int> = combine(nowPlaying, localVolume) { np, local -> local ?: np.volume }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Optimistic play state so the button flips instantly on tap. */
    private val optimisticPlaying = MutableStateFlow<Boolean?>(null)
    val isPlaying: StateFlow<Boolean> =
        combine(nowPlaying, optimisticPlaying) { np, opt -> opt ?: np.isPlaying }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Drop the optimistic value as soon as the phone confirms anything.
        viewModelScope.launch { nowPlaying.collect { optimisticPlaying.value = null } }
    }

    fun playPause() {
        optimisticPlaying.value = !isPlaying.value
        link.send(Command.PLAY_PAUSE)
    }

    fun next() = link.send(Command.NEXT)
    fun previous() = link.send(Command.PREVIOUS)
    fun seekTo(positionMs: Long) = link.send(Command.SEEK, positionMs.toBytes())

    /** Called once per bezel detent (or per crown "step"). */
    fun changeVolume(delta: Int) {
        val max = nowPlaying.value.maxVolume
        val target = (volume.value + delta).coerceIn(0, max)
        if (target == volume.value) return
        localVolume.value = target
        link.send(Command.SET_VOLUME, byteArrayOf(target.toByte()))

        releaseLocalVolume?.cancel()
        releaseLocalVolume = viewModelScope.launch {
            delay(1_200)
            localVolume.value = null
        }
    }

    override fun onCleared() {
        link.stop()
    }
}
