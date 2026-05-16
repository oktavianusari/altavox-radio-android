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
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.AdtsExtractor
import coil.imageLoader
import coil.request.ImageRequest
import com.ari.streamer.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.TimeUnit

@UnstableApi
class RadioPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Robust cookie jar that handles domain/path matching correctly
    private val cookieJar = object : CookieJar {
        private val cookieStore = mutableListOf<Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            // Remove old cookies for the same name and domain to avoid bloat
            cookies.forEach { cookie ->
                cookieStore.removeAll { it.name == cookie.name && it.domain == cookie.domain }
            }
            cookieStore.addAll(cookies)
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            // Use OkHttp's built-in matching logic
            return cookieStore.filter { it.matches(url) }
        }
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

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
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                5_000,  // min buffer 5s (more responsive for live)
                30_000, // max buffer 30s
                1_000,  // buffer for playback 1s
                2_000   // buffer after rebuffer 2s
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Use OkHttpDataSource for better redirection and cookie handling
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(mapOf(
                "Accept" to "*/*",
                "Connection" to "keep-alive"
            ))

        // Configure ADTS extractor to be more resilient to VBR and sync issues
        val extractorsFactory = DefaultExtractorsFactory()
            .setAdtsExtractorFlags(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)

        val mediaSourceFactory = DefaultMediaSourceFactory(this, extractorsFactory)
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
                var uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
                
                if (uri != null) {
                    var uriString = uri.toString()
                    
                    if (uriString.contains("streamtheworld.com")) {
                        // Force FLV container by removing extensions. FLV handles ad-to-stream 
                        // transitions (MP3 to AAC) much better than raw ADTS (.aac) files.
                        uriString = uriString
                            .replace(".aac", "")
                            .replace(".mp3", "")
                            .replace("GOLD905AAC", "GOLD905")
                            .replace("SYMPHONY924AAC", "SYMPHONY924")
                            .replace("ONE_FM_913AAC", "ONE_FM_913")
                        
                        // Add stability and SDK identification parameters
                        if (!uriString.contains("dist=")) {
                            val separator = if (uriString.contains("?")) "&" else "?"
                            uriString += "${separator}dist=triton_player_sdk&sb=1"
                        } else if (!uriString.contains("sb=")) {
                            uriString += "&sb=1"
                        }
                        
                        uri = Uri.parse(uriString)
                    }
                    
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
