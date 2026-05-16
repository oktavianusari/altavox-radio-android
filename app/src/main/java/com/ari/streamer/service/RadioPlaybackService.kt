package com.ari.streamer.service

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil.imageLoader
import coil.request.ImageRequest
import com.ari.streamer.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class RadioPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val userPreferences = UserPreferences(this)
        
        serviceScope.launch {
            val useLargerBuffer = userPreferences.useLargerBufferFlow.first()
            initializePlayer(useLargerBuffer)
        }
    }

    @UnstableApi
    private fun initializePlayer(useLargerBuffer: Boolean) {
        val loadControl = if (useLargerBuffer) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    DefaultLoadControl.DEFAULT_MIN_BUFFER_MS * 4,
                    DefaultLoadControl.DEFAULT_MAX_BUFFER_MS * 4,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS * 2,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS * 2
                ).build()
        } else {
            DefaultLoadControl()
        }

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Set a generic User-Agent for standard web radios that block ExoPlayer default UA
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(CustomMediaSessionCallback())
            .setBitmapLoader(CoilBitmapLoader())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = player
        if (currentPlayer != null && !currentPlayer.playWhenReady || currentPlayer?.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val updatedMediaItems = mediaItems.map { item ->
                val uri = item.requestMetadata.mediaUri
                if (uri != null) {
                    item.buildUpon()
                        .setMediaId(item.mediaId)
                        .setUri(uri)
                        .build()
                } else {
                    item
                }
            }.toMutableList()
            return Futures.immediateFuture(updatedMediaItems)
        }
    }

    private inner class CoilBitmapLoader : BitmapLoader {
        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
            val future = com.google.common.util.concurrent.SettableFuture.create<Bitmap>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
                    if (bitmap != null) {
                        future.set(bitmap)
                    } else {
                        future.setException(IllegalArgumentException("Could not decode image data"))
                    }
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }

        override fun loadBitmap(uri: Uri, options: android.graphics.BitmapFactory.Options?): ListenableFuture<Bitmap> {
            val future = com.google.common.util.concurrent.SettableFuture.create<Bitmap>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(this@RadioPlaybackService)
                        .data(uri)
                        .allowHardware(false) // Software bitmap is required for MediaSession
                        .build()
                    val result = this@RadioPlaybackService.imageLoader.execute(request)
                    val drawable = result.drawable
                    if (drawable != null) {
                        future.set(drawable.toBitmap())
                    } else {
                        future.setException(IllegalArgumentException("Could not load image from uri: $uri"))
                    }
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }
    }
}
