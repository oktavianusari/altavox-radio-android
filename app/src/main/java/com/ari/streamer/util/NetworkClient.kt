package com.ari.streamer.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

object NetworkClient {
    private val client = OkHttpClient()

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
}
