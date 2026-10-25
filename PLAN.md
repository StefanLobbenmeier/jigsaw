# Implementation plan

## 1. Scaffold — complete

- Kotlin/JS Compose canvas and desktop JVM targets, sharing a single application.
- Gradle wrapper, responsive scrollable landing screen, piece-count selection and grid preview.
- Static browser distribution and GitHub Actions Pages workflow; no remote or publication yet.

## 2. Deterministic jigsaw geometry and play

- Add pure common Kotlin geometry types under `dev.jigsaw.model` (or a `geometry` package).
- Consume `PuzzleSpec(columns, rows, seed)`; generate each internal edge once and reverse it for its neighbor. Boundary edges stay flat. Distinct seeded profiles must give unique complementary cuts.
- Keep geometry independent of Compose Path; adapt curves to rendering in UI.
- Introduce piece poses, board coordinates, hit testing, drag handling, snap tolerances and connected groups. Snapping must validate actual neighbors and rigid group offsets.
- Add deterministic, complementarity, boundary and group-merging tests. Replace grid preview with a playable procedural-artwork puzzle.

## 3. Image library and local uploads

- Add curated images with explicit source, author, license and attribution metadata; only ship assets with verified redistribution rights.
- Add platform-specific local file pickers behind shared interfaces, decode/crop images and render each piece using its shared outline.
- Handle invalid/large files and cancellation. Keep geometry and artwork selection independent.

## 4. Stash, polish and save

- Stash and sort pieces by color, shape, and manual placement; preserve user groups.
- Add zoom/pan, reference preview, edge filters, progress/completion, keyboard/accessibility behavior and small-screen controls.
- Define versioned session serialization (spec, image identity, piece/group poses, stash arrangement). Supply browser and desktop persistence adapters; reload and resume.

## 5. Validate and publish

- Run geometry/session tests, production browser build, desktop compilation and browser interaction checks, including touch, reload/resume, uploads and Pages subpath asset loading.
- Check image rights, choose project license, review accessibility/performance, and document browser/runtime limitations.
- Create public GitHub repository only when authorized, push, enable Pages Actions source, and verify the deployed URL and workflow.

## Next-agent interface

Start from `PuzzleSpec` and `starterPuzzles`; keep the existing package roots and entry points. The `JigsawApp` currently owns temporary selection/preview state. Extract session state when gameplay arrives. `Landscape` is private original procedural artwork, intentionally disposable as an initial renderer; it can be extracted for clipping pieces. The current grid is explicitly a preview and provides no geometry guarantees. Avoid platform APIs in common geometry/state; use adapters in target source sets. Commit each stage with verification notes and update this plan.
