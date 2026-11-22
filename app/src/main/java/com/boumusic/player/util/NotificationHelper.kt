package com.boumusic.player.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.boumusic.player.R

object NotificationHelper {
    private const val CHANNEL_ID = "boumusic_alerts"
    private const val CHANNEL_NAME = "Avisos de Boumusic"
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
            // Manejo de seguridad en caso de permisos denegados en Android 13+
            e.printStackTrace()
        }
    }
}
