package com.boumusic.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.boumusic.player.MainActivity
import com.boumusic.player.R
import com.boumusic.player.service.MusicService

class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        when (action) {
            ACTION_UPDATE_WIDGET -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Boumusic"
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Toca para reproducir"
                val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                updateAllWidgets(context, title, artist, isPlaying)
            }
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.boumusic.player.widget.ACTION_UPDATE_WIDGET"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        fun updateAllWidgets(context: Context, title: String, artist: String, isPlaying: Boolean) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, MusicWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

            for (appWidgetId in allWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_music).apply {
                    setTextViewText(R.id.widget_song_title, title)
                    setTextViewText(R.id.widget_song_artist, artist)
                    setImageViewResource(
                        R.id.widget_btn_play_pause,
                        if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                    )
                    setupIntents(context, this)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_music).apply {
                setupIntents(context, this)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun setupIntents(context: Context, views: RemoteViews) {
            // Abrir app al tocar el widget
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            // Play / Pause
            val playPauseIntent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_PLAY_PAUSE
            }
            val playPausePendingIntent = PendingIntent.getService(
                context, 1, playPauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePendingIntent)

            // Siguiente
            val nextIntent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_NEXT
            }
            val nextPendingIntent = PendingIntent.getService(
                context, 2, nextIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)

            // Anterior
            val prevIntent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_PREVIOUS
            }
            val prevPendingIntent = PendingIntent.getService(
                context, 3, prevIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_btn_previous, prevPendingIntent)
        }
    }
}
