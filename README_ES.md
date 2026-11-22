# Mochy Music - Spanish Translation & Localization Report / Reporte de Traducción al Español

> **Forked from Mochy Music by realidkroo** — [github.com/realidkroo/mochy-music](https://github.com/realidkroo/mochy-music)

---

## 🇪🇸 Español (Reporte en Español)

### 📌 Resumen de la Contribución
Esta contribución añade y completa el soporte para el idioma **Español** en toda la aplicación Mochy Music. Anteriormente, la carpeta de recursos en español (`app/src/main/res/values-es/`) solo contenía 3 archivos desactualizados, lo que provocaba que la mayor parte de la interfaz (pantalla de Inicio, Ajustes, Comportamiento de la app, Biblioteca, Laboratorio de Audio, Perfil y Recap) se mostrase en inglés mediante el mecanismo de reserva (*fallback*) de Android.

---

### 🛠️ Cambios Realizados

#### 1. Archivos XML Modificados (Existentes)
* `app/src/main/res/values-es/strings.xml`: Se completaron las claves faltantes de la interfaz base.
* `app/src/main/res/values-es/mochy_strings.xml`: Se añadieron las traducciones de las tarjetas de Inicio (Marcado Rápido, Más escuchadas, etc.) y Listen Together.
* `app/src/main/res/values-es/updater_strings.xml`: Se actualizaron los textos del módulo de actualizaciones.
* `app/src/main/res/values-es-rUS/mochy_strings.xml`: Sincronización para la variante de español de EE. UU.
* `app/src/main/res/values/recap_strings.xml`: Se agregaron las claves base traducibles para sellos del escáner y avisos de estado del Recap.

#### 2. Archivos XML Creados (Nuevos)
Se crearon 35 archivos XML en `app/src/main/res/values-es/` para igualar los recursos modulares en inglés (`values/`):
* `app_language.xml`: Idioma y ajustes generales.
* `settings_language.xml`: Ajustes, comportamiento de la app, proxy y personalización.
* `library_strings.xml`: Pestañas de biblioteca, archivos locales, descargas y caché.
* `content_screens_strings.xml`: Artistas, álbumes, listas destacadas, donaciones.
* `modules_strings.xml`: Tienda de módulos, Discord RPC e integraciones.
* `player_core_strings.xml`: Controles del reproductor, estadísticas avanzadas y tarjetas Hero.
* `player_sheets_strings.xml`: Menús desplegables del reproductor, traducción de letras y sincronización.
* `player_extra_strings.xml`: Registros y gestión de descargas.
* `player_bedside_strings.xml`: Modo nocturno y pantalla de bloqueo.
* `player_flagship_strings.xml`: Salida de audio y controles principales.
* `onboarding_strings.xml`: Flujo de bienvenida y configuración inicial.
* `importsync_strings.xml`: Sincronización con YouTube Music, Spotify, TuneMyMusic y CSV.
* `audiopass_strings.xml`: Pasarela Bit-Perfect, DAC USB y códecs Bluetooth.
* `audiolab_fx_strings.xml`: Efectos de audio, sonoridad y reverberación.
* `audiolab_core_strings.xml`: Ajustes del Laboratorio de Audio.
* `audiolab_stage_strings.xml`: Posicionamiento y simulación 3D.
* `audiolab_quality_strings.xml`: Calidad de transmisión de proveedores.
* `audiolab_transition_strings.xml`: Fundido cruzado (Crossfade) y CrossMix.
* `backup_strings.xml`: Copias de seguridad en la nube (WebDAV, Dropbox, OneDrive).
* `car_strings.xml`: Android Auto.
* `tv_strings.xml`: Android TV.
* `storage_strings.xml`: Gestión de almacenamiento y exportación.
* `recap_strings.xml`: Estadísticas y resumen/Wrapped musical.
* `profile_strings.xml`: Perfil de usuario y tarjeta ID.
* `song_edit_strings.xml`: Editor de metadatos de canciones y letras.
* `device_link_strings.xml`: Integración de dispositivos y control remoto.
* `builtin_module_strings.xml`: Descripciones de módulos integrados.
* `local_playback_strings.xml`: Reproducción local y manejo de errores.
* `supplemental_ui_strings.xml`: Mensajes de apoyo y búsqueda adicional.
* `larp_strings.xml`: Modo Larp de simulación de audio.
* `origin_card_strings.xml`: Tarjeta musical OriginOS.
* `platform_ui_strings.xml`: Suscriptores y oyentes mensuales.
* `nowplaying_widget_strings.xml`: Widgets del sistema.
* `equalizer_strings.xml`: Ecualizador y AutoEQ.
* `voice_strings.xml`: Comandos de voz.

#### 3. Refactorización en Código Kotlin
* `app/src/main/kotlin/com/mochyy/mochymusic/ui/screens/settings/ProfileScreen.kt`:
  * Se reemplazó la cadena fija `"Update"` por `stringResource(R.string.profile_capsule_update)`.
  * Se vinculó el aviso descriptivo de estado del Recap con `comeBackText(context)`.
* `app/src/main/kotlin/com/mochyy/mochymusic/ui/screens/recap/IdCardBack.kt`:
  * Se conectaron los sellos `"VERIFIED"` y `"INVALID"` a los recursos `stringResource(R.string.recap_stamp_verified)` y `stringResource(R.string.recap_stamp_invalid)`.
* `app/src/main/kotlin/com/mochyy/mochymusic/ui/screens/recap/RecapData.kt`:
  * Se refactorizó `RecapAvailability.NotYet` para usar una función lambda `(Context) -> String` que evalúa dinámicamente los recursos XML traducibles según el idioma del dispositivo.

---

## 🇬🇧 English (Technical Pull Request / Contribution Summary)

### 📌 Contribution Summary
This pull request adds full, 100% comprehensive **Spanish (es)** language support to Mochy Music. Previously, the Spanish resource directory (`app/src/main/res/values-es/`) contained only 3 outdated XML files, causing most of the app UI (Home feed, Settings, App Behaviour, Library, Audio Lab, Profile, and Recap) to fall back to English string resources.

---

### 🛠️ Changes Breakdown

#### 1. Modified Existing XML Files
* `app/src/main/res/values-es/strings.xml`: Added missing UI strings.
* `app/src/main/res/values-es/mochy_strings.xml`: Added Home Speed Dial, Most Played, and Listen Together keys.
* `app/src/main/res/values-es/updater_strings.xml`: Updated updater strings.
* `app/src/main/res/values-es-rUS/mochy_strings.xml`: Aligned US Spanish variant strings.
* `app/src/main/res/values/recap_strings.xml`: Added base translatable string resources for barcode scanner stamps and Recap unlock status.

#### 2. Newly Created XML Files (35 files in `app/src/main/res/values-es/`)
Mirrored all modular string files from `app/src/main/res/values/` into `app/src/main/res/values-es/` with complete Spanish translations and proper Android plurals (`one`, `many`, `other`):
* `app_language.xml`, `settings_language.xml`, `library_strings.xml`, `content_screens_strings.xml`, `modules_strings.xml`, `player_core_strings.xml`, `player_sheets_strings.xml`, `player_extra_strings.xml`, `player_bedside_strings.xml`, `player_flagship_strings.xml`, `onboarding_strings.xml`, `importsync_strings.xml`, `audiopass_strings.xml`, `audiolab_fx_strings.xml`, `audiolab_core_strings.xml`, `audiolab_stage_strings.xml`, `audiolab_quality_strings.xml`, `audiolab_transition_strings.xml`, `backup_strings.xml`, `car_strings.xml`, `tv_strings.xml`, `storage_strings.xml`, `recap_strings.xml`, `profile_strings.xml`, `song_edit_strings.xml`, `device_link_strings.xml`, `builtin_module_strings.xml`, `local_playback_strings.xml`, `supplemental_ui_strings.xml`, `larp_strings.xml`, `origin_card_strings.xml`, `platform_ui_strings.xml`, `nowplaying_widget_strings.xml`, `equalizer_strings.xml`, `voice_strings.xml`.

#### 3. Kotlin Source Code Refactoring
* `ProfileScreen.kt`: Replaced hardcoded `"Update"` string in `VersionCapsule` with `stringResource(R.string.profile_capsule_update)`. Updated `RecapAvailability.NotYet` message rendering to evaluate dynamically using `LocalContext.current`.
* `IdCardBack.kt`: Replaced hardcoded `"VERIFIED"` and `"INVALID"` canvas stamp labels with `stringResource(R.string.recap_stamp_verified)` and `stringResource(R.string.recap_stamp_invalid)`.
* `RecapData.kt`: Refactored `RecapAvailability.NotYet` from a hardcoded English String payload to a `(Context) -> String` evaluator, enabling localized status messages when Recap is locked (e.g. insufficient song count or date threshold).

### ✅ Verification
* Verified compilation locally via `./gradlew :app:compileArm64FossDebugKotlin`.
* Build status: **SUCCESS**.
* Tested UI rendering across Home, Library, Settings, Audio Lab, Profile, and Recap screens.

---
*Note: AI assistance was used for translation auditing and code refactoring. Credit to Mochy Music*
