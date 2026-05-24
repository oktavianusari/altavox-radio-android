package com.ari.streamer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import coil.request.ImageRequest
import com.ari.streamer.MainActivity
import com.ari.streamer.R
import com.ari.streamer.data.AppDatabase
import com.ari.streamer.data.Station
import com.ari.streamer.service.RadioPlaybackService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RadioWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_STATE = "com.ari.streamer.widget.ACTION_UPDATE_STATE"
        const val ACTION_PLAY_PAUSE = "com.ari.streamer.widget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.ari.streamer.widget.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.ari.streamer.widget.ACTION_PREVIOUS"
        const val ACTION_PLAY_STATION = "com.ari.streamer.widget.ACTION_PLAY_STATION"

        const val EXTRA_STATION_NAME = "extra_station_name"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_LOGO_URL = "extra_logo_url"
        const val EXTRA_FORMAT_BITRATE = "extra_format_bitrate"
        const val EXTRA_STATION_ID = "extra_station_id"

        private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        coroutineScope.launch {
            try {
                // Fetch the current playing state if the service is running
                val sessionToken = SessionToken(appContext, ComponentName(appContext, RadioPlaybackService::class.java))
                val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
                
                var isPlaying = false
                var title = "No Active Station"
                var logoUrl: String? = null
                var formatBitrate = "MP3 • 128 kbps"
                
                try {
                    val controller = withContext(Dispatchers.IO) {
                        try {
                            controllerFuture.get(1, TimeUnit.SECONDS)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (controller != null) {
                        isPlaying = controller.isPlaying
                        val mediaMetadata = controller.currentMediaItem?.mediaMetadata
                        title = mediaMetadata?.title?.toString() ?: mediaMetadata?.displayTitle?.toString() ?: "AltaVox Radio"
                        logoUrl = mediaMetadata?.artworkUri?.toString()
                        
                        var audioFormat: androidx.media3.common.Format? = null
                        val currentTracks = controller.currentTracks
                        for (group in currentTracks.groups) {
                            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                                for (i in 0 until group.length) {
                                    if (group.isTrackSelected(i)) {
                                        audioFormat = group.getTrackFormat(i)
                                        break
                                    }
                                }
                            }
                            if (audioFormat != null) break
                        }
                        
                        val mimeType = audioFormat?.sampleMimeType
                        val bitrate = audioFormat?.bitrate ?: -1
                        val formatName = when {
                            mimeType?.contains("mpeg", ignoreCase = true) == true -> "MP3"
                            mimeType?.contains("mp4", ignoreCase = true) == true || mimeType?.contains("aac", ignoreCase = true) == true -> "AAC"
                            else -> "MP3"
                        }
                        val bitrateKbps = if (bitrate > 0) "${bitrate / 1000} kbps" else "128 kbps"
                        formatBitrate = "$formatName • $bitrateKbps"
                    } else {
                        // Fallback: show the first station in the database if nothing is active
                        val db = AppDatabase.getDatabase(context)
                        val stations = db.stationDao().getAllStations().first()
                        if (stations.isNotEmpty()) {
                            title = stations.first().name
                            logoUrl = stations.first().logoUrl
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to database
                    val db = AppDatabase.getDatabase(context)
                    val stations = db.stationDao().getAllStations().first()
                    if (stations.isNotEmpty()) {
                        title = stations.first().name
                        logoUrl = stations.first().logoUrl
                    }
                } finally {
                    MediaController.releaseFuture(controllerFuture)
                }

                for (appWidgetId in appWidgetIds) {
                    updateWidgetUi(context, appWidgetManager, appWidgetId, title, isPlaying, logoUrl, formatBitrate)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        if (action == ACTION_UPDATE_STATE) {
            val title = intent.getStringExtra(EXTRA_STATION_NAME) ?: "AltaVox Radio"
            val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
            val logoUrl = intent.getStringExtra(EXTRA_LOGO_URL)
            val formatBitrate = intent.getStringExtra(EXTRA_FORMAT_BITRATE) ?: "MP3 • 128 kbps"

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, RadioWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(component)

            coroutineScope.launch {
                for (appWidgetId in appWidgetIds) {
                    updateWidgetUi(context, appWidgetManager, appWidgetId, title, isPlaying, logoUrl, formatBitrate)
                }
            }
        } else if (action == ACTION_PLAY_PAUSE ||
                   action == ACTION_NEXT ||
                   action == ACTION_PREVIOUS ||
                   action == ACTION_PLAY_STATION) {
            
            val pendingResult = goAsync()
            val appContext = context.applicationContext
            coroutineScope.launch {
                try {
                    val sessionToken = SessionToken(appContext, ComponentName(appContext, RadioPlaybackService::class.java))
                    val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
                    
                    val controller = withContext(Dispatchers.IO) {
                        try {
                            controllerFuture.get(2, TimeUnit.SECONDS)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    
                    if (controller != null) {
                        handleWidgetAction(context, controller, action, intent)
                    }
                    MediaController.releaseFuture(controllerFuture)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private suspend fun updateWidgetUi(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        title: String,
        isPlaying: Boolean,
        logoUrl: String?,
        formatBitrate: String
    ) {
        val views = RemoteViews(context.packageName, R.layout.radio_widget_layout)
        
        // Dynamically style background shape overlay based on user preferences
        try {
            val userPrefs = com.ari.streamer.data.UserPreferences(context)
            val bgColorHex = userPrefs.widget1BgColorFlow.first()
            val opacity = userPrefs.widget1OpacityFlow.first()
            val color = android.graphics.Color.parseColor(bgColorHex)
            views.setInt(R.id.widget_bg_shape, "setColorFilter", color)
            views.setInt(R.id.widget_bg_shape, "setImageAlpha", (opacity * 255).toInt())
        } catch (e: Exception) {
            views.setInt(R.id.widget_bg_shape, "setColorFilter", android.graphics.Color.parseColor("#151517"))
            views.setInt(R.id.widget_bg_shape, "setImageAlpha", (0.9f * 255).toInt())
        }

        // Handle "No Active Station" name fallback
        val displayTitle = if (title == "No Active Station") "AltaVox Radio" else title
        val displayFormat = if (title == "No Active Station") "Live Streaming" else formatBitrate

        views.setTextViewText(R.id.widget_title, displayTitle)
        views.setTextViewText(R.id.widget_artist, displayFormat)

        // Update Play/Pause Icon
        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)

        // Asynchronously load the active logo bitmap with beautiful rounded corners natively
        val density = context.resources.displayMetrics.density
        val bitmap = if (!logoUrl.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(logoUrl)
                        .size(128)
                        .allowHardware(false) // Software bitmaps only for canvas drawing!
                        .build()
                    val raw = context.imageLoader.execute(request).drawable?.toBitmap()
                    raw?.let { getRoundedCornerBitmap(it, (12 * density).toInt()) }
                } catch (e: Throwable) {
                    null
                }
            }
        } else null

        if (bitmap != null) {
            views.setImageViewBitmap(R.id.widget_background, bitmap)
        } else {
            views.setImageViewResource(R.id.widget_background, R.drawable.ic_radio)
        }

        // BIND CONTROL PENDING INTENTS (Secure Broadcast Receiver calls)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val playPauseIntent = Intent(context, RadioWidgetProvider::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getBroadcast(context, 1, playPauseIntent, pendingIntentFlags)
        views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePendingIntent)

        val nextIntent = Intent(context, RadioWidgetProvider::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getBroadcast(context, 2, nextIntent, pendingIntentFlags)
        views.setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)

        val prevIntent = Intent(context, RadioWidgetProvider::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val prevPendingIntent = PendingIntent.getBroadcast(context, 3, prevIntent, pendingIntentFlags)
        views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPendingIntent)

        // Separate launch app click targets from controls container to avoid interception
        val appIntent = Intent(context, MainActivity::class.java)
        val appPendingIntent = PendingIntent.getActivity(context, 4, appIntent, pendingIntentFlags)
        views.setOnClickPendingIntent(R.id.widget_background, appPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_meta_container, appPendingIntent)

        // BIND FAVOURITES QUICK PLAY LIST (Top 2 Favourites)
        val db = AppDatabase.getDatabase(context)
        val categories = withContext(Dispatchers.IO) { db.stationDao().getAllCategories().first() }
        val favCategory = categories.find { it.name.equals("Favourites", ignoreCase = true) }
        
        val favStations = if (favCategory != null) {
            withContext(Dispatchers.IO) { db.stationDao().getStationsByCategory(favCategory.id).first().take(2) }
        } else emptyList()

        val displayFavs = if (favStations.isNotEmpty()) favStations else {
            val allStations = withContext(Dispatchers.IO) { db.stationDao().getAllStations().first() }
            allStations.take(2)
        }

        if (displayFavs.isEmpty()) {
            views.setViewVisibility(R.id.widget_bottom_card, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_bottom_card, View.VISIBLE)

            // Setup Item 1
            if (displayFavs.size >= 1) {
                val station = displayFavs[0]
                views.setViewVisibility(R.id.widget_fav_item_1, View.VISIBLE)
                views.setTextViewText(R.id.widget_fav_1_name, station.name)
                
                val favLogo1 = station.logoUrl
                val favBitmap1 = if (!favLogo1.isNullOrEmpty()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val request = ImageRequest.Builder(context)
                                .data(favLogo1)
                                .size(128)
                                .allowHardware(false)
                                .build()
                            val raw = context.imageLoader.execute(request).drawable?.toBitmap()
                            raw?.let { getRoundedCornerBitmap(it, (12 * density).toInt()) }
                        } catch (e: Throwable) {
                            null
                        }
                    }
                } else null
                
                if (favBitmap1 != null) {
                    views.setImageViewBitmap(R.id.widget_fav_1_logo, favBitmap1)
                } else {
                    views.setImageViewResource(R.id.widget_fav_1_logo, R.drawable.ic_radio)
                }

                val favPlayIntent1 = Intent(context, RadioWidgetProvider::class.java).apply {
                    action = ACTION_PLAY_STATION
                    putExtra(EXTRA_STATION_ID, station.id)
                }
                val favPlayPending1 = PendingIntent.getBroadcast(context, 10, favPlayIntent1, pendingIntentFlags)
                views.setOnClickPendingIntent(R.id.widget_fav_item_1, favPlayPending1)
            } else {
                views.setViewVisibility(R.id.widget_fav_item_1, View.GONE)
            }

            // Setup Item 2
            if (displayFavs.size >= 2) {
                val station = displayFavs[1]
                views.setViewVisibility(R.id.widget_fav_item_2, View.VISIBLE)
                views.setTextViewText(R.id.widget_fav_2_name, station.name)
                
                val favLogo2 = station.logoUrl
                val favBitmap2 = if (!favLogo2.isNullOrEmpty()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val request = ImageRequest.Builder(context)
                                .data(favLogo2)
                                .allowHardware(false)
                                .build()
                            val raw = context.imageLoader.execute(request).drawable?.toBitmap()
                            raw?.let { getRoundedCornerBitmap(it, (12 * density).toInt()) }
                        } catch (e: Throwable) {
                            null
                        }
                    }
                } else null
                
                if (favBitmap2 != null) {
                    views.setImageViewBitmap(R.id.widget_fav_2_logo, favBitmap2)
                } else {
                    views.setImageViewResource(R.id.widget_fav_2_logo, R.drawable.ic_radio)
                }

                val favPlayIntent2 = Intent(context, RadioWidgetProvider::class.java).apply {
                    action = ACTION_PLAY_STATION
                    putExtra(EXTRA_STATION_ID, station.id)
                }
                val favPlayPending2 = PendingIntent.getBroadcast(context, 11, favPlayIntent2, pendingIntentFlags)
                views.setOnClickPendingIntent(R.id.widget_fav_item_2, favPlayPending2)
            } else {
                views.setViewVisibility(R.id.widget_fav_item_2, View.GONE)
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap, pixels: Int): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val rect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = android.graphics.RectF(rect)
        val roundPx = pixels.toFloat()

        canvas.drawARGB(0, 0, 0, 0)
        paint.color = android.graphics.Color.WHITE
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint)

        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    private suspend fun handleWidgetAction(context: Context, controller: MediaController, action: String, intent: Intent) {
        val db = AppDatabase.getDatabase(context)
        val userPrefs = com.ari.streamer.data.UserPreferences(context)

        when (action) {
            ACTION_PLAY_PAUSE -> {
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    if (controller.mediaItemCount > 0) {
                        controller.play()
                    } else {
                        val lastPlayedId = userPrefs.lastPlayedStationIdFlow.first()
                        val stations = db.stationDao().getAllStations().first()
                        if (stations.isNotEmpty()) {
                            val targetStation = stations.find { it.id == lastPlayedId } ?: stations.first()
                            playStationOnController(controller, targetStation, userPrefs)
                        }
                    }
                }
            }
            ACTION_NEXT -> {
                val stations = db.stationDao().getAllStations().first()
                if (stations.isNotEmpty()) {
                    val currentId = controller.currentMediaItem?.mediaId
                    val currentIndex = stations.indexOfFirst { it.id.toString() == currentId }
                    val targetStation = if (currentIndex == -1 || currentIndex == stations.size - 1) {
                        stations.first()
                    } else {
                        stations[currentIndex + 1]
                    }
                    playStationOnController(controller, targetStation, userPrefs)
                }
            }
            ACTION_PREVIOUS -> {
                val stations = db.stationDao().getAllStations().first()
                if (stations.isNotEmpty()) {
                    val currentId = controller.currentMediaItem?.mediaId
                    val currentIndex = stations.indexOfFirst { it.id.toString() == currentId }
                    val targetStation = if (currentIndex == -1 || currentIndex == 0) {
                        stations.last()
                    } else {
                        stations[currentIndex - 1]
                    }
                    playStationOnController(controller, targetStation, userPrefs)
                }
            }
            ACTION_PLAY_STATION -> {
                val stationId = intent.getLongExtra(EXTRA_STATION_ID, -1L)
                if (stationId != -1L) {
                    val stations = db.stationDao().getAllStations().first()
                    val targetStation = stations.find { it.id == stationId }
                    if (targetStation != null) {
                        playStationOnController(controller, targetStation, userPrefs)
                    }
                }
            }
        }
    }

    private suspend fun playStationOnController(controller: MediaController, station: Station, userPrefs: com.ari.streamer.data.UserPreferences) {
        val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtworkUri(station.logoUrl?.let { Uri.parse(it) })
            .build()

        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setUri(station.streamUrl)
            .setMediaId(station.id.toString())
            .setMediaMetadata(mediaMetadata)
            .build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()

        userPrefs.setLastPlayedStationId(station.id)
    }
}

