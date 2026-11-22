# 🎵 Boumusic — Reproductor de Música para Android (Sin Anuncios)

**Boumusic** es una aplicación nativa moderna para Android diseñada específicamente para escuchar música local con la máxima calidad, sin anuncios publicitarios ni interrupciones, y con un sistema integrado para **transferir canciones directamente desde tu ordenador por Wi-Fi sin cables**.

---

## ✨ Características Principales

* 🚫 **100% Libre de Anuncios**: Sin banners, sin anuncios emergentes ni cortes comerciales.
* 💻➡️📱 **Transferencia Directa PC a Móvil (Wi-Fi)**:
  * Servidor web local integrado en la propia aplicación.
  * Abre una dirección web (ej. `http://192.168.1.45:8080`) en el navegador de tu ordenador (Chrome, Edge, Firefox).
  * Arrastra y suelta canciones (`.mp3`, `.flac`, `.m4a`, `.wav`).
  * Las canciones se guardan automáticamente en la carpeta **`Music/Boumusic/`** de tu teléfono y aparecen al instante en la lista de reproducción.
* 🎧 **Reproducción en Segundo Plano**: Soporte completo con **AndroidX Media3 / ExoPlayer** y `MediaSessionService`. El audio sigue sonando con la pantalla bloqueada o mientras usas otras apps.
* 📱 **Controles en Notificación y Pantalla de Bloqueo**: Pausa, reproduce, retrocede y avanza directamente desde la barra de notificaciones del sistema Android.
* 🔍 **Búsqueda Instantánea**: Encuentra rápidamente cualquier canción por su título, artista o álbum.
* 🎛️ **Controles Avanzados de Reproducción**:
  * Modo Aleatorio (*Shuffle*).
  * Modo Repetición (*Repeat Off*, *Repeat All*, *Repeat One*).
  * Barra de progreso fluida (*Scrubbing*) con indicador de tiempo actual y duración total.
* 🖼️ **Diseño Moderno Jetpack Compose**:
  * Interfaz oscura (*Dark Theme*) elegante con degradados cian y púrpura.
  * Mini-reproductor inferior con barra de progreso superior.
  * Pantalla de reproducción a pantalla completa expansible con carátula de álbum.

---

## 📦 ¿Cómo obtener el archivo APK directamente?

Un archivo `.apk` es un paquete binario compilado y firmado digitalmente para Android que requiere las herramientas del SDK de Android y Java para generarse. Para obtener tu archivo APK listo para instalar en tu móvil:

### Método Recomendado: Con Android Studio (En 1 clic)

1. Descarga e instala [Android Studio](https://developer.android.com/studio) (es gratuito y oficial de Google).
2. Abre Android Studio y pulsa en **Open**.
3. Selecciona esta carpeta:
   ```
   c:\Users\Bouet\Documents\Boumusic
   ```
4. En el menú superior de Android Studio, haz clic en:
   **Build > Build Bundle(s) / APK(s) > Build APK(s)**
5. En unos segundos, Android Studio compilará el proyecto y te mostrará una notificación en la esquina inferior derecha con un enlace azul: **`locate`**.
6. Al hacer clic en `locate`, se abrirá la carpeta con tu archivo:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```
7. Pasa ese archivo `app-debug.apk` a tu teléfono (por WhatsApp, Telegram, correo o cable USB) e instálalo.

---

## 🌐 ¿Cómo usar la Transferencia desde el Ordenador?

1. En la app **Boumusic** en tu teléfono, pulsa el icono de **Wi-Fi** en la esquina superior derecha (o el botón **Transferir desde PC**).
2. Activa el interruptor **Servidor Wi-Fi Local**.
3. Verás una dirección como:
   ```
   http://192.168.1.X:8080
   ```
4. En tu ordenador (conectado al mismo Wi-Fi), abre Google Chrome, Edge o Firefox y escribe esa dirección.
5. Verás el portal web de Boumusic: simplemente arrastra los archivos `.mp3` o pulsa **Elegir archivos**.
6. Las canciones se enviarán a tu teléfono a la carpeta **`Music/Boumusic/`** y estarán disponibles para escuchar al instante.
