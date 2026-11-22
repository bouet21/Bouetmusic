# 🎵 Boumusic — Reproductor de Música Nativo para Android

[![Release](https://img.shields.io/github/v/release/bouet21/Bouetmusic?color=00F5D4&label=Versi%C3%B3n)](https://github.com/bouet21/Bouetmusic/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-7B2CBF)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-brightgreen)](https://developer.android.com/jetpack/compose)
[![No Ads](https://img.shields.io/badge/Publicidad-0%25%20(Sin%20Anuncios)-success)](#)

**Boumusic** es un reproductor de música nativo para Android diseñado desde cero para disfrutar de tu música local y remota con la máxima calidad, interfaz moderna con tema oscuro (Neon Cyan y Electric Purple), cero anuncios y herramientas innovadoras como **transferencia por código QR** y **exploración/streaming desde servidores FTP**.

---

## 📲 Descarga Directa

Descarga siempre la versión más reciente en formato APK lista para instalar:

👉 **[Descargar Boumusic v1.3.0 (APK)](https://github.com/bouet21/Bouetmusic/releases/download/v1.3.0/Boumusic.apk)**  
*(o visita todas las versiones en la sección de [Releases de GitHub](https://github.com/bouet21/Bouetmusic/releases))*

---

## ✨ Características Principales

### 📱 1. Compartir Música por Código QR (¡Novedad v1.3.0!)
* **Pasa canciones y listas a tus amigos sin cables**:
  * Pulsa en el menú (⋮) de cualquier canción o en el botón QR del reproductor para generar su código QR al instante.
  * Pulsa en el icono de QR de cualquier lista de reproducción para compartirla completa.
* **Sin necesidad de instalar aplicaciones**:
  * Tu amigo escanea el código con la cámara de su móvil (Android o iPhone) y se abre una página web elegante en su navegador.
  * **Vista previa y streaming**: Puede escuchar la canción directamente en el navegador antes de descargarla.
  * **Descarga directa**: Guarda el archivo de audio original en el móvil del receptor sin compresión.
  * **Descarga de listas en ZIP**: Generación dinámica en memoria de un único archivo `.zip` con todas las pistas de la lista.
  * Funciona conectándose a la misma red Wi-Fi o a la **Zona Wi-Fi móvil (Hotspot)** de tu teléfono si estás fuera de casa.

---

### 🌐 2. Explorador y Streaming FTP Avanzado
* **Conexión a servidores FTP**: Conéctate a tu NAS, PC, servidor doméstico o router mediante Host, Puerto, Usuario y Contraseña.
* **Servidores favoritos**: Guarda tus configuraciones frecuentes para reconectarte en un toque.
* **Streaming remoto fluido con soporte HTTP `Range`**: Reproduce directamente desde el servidor FTP sin esperar a que se descargue el archivo completo, con pre-buffer para evitar pausas.
* **Cola de reproducción completa en FTP**: Al seleccionar una canción en una carpeta FTP, se encolan automáticamente todas las pistas del directorio, permitiendo saltar canciones (⏭️ / ⏮️) sin interrupciones.
* **Descarga a local**: Descarga cualquier canción del servidor FTP a la carpeta `Music/Boumusic/` de tu teléfono para escucharla sin conexión.

---

### 💻➡️📱 3. Transferencia Wi-Fi por Lotes desde PC
* **Servidor web local integrado (Puerto 8080)**: Olvídate de los cables USB.
* **Subida masiva por lotes (Drag & Drop)**: Arrastra decenas de archivos de audio desde tu navegador de PC (Chrome, Firefox, Edge).
* **Organización automática**: Los archivos se guardan directamente en `Music/Boumusic/` y se indexan en tiempo real en la biblioteca sin necesidad de reiniciar la app.

---

### 📑 4. Gestión Completa de Listas de Reproducción
* **Creación y personalización**: Crea listas de reproducción desde la pestaña **Listas** o directamente al escuchar una canción.
* **Añadir / quitar canciones**: Gestión rápida desde el menú contextual (⋮) de cualquier canción.
* **Exportación e Importación `.m3u`**: Exporta tus listas al formato estándar `.m3u` en `Music/Boumusic/Playlists/` para compartirlas o respaldarlas.

---

### 🎧 5. Reproducción y Audio de Alta Fidelidad
* **Motor AndroidX Media3 (ExoPlayer)**: Máxima compatibilidad con formatos de alta resolución: **MP3, FLAC, M4A, AAC, WAV, OGG**.
* **Reproducción en segundo plano**: Servicio en segundo plano con `MediaSessionService`. La música no se detiene al apagar la pantalla ni al cambiar de aplicación.
* **Controles en barra de notificaciones y bloqueo**: Pausa, reanuda y salta pistas desde los controles nativos de Android.
* **Cola editable**: Opción *"Reproducir a continuación"* para colar temas prioritarios.
* **Modos de reproducción**: Reproducción aleatoria (Shuffle) y repetición (Repetir todo / Repetir una canción / Desactivado).

---

### ⏱️ 6. Temporizador de Apagado (Sleep Timer)
* Temporizador integrado con ajustes rápidos de **15, 30, 45, 60 y 90 minutos**.
* Indicador visual en la pantalla de reproducción con cuenta regresiva en vivo del tiempo restante.
* Detiene la reproducción suavemente al finalizar el tiempo programado.

---

### 🎨 7. Experiencia de Usuario y Diseño
* **100% Jetpack Compose y Material 3**: Animaciones fluidas, componentes modernos y tipografía cuidada.
* **Tema Oscuro Neón**: Paleta con fondos oscuros profundos (`#0C0A17`), contrastes Neon Cyan (`#00F5D4`) y acentos Electric Purple (`#7B2CBF`).
* **Navegación por Pestañas**:
  * **Canciones**: Con pestañas secundarias para filtrar por *Todas*, *Artistas* o *Álbumes*.
  * **Listas**: Vista de tarjetas con conteo de temas y acciones rápidas.
  * **Servidor FTP**: Explorador de archivos con indicador de carga y estado.
* **Búsqueda Instantánea**: Filtra en tiempo real por título, artista o álbum.
* **Internacionalización**: Compatible en Español e Inglés según el idioma del dispositivo.

---

## 🛠️ Stack Tecnológico

| Componente | Tecnología |
| :--- | :--- |
| **Lenguaje** | Kotlin 2.0 (JVM 17) |
| **UI Framework** | Jetpack Compose con Material Design 3 |
| **Audio Core** | AndroidX Media3 1.4.0 (ExoPlayer + MediaSession) |
| **Generador QR** | ZXing Core 3.5.3 |
| **Cliente FTP** | Apache Commons Net 3.10.0 |
| **Carga de Imágenes** | Coil Compose 2.7.0 |
| **Servidores Locales** | Sockets HTTP multihilo nativos en Kotlin con soporte `Range` y streaming ZIP |
| **Automatización / CI** | GitHub Actions (`.github/workflows/build-apk.yml`) |

---

## 📥 Guía de Instalación

1. Descarga el archivo [Boumusic.apk](https://github.com/bouet21/Bouetmusic/releases/download/v1.3.0/Boumusic.apk).
2. Si lo descargas desde el navegador de tu móvil, pulsa la notificación de descarga completada.
3. Si el sistema te lo solicita, activa la casilla **"Confiar en esta fuente"** para permitir la instalación de APKs.
4. Concede los permisos de almacenamiento al abrir la aplicación para que Boumusic pueda indexar tu música.

---

## 💻 Compilación desde el Código Fuente

Si deseas compilar la aplicación tú mismo desde el código fuente:

### Requisitos previos:
- **JDK 17** (ej. Eclipse Temurin 17 LTS o Android Studio JBR compatible).
- **Android SDK** con API Level 34 instalado.

### Pasos:
```powershell
# Clonar el repositorio
git clone https://github.com/bouet21/Bouetmusic.git
cd Bouetmusic

# Configurar ruta del SDK en local.properties (ejemplo en Windows)
echo "sdk.dir=C:\\Users\\TU_USUARIO\\AppData\\Local\\Android\\Sdk" > local.properties

# Compilar APK de depuración
.\gradlew.bat assembleDebug
```

El archivo compilado se generará en:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📄 Licencia

Este proyecto está disponible bajo la licencia libre para uso personal y comunitario. Desarrollado con ❤️ para una experiencia musical pura, moderna y sin anuncios.
