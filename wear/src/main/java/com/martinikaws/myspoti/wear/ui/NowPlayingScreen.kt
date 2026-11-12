package com.martinikaws.myspoti.wear.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.martinikaws.myspoti.wear.PlayerViewModel
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * Pixels of rotary scroll per volume step. The Watch6 Classic bezel sends one event per
 * detent, normally larger than this, so one click = one step. Raise it if a single click
 * jumps two steps on your watch.
 */
private const val PIXELS_PER_VOLUME_STEP = 40f

@Composable
fun NowPlayingScreen(vm: PlayerViewModel = viewModel()) {
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val artwork by vm.artwork.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val phoneConnected by vm.phoneConnected.collectAsStateWithLifecycle()

    // Progress ticks locally between phone updates.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val progress = if (np.durationMs > 0) np.positionAt(now).toFloat() / np.durationMs else 0f

    // ---- Bezel / crown -> volume
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    var rotaryAccumulator by remember { mutableFloatStateOf(0f) }
    var showVolume by remember { mutableStateOf(false) }
    var volumeTouch by remember { mutableIntStateOf(0) }

    LaunchedEffect(volumeTouch) {
        if (volumeTouch == 0) return@LaunchedEffect
        showVolume = true
        delay(1_500)
        showVolume = false
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MyspotiColors.Black)
            .onRotaryScrollEvent { event ->
                rotaryAccumulator += event.verticalScrollPixels
                if (abs(rotaryAccumulator) >= PIXELS_PER_VOLUME_STEP) {
                    vm.changeVolume(if (rotaryAccumulator > 0) 1 else -1)
                    rotaryAccumulator = 0f
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    volumeTouch++
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        // Album art as a blurred, darkened backdrop (blur needs Wear OS 4 / API 31+).
        artwork?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(24.dp),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.9f))
                )
            )
        )

        // Edge ring: track progress normally, volume while the bezel is turning.
        CircularProgressIndicator(
            progress = if (showVolume) volume.toFloat() / np.maxVolume.coerceAtLeast(1) else progress,
            modifier = Modifier.fillMaxSize().padding(2.dp),
            indicatorColor = if (showVolume) MyspotiColors.TextPrimary else MyspotiColors.Green,
            trackColor = MyspotiColors.Track.copy(alpha = 0.5f),
            strokeWidth = 4.dp,
        )

        TimeText()

        when {
            phoneConnected == false -> StatusMessage("Phone not reachable.\nOpen Myspoti Bridge on your phone.")
            !np.hasSession && np.title.isEmpty() -> StatusMessage("Nothing playing.\nStart Spotify on your phone.")
            else -> PlayerContent(
                title = np.title,
                artist = np.artist,
                isPlaying = isPlaying,
                onPrevious = vm::previous,
                onPlayPause = vm::playPause,
                onNext = vm::next,
                onVolumeTap = { volumeTouch++ },
            )
        }

        AnimatedVisibility(visible = showVolume, enter = fadeIn(), exit = fadeOut()) {
            VolumeOverlay(volume, np.maxVolume)
        }
    }
}

@Composable
private fun PlayerContent(
    title: String,
    artist: String,
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onVolumeTap: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            color = MyspotiColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().basicMarquee(),
            textAlign = TextAlign.Center,
        )
        Text(
            artist,
            color = MyspotiColors.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Filled.SkipPrevious, "Previous", 40.dp, onPrevious)
            Spacer(Modifier.size(8.dp))
            // The signature filled green circle.
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MyspotiColors.Green)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MyspotiColors.Black,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.size(8.dp))
            RoundIcon(Icons.Filled.SkipNext, "Next", 40.dp, onNext)
        }
        Spacer(Modifier.height(8.dp))
        RoundIcon(Icons.AutoMirrored.Filled.VolumeUp, "Volume", 32.dp, onVolumeTap)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = MyspotiColors.TextPrimary,
            modifier = Modifier.size(size * 0.65f))
    }
}

@Composable
private fun VolumeOverlay(volume: Int, max: Int) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = MyspotiColors.TextPrimary)
            Text(
                "${(volume * 100) / max.coerceAtLeast(1)}%",
                color = MyspotiColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("Turn bezel", color = MyspotiColors.TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StatusMessage(text: String) {
    Text(
        text,
        color = MyspotiColors.TextSecondary,
        textAlign = TextAlign.Center,
        fontSize = 13.sp,
        modifier = Modifier.padding(28.dp),
    )
}
