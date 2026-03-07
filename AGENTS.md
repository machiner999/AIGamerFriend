# Repository Guidelines

## Communication
- All user-facing responses must be in Japanese.
- Code, commands, logs, and error messages may remain in original language (English).

## Project Structure & Module Organization
Single-module Android app built with Kotlin + Jetpack Compose.
- `app/src/main/java/com/example/aigamerfriend/`: application code
- `app/src/main/java/.../ui/`: Compose UI (`screen/`, `component/`, `theme/`)
- `app/src/main/java/.../data/`: WebSocket client, audio, and DataStore-backed state
- `app/src/main/java/.../viewmodel/`: screen state and session orchestration
- `app/src/test/java/com/example/aigamerfriend/`: JVM unit tests (no instrumented test folder currently)
- `.github/workflows/ci.yml`: CI pipeline (lint + unit tests + release build)

## Build, Test, and Development Commands
Run from repository root:
- `./gradlew assembleDebug`: build debug APK
- `./gradlew assembleRelease`: build release APK (uses signing config if `keystore.properties` exists)
- `./gradlew testDebugUnitTest`: run JVM unit tests
- `./gradlew ktlintCheck`: Kotlin lint checks
- `./gradlew ktlintFormat`: auto-format Kotlin sources
- `./gradlew ktlintCheck testDebugUnitTest assembleRelease`: same sequence enforced in CI

## Coding Style & Naming Conventions
- Follow `.editorconfig`: 4-space indentation, LF endings, UTF-8, max line length 120.
- Kotlin naming: `PascalCase` for classes/objects, `camelCase` for functions/properties, `UPPER_SNAKE_CASE` for constants.
- Test files should mirror production types (examples: `GamerViewModelTest.kt`, `AIFaceKtTest.kt`).
- Use ktlint as the source of truth before opening a PR.
- Branch naming: `feature/<topic>`, `fix/<topic>`, `docs/<topic>`, `chore/<topic>`.

## Testing Guidelines
- Frameworks: JUnit 4, MockK, and `kotlinx-coroutines-test`.
- Add tests for non-trivial ViewModel/data-layer changes and JVM-testable UI logic.
- Prefer deterministic coroutine tests with controlled dispatchers.
- For ViewModel behavior changes, include at least:
  - one success-path test,
  - one failure/edge-case test.
- Run `./gradlew testDebugUnitTest` locally before pushing.

## Commit & Pull Request Guidelines
- Commit style in this repo follows Conventional Commit-like prefixes: `feat:`, `fix:`, `docs:`.
- Keep commits focused and descriptive.
- PR checklist:
  - concise behavior summary,
  - affected area(s) (`ui`, `viewmodel`, `data`, etc.),
  - test evidence (exact commands run),
  - linked issue (if applicable),
  - screenshots/GIFs for UI changes.
- Ensure CI passes on `main` targets (`ktlintCheck`, `testDebugUnitTest`, `assembleRelease`).

## Security & Configuration Tips
- Do not commit secrets. Keep `GEMINI_API_KEY` in `local.properties`.
- Keep `app/google-services.json`, `keystore.properties`, and `*.jks` local only (already gitignored).

## Setup Notes
- Required local environment: Android SDK + JDK 17.
- Minimum local config before build:
  - `local.properties` with `GEMINI_API_KEY=...`
  - `app/google-services.json` for Firebase features
- Release signing is optional locally; config loads only when `keystore.properties` exists.

## Definition of Done
- Code is formatted/lint-clean: `./gradlew ktlintCheck`
- Unit tests pass: `./gradlew testDebugUnitTest`
- Build is healthy: `./gradlew assembleDebug` (and `assembleRelease` when release-related changes are included)
- UI changes include updated screenshots/GIFs in the PR.
