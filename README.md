# ASTRA Launcher v0.2 — Edge + Weather + Universal Search

Android home-screen replacement prototype with an ASTRA-specific visual language.

## v0.2 completed
- New **ASTRA Edge** home design inspired by asymmetric / split-layout references, without copying them.
- Vertical smart app rail for frequently recognizable apps.
- Orange/black editorial date-and-time treatment.
- **ASTRA Weather** inside the launcher:
  - current temperature and condition
  - feels-like temperature
  - humidity, wind and precipitation
  - next-hours forecast
  - 5-day forecast
  - location-aware cache
  - no background-location permission
- Weather provider adapter foundation using Open-Meteo for the prototype.
- **ASTRA Universal Search** overlay:
  - installed apps
  - Android settings
  - quick device actions
  - weather shortcut
  - web search fallback
  - English + initial Bengali command aliases such as `WhatsApp খোলো`
- AI entry point is visible, but cloud-AI credentials are deliberately **not** embedded in the APK. Secure AI gateway integration is reserved for v0.3.
- Existing default HOME role, app drawer, search, dock and app launch support retained.

## Privacy behavior
Location is requested only after the user opens the weather experience. The manifest declares coarse/fine foreground location only. There is no `ACCESS_BACKGROUND_LOCATION` permission.

## Build compatibility note
This project stays on AGP 8.13.2 / Gradle 8.13 / compileSdk 36. Compose BOM is pinned to `2026.06.01` so it does not pull the Compose 1.12 generation that requires API 37 / AGP 9.2.

## Build
1. Install Android Studio / SDK 36.
2. Open the project and sync Gradle.
3. Build with `gradle :app:assembleDebug` using Gradle 8.13.
4. Install `app/build/outputs/apk/debug/app-debug.apk`.
5. Choose **ASTRA Launcher** as the default Home app.
6. Open ASTRA Weather and grant approximate or precise location when requested.

GitHub Actions is included and uploads a debug APK artifact automatically.

## v0.3 planned
- Secure ASTRA AI gateway/provider interface
- Voice command entry
- Persistent pin/reorder favorites
- Wallpaper integration + adaptive accent
- Launcher settings screen
- Optional search modules for contacts/files with explicit permissions
