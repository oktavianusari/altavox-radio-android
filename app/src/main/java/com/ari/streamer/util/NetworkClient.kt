package com.ari.streamer.util

import com.ari.streamer.data.RadioSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.InputStream
import java.util.concurrent.TimeUnit

object NetworkClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun downloadM3u(url: String): InputStream? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                return@withContext response.body?.byteStream()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * Dynamically discovers and returns an active radio-browser.info API server URL.
     * Implements random selection across available mirrors to prevent point-of-failure issues.
     */
    private suspend fun getRadioBrowserServer(): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://all.api.radio-browser.info/json/servers")
                .header("User-Agent", "AltaVoxRadio/1.1")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrEmpty()) {
                        val jsonArray = JSONArray(bodyString)
                        val servers = mutableListOf<String>()
                        for (i in 0 until jsonArray.length()) {
                            val serverObj = jsonArray.getJSONObject(i)
                            val name = serverObj.optString("name")
                            if (name.isNotEmpty()) {
                                servers.add("https://$name")
                            }
                        }
                        if (servers.isNotEmpty()) {
                            return@withContext servers.random()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Fallback to a highly reliable default server if discovery fails
        return@withContext "https://de1.api.radio-browser.info"
    }

    /**
     * Queries the radio-browser.info API to search for stations based on name, country, and city.
     */
    suspend fun searchRadioStations(name: String, country: String, city: String): List<RadioSearchResult> = withContext(Dispatchers.IO) {
        val baseUrl = getRadioBrowserServer()
        val httpUrl = "$baseUrl/json/stations/search".toHttpUrlOrNull() ?: return@withContext emptyList()
        val urlBuilder = httpUrl.newBuilder()

        if (name.isNotBlank()) {
            urlBuilder.addQueryParameter("name", name.trim())
        }
        if (country.isNotBlank()) {
            val trimmedCountry = country.trim()
            if (trimmedCountry.length == 2) {
                urlBuilder.addQueryParameter("countrycode", trimmedCountry.uppercase())
            } else {
                urlBuilder.addQueryParameter("country", trimmedCountry)
            }
        }
        if (city.isNotBlank()) {
            urlBuilder.addQueryParameter("state", city.trim())
        }
        
        // Sorting and result limit settings for optimal performance
        urlBuilder.addQueryParameter("limit", "50")
        urlBuilder.addQueryParameter("order", "votes")
        urlBuilder.addQueryParameter("reverse", "true")
        urlBuilder.addQueryParameter("hidebroken", "true")

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "AltaVoxRadio/1.1")
            .build()

        val results = mutableListOf<RadioSearchResult>()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrEmpty()) {
                        val jsonArray = JSONArray(bodyString)
                        for (i in 0 until jsonArray.length()) {
                            val stationObj = jsonArray.getJSONObject(i)
                            val stationName = stationObj.optString("name", "Unknown Station")
                            val streamUrl = stationObj.optString("url_resolved", "").ifEmpty { stationObj.optString("url", "") }
                            
                            // Skip stations with empty stream urls
                            if (streamUrl.isEmpty()) continue
                            
                            val favicon = stationObj.optString("favicon", "").ifEmpty { null }
                            val stationCountry = stationObj.optString("country", "Unknown")
                            val codec = stationObj.optString("codec", "MP3")
                            val bitrate = stationObj.optInt("bitrate", 128)
                            val stationuuid = stationObj.optString("stationuuid", "")

                            results.add(
                                RadioSearchResult(
                                    name = stationName,
                                    streamUrl = streamUrl,
                                    logoUrl = favicon,
                                    country = stationCountry,
                                    codec = codec,
                                    bitrate = bitrate,
                                    stationuuid = stationuuid
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext results
    }
}
