# 🎵 Boumusic — Reproductor de Música Nativo para Android

[![Release](https://img.shields.io/github/v/release/bouet21/Bouetmusic?color=00F5D4&label=Versi%C3%B3n)](https://github.com/bouet21/Bouetmusic/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-7B2CBF)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-brightgreen)](https://developer.android.com/jetpack/compose)
[![No Ads](https://img.shields.io/badge/Publicidad-0%25%20(Sin%20Anuncios)-success)](#)

**Boumusic** es un reproductor de música nativo para Android diseñado desde cero para disfrutar de tu música local y remota con la máxima fidelidad, interfaz futurista con tema oscuro neón (Neon Cyan y Electric Purple), cero anuncios y herramientas avanzadas: **ecualizador de 5 bandas con Bass Boost**, **cola de reproducción editable**, **widget de pantalla de inicio**, **navegación por géneros**, **conexión segura FTPS (TLS/SSL)**, **buscador profundo en FTP**, **transferencia Wi-Fi con PIN** y **compartición por Bluetooth / Quick Share y Código QR**.

---

## 📲 Descarga Directa

Descarga siempre la versión más reciente en formato APK lista para instalar en tu móvil:

👉 **[Descargar Boumusic v1.7.0 (APK)](https://github.com/bouet21/Bouetmusic/releases/download/v1.7.0/Boumusic.apk)**  
*(o visita todas las versiones en la sección de [Releases de GitHub](https://github.com/bouet21/Bouetmusic/releases))*

---

## ✨ Novedades y Características Principales

### 🎚️ 1. Ecualizador de 5 Bandas y Refuerzo de Graves (Bass Boost) (¡Nuevo v1.7.0!)
* **Control acústico paramétrico**: Ajuste preciso en 5 bandas de frecuencias (60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz) con rango dinámico de -15 dB a +15 dB.
* **Presets de ecualización**: Alterna con un toque entre estilos predefinidos: *Normal, Rock, Pop, Clásica, Jazz, Electrónica y Bass Boost*.
* **Potenciador de graves (Bass Boost)**: Deslizador con control fino de 0 a 1000 para graves profundos y contundentes.
* **Interruptor activable**: Habilita o desactiva los efectos de audio al instante desde el reproductor o desde la pantalla de Ajustes.

---

### 📑 2. Cola de Reproducción Dinámica y Editable (¡Nuevo v1.7.0!)
* **Panel de cola (Queue Sheet)**: Accesible con un toque desde la barra superior de la pantalla de reproducción.
* **Reorganización en vivo**: Mueve canciones arriba o abajo en la lista para elegir el orden exacto de reproducción.
* **Saltar y descartar**: Toca cualquier canción de la cola para saltar inmediatamente a ella, o elimina canciones individuales sin alterar el resto de la lista.
* **Vaciar cola**: Botón rápido para limpiar la lista activa cuando quieras empezar de cero.
* **Reproducir a continuación**: Prioriza cualquier tema desde el menú contextual (⋮) para colocarlo inmediatamente después del tema actual.

---

### 📱 3. Widget Nativo para la Pantalla de Inicio (¡Nuevo v1.7.0!)
* **AppWidget 4x1 elegante**: Diseño redondeado con la estética Cyber Neon de Boumusic para tu pantalla de inicio o bloqueo.
* **Información en tiempo real**: Muestra el título de la canción actual, artista y estado de reproducción sincronizados al instante con el servicio en segundo plano.
* **Controles rápidos sin abrir la app**: Botones interactivos de pista anterior (⏮️), reproducir/pausar (⏯️) y pista siguiente (⏭️).
* **Acceso directo**: Toca la información de la pista para abrir la aplicación directamente en la vista principal.

---

### 🎸 4. Navegación por Géneros, Artistas y Álbumes (¡Nuevo v1.7.0!)
* **Pestaña Géneros**: Nueva subpestaña en la vista principal que agrupa toda tu música por género musical (Rock, Pop, Electrónica, Hip-Hop, etc.).
* **Contador de canciones**: Consulta de un vistazo cuántas pistas componen cada género en tu almacenamiento.
* **Filtrado instantáneo**: Toca cualquier género para ver y reproducir únicamente sus temas correspondientes.

---

### 🔒 5. Compatibilidad con FTPS Seguro (TLS/SSL) (¡Nuevo v1.7.0!)
* **Conexión encriptada**: Soporte nativo para conexiones seguras FTPS (`FTPSClient` TLS) con protección estricta de canales de datos (`PBSZ 0` y `PROT P`).
* **Selector FTP / FTPS**: Elige el protocolo directamente al añadir o editar un servidor favorito.
* **Streaming y búsqueda cifrada**: Reproduce y busca canciones en tu NAS o servidor con total privacidad tanto en red local como a través de Internet.

---

### 🛡️ 6. Transferencia Wi-Fi desde PC con PIN y Auto-Apagado (¡Nuevo v1.7.0!)
* **Código PIN opcional**: Protege la subida de archivos desde tu ordenador solicitando un código PIN de seguridad antes de procesar envíos.
* **Auto-apagado inteligente**: El servidor HTTP local se apaga automáticamente tras 15 minutos de inactividad para conservar la batería de tu teléfono.
* **Subida Drag & Drop por lotes**: Sube decenas de pistas directamente desde el navegador de tu PC (puerto 8080) sin cables USB.

---

### ⚙️ 7. Menú y Diálogo de Ajustes de la App (¡Nuevo v1.7.0!)
* **Configuración centralizada**: Icono de ajustes accesible en la esquina superior de la biblioteca.
* **Salto inteligente FTP**: Activa o desactiva el salto automático de canciones remotas cuando no hay conexión.
* **Reescaneo de biblioteca**: Actualiza y recarga toda la colección de audio local en un solo clic.
* **Formatos soportados e información**: Consulta códecs compatibles, versión instalada y estado del reproductor.

---

### 🔍 8. Buscador Inteligente en Servidores FTP
* **Filtro local**: Búsqueda instantánea en la carpeta actual del servidor.
* **Rastreo profundo BFS**: Rastreo en segundo plano multihilo por todas las subcarpetas del servidor.
* **Acciones directas**: Streaming en directo, añadir a playlists, encolado automático y descarga a local (`Music/Boumusic/`).

---

### 🔵 9. Compartir Música: Bluetooth / Quick Share y Código QR
* **🔵 Bluetooth / Quick Share**: Transfiere canciones o listas completas de móvil a móvil sin internet ni red Wi-Fi compartida.
* **📷 Código QR**: Genera un portal web local en tiempo real para que cualquier amigo escuche en streaming o descargue listas enteras en `.zip`.

---

### 🌐 10. Canciones FTP en Listas de Reproducción
* **Integración en playlists**: Mezcla canciones locales con temas alojados en tu NAS/servidor.
* **Salto automático con notificación**: Si estás fuera de tu red, Boumusic detecta la falta de conexión, salta la canción remota y te avisa mediante notificación push.

---

### ⏱️ 11. Temporizador de Apagado (Sleep Timer)
* Ajustes rápidos de **15, 30, 45, 60 y 90 minutos**.
* Indicador de cuenta regresiva en vivo y apagado gradual de reproducción.

---

## 🛠️ Stack Tecnológico

| Componente | Tecnología |
| :--- | :--- |
| **Lenguaje** | Kotlin 2.0 (JVM 17) |
| **UI Framework** | Jetpack Compose con Material Design 3 |
| **Widget Nativo** | AppWidgetProvider + RemoteViews (Android 12+ compat) |
| **Audio Core** | AndroidX Media3 1.4.0 (ExoPlayer + MediaSessionService) |
| **Audio Processing** | Android AudioEffect (Equalizer + BassBoost) |
| **Cliente FTP / FTPS** | Apache Commons Net 3.10.0 (FTPClient & FTPSClient TLS) |
| **Compartir / Transferencia** | FileProvider Android + Bluetooth / Quick Share + Sockets HTTP / ZIP |
| **Generador QR** | ZXing Core 3.5.3 |
| **Carga de Imágenes** | Coil Compose 2.7.0 |
| **Automatización / CI** | GitHub Actions (`.github/workflows/build-apk.yml`) |

---

## 📥 Guía de Instalación

1. Descarga el archivo [Boumusic.apk](https://github.com/bouet21/Bouetmusic/releases/download/v1.7.0/Boumusic.apk).
2. Si lo descargas desde el navegador de tu móvil, pulsa la notificación de descarga completada.
3. Si el sistema te lo solicita, activa la casilla **"Confiar en esta fuente"** para permitir la instalación de APKs.
4. Concede los permisos de almacenamiento al abrir la aplicación para que Boumusic pueda indexar tu música.

---

## 💻 Compilación desde el Código Fuente

```powershell
# Clonar el repositorio
git clone https://github.com/bouet21/Bouetmusic.git
cd Bouetmusic

# Configurar ruta del SDK en local.properties (ejemplo en Windows)
echo "sdk.dir=C:\\Users\\TU_USUARIO\\AppData\\Local\\Android\\Sdk" > local.properties

# Compilar APK de depuración
.\gradlew.bat assembleDebug
```

El archivo APK generado se encontrará en:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📄 Licencia

Este proyecto está disponible bajo la licencia libre para uso personal y comunitario. Desarrollado con ❤️ para una experiencia musical pura, moderna, personalizable y sin anuncios.
