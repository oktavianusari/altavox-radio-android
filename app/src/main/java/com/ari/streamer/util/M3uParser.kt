package com.ari.streamer.util

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
                // Robust regexes supporting spaces around equal signs and single or double quotes
                val logoRegex = Regex("""tvg-logo\s*=\s*["']([^"']*)["']""")
                val logoMatch = logoRegex.find(line)
                currentLogoUrl = logoMatch?.groupValues?.get(1)?.trim()

                val groupRegex = Regex("""group-title\s*=\s*["']([^"']*)["']""")
                val groupMatch = groupRegex.find(line)
                currentCategory = groupMatch?.groupValues?.get(1)?.trim()

                // Robust title extraction:
                // Find separating comma by scanning character by character.
                // This ensures commas inside attribute strings (like URLs) are ignored,
                // while commas separating attributes from titles are correctly identified.
                var commaIndex = -1
                var inDoubleQuotes = false
                var inSingleQuotes = false
                for (i in line.indices) {
                    val char = line[i]
                    if (char == '"' && !inSingleQuotes) {
                        inDoubleQuotes = !inDoubleQuotes
                    } else if (char == '\'' && !inDoubleQuotes) {
                        inSingleQuotes = !inSingleQuotes
                    } else if (char == ',' && !inDoubleQuotes && !inSingleQuotes) {
                        commaIndex = i
                        break
                    }
                }
                
                if (commaIndex != -1 && commaIndex < line.length - 1) {
                    currentTitle = line.substring(commaIndex + 1).trim()
                } else {
                    currentTitle = "Unknown Station"
                }
            } else if (!line.startsWith("#")) {
                // This is the stream URL
                val url = line
                if (currentTitle.isNotEmpty() || url.isNotEmpty()) {
                    entries.add(
                        ParsedM3uEntry(
                            title = if (currentTitle.isEmpty()) "Unknown Station" else currentTitle,
                            url = url,
                            logoUrl = currentLogoUrl,
                            categoryName = currentCategory
                        )
                    )
                }
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
