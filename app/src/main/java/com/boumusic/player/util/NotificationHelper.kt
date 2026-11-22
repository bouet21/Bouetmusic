package com.boumusic.player.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.boumusic.player.MainActivity
import com.boumusic.player.R

object NotificationHelper {
    private const val CHANNEL_ID = "boumusic_alerts"
    private const val CHANNEL_NAME = "Avisos de Boumusic"
    private const val STATS_CHANNEL_ID = "boumusic_stats"
    private const val STATS_CHANNEL_NAME = "Resumen y Estadísticas"
    private var isChannelCreated = false

    fun notifyFtpSkipped(context: Context, songTitle: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isChannelCreated) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de salto cuando un servidor FTP o canción remota no está disponible"
            }
            notificationManager.createNotificationChannel(channel)
            isChannelCreated = true
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setContentTitle("Servidor FTP no disponible")
            .setContentText("Saltando canción: $songTitle")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("El servidor FTP no está conectado o no se pudo acceder al archivo. Se ha saltado \"$songTitle\" automáticamente.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(1002, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun notifyQuarterlyStatsReady(
        context: Context,
        quarterLabel: String,
        totalMinutes: Long,
        topSongTitle: String,
        topArtist: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                STATS_CHANNEL_ID,
                STATS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones periódicas cada 3 meses con tus estadísticas y resumen musical"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_stats", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2001,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, STATS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setContentTitle("🎉 ¡Tu Resumen de $quarterLabel está listo!")
            .setContentText("Has escuchado $totalMinutes min. Top: $topSongTitle • $topArtist")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Durante este trimestre has disfrutado de $totalMinutes minutos de música en Boumusic.\n\n🥇 Canción más escuchada: $topSongTitle\n🎤 Artista destacado: $topArtist\n\nToca para explorar tu resumen completo y gráficos.")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(1003, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
