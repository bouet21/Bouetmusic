# 🤝 Guía de Contribución a Boumusic Android

¡Bienvenido al repositorio oficial de **Boumusic para Android**!

---

## 🛠️ Entorno de Desarrollo

- **Lenguaje:** Kotlin 2.0
- **Framework UI:** Jetpack Compose con Material Design 3
- **Motor de Audio:** AndroidX Media3 (ExoPlayer)
- **Compilador:** Android Gradle Plugin 8.5+, Gradle 8.7, JDK 17
- **Min SDK:** Android 7.0 (API 24) | **Target SDK:** Android 14 (API 34)

### Comandos útiles

```bash
# Compilar la aplicación en modo Debug
./gradlew assembleDebug

# Ejecutar las pruebas unitarias
./gradlew testDebugUnitTest

# Generar el APK de Release firmado
./gradlew assembleRelease
```

---

## 🧪 Normas de Código y Commits

Seguimos [Conventional Commits](https://www.conventionalcommits.org/):
- `feat(...)`: Nuevas funciones
- `fix(...)`: Corrección de errores
- `docs(...)`: Documentación
- `refactor(...)`: Mejoras internas de código
