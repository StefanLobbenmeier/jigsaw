# Implementation plan

## 1. Scaffold — complete

- Kotlin/JS Compose canvas and desktop JVM targets, sharing a single application.
- Gradle wrapper, responsive scrollable landing screen, piece-count selection and grid preview.
- Static browser distribution and GitHub Actions Pages workflow; no remote or publication yet.

## 2. Deterministic jigsaw geometry and play — complete

- Add pure common Kotlin geometry types under `dev.jigsaw.model` (or a `geometry` package).
- Consume `PuzzleSpec(columns, rows, seed)`; generate each internal edge once and reverse it for its neighbor. Boundary edges stay flat. Distinct seeded profiles must give unique complementary cuts.
- Keep geometry independent of Compose Path; adapt curves to rendering in UI.
- Introduce piece poses, board coordinates, hit testing, drag handling, snap tolerances and connected groups. Snapping must validate actual neighbors and rigid group offsets.
- Add deterministic, complementarity, boundary and group-merging tests. Replace grid preview with a playable procedural-artwork puzzle.

## 3. Image library and local uploads — complete

- Add curated images with explicit source, author, license and attribution metadata; only ship assets with verified redistribution rights.
- Add platform-specific local file pickers behind shared interfaces, decode/crop images and render each piece using its shared outline.
- Handle invalid/large files and cancellation. Keep geometry and artwork selection independent.

## 4. Stash, polish and save — complete

- Stash and sort pieces by color, shape, and manual placement; preserve user groups.
- Add zoom/pan, reference preview, edge filters, progress/completion, keyboard/accessibility behavior and small-screen controls.
- Define versioned session serialization (spec, image identity, piece/group poses, stash arrangement). Supply browser and desktop persistence adapters; reload and resume.

## 5. Installable offline PWA — complete, final implementation stage

- Add a web app manifest, app name/theme and install icons (including maskable icons).
- Add a service worker that caches the app shell and bundled puzzles for offline play; define update/cache migration behavior.
- Support deployment under the GitHub Pages repository subpath: manifest start URL/scope, service worker scope, assets and puzzle URLs must resolve there.
- Validate installability and installed launch, then offline cold launch and bundled puzzle play under the Pages subpath. Document browser differences and upload/persistence limitations.
- Implement this after images and stash/persistence, before final validation/publication. PWA implementation is complete; final review/publication remains stage 6.

## 6. Validate and publish — release complete; platform checks remain

- Run geometry/session tests, production browser build, desktop compilation and browser interaction checks, including touch, reload/resume, uploads and Pages subpath asset loading.
- Check image rights, choose project license, review accessibility/performance, and document browser/runtime limitations.
- The user has authorized a public GitHub repository and Pages. After implementation and validation, create the repository, push, enable Pages Actions source, and verify the deployed URL and workflow. Publication is authorized in this final stage.

## Stage 2 verification and behavior

- Pure common `PuzzleGeometry` generates deterministic cubic curves once per seam, reversing the same curves for neighbors; boundary edges are flat. Profiles vary with the seed.
- Immutable `PuzzleSession` / `PieceGroup` store solved-coordinate translations. Dragging moves every piece in a group. Release snaps only actual grid neighbors within 0.28 cell units, adopts the target translation and recursively checks further connections. Hit testing uses sampled curved outlines, in reverse group draw order.
- Canvas clips original procedural artwork to each outline, draws piece boundaries, handles dragging and displays connection progress/completion. Restart scatters deterministically.
- Verification: `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/current PATH=/Users/stefan/.sdkman/candidates/java/current/bin:$PATH ./gradlew :composeApp:desktopTest :composeApp:jsBrowserDistribution :composeApp:compileKotlinDesktop`.
- Pure tests cover reproducibility, seed variation, complementary curves, boundaries, hit testing, group translation, recursive merges, wrong neighbors (including row wrapping), tolerance and completion.

## Next-agent interface

Stage 3 complete: `dev.jigsaw.artwork` owns library credits, common validation/crop/decode and expected `pickLocalImage()` with browser/desktop implementations. `PuzzleArtwork` holds a bitmap independently of `PuzzleSpec` / geometry / session. `ArtworkCredit.id` is stable for bundled choices, `file` resolves via Compose resources. `PuzzleBoard` draws the same centered crop through every existing piece outline. Original artwork is the fallback/default. UI supports source credits, import, collection switching, reference preview and new seeded games.

Stage 4: session state is immutable and currently owned by `JigsawApp`; groups contain piece IDs and one translation (no rotations). Add versioned serialization and stash metadata around these models. `move`, `snap`, `raise` and `hitTest` are pure. Progress counts joined connections, not board placement; completed groups can still move. Zoom/pan, persistence, stash and keyboard controls remain stage 4 work. Hit testing approximates curves with 12 segments per cubic. Browser/touch interaction still needs final manual validation. No PWA code has been added in stage 2.

Stage 5: implement the newly required installable/offline PWA before stage 6 validation/publication. Public repository and Pages authorization is already granted. Commit each stage with verification notes and update this plan.


## Stage 3 verification and handoff

- Bundled two reduced Wikimedia JPEGs (1280px wide) after browsing their original Commons file/licensing pages, the Public Domain Mark deed and PD-Art reuse guidance. `IMAGE_RIGHTS.md` records exact assets, creator/digitizer credits, source URLs, rights basis, revision IDs and SHA-256 hashes. Runtime image loading is local to the distribution.
- Both pickers return `PickedImage?`; null is cancellation. Loading/decode errors retain the prior image/session. File reads are capped at 12 MiB; shared PNG/JPEG header validation caps dimensions at 8192 per edge and 16 MP before native decode. Desktop file reading and decoding run off the UI thread; web decoding can briefly block it.
- `centerCrop` is shared by rendering and pixel sampling. `PuzzleArtwork.bitmap` exposes pixels; `averagePieceColor` samples 64 points per cell for sorting. Cache colors when implementing stash, rather than sampling during drawing. Procedural artwork has no bitmap and returns null colors: stage 4 can add a procedural sampler if needed.
- Persistence must store bundled artwork ID, puzzle spec including seed, immutable group poses, and uploaded bytes under a generated unique identity. The current generic `upload` ID is only an in-memory UI marker, not a persistence key. Restore bytes via validation/decode and then restore geometry/session. Do not serialize Compose bitmap objects.
- Passed SDKMAN-current Java command: `./gradlew :composeApp:desktopTest :composeApp:jsBrowserDistribution :composeApp:compileKotlinDesktop`. Ten desktop tests cover existing models plus bounds, crop, actual bundled resource decode and color sampling. Production distribution contains both images.
- Manual picker/dialog and browser/touch validation remains. Native browser `cancel` event needs a modern browser; older browsers may leave import busy after cancellation. EXIF orientation is not normalized; PNG/JPEG only, first frame only, no automatic downsampling. Accepted large images may use ~64 MB plus renderer copies. Bounded `PuzzleArtwork.encodedBytes` are available in memory for the next persistence adapter; reload storage is not implemented yet.
- No publication or PWA implementation in this stage. Preserve sequence: stash/persistence next, installable offline PWA after that, then final validation/publication. Bundled files alone do not make browser cold launch work offline.

## Stage 4 verification and next-agent handoff

- `Workspace` wraps immutable session state with group representative stash order and `StashSort`. Transfers preserve whole groups; only visible board groups participate in snapping. Stash thumbnails use the actual clipped artwork. Color keys are cached outside draw loops (8×8 bitmap samples; equivalent primitive sampling for the original landscape). Shape keys sort corner/edge count then clockwise flat/tab/socket profile. Manual arrows retain explicit order.
- Shared UI supplies separate scrollable stash, edge-only stash filter, board selection/return, keyboard-operable group cycling/nudging/join, zoom with scroll pan, fit-all bounds, reference, connection progress/completion, reset/new seed and collection. Image/size changes start fresh games. Fit-all bounds include all board pieces; filters never delete pieces.
- `save/SessionSave.kt` defines `JIGSAW:1`: bounded text envelope, base64 image identity/name/upload bytes, spec/seed, group translations/membership, stash order and sort. Geometry regenerates deterministically. Decode rejects unsupported versions, oversized specs/envelopes/uploads, nonfinite/unbounded poses, duplicate/missing IDs, disconnected groups and invalid stash representatives. Uploaded images are validated/decoded again on restore.
- Browser adapter: localStorage `jigsaw.session.v1`. Desktop adapter: `~/.jigsaw/session-v1.txt`, temporary file plus atomic replacement. Autosave debounce 500 ms; failures preserve prior save and show storage/error feedback. Uploads above 2 MiB are playable but unsavable; no silent byte omission. Save is device/origin local, single-slot, no synchronization. An immediate close can lose the last debounce window; imported images retain stage 3 decode limits/orientation caveats.
- Verification: 15 desktop tests (five new pure Workspace/save tests), production JS distribution, desktop compilation using SDKMAN current Java. T3 preview loaded the title; two snapshots returned `Preview automation snapshot timed out after 15000ms.` Visual, touch, native dialog, reload and assistive-technology behavior remain unverified; keep these in final validation. Desktop was compiled/tested, not interactively launched.
- NEXT: stage 5 only, installable offline PWA. Preserve common models and save version/key; cache shell and bundled art under repository-relative URLs. Save storage does not provide offline cold launch. Verify manifest scope/start URL, install icons/maskable, service-worker cache migration and offline launch under a Pages subpath. Then stage 6 validation/publication. No PWA files, remote, publication or delegation added in stage 4.

## Stage 5 verification and final-review handoff

- Added relative standalone manifest and normal/maskable/touch PNG icons; HTML help exposes native install where available, browser menu/Safari instructions and offline/update/storage feedback. Compose mounts inside a flex-sized app container so help remains reachable outside the canvas.
- Production distribution generates a content-hashed worker from the final output (17 precached assets, excluding source maps). Includes both Wasm payloads, all JS/Skiko modules, both bundled images and the licensed Noto Sans Symbols fallback discovered during offline browser testing. The explicit runtime font URL maps to its bundled cache entry. Audit fallback URLs after dependency updates; arbitrary non-Latin upload filenames may need additional fallback fonts.
- Installation is atomic with respect to activation: failed downloads delete the partial cache; no skipWaiting; updates wait for all old windows to close. Activation cleans only that repository path's obsolete caches. Saves retain `JIGSAW:1` and `jigsaw.session.v1` unchanged.
- SDKMAN-current Java: `./gradlew :composeApp:desktopTest :composeApp:jsBrowserDistribution :composeApp:compileKotlinDesktop` passed, including configuration-cache reuse. Existing webpack size/deprecation warnings remain. `node scripts/test-pwa.cjs` passed: all final assets, HTTP/network failure cleanup, directory/index navigation, font routing, no arbitrary-response caching and cache isolation.
- T3 preview opened the local `/jigsaw/` URL; two snapshots returned `Preview automation snapshot timed out after 15000ms.` Following fallback rules, used local Playwright/Chromium 149. Persistent profile DevTools `Page.getInstallabilityErrors` returned an empty list. Verified worker scope `/jigsaw/`, all cached assets under that path, and a new page launching with the browser context offline after closing the original page. Both bundled artworks decoded/rendered offline; a Great Wave piece transferred from stash to play and autosaved. The symbol font returned 57,708 bytes offline. No page errors. Browser artifacts/logs remain in `/tmp/jigsaw-*` for this workstation.
- Remaining stage 6 validation: actual OS installation/standalone window launch, iOS/Safari and other browsers, touch and assistive technology, desktop interactive launch, real deployed HTTPS Pages/update lifecycle, quota/eviction and save-resume edge cases. No remote, public repository, push or deployment was created. Proceed to final review, then public GitHub + Pages publication.

## Stage 6 final review and release evidence

- Final source/history review found no common embedded credential patterns. Added MIT license for code/original artwork; retained independent image and OFL notices. Rechecked Commons rights pages and both image hashes.
- Fixed canvas overflow from stash thumbnails by clipping each puzzle canvas to its layout bounds.
- After the fix, 15 desktop tests, production distribution and desktop compilation pass; the PWA checker passes with 17 assets. CI now runs those tests/checks before deployment.
- Chromium interaction checks: final correct-neighbor snap from a prepared 23-piece group plus last piece, resulting 24-piece rigid-group drag, exact saved-state restore, native JPEG chooser import and uploaded-image restore, color/shape stash sorting, manual reorder and Play transfer. Mobile 390×844 Chromium touch events moved a piece and autosaved it. Physical touch devices, Safari/iOS, actual OS installation, screen readers, interactive desktop and exhaustive completion from the initial stash remain unvalidated; these are follow-up checks, not claimed complete.
- Public repository created at https://github.com/StefanLobbenmeier/jigsaw with Pages Actions source enabled. Workflow https://github.com/StefanLobbenmeier/jigsaw/actions/runs/40195233170 passed build, tests, PWA checks and Pages deployment.

- Live HTTPS verification: HTTP 200 and all 17 cached asset URLs succeeded under the actual `/jigsaw/` path; manifest and worker scope verified. Chromium installability errors were empty. A fresh page cold-launched offline, decoded both bundled images, transferred a Great Wave piece into play, autosaved and loaded the 57,708-byte cached symbol font without page errors. Final snap/group drag/reload and JPEG import/restore were also exercised on the deployed site.
- Release/publication scope is complete. The platform/accessibility/installation checks listed above remain explicitly unvalidated follow-up work; stage 5's completion refers to PWA implementation and tested browser offline behavior, not actual OS installation. CI emits action-runtime deprecation notices but completes successfully.
