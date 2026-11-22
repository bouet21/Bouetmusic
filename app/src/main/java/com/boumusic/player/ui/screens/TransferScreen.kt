package com.boumusic.player.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boumusic.player.ui.theme.DarkBackground
import com.boumusic.player.ui.theme.DarkCardBorder
import com.boumusic.player.ui.theme.DarkSurface
import com.boumusic.player.ui.theme.DarkSurfaceVariant
import com.boumusic.player.ui.theme.DeepCyan
import com.boumusic.player.ui.theme.ElectricPurple
import com.boumusic.player.ui.theme.NeonCyan
import com.boumusic.player.ui.theme.TextPrimary
import com.boumusic.player.ui.theme.TextSecondary
import com.boumusic.player.ui.viewmodel.MusicViewModel

@Composable
fun TransferScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isRunning by viewModel.isTransferServerRunning.collectAsState()
    val serverUrl by viewModel.transferServerUrl.collectAsState()
    val transferredCount by viewModel.transferredFilesCount.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(20.dp)
    ) {
        // Cabecera superior
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Volver",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Transferir desde PC",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tarjeta de estado del servidor Wi-Fi
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface)
                .border(1.dp, if (isRunning) NeonCyan.copy(alpha = 0.5f) else DarkCardBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) NeonCyan.copy(alpha = 0.15f) else DarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = if (isRunning) NeonCyan else TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Servidor Wi-Fi Local",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isRunning) "En línea y esperando canciones" else "Desactivado",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isRunning) NeonCyan else TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = isRunning,
                        onCheckedChange = { active ->
                            if (active) viewModel.startTransferServer() else viewModel.stopTransferServer()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = NeonCyan,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )
                }

                if (isRunning && serverUrl != null) {
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Abre este enlace en el navegador de tu ordenador:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = serverUrl!!,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = NeonCyan
                        )

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("URL de Boumusic Transfer", serverUrl)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Enlace copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copiar enlace",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = ElectricPurple,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Carpeta de destino: Music/Boumusic",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    if (transferredCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Canciones recibidas en esta sesión: $transferredCount",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = NeonCyan
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Instrucciones paso a paso
        Text(
            text = "¿CÓMO FUNCIONA?",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold),
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        InstructionStep(
            number = "1",
            title = "Misma red Wi-Fi",
            description = "Conecta tu ordenador y tu móvil a la misma red Wi-Fi o punto de acceso."
        )

        Spacer(modifier = Modifier.height(12.dp))

        InstructionStep(
            number = "2",
            title = "Abre la dirección en tu PC",
            description = "Escribe la dirección web indicada arriba en Chrome, Edge o Firefox en tu ordenador."
        )

        Spacer(modifier = Modifier.height(12.dp))

        InstructionStep(
            number = "3",
            title = "Arrastra y suelta tus archivos",
            description = "Arrastra tus canciones MP3 o FLAC a la ventana del navegador. Boumusic creará la carpeta automáticamente y las indexará al instante."
        )

        Spacer(modifier = Modifier.height(12.dp))

        InstructionStep(
            number = "4",
            title = "Comparte con amigos mediante QR",
            description = "Pulsa en los tres puntos (⋮) de cualquier canción o lista y elige 'Compartir con QR'. Tu amigo podrá escanearlo con su cámara y descargar la música al instante."
        )
    }
}

@Composable
private fun InstructionStep(
    number: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(NeonCyan, DeepCyan)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = DarkBackground
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}
