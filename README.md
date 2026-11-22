# 🎵 Boumusic — Reproductor de Música Nativo para Android

[![Release](https://img.shields.io/github/v/release/bouet21/Bouetmusic?color=00F5D4&label=Versi%C3%B3n)](https://github.com/bouet21/Bouetmusic/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-7B2CBF)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-brightgreen)](https://developer.android.com/jetpack/compose)
[![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-blueviolet)](https://github.com/bouet21/Bouetmusic/actions)
[![No Ads](https://img.shields.io/badge/Publicidad-0%25%20(Sin%20Anuncios)-success)](#)

**Boumusic** es un reproductor de música nativo para Android diseñado desde cero para disfrutar de tu música local y remota con la máxima fidelidad, interfaz futurista con tema oscuro neón (Neon Cyan y Electric Purple), cero anuncios y herramientas de nivel profesional: **soporte para servidores WebDAV y FTP/FTPS con streaming directo y caché**, **crossfade configurable y normalización de volumen (Loudness Enhancer)**, **filtros de biblioteca con lista negra de carpetas y exclusión de audios de WhatsApp**, **historial de reproducciones y estadísticas trimestrales tipo Wrapped**, **soporte bilingüe completo (Español/Inglés)** y **compilación continua automatizada (CI/CD)**.

---

## 📲 Descarga Directa

Descarga siempre la versión más reciente en formato APK lista para instalar en tu móvil:

👉 **[Descargar Boumusic v1.9.0 (APK)](https://github.com/bouet21/Bouetmusic/releases/download/v1.9.0/Boumusic.apk)**  
*(o visita todas las versiones en la sección de [Releases de GitHub](https://github.com/bouet21/Bouetmusic/releases))*

---

## ✨ Novedades Principales de la v1.9.0

### 🎚️ 1. Audio Avanzado: Crossfade y Normalización de Volumen (¡Nuevo v1.9.0!)
* **Fundido cruzado (Crossfade) configurable**:
  * Transición acústica fluida entre canciones con desvanecimiento de salida (fade-out) y entrada suave (fade-in) ajustable de 0 a 10 segundos.
  * Elimina los silencios bruscos entre pistas, perfecto para sesiones de música, conciertos y álbumes conceptuales.
* **Normalización de Volumen (Loudness Enhancer / ReplayGain)**:
  * Nivelado dinámico inteligente que previene saltos bruscos de volumen ("sustos") entre pistas grabadas con diferente ganancia o procedentes de distintas épocas.
  * Ganancia configurable en el diálogo de ecualizador con persistencia de ajustes en memoria segura.

---

### 🛡️ 2. Filtro de Biblioteca y Lista Negra de Carpetas (¡Nuevo v1.9.0!)
* **Exclusión automática de audios de mensajería**:
  * Filtro inteligente para ocultar notas de voz, audios y tonos de WhatsApp, Telegram, Signal, grabadoras de llamadas o notificaciones del sistema.
* **Gestión de carpetas excluidas (Lista Negra)**:
  * Excluye cualquier carpeta no deseada directamente desde el menú contextual (⋮) de cualquier canción con **"Excluir esta carpeta de la biblioteca"**.
  * Administrador de carpetas ignoradas en la pantalla de Ajustes con opción de restaurar carpetas en cualquier momento.
* **Filtro por duración mínima**:
  * Configura la duración mínima de pista (3s, 15s, 30s, 1m) para omitir efectos de sonido y avisos cortos.

---

### 🌐 3. Servidores WebDAV & Red Ampliada (¡Nuevo v1.9.0!)
* **Soporte nativo WebDAV**:
  * Conéctate a servidores NAS domésticos (Synology, QNAP), nubes personales (Nextcloud, ownCloud) y servidores WebDAV estándar vía HTTP o HTTPS (puertos 80, 443, 5005 o personalizados).
* **Navegación y streaming idénticos a FTP**:
  * Exploración por carpetas, búsqueda recursiva de archivos, streaming directo con saltos de posición (*Range requests*), transiciones entre carpetas y descargas en segundo plano.
* **Selector unificado de protocolo**:
  * Alterna fácilmente entre **FTP**, **FTPS (TLS)** y **WebDAV** en el formulario de nueva conexión.

---

### 🌍 4. Internacionalización Completa (i18n Español & English) (¡Nuevo v1.9.0!)
* Detección automática del idioma del sistema con recursos localizados en Español (`values/strings.xml`) e Inglés (`values-en/strings.xml`).
* Toda la interfaz, navegación, diálogos de ajustes, ecualizador y notificaciones disponibles en ambos idiomas.

---

### 🚀 5. Automatización CI/CD con GitHub Actions (¡Nuevo v1.9.0!)
* Flujo de trabajo automatizado `.github/workflows/build-apk.yml`.
* Ejecuta pruebas unitarias (`testDebugUnitTest`), compila los binarios Release y Debug y genera automáticamente el paquete `Boumusic.apk` adjunto a cada Release en GitHub.

---

## ✨ Características Consolidadas

### ⚡ Rendimiento y Ligereza
* **Generación instantánea de QR** vía buffer nativo en bloque (`setPixels`) en ~3ms (-98% de tiempo).
* **Filtrado reactivo O(N)** sin colecciones intermedias para búsquedas fluidas.
* **Caché en Jetpack Compose** con `remember` para evitar recomposiciones costosas en listas grandes.

### 📁 Navegación Remota Continua (FTP & WebDAV)
* **Pasar entre carpetas**: Reproducción secuencial y continua a través de múltiples directorios remotos.
* **Shuffle carpetas**: Reproducción aleatoria de canciones mezclando subcarpetas de todo el servidor.
* **Buscador profundo en servidores remotos**: Encuentra cualquier tema en cualquier nivel del servidor en segundos.
* **Caché offline y saltos inteligentes**: Reproduce sin cortes y omite pistas si el servidor se encuentra temporalmente apagado.

### 📊 Historial y Estadísticas Trimestrales (Wrapped)
* Seguimiento de veces escuchada (`playCount`), última fecha de reproducción y minutos totales acumulados.
* Resumen trimestral con artista más escuchado, top 5 canciones y tiempo de escucha.

### 📲 Conectividad y Transferencia sin Cables
* **Transferencia Wi-Fi para PC**: Servidor web local integrado con PIN opcional y apagado por inactividad.
* **Compartir canciones y listas**: Vía Bluetooth / Quick Share nativo de Android o Código QR instantáneo.

---

## 🛠️ Tecnologías Utilizadas

* **Lenguaje:** Kotlin 2.0 (JVM 17)
* **UI:** Jetpack Compose + Material 3 (Tema Neón Oscuro)
* **Audio Engine:** AndroidX Media3 ExoPlayer 1.4.0 + MediaSessionService + LoudnessEnhancer + Equalizer
* **Red:** OkHttp 4.12.0 (WebDAV) + Apache Commons Net 3.10.0 (FTP/FTPS)
* **Carga de Imágenes:** Coil Compose 2.7.0
* **Códigos QR:** Google ZXing 3.5.3
* **Persistencia:** SharedPreferences nativo y JSON con debounce

---

## 📄 Licencia

Este proyecto es código abierto y está disponible bajo los términos de la licencia MIT.
