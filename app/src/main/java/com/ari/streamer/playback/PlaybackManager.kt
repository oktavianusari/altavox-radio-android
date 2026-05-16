package com.ari.streamer.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ari.streamer.data.Station
import com.ari.streamer.service.RadioPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentStation: Station? = null,
    val nowPlayingTitle: String? = null,
    val format: String? = null,
    val bitrate: String? = null
)

class PlaybackManager(context: Context) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    init {
        val sessionToken = SessionToken(context, ComponentName(context, RadioPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                mediaController = controllerFuture?.get()
                setupControllerListener()
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun setupControllerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                // Icy metadata or regular metadata
                val title = mediaMetadata.title?.toString() ?: mediaMetadata.displayTitle?.toString()
                _playbackState.value = _playbackState.value.copy(nowPlayingTitle = title)
            }

            override fun onTracksChanged(tracks: Tracks) {
                var formatName: String? = null
                var bitrateStr: String? = null

                for (trackGroup in tracks.groups) {
                    if (trackGroup.type == C.TRACK_TYPE_AUDIO && trackGroup.isSelected) {
                        val format = trackGroup.getTrackFormat(0)
                        val mimeType = format.sampleMimeType
                        val bitrate = format.bitrate
                        
                        formatName = when {
                            mimeType?.contains("mpeg") == true -> "MP3"
                            mimeType?.contains("mp4a") == true || mimeType?.contains("aac") == true -> "AAC"
                            mimeType?.contains("ogg") == true -> "OGG"
                            else -> mimeType?.substringAfterLast("/")?.uppercase() ?: "UNKNOWN"
                        }
                        
                        if (bitrate > 0) {
                            bitrateStr = "${bitrate / 1000} kbps"
                        }
                        break
                    }
                }
                // For HLS streams, the audio might be AAC but the stream is HLS.
                // We can guess it's HLS if the url ends with m3u8. We can check that in currentStation.
                val streamUrl = _playbackState.value.currentStation?.streamUrl?.lowercase()
                if (streamUrl?.contains(".m3u8") == true) {
                    formatName = "HLS"
                }

                _playbackState.value = _playbackState.value.copy(
                    format = formatName,
                    bitrate = bitrateStr
                )
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                super.onPlayerError(error)
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    nowPlayingTitle = "Error: ${error.message ?: "Playback failed"}",
                    format = null,
                    bitrate = null
                )
            }
        })
    }

    fun playStation(station: Station) {
        val controller = mediaController ?: return
        
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtworkUri(station.logoUrl?.let { Uri.parse(it) })
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(station.streamUrl)
            .setMediaId(station.id.toString())
            .setMediaMetadata(mediaMetadata)
            .build()

        _playbackState.value = _playbackState.value.copy(
            currentStation = station,
            nowPlayingTitle = null,
            format = null,
            bitrate = null
        )

        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun play() {
        mediaController?.play()
    }

    fun stop() {
        mediaController?.stop()
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    fun release() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
    }
}
