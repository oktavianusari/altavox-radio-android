package com.ari.streamer.data

data class RadioSearchResult(
    val name: String,
    val streamUrl: String,
    val logoUrl: String?,
    val country: String,
    val codec: String,
    val bitrate: Int,
    val stationuuid: String
)
