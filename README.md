# Jigsaw

A quiet jigsaw app built with Kotlin Compose Multiplatform. Shared canvas UI runs in the browser (Kotlin/JS) and on desktop (JVM).

Play the original procedural landscape with seeded interlocking pieces, dragging, neighbor snapping, rigid connected groups and completion progress. Choose bundled public-domain art or a local PNG/JPEG, view the reference and start a new game. A separate sortable stash and device-local progress saves are included. The installable offline PWA is next; see [PLAN.md](PLAN.md).

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

Two Wikimedia public-domain reproductions are bundled locally: The Starry Night and The Great Wave. Full creator/source/rights records and asset hashes are in [IMAGE_RIGHTS.md](IMAGE_RIGHTS.md); the application also shows credits and a source link. The original Kotlin landscape remains available. A software/project license must still be chosen before public release.

## Scaffold validation

`./gradlew :composeApp:desktopTest :composeApp:jsBrowserDistribution :composeApp:compileKotlinDesktop` passed on 2026-10-25 with SDKMAN's active Java. The static distribution was served locally and the collaborative browser loaded the HTML title. Two browser snapshot attempts timed out, so visual rendering and click behavior remain unverified. Desktop was compiled, not launched.

Known build warnings: Compose dependency DSL and Gradle delegate APIs are deprecated (Gradle 10 migration will need attention); webpack reports large canvas runtime bundles. Current production output includes approximately 2.73 MiB JS plus an 8.25 MiB Skia Wasm payload, with additional runtime assets. Keep the entire distribution when publishing. `kotlin-js-store/yarn.lock` is committed for repeatable JS tooling resolution.

## Stage 2 validation

Pure model tests cover deterministic complementary seams, hit testing, rigid group moves, recursive snapping, wrong neighbors, tolerance and completion. The production browser build and desktop compilation pass with the warnings above. Manual browser/touch gameplay checks remain for final validation. Sessions are currently in memory; refresh resets play. The PWA stage is explicitly pending.

## Images and local import

The collection is packaged with both targets; playing bundled art needs no remote image server. Browser offline cold launch still requires the planned PWA cache stage. Source links require internet access.

The browser uses the native file chooser; desktop uses an AWT file dialog. Choose PNG or JPEG, at most **12 MiB**, **8192 pixels per side**, and **16 megapixels**. File size is bounded before reading; image dimensions are checked before decoding. Unsupported, corrupt and oversized images show an error while preserving the current image/game. Cancelling preserves the game. Modern browsers' native `cancel` event is required; older browsers that omit it may leave the import button busy until reload. Files stay local and are not sent to a server.

Images are center cropped to the selected puzzle aspect ratio, without stretching. Preview, all clipped piece images and color samples share this crop. Only the first image frame is used; EXIF orientation is not normalized (rotate/export a photo first if needed). Decoded images are retained at source size within the limits above; an accepted 16 MP image can consume about 64 MB plus renderer copies, and web decoding may briefly pause the UI. Imports and progress remain in memory until stage 4 adds persistence.

Stage 3 verification: desktop tests (10 total), `jsBrowserDistribution`, and `compileKotlinDesktop` passed using SDKMAN's current Java. Tests cover image/header limits, crop coordinates, bundled resource decoding and color access alongside the stage 2 geometry/session suite. Browser file chooser interaction, touch play and desktop dialog interaction still need manual validation. Existing Gradle/webpack warnings remain; Skiko also warns about future JVM native-access requirements.

For stage 4, see `dev.jigsaw.artwork`: `ArtworkCredit.id` identifies bundled choices; `PuzzleArtwork.bitmap` gives pixel access and `averagePieceColor(column, row, columns, rows)` samples a crop-aligned 8×8 grid per cell for color sorting (cache results outside draw loops). Original procedural artwork returns null for pixel/color access. Persist `PuzzleArtwork.encodedBytes` separately under a unique key, not the generic `upload` identity; restore through `decodeArtwork` so limits are enforced again.

## Stash and saved progress (stage 4)

New puzzles begin in a separate scrollable stash. Play moves a whole piece/group into the playing area; select a board group to return it. Joined groups are never split. Sort by sampled average image brightness/color, or corners/edges followed by clockwise flat/tab/socket profile. Arrow buttons reorder manually. The edge filter hides stash interiors only; choose All pieces to recover the full list.

Drag to join, or use Select next board group, directional controls and Join with Tab/Enter. Zoom enlarges the workspace; scroll horizontally/vertically to pan. Fit all restores 1× and includes every board piece. Reference and connection progress remain available during play. Reset keeps the image/spec; New game changes the seed. Image or size selection starts a fresh puzzle.

Progress autosaves after 500 ms without changes and restores on reopening. Browser storage uses localStorage key `jigsaw.session.v1` (per origin); desktop uses `~/.jigsaw/session-v1.txt` with atomic replacement. The versioned envelope stores deterministic spec/seed, image identity, group membership/translations and stash order/sort. Uploaded PNG/JPEG bytes are included under a unique upload identity up to **2 MiB**, below the 12 MiB import limit. Larger uploads remain playable but cannot save that game. Storage/quota/corrupt-save failures show feedback and retain the last successfully written save. No server or cross-device synchronization. Clearing browser site data removes progress. Private browsing/storage policies can prevent saving. Closing immediately within the debounce window may lose the final action.

Stage 4 verification: 15 desktop tests pass, including five new pure tests for serialization round trips, malformed/version/duplicate/missing/disconnected/non-finite data, upload limits, color/shape ordering, manual order and group transfer invariants. Production JS distribution and desktop compilation pass using SDKMAN current Java. T3 preview navigation loaded the title; two snapshot attempts failed with `Preview automation snapshot timed out after 15000ms.` Browser rendering, touch, keyboard/screen-reader behavior, reload and native picker/dialog interaction remain manually unverified. Compose canvas semantics depend on runtime support; keyboard group controls provide an alternative to dragging. Existing bundle/deprecation/native-access warnings remain.
