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
    private const val DOWNLOAD_CHANNEL_ID = "boumusic_downloads"
    private const val DOWNLOAD_CHANNEL_NAME = "Descargas de Música"
    private const val TRANSFER_CHANNEL_ID = "boumusic_transfer"
    private const val TRANSFER_CHANNEL_NAME = "Servidor Wi-Fi PC"

    private const val NOTIFICATION_ID_SKIP = 1002
    private const val NOTIFICATION_ID_STATS = 1003
    private const val NOTIFICATION_ID_DOWNLOAD = 1004
    private const val NOTIFICATION_ID_TRANSFER = 1005

    private var isChannelsCreated = false

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isChannelsCreated) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val alertChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de salto cuando un servidor remoto (FTP/SMB/WebDAV) no está disponible"
            }

            val statsChannel = NotificationChannel(
                STATS_CHANNEL_ID,
                STATS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones periódicas cada 3 meses con tus estadísticas y resumen musical"
            }

            val downloadChannel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                DOWNLOAD_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Progreso y estado de descargas desde servidores remotos"
            }

            val transferChannel = NotificationChannel(
                TRANSFER_CHANNEL_ID,
                TRANSFER_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Estado del servidor Wi-Fi de transferencia desde PC"
            }

            notificationManager.createNotificationChannels(listOf(alertChannel, statsChannel, downloadChannel, transferChannel))
            isChannelsCreated = true
        }
    }

    fun notifyRemoteSkipped(context: Context, songTitle: String, protocol: String = "Remoto") {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        ensureChannels(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setContentTitle("Servidor $protocol no disponible")
            .setContentText("Saltando: $songTitle")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("El servidor $protocol no responde o el archivo no está accesible. Se ha saltado \"$songTitle\" automáticamente.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_SKIP, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun notifyFtpSkipped(context: Context, songTitle: String) {
        notifyRemoteSkipped(context, songTitle, "FTP/SMB")
    }

    fun notifyDownloadStarted(context: Context, fileName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        ensureChannels(context)

        val notification = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setContentTitle("Descargando música")
            .setContentText(fileName)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_DOWNLOAD, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun notifyDownloadCompleted(context: Context, fileName: String, success: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        ensureChannels(context)

        val title = if (success) "Descarga completada" else "Error al descargar"
        val message = if (success) "$fileName guardada en Música/Boumusic" else "No se pudo descargar $fileName"

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2002,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setContentTitle(title)
            .setContentText(fileName)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_DOWNLOAD, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun notifyTransferServerState(context: Context, isRunning: Boolean, serverUrl: String?) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        ensureChannels(context)

        if (!isRunning || serverUrl == null) {
            try {
                notificationManager.cancel(NOTIFICATION_ID_TRANSFER)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2003,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, TRANSFER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setContentTitle("Servidor Wi-Fi activo")
            .setContentText(serverUrl)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Pasa canciones desde tu PC navegando a:\n$serverUrl"))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_TRANSFER, notification)
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
        ensureChannels(context)

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
            notificationManager.notify(NOTIFICATION_ID_STATS, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
