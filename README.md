# Jigsaw

A quiet jigsaw app built with Kotlin Compose Multiplatform. Shared canvas UI runs in the browser (Kotlin/JS) and on desktop (JVM).

Play the original procedural landscape with seeded interlocking pieces, dragging, neighbor snapping, rigid connected groups and completion progress. Uploads, stash/persistence and an installable offline PWA are planned; see [PLAN.md](PLAN.md).

## Run and build

Requires a JDK (21 recommended). Gradle downloads its own Node/Yarn toolchain for web builds. On this workstation use SDKMAN's active Java:

```sh
export JAVA_HOME=/Users/stefan/.sdkman/candidates/java/current
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :composeApp:jsBrowserDevelopmentRun
./gradlew :composeApp:run
```

Verification and production distribution:

```sh
./gradlew :composeApp:desktopTest :composeApp:jsBrowserDistribution :composeApp:compileKotlinDesktop
```

Serve `composeApp/build/dist/js/productionExecutable/` with any static HTTP server. Open via HTTP rather than `file://` because the canvas runtime loads additional assets. The distribution uses relative asset paths for GitHub Pages project subdirectories. Kotlin/JS still uses Skia's WebAssembly renderer; a browser with WebAssembly is required.

## Layout

- `composeApp/src/commonMain/kotlin/dev/jigsaw/model`: platform-independent puzzle inputs, cubic geometry, hit testing and immutable session/group logic.
- `composeApp/src/commonMain/kotlin/dev/jigsaw/ui`: shared Compose UI and original canvas artwork.
- `composeApp/src/jsMain`: browser entry point and static HTML shell.
- `composeApp/src/desktopMain`: JVM window entry point.

Kotlin 2.4.21, Compose Multiplatform 1.12.2, Gradle 9.8.2. Generic build conventions were checked against the local FreeCell project; no FreeCell application code or assets were copied. Versions and canvas entry point follow the [official compatibility guide](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) and [web release documentation](https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html).

## Publishing later

The user has authorized a public GitHub repository and Pages. No remote has been created yet. After the remaining implementation and validation stages, create the public repository, push `main`, and select **Settings → Pages → Source → GitHub Actions**. `.github/workflows/pages.yml` builds browser assets, compiles desktop, and deploys the static artifact. Repository/organization Pages permissions must allow deployment. The workflow cannot be validated end-to-end until a repository exists.

No third-party images are bundled. The landscape is drawn directly in Kotlin. A project license must be chosen before public release; image attribution/license metadata will be part of stage 3.

## Scaffold validation

`./gradlew :composeApp:desktopTest :composeApp:jsBrowserDistribution :composeApp:compileKotlinDesktop` passed on 2026-10-25 with SDKMAN's active Java. The static distribution was served locally and the collaborative browser loaded the HTML title. Two browser snapshot attempts timed out, so visual rendering and click behavior remain unverified. Desktop was compiled, not launched.

Known build warnings: Compose dependency DSL and Gradle delegate APIs are deprecated (Gradle 10 migration will need attention); webpack reports large canvas runtime bundles. Current production output includes approximately 2.73 MiB JS plus an 8.25 MiB Skia Wasm payload, with additional runtime assets. Keep the entire distribution when publishing. `kotlin-js-store/yarn.lock` is committed for repeatable JS tooling resolution.

## Stage 2 validation

Pure model tests cover deterministic complementary seams, hit testing, rigid group moves, recursive snapping, wrong neighbors, tolerance and completion. The production browser build and desktop compilation pass with the warnings above. Manual browser/touch gameplay checks remain for final validation. Sessions are currently in memory; refresh resets play. The PWA stage is explicitly pending.
