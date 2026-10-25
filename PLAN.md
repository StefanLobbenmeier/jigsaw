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

## 4. Stash, polish and save

- Stash and sort pieces by color, shape, and manual placement; preserve user groups.
- Add zoom/pan, reference preview, edge filters, progress/completion, keyboard/accessibility behavior and small-screen controls.
- Define versioned session serialization (spec, image identity, piece/group poses, stash arrangement). Supply browser and desktop persistence adapters; reload and resume.

## 5. Installable offline PWA — pending, final implementation stage

- Add a web app manifest, app name/theme and install icons (including maskable icons).
- Add a service worker that caches the app shell and bundled puzzles for offline play; define update/cache migration behavior.
- Support deployment under the GitHub Pages repository subpath: manifest start URL/scope, service worker scope, assets and puzzle URLs must resolve there.
- Validate installability and installed launch, then offline cold launch and bundled puzzle play under the Pages subpath. Document browser differences and upload/persistence limitations.
- Implement this after images and stash/persistence, before final validation/publication. PWA implementation is intentionally deferred to a later sequential agent.

## 6. Validate and publish

- Run geometry/session tests, production browser build, desktop compilation and browser interaction checks, including touch, reload/resume, uploads and Pages subpath asset loading.
- Check image rights, choose project license, review accessibility/performance, and document browser/runtime limitations.
- The user has authorized a public GitHub repository and Pages. After implementation and validation, create the repository, push, enable Pages Actions source, and verify the deployed URL and workflow. This stage does not publish.

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
