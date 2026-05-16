package com.ari.streamer.util

import com.ari.streamer.data.Station
import java.io.InputStream
import java.util.Scanner

data class ParsedM3uEntry(
    val title: String,
    val url: String,
    val logoUrl: String?,
    val categoryName: String?
)

object M3uParser {
    fun parse(inputStream: InputStream): List<ParsedM3uEntry> {
        val entries = mutableListOf<ParsedM3uEntry>()
        val scanner = Scanner(inputStream)
        
        var currentTitle = ""
        var currentLogoUrl: String? = null
        var currentCategory: String? = null

        while (scanner.hasNextLine()) {
            val line = scanner.nextLine().trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:")) {
                // Parse #EXTINF:duration tvg-logo="url" group-title="category",Title
                
                val logoRegex = Regex("tvg-logo=\"([^\"]+)\"")
                val logoMatch = logoRegex.find(line)
                currentLogoUrl = logoMatch?.groupValues?.get(1)?.trim()

                val groupRegex = Regex("group-title=\"([^\"]+)\"")
                val groupMatch = groupRegex.find(line)
                currentCategory = groupMatch?.groupValues?.get(1)

                // Extract title (everything after the last comma)
                val commaIndex = line.lastIndexOf(',')
                if (commaIndex != -1 && commaIndex < line.length - 1) {
                    currentTitle = line.substring(commaIndex + 1).trim()
                } else {
                    currentTitle = "Unknown Station"
                }
            } else if (!line.startsWith("#")) {
                // This is likely the URL
                val url = line
                entries.add(
                    ParsedM3uEntry(
                        title = currentTitle,
                        url = url,
                        logoUrl = currentLogoUrl,
                        categoryName = currentCategory
                    )
                )
                // Reset for next entry
                currentTitle = ""
                currentLogoUrl = null
                currentCategory = null
            }
        }
        scanner.close()
        return entries
    }
}
