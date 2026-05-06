# Repository Guidelines

## Project Structure & Modules
- Root Gradle config in `build.gradle.kts`, settings in `settings.gradle.kts`.
- App module at `app/` with source in `app/src/main/java/`, UI resources in `app/src/main/res/`.
- Unit tests in `app/src/test/java/`; instrumented tests in `app/src/androidTest/java/`.
- Local libs in `app/libs/` (e.g., `TarsosDSP-Android-2.4.jar`).

## Build, Test, and Run
- Build debug APK: `./gradlew :app:assembleDebug` (outputs to `app/build/outputs/apk/debug/`).
- Install on device/emulator: `./gradlew :app:installDebug`.
- Unit tests: `./gradlew testDebugUnitTest`.
- Instrumented tests: `./gradlew connectedDebugAndroidTest` (requires a running emulator/device).
- Lint check: `./gradlew :app:lint`.
- Clean: `./gradlew clean`.

## Coding Style & Naming
- Language: Kotlin (Compose enabled). Kotlin style is set to `official` in `gradle.properties`.
- Indentation: 4 spaces; keep lines readable; prefer explicit types at public APIs.
- Names: classes `PascalCase`, functions/vars `camelCase`, constants `UPPER_SNAKE_CASE`.
- Resources: lowercase with underscores (e.g., `activity_main.xml`, `ic_waveform.png`).
- Packages under `com.rokx.liano`; avoid cyclic deps and keep files small, focused.

## Testing Guidelines
- Frameworks: JUnit for unit tests; AndroidX Test for instrumented tests.
- Place unit tests beside mirrored package paths in `app/src/test/java`.
- Names: `FeatureNameTest` with readable test method names (Kotlin backticks allowed).
- For new features/bugfixes, include unit tests; add instrumented tests when Android APIs/UI are involved.

## Commit & Pull Requests
- Prefer Conventional Commits (`feat:`, `fix:`, `chore:`) with a concise scope (e.g., `feat(audio): normalize levels`).
- Keep commits focused and small. Reference issues (`Closes #123`).
- PRs should include: summary, rationale, before/after notes, screenshots/screen recordings for UI, and test instructions (device/emulator, API level).

## Security & Configuration
- Do not commit secrets or API keys. Keep local SDK/NDK paths in `local.properties` (already gitignored).
- Repro builds: use `Java 11` (see `compileOptions`), Android SDK `compileSdk 36`/`targetSdk 36`.
