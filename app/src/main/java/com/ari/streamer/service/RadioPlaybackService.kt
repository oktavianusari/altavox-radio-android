package com.ari.streamer.service

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
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
import com.ari.streamer.data.AppDatabase
import com.ari.streamer.data.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.TimeUnit
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import com.google.android.gms.cast.framework.CastContext

@UnstableApi
class RadioPlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.ari.streamer.service.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.ari.streamer.service.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.ari.streamer.service.ACTION_PREVIOUS"
        const val ACTION_PLAY_STATION = "com.ari.streamer.service.ACTION_PLAY_STATION"
        const val EXTRA_STATION_ID = "extra_station_id"
        const val EXTRA_IS_ALARM = "extra_is_alarm"
    }

    private var isCurrentlyPlayingAlarm = false
    private var fallbackRingtone: android.media.Ringtone? = null

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var castPlayer: CastPlayer? = null
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
        
        // Read preference synchronously to guarantee player and session are immediately ready.
        // This prevents the MediaController binding from throwing a SecurityException due to a null session.
        val useLargerBuffer = runBlocking {
            userPreferences.useLargerBufferFlow.first()
        }
        initializePlayer(useLargerBuffer)
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
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            
        player?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL

        player!!.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                super.onPlayerError(error)
                broadcastWidgetState()
                
                if (isCurrentlyPlayingAlarm) {
                    playFallbackRingtone()
                }
            }
            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                if (events.containsAny(
                        androidx.media3.common.Player.EVENT_IS_PLAYING_CHANGED,
                        androidx.media3.common.Player.EVENT_MEDIA_METADATA_CHANGED,
                        androidx.media3.common.Player.EVENT_PLAYBACK_STATE_CHANGED
                    )) {
                    broadcastWidgetState()
                    
                    val currentId = player.currentMediaItem?.mediaId
                    if (currentId != null) {
                        val stationId = currentId.toLongOrNull()
                        if (stationId != null) {
                            serviceScope.launch {
                                val userPrefs = UserPreferences(this@RadioPlaybackService)
                                userPrefs.setLastPlayedStationId(stationId)
                            }
                        }
                    }
                }
            }
        })

        mediaSession = MediaSession.Builder(this, createForwardingPlayer(player!!))
            .setCallback(CustomMediaSessionCallback())
            .setBitmapLoader(CoilBitmapLoader())
            .build()

        try {
            val castContext = CastContext.getSharedInstance(this)
            castPlayer = CastPlayer(castContext)
            castPlayer?.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() {
                    val currentMediaItem = player?.currentMediaItem
                    val playWhenReady = player?.playWhenReady ?: false
                    player?.stop()
                    
                    mediaSession?.player = createForwardingPlayer(castPlayer!!)
                    if (currentMediaItem != null) {
                        castPlayer?.setMediaItem(currentMediaItem)
                        castPlayer?.playWhenReady = playWhenReady
                        castPlayer?.prepare()
                    }
                }

                override fun onCastSessionUnavailable() {
                    val currentMediaItem = castPlayer?.currentMediaItem
                    val playWhenReady = castPlayer?.playWhenReady ?: false
                    castPlayer?.stop()
                    
                    mediaSession?.player = createForwardingPlayer(player!!)
                    if (currentMediaItem != null) {
                        player?.setMediaItem(currentMediaItem)
                        player?.playWhenReady = playWhenReady
                        player?.prepare()
                    }
                }
            })
            
            castPlayer?.addListener(object : androidx.media3.common.Player.Listener {
                override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                    if (events.containsAny(
                            androidx.media3.common.Player.EVENT_IS_PLAYING_CHANGED,
                            androidx.media3.common.Player.EVENT_MEDIA_METADATA_CHANGED,
                            androidx.media3.common.Player.EVENT_PLAYBACK_STATE_CHANGED
                        )) {
                        broadcastWidgetState()
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun createForwardingPlayer(player: Player): ForwardingPlayer {
        return object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .build()
            }

            override fun seekToNext() {
                skipStation(true)
            }

            override fun seekToPrevious() {
                skipStation(false)
            }
        }
    }
    
    private fun playFallbackRingtone() {
        try {
            val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            fallbackRingtone = android.media.RingtoneManager.getRingtone(this@RadioPlaybackService, uri)
            fallbackRingtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun broadcastWidgetState() {
        val currentPlayer = mediaSession?.player ?: player ?: return
        val metadata = currentPlayer.currentMediaItem?.mediaMetadata
        val title = metadata?.title?.toString() ?: metadata?.displayTitle?.toString() ?: "AltaVox Radio"
        val logoUrl = metadata?.artworkUri?.toString()
        val isPlaying = currentPlayer.isPlaying

        // Fetch dynamic audio format details
        val audioFormat = (currentPlayer as? ExoPlayer)?.audioFormat
        val mimeType = audioFormat?.sampleMimeType
        val bitrate = audioFormat?.bitrate ?: -1
        
        val formatName = when {
            mimeType?.contains("mpeg", ignoreCase = true) == true -> "MP3"
            mimeType?.contains("mp4", ignoreCase = true) == true || mimeType?.contains("aac", ignoreCase = true) == true -> "AAC"
            mimeType?.contains("ogg", ignoreCase = true) == true || mimeType?.contains("opus", ignoreCase = true) == true -> "Opus"
            mimeType?.contains("flac", ignoreCase = true) == true -> "FLAC"
            else -> "MP3"
        }
        
        val bitrateKbps = if (bitrate > 0) "${bitrate / 1000} kbps" else "128 kbps"
        val formatBitrate = "$formatName • $bitrateKbps"

        val intent = Intent("com.ari.streamer.widget.ACTION_UPDATE_STATE").apply {
            setPackage(packageName)
            putExtra("extra_station_name", title)
            putExtra("extra_is_playing", isPlaying)
            putExtra("extra_logo_url", logoUrl)
            putExtra("extra_format_bitrate", formatBitrate)
        }
        sendBroadcast(intent)

        val intentLite = Intent("com.ari.streamer.widget.lite.ACTION_UPDATE_STATE").apply {
            setPackage(packageName)
            putExtra("extra_station_name", title)
            putExtra("extra_is_playing", isPlaying)
            putExtra("extra_logo_url", logoUrl)
            putExtra("extra_format_bitrate", formatBitrate)
        }
        sendBroadcast(intentLite)

        val intentFav = Intent("com.ari.streamer.widget.favouritelist.ACTION_UPDATE_STATE").apply {
            setPackage(packageName)
            putExtra("extra_station_name", title)
            putExtra("extra_is_playing", isPlaying)
            putExtra("extra_logo_url", logoUrl)
            putExtra("extra_format_bitrate", formatBitrate)
        }
        sendBroadcast(intentFav)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null) {
            when (action) {
                ACTION_PLAY_PAUSE -> {
                    val currentPlayer = mediaSession?.player ?: player
                    if (currentPlayer != null) {
                        fallbackRingtone?.stop()
                        if (currentPlayer.isPlaying) currentPlayer.pause() else currentPlayer.play()
                    } else {
                        // Cold start: wait for player initialization and automatically start playback
                        serviceScope.launch {
                            var p = player
                            for (i in 1..15) {
                                if (p != null) break
                                delay(200)
                                p = player
                            }
                            if (p != null) {
                                if (p.isPlaying) {
                                    p.pause()
                                } else if (p.mediaItemCount > 0) {
                                    p.play()
                                } else {
                                    val db = AppDatabase.getDatabase(this@RadioPlaybackService)
                                    val stations = db.stationDao().getAllStations().first()
                                    if (stations.isNotEmpty()) {
                                        playStationOnPlayer(stations.first())
                                    }
                                }
                            }
                        }
                    }
                }
                ACTION_NEXT -> {
                    skipStation(true)
                }
                ACTION_PREVIOUS -> {
                    skipStation(false)
                }
                ACTION_PLAY_STATION -> {
                    val stationId = intent.getLongExtra(EXTRA_STATION_ID, -1L)
                    val isAlarm = intent.getBooleanExtra(EXTRA_IS_ALARM, false)
                    if (stationId != -1L) {
                        isCurrentlyPlayingAlarm = isAlarm
                        playStationById(stationId)
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun playStationById(stationId: Long) {
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@RadioPlaybackService)
            val stations = db.stationDao().getAllStations().first()
            val station = stations.find { it.id == stationId } ?: return@launch
            withContext(Dispatchers.Main) {
                playStationOnPlayer(station)
            }
        }
    }

    private fun skipStation(forward: Boolean) {
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@RadioPlaybackService)
            val stations = db.stationDao().getAllStations().first()
            if (stations.isEmpty()) return@launch

            var currentPlayer = mediaSession?.player ?: player
            for (i in 1..10) {
                if (currentPlayer != null) break
                delay(200)
                currentPlayer = mediaSession?.player ?: player
            }
            if (currentPlayer == null) return@launch

            val (currentStationId, currentStreamUrl) = withContext(Dispatchers.Main) {
                Pair(
                    currentPlayer.currentMediaItem?.mediaId,
                    currentPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
                )
            }

            var currentIndex = stations.indexOfFirst { it.id.toString() == currentStationId }
            if (currentIndex == -1 && currentStreamUrl != null) {
                currentIndex = stations.indexOfFirst { it.streamUrl.equals(currentStreamUrl, ignoreCase = true) }
            }

            val targetStation = if (forward) {
                if (currentIndex == -1 || currentIndex == stations.size - 1) stations.first() else stations[currentIndex + 1]
            } else {
                if (currentIndex == -1 || currentIndex == 0) stations.last() else stations[currentIndex - 1]
            }

            withContext(Dispatchers.Main) {
                playStationOnPlayer(targetStation)
            }
        }
    }

    private fun playStationOnPlayer(station: Station) {
        val currentPlayer = mediaSession?.player ?: player ?: return
        currentPlayer.stop()
        val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtworkUri(station.logoUrl?.let { Uri.parse(it) })
            .build()

        val mimeType = when {
            station.streamUrl.endsWith(".m3u8", ignoreCase = true) -> androidx.media3.common.MimeTypes.APPLICATION_M3U8
            station.streamUrl.endsWith(".aac", ignoreCase = true) -> androidx.media3.common.MimeTypes.AUDIO_AAC
            else -> androidx.media3.common.MimeTypes.AUDIO_MPEG // Default fallback for Cast
        }

        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setUri(station.streamUrl)
            .setMediaId(station.id.toString())
            .setMediaMetadata(mediaMetadata)
            .setMimeType(mimeType)
            .build()

        currentPlayer.setMediaItem(mediaItem)
        currentPlayer.prepare()
        currentPlayer.play()
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
        // Reset widget when playback service is destroyed
        val intent = Intent("com.ari.streamer.widget.ACTION_UPDATE_STATE").apply {
            setPackage(packageName)
            putExtra("extra_station_name", "No Active Station")
            putExtra("extra_is_playing", false)
            putExtra("extra_logo_url", null as String?)
        }
        sendBroadcast(intent)

        val intentLite = Intent("com.ari.streamer.widget.lite.ACTION_UPDATE_STATE").apply {
            setPackage(packageName)
            putExtra("extra_station_name", "No Active Station")
            putExtra("extra_is_playing", false)
            putExtra("extra_logo_url", null as String?)
        }
        sendBroadcast(intentLite)

        fallbackRingtone?.stop()
        castPlayer?.release()
        player?.release()
        mediaSession?.release()
        super.onDestroy()
    }

    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
                .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)
                .build()
                
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

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
