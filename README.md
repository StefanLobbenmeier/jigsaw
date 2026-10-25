# Jigsaw

[Play Jigsaw](https://stefanlobbenmeier.github.io/jigsaw/) · [Public repository](https://github.com/StefanLobbenmeier/jigsaw)

A quiet jigsaw app built with Kotlin Compose Multiplatform. Shared canvas UI runs in the browser (Kotlin/JS) and on desktop (JVM).

Play the original procedural landscape with seeded interlocking pieces, dragging, neighbor snapping, rigid connected groups and completion progress. Choose bundled public-domain art or a local PNG/JPEG, view the reference and start a new game. A separate sortable stash and device-local progress saves are included. The browser app is installable and supports offline bundled puzzles after its first complete download; see [PLAN.md](PLAN.md).

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

## License and deployment

Application code and original procedural artwork use the [MIT license](LICENSE). Bundled public-domain reproductions have separate records in [IMAGE_RIGHTS.md](IMAGE_RIGHTS.md); the Noto font retains its OFL license.

Pushes to `main` run desktop model tests, build the browser distribution, compile desktop, check the PWA cache, then deploy through GitHub Pages Actions. The complete output is published under `/jigsaw/`. Deployment validation is recorded below.

## Scaffold validation

`./gradlew :composeApp:desktopTest :composeApp:jsBrowserDistribution :composeApp:compileKotlinDesktop` passed on 2026-10-25 with SDKMAN's active Java. The static distribution was served locally and the collaborative browser loaded the HTML title. Two browser snapshot attempts timed out, so visual rendering and click behavior remain unverified. Desktop was compiled, not launched.

Known build warnings: Compose dependency DSL and Gradle delegate APIs are deprecated (Gradle 10 migration will need attention); webpack reports large canvas runtime bundles. Current production output includes approximately 2.73 MiB JS plus an 8.25 MiB Skia Wasm payload, with additional runtime assets. Keep the entire distribution when publishing. `kotlin-js-store/yarn.lock` is committed for repeatable JS tooling resolution.

## Stage 2 validation

Pure model tests cover deterministic complementary seams, hit testing, rigid group moves, recursive snapping, wrong neighbors, tolerance and completion. The production browser build and desktop compilation pass with the warnings above. Manual browser/touch gameplay checks remain for final validation. Stage 4 adds saved sessions as described below. Offline launch is now supplied by stage 5 below.

## Images and local import

The collection is packaged with both targets; playing bundled art needs no remote image server. Browser offline cold launch is supplied by the production PWA cache. Source links require internet access.

The browser uses the native file chooser; desktop uses an AWT file dialog. Choose PNG or JPEG, at most **12 MiB**, **8192 pixels per side**, and **16 megapixels**. File size is bounded before reading; image dimensions are checked before decoding. Unsupported, corrupt and oversized images show an error while preserving the current image/game. Cancelling preserves the game. Modern browsers' native `cancel` event is required; older browsers that omit it may leave the import button busy until reload. Files stay local and are not sent to a server.

Images are center cropped to the selected puzzle aspect ratio, without stretching. Preview, all clipped piece images and color samples share this crop. Only the first image frame is used; EXIF orientation is not normalized (rotate/export a photo first if needed). Decoded images are retained at source size within the limits above; an accepted 16 MP image can consume about 64 MB plus renderer copies, and web decoding may briefly pause the UI. Imports and progress use the stage 4 persistence limits described below.

Stage 3 verification: desktop tests (10 total), `jsBrowserDistribution`, and `compileKotlinDesktop` passed using SDKMAN's current Java. Tests cover image/header limits, crop coordinates, bundled resource decoding and color access alongside the stage 2 geometry/session suite. Browser file chooser interaction, touch play and desktop dialog interaction still need manual validation. Existing Gradle/webpack warnings remain; Skiko also warns about future JVM native-access requirements.

For stage 4, see `dev.jigsaw.artwork`: `ArtworkCredit.id` identifies bundled choices; `PuzzleArtwork.bitmap` gives pixel access and `averagePieceColor(column, row, columns, rows)` samples a crop-aligned 8×8 grid per cell for color sorting (cache results outside draw loops). Original procedural artwork returns null for pixel/color access. Persist `PuzzleArtwork.encodedBytes` separately under a unique key, not the generic `upload` identity; restore through `decodeArtwork` so limits are enforced again.

## Stash and saved progress (stage 4)

New puzzles begin in a separate scrollable stash. Play moves a whole piece/group into the playing area; select a board group to return it. Joined groups are never split. Sort by sampled average image brightness/color, or corners/edges followed by clockwise flat/tab/socket profile. Arrow buttons reorder manually. The edge filter hides stash interiors only; choose All pieces to recover the full list.

Drag to join, or use Select next board group, directional controls and Join with Tab/Enter. Zoom enlarges the workspace; scroll horizontally/vertically to pan. Fit all restores 1× and includes every board piece. Reference and connection progress remain available during play. Reset keeps the image/spec; New game changes the seed. Image or size selection starts a fresh puzzle.

Progress autosaves after 500 ms without changes and restores on reopening. Browser storage uses localStorage key `jigsaw.session.v1` (per origin); desktop uses `~/.jigsaw/session-v1.txt` with atomic replacement. The versioned envelope stores deterministic spec/seed, image identity, group membership/translations and stash order/sort. Uploaded PNG/JPEG bytes are included under a unique upload identity up to **2 MiB**, below the 12 MiB import limit. Larger uploads remain playable but cannot save that game. Storage/quota/corrupt-save failures show feedback and retain the last successfully written save. No server or cross-device synchronization. Clearing browser site data removes progress. Private browsing/storage policies can prevent saving. Closing immediately within the debounce window may lose the final action.

Stage 4 verification: 15 desktop tests pass, including five new pure tests for serialization round trips, malformed/version/duplicate/missing/disconnected/non-finite data, upload limits, color/shape ordering, manual order and group transfer invariants. Production JS distribution and desktop compilation pass using SDKMAN current Java. T3 preview navigation loaded the title; two snapshot attempts failed with `Preview automation snapshot timed out after 15000ms.` Browser rendering, touch, keyboard/screen-reader behavior, reload and native picker/dialog interaction remain manually unverified. Compose canvas semantics depend on runtime support; keyboard group controls provide an alternative to dragging. Existing bundle/deprecation/native-access warnings remain.

## Install and play offline (stage 5)

Serve the **complete production distribution** over HTTPS (localhost HTTP works for testing). Development builds do not generate the production service worker. Open the app online and wait for **Install & offline — Ready offline** at the bottom. Expand that help for browser-specific instructions. Chrome/Edge provide an address-bar/menu install action and, when available, an Install Jigsaw button. Safari on iPhone/iPad provides Share → Add to Home Screen. Installation UI varies by browser/OS; a normal tab remains usable with JavaScript and WebAssembly. Offline caching requires service workers and sufficient site storage.

The manifest uses `id`, `start_url` and `scope` of `./`, standalone display, normal 192/512px PNG icons and a separate 512px maskable icon with its artwork inside the safe zone. An Apple touch icon is included. All links, worker registration and cache URLs resolve relative to the deployment directory, including `/jigsaw/` on GitHub Pages; no root-domain worker permission is required. Preserve the trailing slash on the published app URL.

`jsBrowserDistribution` generates `service-worker.js` **after webpack/resource copying**, hashing the worker template and every production file except source maps/the worker itself. It precaches all shipped JS/modules, both Wasm payloads, Compose resources, bundled images, icons and fonts. Adding an asset automatically includes it. Failed HTTP/network downloads discard the partial new cache; a previous installed release stays usable. Ordinary uncatalogued requests/errors are never added. Source maps remain deployable but are not needed offline.

Updates download when the app opens online, wait while app windows are open, then activate after all windows close. Reopen to use the new release; no forced reload interrupts a game. Activation removes only older Jigsaw caches for the same deployment path. Cache changes do not touch localStorage or the `JIGSAW:1` / `jigsaw.session.v1` save format/key. Browser eviction or clearing site data can remove both offline assets and progress; reconnect to download again. Uploaded image persistence keeps its existing 2 MiB limit. External source/rights links require a network connection.

Compose includes its main text font in its runtime; its pinned Noto Sans Symbols fallback is also bundled locally, with its [OFL license](composeApp/src/jsMain/resources/fonts/OFL.txt). The worker maps the runtime's Google Fonts symbol URL to that precached copy, so first use of arrows/symbols works offline. Font source: `https://fonts.gstatic.com/s/notosanssymbols/v47/rP2up3q65FkAtHfwd-eIS2brbDN6gxP34F9jRRCe4W3gfQ8QA_9Edkw.woff2`; SHA-256 `2386dfba50e3a0df9efebd63f5774e4a455bc4c86f47b03eed82fea92ad86e3b`. Review fallback mappings when updating Compose; arbitrary uploaded filenames in other scripts can trigger additional font fallbacks.

After building, run `node scripts/test-pwa.cjs` (Node 18+, no npm dependencies) to check complete asset coverage, failed-install cleanup, subpath navigation, font mapping and cache isolation. For a local Pages simulation, serve a parent directory containing a `jigsaw` directory/symlink to the production output and open `http://localhost:<port>/jigsaw/`.

Stage 5 verification (2026-10-25): the SDKMAN Java verification command passed (15 desktop tests, production build and desktop compile), with configuration-cache reuse. `scripts/test-pwa.cjs` passed for 17 assets. Local Chromium 149 persistent-profile testing at `/jigsaw/` reported no installability errors. With the browser context offline, a new page cold-launched, both bundled artworks decoded/rendered, a Great Wave piece moved from stash into play, progress saved, and the symbol font returned 57,708 bytes from the worker. T3 snapshots timed out twice before this permitted browser fallback. Actual OS installation/standalone launch, Safari/iOS, touch, full puzzle completion, real Pages HTTPS deployment and end-to-end update activation still need final review. Headless installability checks do not verify an installed OS window.
