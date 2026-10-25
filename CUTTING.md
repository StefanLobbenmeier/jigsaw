# Rectangular puzzle cuts

The generator keeps the rectangular cell grid and creates each internal seam once. Neighbors use the exact same cubic curves in reverse, so matching outlines cannot drift apart. Borders remain flat.

## Approach and investigation

Procedural generators commonly use shared edges and parameterized Bézier connectors. Examples reviewed:

- https://github.com/Shadow-Puppet/JigsawGenerator — rectangular shared-edge grid, knob offset, taper, size and jitter.
- https://github.com/playjigsaw/play-jigsaw-puzzles-online/blob/main/docs/ALGORITHM.md — complementary edges and cubic Bézier construction.
- https://github.com/proceduraljigsaw/ImpactPuzzle — alternative irregular cuts with intersection/clearance checks; its radial/glass layout does not fit this game's rectangular requirement.

Our implementation is original, with no source code copied from these projects. Cut version 2 varies connector position, neck width, head width, depth, polarity and shear (lean), producing tilted, offset knobs rather than repeating one centered profile. A seeded PRNG keeps cuts reproducible. Shearing the head relative to the neck changes its angle without rotating the rectangular cell.

## Safety by construction

In seam-local cell units, every control point of the curved connector lies between 0.26 and 0.74 along the edge, and at most 0.22 away from it. The remaining shoulders are straight. Cubic Bézier curves stay within the convex hull of their control points, so these bounds apply to the entire curve, not only samples.

Adjacent-side socket envelopes have at least 0.04 cells clearance (0.26 minus 0.22). Opposing sockets leave a central 0.56 × 0.56 square intact: at least 31.36% of the cell area, even with four inlets. The neck's base width is at least 0.07 cells. Randomization never bypasses these limits. Tests exercise 4,800 pieces across 100 seeds, check all curved control points, sample the protected core and verify complementary deterministic seams.

## Saves and rendering

The spec includes a cut version. Existing three-field saved specs load version 1 and retain their original cuts; new puzzles use version 2. The save envelope/storage key remain compatible with existing local data, and the reader accepts up to 1,000 pieces. New game or choosing an image/count uses version 2; resetting an existing puzzle preserves its cuts.

Paths and hit-test polygons are cached per geometry. Dragging changes only a visual preview until release, and the camera does not auto-fit on piece movement. The tray draws just the visible columns. Image color sampling shares a single cached pixel map.

## Verification

Use SDKMAN's current Java for Gradle:

```sh
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/current PATH=/Users/stefan/.sdkman/candidates/java/current/bin:$PATH ./gradlew :composeApp:desktopTest :composeApp:jsBrowserDistribution
node scripts/test-pwa.cjs
```

Serve `composeApp/build/dist/js/productionExecutable` on port 8767, then run `node scripts/test-interactions.cjs` with Playwright/Chromium installed. `URL` overrides the server URL; `PLAYWRIGHT_MODULE` can point to an external Playwright installation. The interaction test checks actual touch input at 24 and 1,000 pieces, exact displacement before/after pinch zoom, tray transfers and saved-state restore. Physical devices and Safari are separate manual checks.
