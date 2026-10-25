# Jigsaw

A quiet jigsaw app built with Kotlin Compose Multiplatform. Shared canvas UI runs in the browser (Kotlin/JS) and on desktop (JVM).

The scaffold includes an original procedural landscape, working piece-count selection, and a grid preview. Actual interlocking pieces, dragging, snapping, uploads, and persistence are planned; see [PLAN.md](PLAN.md).

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
./gradlew :composeApp:jsBrowserDistribution :composeApp:compileKotlinDesktop
```

Serve `composeApp/build/dist/js/productionExecutable/` with any static HTTP server. Open via HTTP rather than `file://` because the canvas runtime loads additional assets. The distribution uses relative asset paths for GitHub Pages project subdirectories. Kotlin/JS still uses Skia's WebAssembly renderer; a browser with WebAssembly is required.

## Layout

- `composeApp/src/commonMain/kotlin/dev/jigsaw/model`: platform-independent puzzle inputs and, next, geometry/session logic.
- `composeApp/src/commonMain/kotlin/dev/jigsaw/ui`: shared Compose UI and original canvas artwork.
- `composeApp/src/jsMain`: browser entry point and static HTML shell.
- `composeApp/src/desktopMain`: JVM window entry point.

Kotlin 2.4.21, Compose Multiplatform 1.12.2, Gradle 9.8.2. Generic build conventions were checked against the local FreeCell project; no FreeCell application code or assets were copied. Versions and canvas entry point follow the [official compatibility guide](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) and [web release documentation](https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html).

## Publishing later

No GitHub repository or remote has been created. When ready, create the public repository, push `main`, and select **Settings → Pages → Source → GitHub Actions**. `.github/workflows/pages.yml` builds browser assets, compiles desktop, and deploys the static artifact. Repository/organization Pages permissions must allow deployment. The workflow cannot be validated end-to-end until a repository exists.

No third-party images are bundled. The landscape is drawn directly in Kotlin. A project license must be chosen before public release; image attribution/license metadata will be part of stage 3.

## Scaffold validation

`./gradlew :composeApp:jsBrowserDistribution :composeApp:compileKotlinDesktop` passed on 2026-10-25 with SDKMAN's active Java. The static distribution was served locally and the collaborative browser loaded the HTML title. Two browser snapshot attempts timed out, so visual rendering and click behavior remain unverified. Desktop was compiled, not launched.

Known build warnings: Compose dependency DSL and Gradle delegate APIs are deprecated (Gradle 10 migration will need attention); webpack reports large canvas runtime bundles. Current production output includes approximately 2.73 MiB JS plus an 8.25 MiB Skia Wasm payload, with additional runtime assets. Keep the entire distribution when publishing. `kotlin-js-store/yarn.lock` is committed for repeatable JS tooling resolution.
