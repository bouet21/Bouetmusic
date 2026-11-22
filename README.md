# 🎵 Boumusic — Reproductor de Música Nativo para Android

[![Release](https://img.shields.io/github/v/release/bouet21/Bouetmusic?color=00F5D4&label=Versi%C3%B3n)](https://github.com/bouet21/Bouetmusic/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-7B2CBF)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-brightgreen)](https://developer.android.com/jetpack/compose)
[![No Ads](https://img.shields.io/badge/Publicidad-0%25%20(Sin%20Anuncios)-success)](#)

**Boumusic** es un reproductor de música nativo para Android diseñado desde cero para disfrutar de tu música local y remota con la máxima fidelidad, interfaz futurista con tema oscuro neón (Neon Cyan y Electric Purple), cero anuncios y herramientas potentes: **historial de reproducciones y estadísticas trimestrales tipo Wrapped**, **optimización ligera de memoria y rendimiento**, **ecualizador de 5 bandas con Bass Boost**, **cola de reproducción editable**, **widget de pantalla de inicio**, **navegación por géneros**, **conexión segura FTPS (TLS/SSL)**, **buscador profundo en FTP**, **transferencia Wi-Fi con PIN** y **compartición por Bluetooth / Quick Share y Código QR**.

---

## 📲 Descarga Directa

Descarga siempre la versión más reciente en formato APK lista para instalar en tu móvil:

👉 **[Descargar Boumusic v1.8.2 (APK)](https://github.com/bouet21/Bouetmusic/releases/download/v1.8.2/Boumusic.apk)**  
*(o visita todas las versiones en la sección de [Releases de GitHub](https://github.com/bouet21/Bouetmusic/releases))*

---

## ✨ Novedades Principales de la v1.8.2

### 📁 1. Reproducción y Transición Continua entre Carpetas en Modo FTP (¡Nuevo v1.8.2!)
* **Pasar de música entre carpetas**:
  * Ahora en el modo FTP puedes escuchar de forma continua todas tus pistas a través de múltiples directorios y subcarpetas.
  * **Botón "Pasar entre carpetas"**: Escanea recursivamente todas las canciones de la carpeta actual y sus niveles inferiores, ordenándolas por carpeta y nombre de pista. Al terminar una carpeta o al pulsar siguiente (⏭️), avanza naturalmente a la siguiente carpeta sin pausas.
  * **Salto directo de carpetas**: Desde el menú contextual (⋮) del reproductor a pantalla completa puedes saltar directamente con **"Saltar a siguiente carpeta"** o **"Volver a carpeta anterior"** entre carpetas completas del servidor.
  * **Transición automática inteligente**: Si reproduces una carpeta individual y llega a su última canción, Boumusic busca dinámicamente las carpetas hermanas del servidor FTP y avanza a la siguiente sin detenerse.

---

### 🔀 2. Modo Shuffle entre Carpetas FTP (¡Nuevo v1.8.2!)
* **Aleatorio completo a través de múltiples carpetas**:
  * **Botón "Shuffle carpetas"**: Mezcla en modo aleatorio todas las canciones de todas las subcarpetas del directorio actual (o del servidor completo si te encuentras en la raíz `/`), alternando pistas de diferentes álbumes y artistas.
  * **Menú rápido por carpeta**: Cada fila de carpeta en el explorador FTP cuenta con un menú (⋮) para **"Reproducir con subcarpetas"** y **"Shuffle de esta carpeta"** directamente.
  * **Indicador en tiempo real**: Diálogo de escaneo asíncrono que muestra el número de canciones descubiertas mientras explora subcarpetas.
  * **Insignia de carpeta activa**: El reproductor expandido muestra la etiqueta del directorio remoto actual (`📁 Carpeta`) para identificar en todo momento el álbum en reproducción.

---

## ✨ Novedades de la v1.8.1

### 🛡️ 1. Estabilidad y Resiliencia Total en Dispositivos OEM (Solución a "se ralla")
* **Ecualizador y Efectos a prueba de bombas**:
  * En dispositivos donde los fabricantes (Xiaomi MIUI/HyperOS, Samsung Galaxy serie A, etc.) restringen o fallan al inicializar los motores de audio por hardware (`Equalizer` o `BassBoost` arrojando error -3 o -4), Boumusic ahora intercepta de forma segura cualquier excepción (`SecurityException`, `UnsupportedOperationException`, `IllegalArgumentException` por tablas de presets defectuosas).
  * En caso de incompatibilidad de hardware del dispositivo, la app recurre automáticamente a perfiles y bandas virtuales seguros sin cerrarse forzosamente ni congelarse jamás.
* **Sesión de audio y widgets protegidos**:
  * Manejo seguro y dinámico de `audioSessionId` en `MusicService` al cambiar de pista.
  * Declaración formal del permiso `MODIFY_AUDIO_SETTINGS` en el AndroidManifest.
  * Aislamiento en `MusicWidgetProvider` para evitar caídas del servicio ante fallos del sistema (`DeadSystemException`, `RemoteException`).

---

### 🎨 2. Interfaz Limpia, Ergonómica y Responsiva (Solución a botones desbordados)
* **Reproductor a Pantalla Completa Rediseñado**:
  * Cabecera despejada y minimalista con botón de minimizar, título limpio y menú contextual ⋮.
  * **Barra de acciones ergonómica**: Acciones secundarias (*Ecualizador*, *Temporizador*, *Compartir*, *Cola*) organizadas en una fila propia de botones píldora debajo de los controles principales.
  * **Carátula responsiva para pantallas compactas**: La portada del álbum se ajusta dinámicamente al alto de pantalla disponible (`weight(1f, fill = false)`) y el contenedor cuenta con desplazamiento vertical adaptativo, garantizando que en teléfonos con pantallas 16:9, resolución 720p o fuentes grandes ningún botón quede cortado u oculto.
* **Barra Superior Equilibrada**:
  * Iconos optimizados a 36dp con espaciados limpios para que no se desborden ni solapen en pantallas pequeñas.
* **Navegador y Búsqueda FTP Estilizados**:
  * Filas de resultados limpias con botón directo de descarga y menú desplegable contextual (⋮) para añadir a listas de reproducción o ir a la carpeta contenedora.

---

## ✨ Novedades de la v1.8.0

### 📊 1. Historial de Reproducciones y Estadísticas Trimestrales (¡Nuevo v1.8.0!)
* **Historial completo en la biblioteca**:
  * Nueva subpestaña **"Historial"** que registra cada pista reproducida.
  * **Información detallada**: Consulta cuántas veces has escuchado cada tema (`playCount`), la última vez que sonó con tiempo relativo (*"Hace 15 min"*, *"Ayer"*, etc.) y el tiempo total acumulado.
  * **Vistas rápidas**: Alterna con un toque entre canciones **"Recientes"** y **"Más escuchadas"** (Top Plays).
  * **Reproducción instantánea y gestión**: Toca cualquier canción para reproducirla inmediatamente o usa el menú contextual (⋮) para encolarla o agregarla a una lista.
  * Opción de limpiar o reiniciar el historial en cualquier momento.

* **Resumen Trimestral y Estadísticas (Estilo Wrapped)**:
  * Diálogo interactivo accesible desde el banner de la pestaña Historial, el botón de estadísticas en la barra superior o el menú de Ajustes.
  * **Minutos totales escuchados**: Con desglose en horas y minutos y total de reproducciones.
  * 🥇 **Canción más escuchada**: Podio de honor con la pista número 1, número de veces reproducida en bucle y tiempo.
  * 🎤 **Artista más escuchado**: Total de minutos dedicados y temas reproducidos.
  * 🎸 **Género dominante**: Género musical favorito con porcentaje y conteo de temas.
  * ⏰ **Momento del día favorito**: Descubre en qué franja horaria escuchas más música (*Mañanas*, *Tardes*, *Noches* o *Madrugadas*).
  * 📊 **Top 5 Canciones y Top 5 Artistas**: Con barras horizontales de progreso porcentuales en colores neón.
  * **Selector de períodos**: Analiza tus estadísticas por *Últimos 3 Meses*, *Trimestre Actual*, *Trimestre Anterior* o *Histórico Total*.

* **Notificaciones automáticas cada 3 meses**:
  * Al concluir un trimestre (o cada 90 días), Boumusic detecta la finalización del período y te envía una notificación push con tus minutos totales y tus artistas/canciones más escuchadas. Al tocarla, se abre directamente la pantalla de estadísticas.

---

### ⚡ 2. Optimización y Máxima Ligereza (¡Nuevo v1.8.0!)
* **Consumo de memoria RAM ultra-reducido (Coil Downsampling)**:
  * Las carátulas de álbum en listas se decodifican con tamaño optimizado a 120x120 px, eliminando la carga de imágenes pesadas de alta resolución en RAM. Esto ahorra hasta un 80% de memoria y evita tirones al hacer scroll.
* **Scroll a 60/120 FPS sin tirones**:
  * Claves estables (`key`) en todos los listados Compose (`LazyColumn`) de canciones, artistas, álbumes, géneros e historial para evitar recomposiciones innecesarias.
* **Persistencia ligera y asíncrona**:
  * Guardado de historial y estadísticas con `org.json` nativo (sin pesadas librerías de terceros ni reflexión), ejecución en hilos de fondo (`Dispatchers.IO`) y sistema de debounce que minimiza lecturas y escrituras en disco.
* **Limpieza de recursos**:
  * Exclusión de recursos innecesarios en el empaquetado del APK (`INDEX.LIST`, `DEPENDENCIES`).

---

## 🎧 Todas las Características de Boumusic

### 🎚️ 3. Ecualizador de 5 Bandas y Bass Boost
* Ajuste en 5 frecuencias (60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz) de -15 dB a +15 dB.
* Presets integrados: *Normal, Rock, Pop, Clásica, Jazz, Electrónica y Bass Boost*.
* Deslizador de refuerzo de graves con control de 0 a 1000 e interruptor activable.

### 📑 4. Cola de Reproducción Dinámica y Editable
* Panel inferior (Queue Sheet) para reordenar pistas en vivo arrastrando o moviendo arriba/abajo.
* Saltar a cualquier pista, eliminar pistas individuales y función *"Reproducir a continuación"*.

### 📱 5. Widget Nativo para Pantalla de Inicio (4x1)
* AppWidget con diseño Cyber Neon: título, artista y carátula sincronizados con el servicio en segundo plano.
* Botones interactivos inmediatos: Anterior (⏮️), Play/Pausa (⏯️) y Siguiente (⏭️).

### 🎸 6. Navegación por Géneros, Artistas y Álbumes
* Subpestañas en la biblioteca para explorar tu música por *Todas*, *Artistas*, *Álbumes*, *Géneros* e *Historial*.

### 🔒 7. Conexión Segura FTPS (TLS/SSL) y Buscador Profundo
* Compatibilidad con `FTPSClient` (TLS) seguro con canales protegidos (`PROT P`).
* Rastreo BFS en segundo plano por todo el servidor FTP para encontrar canciones al instante.

### 🛡️ 8. Transferencia Wi-Fi desde PC con PIN y Auto-Apagado
* Sube archivos arrastrando y soltando desde el navegador de tu ordenador (puerto 8080).
* PIN de seguridad opcional y apagado automático tras 15 minutos de inactividad.

### 🔵 9. Compartir por Bluetooth / Quick Share y Código QR
* Envía canciones o listas de móvil a móvil sin internet ni Wi-Fi mediante Bluetooth, o mediante portal web local con código QR.

### ⏱️ 10. Temporizador de Apagado (Sleep Timer)
* Ajustes de 15, 30, 45, 60 y 90 minutos con apagado gradual.

---

## 🛠️ Stack Tecnológico

| Componente | Tecnología |
| :--- | :--- |
| **Lenguaje** | Kotlin 2.0 (JVM 17) |
| **UI Framework** | Jetpack Compose con Material Design 3 |
| **Audio Core** | AndroidX Media3 1.4.0 (ExoPlayer + MediaSessionService) |
| **Audio Processing** | Android AudioEffect (Equalizer + BassBoost) |
| **Historial & Stats** | Async JSON Engine + NotificationManager periódica |
| **Widget Nativo** | AppWidgetProvider + RemoteViews (Android 12+ compat) |
| **Cliente FTP / FTPS** | Apache Commons Net 3.10.0 (FTPClient & FTPSClient TLS) |
| **Compartir / Transferencia** | FileProvider Android + Bluetooth / Quick Share + Sockets HTTP / ZIP |
| **Generador QR** | ZXing Core 3.5.3 |
| **Carga de Imágenes** | Coil Compose 2.7.0 (con Downsampling en memoria) |
| **Automatización / CI** | GitHub Actions (`.github/workflows/build-apk.yml`) |

---

## 📥 Guía de Instalación

1. Descarga el archivo [Boumusic.apk](https://github.com/bouet21/Bouetmusic/releases/download/v1.8.0/Boumusic.apk).
2. Abre la notificación o el archivo descargado en tu móvil.
3. Si el sistema te lo solicita, activa la casilla **"Confiar en esta fuente"**.
4. Concede los permisos de almacenamiento y notificaciones al abrir la app.

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
