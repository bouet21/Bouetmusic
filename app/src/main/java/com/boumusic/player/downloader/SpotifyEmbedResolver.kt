package com.boumusic.player.downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object SpotifyEmbedResolver {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val SPOTIFY_LINK_REGEX = Pattern.compile(
        "(?:https?://)?open\\.spotify\\.com/(?:intl-[^/\\s]+/)?(track|album|playlist|artist)/([A-Za-z0-9]+)",
        Pattern.CASE_INSENSITIVE
    )

    private val SPOTIFY_URI_REGEX = Pattern.compile(
        "spotify:(track|album|playlist|artist):([A-Za-z0-9]+)",
        Pattern.CASE_INSENSITIVE
    )

    suspend fun resolve(query: String, limit: Int = 30): List<DownloadTrackItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val matcher = SPOTIFY_LINK_REGEX.matcher(trimmed)
        if (matcher.find()) {
            val type = matcher.group(1)?.lowercase() ?: "track"
            val id = matcher.group(2) ?: ""
            return@withContext resolveSpotifyEmbed(type, id, trimmed)
        }

        val uriMatcher = SPOTIFY_URI_REGEX.matcher(trimmed)
        if (uriMatcher.find()) {
            val type = uriMatcher.group(1)?.lowercase() ?: "track"
            val id = uriMatcher.group(2) ?: ""
            return@withContext resolveSpotifyEmbed(type, id, "https://open.spotify.com/$type/$id")
        }

        resolveTextSearch(trimmed, limit)
    }

    private fun resolveSpotifyEmbed(type: String, id: String, originalUrl: String): List<DownloadTrackItem> {
        val embedUrl = "https://open.spotify.com/embed/$type/$id"
        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        try {
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""
            response.close()

            val scriptTag = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
            if (html.contains(scriptTag)) {
                val jsonStr = html.substringAfter(scriptTag).substringBefore("</script>")
                val root = JSONObject(jsonStr)
                val entity = root.optJSONObject("props")
                    ?.optJSONObject("pageProps")
                    ?.optJSONObject("state")
                    ?.optJSONObject("data")
                    ?.optJSONObject("entity")

                if (entity != null) {
                    val entityName = entity.optString("name", entity.optString("title", ""))
                    val entityCover = entity.optJSONObject("visualIdentity")
                        ?.optJSONObject("image")
                        ?.optJSONArray("sources")
                        ?.optJSONObject(0)?.optString("url")
                        ?: entity.optString("coverArt", "")

                    val trackList = entity.optJSONArray("trackList")
                    if (trackList != null && trackList.length() > 0) {
                        val list = mutableListOf<DownloadTrackItem>()
                        for (i in 0 until trackList.length()) {
                            val item = trackList.getJSONObject(i)
                            val tTitle = item.optString("title", "")
                            val tArtist = item.optString("subtitle", "")
                            val tDur = item.optLong("duration", 0L)
                            val tUri = item.optString("uri", "")
                            val tId = if (tUri.contains(":")) tUri.substringAfterLast(":") else "$id-$i"

                            if (tTitle.isNotBlank()) {
                                list.add(
                                    DownloadTrackItem(
                                        id = tId,
                                        title = tTitle,
                                        artist = if (tArtist.isNotBlank()) tArtist else entityName,
                                        album = entityName,
                                        durationMs = tDur,
                                        coverUrl = entityCover.ifBlank { null },
                                        spotifyUrl = if (tUri.isNotBlank()) "https://open.spotify.com/track/$tId" else originalUrl
                                    )
                                )
                            }
                        }
                        if (list.isNotEmpty()) return list
                    }
                }
            }

            // Fallback de pista individual vía oEmbed
            val oembedUrl = "https://open.spotify.com/oembed?url=" + URLEncoder.encode(originalUrl, "UTF-8")
            val oembedReq = Request.Builder().url(oembedUrl).build()
            val oembedResp = client.newCall(oembedReq).execute()
            if (oembedResp.isSuccessful) {
                val oJson = JSONObject(oembedResp.body?.string() ?: "{}")
                oembedResp.close()
                val rawTitle = oJson.optString("title", "Desconocido")
                val cover = oJson.optString("thumbnail_url", "")
                val (artist, title) = AudioMetadataHelper.parseFileNameMetadata(rawTitle)
                return listOf(
                    DownloadTrackItem(
                        id = id,
                        title = title,
                        artist = artist,
                        album = "",
                        durationMs = 0L,
                        coverUrl = cover.ifBlank { null },
                        spotifyUrl = originalUrl
                    )
                )
            }
            oembedResp.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return emptyList()
    }

    private fun resolveTextSearch(query: String, limit: Int): List<DownloadTrackItem> {
        val list = mutableListOf<DownloadTrackItem>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.youtube.com/results?search_query=$encoded"
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: ""
            resp.close()

            val marker = "ytInitialData = "
            if (html.contains(marker)) {
                val jsonPart = html.substringAfter(marker).substringBefore(";</script>").substringBefore(";\n")
                val root = JSONObject(jsonPart)
                val sections = root.optJSONObject("contents")
                    ?.optJSONObject("twoColumnSearchResultsRenderer")
                    ?.optJSONObject("primaryContents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

                if (sections != null) {
                    for (s in 0 until sections.length()) {
                        val secObj = sections.getJSONObject(s)
                        val itemSection = secObj.optJSONObject("itemSectionRenderer") ?: continue
                        val items = itemSection.optJSONArray("contents") ?: continue

                        for (i in 0 until items.length()) {
                            if (list.size >= limit) break
                            val item = items.getJSONObject(i)
                            val video = item.optJSONObject("videoRenderer") ?: continue

                            val videoId = video.optString("videoId", "")
                            if (videoId.isBlank()) continue

                            val titleRuns = video.optJSONObject("title")?.optJSONArray("runs")
                            val title = titleRuns?.optJSONObject(0)?.optString("text", "") ?: ""

                            val channelRuns = video.optJSONObject("ownerText")?.optJSONArray("runs")
                            val channel = channelRuns?.optJSONObject(0)?.optString("text", "Artista") ?: "Artista"

                            val lengthText = video.optJSONObject("lengthText")?.optString("simpleText", "") ?: ""
                            val durMs = parseLengthTextToMs(lengthText)

                            val thumbs = video.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                            val thumb = if (thumbs != null && thumbs.length() > 0) {
                                thumbs.getJSONObject(thumbs.length() - 1).optString("url")
                            } else null

                            val (artist, cleanTitle) = AudioMetadataHelper.parseFileNameMetadata(title)
                            val finalArtist = if (artist.equals("Desconocido", ignoreCase = true)) channel else artist

                            list.add(
                                DownloadTrackItem(
                                    id = videoId,
                                    title = cleanTitle,
                                    artist = finalArtist,
                                    album = "YouTube Stream",
                                    durationMs = durMs,
                                    coverUrl = thumb,
                                    spotifyUrl = "https://www.youtube.com/watch?v=$videoId"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list
    }

    private fun parseLengthTextToMs(text: String): Long {
        if (text.isBlank()) return 180000L
        val parts = text.split(':')
        return try {
            when (parts.size) {
                2 -> ((parts[0].toLong() * 60) + parts[1].toLong()) * 1000L
                3 -> ((parts[0].toLong() * 3600) + (parts[1].toLong() * 60) + parts[2].toLong()) * 1000L
                else -> 180000L
            }
        } catch (_: Exception) {
            180000L
        }
    }
}
