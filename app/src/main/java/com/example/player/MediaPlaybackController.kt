package com.example.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.MediaItem
import com.example.data.model.PlayerResizeMode
import com.example.data.repository.MediaRepository
import com.example.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val currentItem: MediaItem? = null,
    val queue: List<MediaItem> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val resizeMode: PlayerResizeMode = PlayerResizeMode.FIT,
    val isPlayerExpanded: Boolean = false,
    val errorMessage: String? = null
) {
    val hasActiveMedia: Boolean get() = currentItem != null
    val hasNext: Boolean get() = currentIndex in 0 until (queue.size - 1)
    val hasPrevious: Boolean get() = currentIndex > 0
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

class MediaPlaybackController(
    private val context: Context,
    private val repository: MediaRepository,
    private val preferences: AppPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(
        PlaybackUiState(
            playbackSpeed = preferences.defaultPlaybackSpeed,
            resizeMode = preferences.playerResizeMode
        )
    )
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private var tickerJob: Job? = null
    private var lastPersistedAtMs: Long = 0L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startTicker()
            } else {
                stopTicker()
                persistCurrentProgress(force = true)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val buffering = playbackState == Player.STATE_BUFFERING
            val dur = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: _uiState.value.durationMs
            _uiState.update {
                it.copy(
                    isBuffering = buffering,
                    durationMs = dur,
                    positionMs = player.currentPosition.coerceAtLeast(0L)
                )
            }
            if (playbackState == Player.STATE_ENDED) {
                onMediaEnded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = error.localizedMessage ?: "Playback failed (${error.errorCodeName})"
                )
            }
        }
    }

    init {
        player.addListener(playerListener)
        player.playbackParameters = PlaybackParameters(preferences.defaultPlaybackSpeed)
    }

    fun playMedia(
        item: MediaItem,
        queue: List<MediaItem> = listOf(item),
        startFromBeginning: Boolean = false,
        expandPlayer: Boolean = true
    ) {
        persistCurrentProgress(force = true)

        val effectiveQueue = if (queue.any { it.id == item.id }) queue else listOf(item) + queue
        val targetIndex = effectiveQueue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        val startPosition = if (startFromBeginning) 0L else item.playbackPositionMs.coerceAtLeast(0L)

        val exoMediaItem = ExoMediaItem.Builder()
            .setUri(Uri.parse(item.streamUrl))
            .setMediaId(item.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setSubtitle(item.subtitle)
                    .build()
            )
            .build()

        _uiState.update {
            it.copy(
                currentItem = item,
                queue = effectiveQueue,
                currentIndex = targetIndex,
                isBuffering = true,
                positionMs = startPosition,
                durationMs = item.durationMs,
                playbackSpeed = preferences.defaultPlaybackSpeed,
                resizeMode = preferences.playerResizeMode,
                isPlayerExpanded = expandPlayer,
                errorMessage = null
            )
        }

        player.setMediaItem(exoMediaItem, startPosition)
        player.playbackParameters = PlaybackParameters(_uiState.value.playbackSpeed)
        player.prepare()
        player.play()

        scope.launch {
            repository.updatePlaybackProgress(
                itemId = item.id,
                positionMs = startPosition,
                durationMs = item.durationMs,
                incrementPlayCount = true
            )
        }
    }

    fun retryCurrent() {
        val item = _uiState.value.currentItem ?: return
        val resumePos = _uiState.value.positionMs
        playMedia(
            item = item.copy(playbackPositionMs = resumePos),
            queue = _uiState.value.queue,
            startFromBeginning = false,
            expandPlayer = _uiState.value.isPlayerExpanded
        )
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0L)
            }
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        player.seekTo(clamped)
        _uiState.update { it.copy(positionMs = clamped) }
        persistCurrentProgress(force = true)
    }

    fun seekBy(deltaMs: Long) {
        val dur = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, dur)
        seekTo(target)
    }

    fun skipNext() {
        val state = _uiState.value
        val nextIdx = state.currentIndex + 1
        if (nextIdx in state.queue.indices) {
            playMedia(
                item = state.queue[nextIdx],
                queue = state.queue,
                startFromBeginning = false,
                expandPlayer = state.isPlayerExpanded
            )
        }
    }

    fun skipPrevious() {
        val state = _uiState.value
        if (player.currentPosition > 5_000L) {
            seekTo(0L)
            return
        }
        val prevIdx = state.currentIndex - 1
        if (prevIdx in state.queue.indices) {
            playMedia(
                item = state.queue[prevIdx],
                queue = state.queue,
                startFromBeginning = false,
                expandPlayer = state.isPlayerExpanded
            )
        } else {
            seekTo(0L)
        }
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.75f)
        val cur = _uiState.value.playbackSpeed
        val idx = speeds.indexOfFirst { kotlin.math.abs(it - cur) < 0.05f }
        val next = speeds[(idx + 1) % speeds.size]
        setPlaybackSpeed(next)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.5f)
        player.playbackParameters = PlaybackParameters(clamped)
        preferences.defaultPlaybackSpeed = clamped
        _uiState.update { it.copy(playbackSpeed = clamped) }
    }

    fun cycleResizeMode() {
        val modes = PlayerResizeMode.entries
        val next = modes[(_uiState.value.resizeMode.ordinal + 1) % modes.size]
        preferences.playerResizeMode = next
        _uiState.update { it.copy(resizeMode = next) }
    }

    fun expandPlayer() {
        if (_uiState.value.currentItem != null) {
            _uiState.update { it.copy(isPlayerExpanded = true) }
        }
    }

    fun minimizePlayer() {
        _uiState.update { it.copy(isPlayerExpanded = false) }
    }

    fun stopAndClose() {
        persistCurrentProgress(force = true)
        stopTicker()
        player.stop()
        player.clearMediaItems()
        _uiState.update {
            it.copy(
                currentItem = null,
                queue = emptyList(),
                currentIndex = -1,
                isPlaying = false,
                isBuffering = false,
                positionMs = 0L,
                durationMs = 0L,
                isPlayerExpanded = false,
                errorMessage = null
            )
        }
    }

    private fun onMediaEnded() {
        val state = _uiState.value
        val finishedItem = state.currentItem
        if (finishedItem != null) {
            val dur = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: finishedItem.durationMs
            scope.launch {
                repository.updatePlaybackProgress(
                    itemId = finishedItem.id,
                    positionMs = dur,
                    durationMs = dur,
                    incrementPlayCount = false
                )
            }
        }
        if (preferences.autoPlayNext && state.hasNext) {
            skipNext()
        } else {
            _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
        }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                val pos = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: _uiState.value.durationMs
                _uiState.update {
                    it.copy(
                        positionMs = pos,
                        durationMs = dur
                    )
                }
                val now = System.currentTimeMillis()
                if (now - lastPersistedAtMs >= 5_000L) {
                    persistCurrentProgress(force = false)
                }
                delay(500L)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun persistCurrentProgress(force: Boolean) {
        val item = _uiState.value.currentItem ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistedAtMs < 4_500L) return
        lastPersistedAtMs = now
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: item.durationMs
        if (pos <= 0L && dur <= 0L) return
        scope.launch {
            repository.updatePlaybackProgress(
                itemId = item.id,
                positionMs = pos,
                durationMs = dur,
                incrementPlayCount = false
            )
        }
    }

    fun release() {
        persistCurrentProgress(force = true)
        stopTicker()
        player.removeListener(playerListener)
        player.release()
        scope.cancel()
    }
}
