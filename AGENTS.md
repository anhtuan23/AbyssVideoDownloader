# Repository Guidelines

## Project Structure & Module Organization

This is a Kotlin/JVM command-line downloader built with Gradle. Application entry points live in `src/main/kotlin/com/abmo/Main.kt` and `Application.kt`. Provider-specific extraction logic is in `src/main/kotlin/com/abmo/providers`, download and HTTP behavior is in `src/main/kotlin/com/abmo/services`, and shared models/utilities are under `model`, `model/video`, `util`, `crypto`, and `common`. Runtime JavaScript assets are stored in `src/main/resources`. Tests live in `src/test/kotlin`.

## Build, Test, and Development Commands

- `./gradlew test --no-daemon`: runs the JUnit/Kotlin test suite.
- `./gradlew shadowJar --no-daemon`: builds `build/libs/abyss-dl-shadowJar.jar`.
- `./gradlew build --no-daemon`: runs tests and ProGuard, producing `build/libs/abyss-dl.jar`.
- `java -jar build/libs/abyss-dl.jar <id-or-url> -o <path> --connections 4 --verbose`: runs the packaged CLI locally after a build.

Use JDK 21; Gradle is configured with `kotlin.jvmToolchain(21)`.

## Coding Style & Naming Conventions

Follow idiomatic Kotlin with 4-space indentation and concise, expression-oriented functions where readable. Keep packages under `com.abmo`. Name providers as `<Site>Provider`, models as data classes, and tests with descriptive backtick names such as ``extract all episode targets for series page``. Prefer existing helpers in `util`, `CryptoHelper`, and `HttpClientManager` over duplicating parsing, URL, or HTTP behavior.

## Testing Guidelines

Tests use `kotlin.test` with JUnit Jupiter. Add focused tests beside related coverage in `src/test/kotlin`, especially when changing provider extraction, source selection, resume behavior, or segment URL generation. Prefer small fixtures embedded in tests unless a shared resource is clearly useful. Run `./gradlew test --no-daemon` before committing; run `./gradlew build --no-daemon` when changing packaging, ProGuard, or runtime dependencies.

## Commit & Pull Request Guidelines

Recent history uses short imperative subjects, sometimes with `feat:` or `fix:` prefixes. Keep subjects specific, for example `Fix Abyss source selection for zero part size` or `feat: download resumable whole sieutamphim series`. Pull requests should include a concise behavior summary, test results, and any relevant sample CLI command. Link issues when applicable and mention provider-specific risks or network assumptions.

## Security & Configuration Tips

Do not commit downloaded media, credentials, cookies, or local volume paths. Treat scraper headers and tokens as sensitive when copied from browser sessions. Keep generated build artifacts under `build/` out of source changes unless release packaging explicitly requires them.
