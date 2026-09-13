# AGENTS.md — Lumina Client (Lumina v4)

Android (Kotlin, AGP 8.10.1 / Kotlin 2.1.10 / Gradle 8.12.1) Minecraft Bedrock relay client. GPL-3.0.

## Build prerequisites (all required, none checked in)

- Java 17, Android SDK (compileSdk/targetSdk 36, minSdk 28), CMake 3.22.1, NDK. No `local.properties` in repo — create it with `sdk.dir=` yourself.
- `app/google-services.json` is `.gitignore`d and **absent** — `:app` does not configure without it. Create a Firebase project, register Android app with package `com.project.lumina.client`, and place the file at `app/google-services.json`. README's `com.projectlumina.luminaclient` package name is stale; trust `app/build.gradle.kts` (`namespace`/`applicationId`).
- `google-services` + `crashlytics` Gradle plugins apply to `:app` only.

## Commands

- `./gradlew :app:assembleDebug` — normal dev build (uses committed `buildKey.jks`, alias `UntrustedKey`, pw `123456`, for both debug and release; do not "fix" this).
- `./gradlew :app:assembleRelease` — enables R8 fullMode + `isMinifyEnabled` + `isShrinkResources` with `app/proguard-rules.pro`. If release crashes/missing classes, add `-keep` there (already keeps Netty, CloudburstMC, relay, `CPPBridge`, `ModuleManager`, `GameDataManager`, msftauth/raphimc).
- `./gradlew :Network:transport-raknet:test` — only real unit tests in repo (`BitQueueTests`, `RakTests`). There are no `:app` unit tests; `:app` test deps are boilerplate JUnit4/Espresso/Compose-UI-test only.
- `./gradlew <module>:build` for library modules, e.g. `:Lunaris:build`, `:Protocol:bedrock-codec:build`, `:lunarisrpc:build`.
- `build-openssl.sh` (OpenSSL 1.1.1, `NDK` env/arg) is standalone — not wired into Gradle, only run if native TLS work needs it.

## Structure (settings.gradle.kts is source of truth)

- `:app` — Compose Material3 UI + client logic. Entrypoints: `application/AppContext.kt`, `activity/LaunchActivity.kt` (MAIN/LAUNCHER + `lumina://auth` deep link), `service/Services.kt` (foreground relay service). Native lib `client` in `app/src/main/cpp/` (`jni_bridge.cpp`, `hsv_to_rgb.cpp`).
- `:Lunaris` — relay core: `relay/LuminaRelay.kt` (orchestrator) + `relay/LuminaRelaySession.kt` (packet flow, listener hooks). Start here for proxy/packet behavior.
- `:Protocol/*` (`bedrock-codec`, `bedrock-connection`, `common`, `adventure`) and `:Network/*` (`transport-raknet`, `codec-query`, `codec-rcon`) — CloudburstMC-derived Bedrock/RakNet stack. Don't edit for app features; only for protocol changes.
- `:minecraft-msftauth`, `:lunarisrpc`, `:animatedux`, `:Pixie`, `:SSC`, `:TablerIcons`, `imgui/` — auth/RPC/animation/native-render helpers. Game modules: `app/.../game/module/{api(commands,config,setting),impl(combat,motion,visual,world,misc,effect,game)}`; world/chunk + `registry/` (Block/ItemMapping from `assets/mcpedata/`) under `app/.../game/`.
- `dependencyResolutionManagement` uses `FAIL_ON_PROJECT_REPOS` — add new Maven repos only in `settings.gradle.kts` (already: google, mavenCentral, opencollab snapshots+releases, jitpack).

## Conventions / gotchas

- `kotlin.code.style=official`. ABIs: `arm64-v8a, armeabi-v7a, x86_64, x86`.
- Compose compiler flags `OptimizeNonSkippingGroups`, `PausableComposition` are on — keep.
- No CI, no lint/typecheck task, no pre-commit. Verify by building the touched module; manual on-device test against a Bedrock server is the norm (README suggests non-protected test servers, e.g. CubeCraft/Hive).
- Commits in history are small (`PUBLIC-x.y.z`, short messages). Follow that; keep PRs focused per README contribution rules.
- `tools/*.py` are standalone helpers (font/res/math), not part of the build.
