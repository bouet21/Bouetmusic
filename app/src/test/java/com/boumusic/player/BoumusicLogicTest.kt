package com.boumusic.player

import com.boumusic.player.audio.EqualizerBand
import com.boumusic.player.model.FtpConfig
import com.boumusic.player.model.FtpItem
import com.boumusic.player.model.SongSortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoumusicLogicTest {

    @Test
    fun testFtpItemAudioDetection() {
        val mp3Item = FtpItem("song.mp3", "/music/song.mp3", false, 5000000L)
        assertTrue(mp3Item.isAudio)

        val flacItem = FtpItem("track.FLAC", "/music/track.FLAC", false, 25000000L)
        assertTrue(flacItem.isAudio)

        val opusItem = FtpItem("voice.opus", "/audio/voice.opus", false, 100000L)
        assertTrue(opusItem.isAudio)

        val folderItem = FtpItem("Rock", "/music/Rock", true, 0L)
        assertFalse(folderItem.isAudio)

        val textItem = FtpItem("lyrics.txt", "/music/lyrics.txt", false, 1200L)
        assertFalse(textItem.isAudio)
    }

    @Test
    fun testFtpConfigProtocolLabels() {
        val ftpConfig = FtpConfig(name = "Standard FTP", host = "192.168.1.10", port = 21, isFtps = false, isWebDav = false)
        assertEquals("FTP", ftpConfig.protocolLabel)

        val ftpsConfig = FtpConfig(name = "Secure FTPS", host = "192.168.1.10", port = 21, isFtps = true, isWebDav = false)
        assertEquals("FTPS", ftpsConfig.protocolLabel)

        val webDavConfig = FtpConfig(name = "Nextcloud", host = "cloud.example.com", port = 443, isFtps = false, isWebDav = true)
        assertEquals("WebDAV", webDavConfig.protocolLabel)

        val smbConfig = FtpConfig(name = "PC Windows", host = "192.168.1.100", port = 445, isSmb = true, smbShare = "Musica")
        assertEquals("SMB (Windows/Linux)", smbConfig.protocolLabel)
        assertEquals("Musica", smbConfig.cleanSmbShare)
    }

    @Test
    fun testEqualizerBandLevel() {
        val band = EqualizerBand(0, 60, -1500, 1500, 0)
        assertEquals(0.toShort(), band.currentLevel)
        val updated = band.copy(currentLevel = 500)
        assertEquals(500.toShort(), updated.currentLevel)
    }

    @Test
    fun testSongSortOrderValues() {
        assertEquals("Por defecto", SongSortOrder.DEFAULT.displayName)
        assertEquals("Título (A-Z)", SongSortOrder.TITLE_ASC.displayName)
        assertEquals("Artista (A-Z)", SongSortOrder.ARTIST_ASC.displayName)
        assertEquals("Duración", SongSortOrder.DURATION_DESC.displayName)
        assertEquals("Más recientes", SongSortOrder.RECENT.displayName)
    }

    @Test
    fun testAudioMetadataHelper() {
        val (artist, title) = com.boumusic.player.downloader.AudioMetadataHelper.parseFileNameMetadata("Queen - Bohemian Rhapsody.mp3")
        assertEquals("Queen", artist)
        assertEquals("Bohemian Rhapsody", title)

        val (a2, t2) = com.boumusic.player.downloader.AudioMetadataHelper.parseFileNameMetadata("Alone Track.wav")
        assertEquals("Desconocido", a2)
        assertEquals("Alone Track", t2)
    }

    @Test
    fun testDownloadModels() {
        val track = com.boumusic.player.downloader.DownloadTrackItem(
            id = "t1",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            durationMs = 354000L
        )
        assertEquals("5:54", track.formattedDuration)
        assertEquals("Queen • A Night at the Opera", track.displaySubtitle)

        val destLocal = com.boumusic.player.downloader.DownloadDestination(
            type = com.boumusic.player.downloader.DestinationType.LOCAL,
            label = "Almacenamiento del Teléfono"
        )
        assertEquals(com.boumusic.player.downloader.DestinationType.LOCAL, destLocal.type)

        val destSmb = com.boumusic.player.downloader.DownloadDestination(
            type = com.boumusic.player.downloader.DestinationType.SMB,
            label = "Servidor SMB",
            remoteSubPath = "Musica"
        )
        assertEquals(com.boumusic.player.downloader.DestinationType.SMB, destSmb.type)
        assertEquals("Musica", destSmb.remoteSubPath)
    }
}
