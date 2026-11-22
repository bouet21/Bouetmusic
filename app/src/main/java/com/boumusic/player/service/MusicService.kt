package com.boumusic.player.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.boumusic.player.MainActivity
import com.boumusic.player.widget.MusicWidgetProvider

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        try {
            activeAudioSessionId = exoPlayer.audioSessionId
        } catch (e: Exception) {
            e.printStackTrace()
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                notifyWidgetUpdate()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                try {
                    val id = exoPlayer.audioSessionId
                    if (id > 0) activeAudioSessionId = id
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                notifyWidgetUpdate()
            }
        })

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                if (::exoPlayer.isInitialized) {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                }
            }
            ACTION_NEXT -> {
                if (::exoPlayer.isInitialized && exoPlayer.hasNextMediaItem()) {
                    exoPlayer.seekToNextMediaItem()
                }
            }
            ACTION_PREVIOUS -> {
                if (::exoPlayer.isInitialized) {
                    if (exoPlayer.currentPosition > 3000) {
                        exoPlayer.seekTo(0)
                    } else if (exoPlayer.hasPreviousMediaItem()) {
                        exoPlayer.seekToPreviousMediaItem()
                    } else {
                        exoPlayer.seekTo(0)
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun notifyWidgetUpdate() {
        if (!::exoPlayer.isInitialized) return
        try {
            val title = exoPlayer.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Boumusic"
            val artist = exoPlayer.currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Toca para reproducir"
            val isPlaying = exoPlayer.isPlaying
            MusicWidgetProvider.updateAllWidgets(this, title, artist, isPlaying)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        var activeAudioSessionId: Int = 0
        const val ACTION_PLAY_PAUSE = "com.boumusic.player.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.boumusic.player.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.boumusic.player.ACTION_PREVIOUS"
    }
}
