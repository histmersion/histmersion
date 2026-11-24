# 0031. Every PR check runs on Linux; the per-platform bundles are built on demand

- Status: accepted (owner, 2026-11-23, queue row 39)
- Date: 2026-11-24

## Context

Until now a pull request ran two jobs on GitHub's hosted macOS runner (`macos-15`):

- `macos` ran the browser tests except the budgets, the scripts' tests and the CLI reference. It took 28–53 min
  (median 42).
- `macos-budgets` compared the speed budgets with the merge base. It took 38–66 min (median 48).

These jobs were there because the owner runs macOS. The owner asked, and decided (2026-11-23, verbatim):

> "Do we have some native platform specific code? No? Then we only need Linux runners for ci, and optimally test that
> we are able to create bundles for windows/Linux /mac"
>
> "I mean optionally test bundles. We want them on release but currently it's nice to have"

There is no platform-specific code in the application:

- The server and the CLI are Scala on the JVM.
- The web page is Scala.js in Chromium.
- The browser tests drive the same page on every platform.

The platform-specific part is the packaging: the native image, the bundled PostgreSQL and converter, the LaunchAgent
and the systemd unit, code signing, and the relink kit. Those are built and smoke-tested by `bundle.yml`.

The hosted Mac was also not representative of any user's device (row 29(i)). It is a shared virtual 3-core M1, and its
paravirtual GPU paints about 8 frames a second. The tests needed a mode just for it: with `HISTMERSION_VIRTUAL_GPU=1`
and a renderer whose name contains "paravirtual", the frame-bound figures and the click-to-next-frame checks were only
printed, not held (PR #20). The job's flake rate stayed high. Over the 45 `macos` jobs completed between 2026-11-22 and
2026-11-23 (pilot pushes and PRs), 18 were green. The tests that failed, and how often:

| failures | test |
|---|---|
| 8 | #1440: playback draws local and regional events… |
| 7 | the map neither refetches nor rebuilds when the filters fold… |
| 7 | map motion at 1920×1080, every layer on… |
| 6 | a zoom slides the points of a split bubble out… |
| 4 | a cluster of events is a square; a zoom animates by the markers drawn… |
| 2 each | the icon menus…; the canvas markers meet the keyboard…; autopilot: the switch…; a year-slider drag… |
| 1 each | five other tests |

Some of those runs were real defects of the PR under test; #34 later fixed the #1440 ones and several timing ones.
Even on pilot pushes, 2 of 6 `macos` jobs were red.

## Decision

- **Every job in `ci.yml` runs on `ubuntu-24.04`.** The `macos` and `macos-budgets` jobs are removed.
- **`scripts`** runs the scripts' tests (`just test-scripts`), the unit tests of the browser test tools, and the CLI
  reference against the CLI's help on the JVM. In the scripts' tests, the macOS-only parts (codesign, the TCC probe,
  `plutil`) skip themselves off macOS. `relink-test.sh` checks that Linux signs nothing.
- **`browser 1…4`** run the functional browser suite in four parallel shards.
  - Every shard loads every test file with `--import ./test/shard-preload.mjs`.
  - The preload registers only the shard's own tests. `perf.test.mjs` (its soak test alone takes about 9 minutes)
    goes whole to shard 1. Every other test goes to shard 2, 3 or 4 by the FNV-1a hash of its name. The rules are in
    `test-shards.mjs`.
  - A file's hooks are registered only when the file has a test in the shard, so a file with none launches no browser.
  - Each shard writes every test it saw, with the shard it belongs to, to a manifest.
  - A skipped test still fails its shard (G125).
- **`browser-shards`** reads the four manifests and junit reports (`shards-check.mjs`). It checks that the shards saw
  the same test list. It checks that every listed test except the budgets ran exactly once, in its own shard, and that
  none was skipped. Test names must be unique across the files, because the junit report names the preload, not the
  test file.
- **Chromium has no GPU on the Linux runner.** WebGL is drawn by SwiftShader, and the page takes its no-GPU path, as a
  person's browser without a GPU would.
  - Five tests check features of the GPU path: the base maps' relief and credits, the contours, the sea's depth, and
    the options whose data is missing. They call `withPage(…, { gpu: true })`. Without a GPU their pages are told a GPU
    renderer (`engine.mjs` `standInGpu`), so they take the GPU path, drawn by the CPU.
  - Every other test stays on the no-GPU path. Telling every page it has a GPU made each frame 350–425 ms, and the
    motion tests, which count frames, failed.
  - The no-GPU path keeps its own tests, in a browser without the stand-in.
- **`budgets`** compares the change with its merge base on one Linux runner (`budgets-ab.mjs`), as before. It holds
  no absolute limit, prints the frame-bound figures without comparing them, and is not a required check.
- **The virtual-GPU mode is removed**: `HISTMERSION_VIRTUAL_GPU`, the paravirtual renderer test and the main-thread-only
  rule. The click-to-next-frame checks are held under 100 ms on Linux too; none failed in the runs below. A local run
  of the budgets on a real Mac still holds every figure.
- **The class-path steps (`writeClasspath`) never format the sources (`$NOFMT`).** Formatting on compile fetched
  scalafmt from Maven Central, and that download hung 4 of about 20 such steps for their whole time limit.
- **`bundle.yml` runs on demand (`workflow_dispatch`) and for `v*` tags.** It no longer runs on pushes or PRs, so no PR
  waits for it. It builds the bundles for macOS arm64, Linux x64 and Linux arm64. For each it runs the server's
  `--self-check`, then `smoke.sh`, and on Linux it installs and uninstalls the systemd service.
- **At the first release, `bundle.yml` becomes a required check of the release tag.** Windows bundles come after the
  refactor, as a separate row.

## Consequences

- PR checks take less time. The Mac jobs were the longest at a median of 42 and 48 min, and whole runs took 56 min to
  2 h. Now:
  - the longest browser shard takes 19–22 min (shard 1, the soak test), the others 7–17 min;
  - `scripts` takes 9–14 min and `budgets` 31–42 min;
  - a whole run takes 40–47 min, set by `budgets`. Without it (it is not required), the required jobs finish in about
    30 min.
- Measured on Linux after the change: 8 runs of the four shards (commits faef19ab8 and 187c07bc9, push and PR, each
  run twice), 984 test executions.
  - 7 runs were green. One failed one test once: "a zoom slides the points of a split bubble out…", which asserts that
    no point is still flying in the last traced frame. The trace stops on the first frame at least 450 ms after the
    zoom's end, so the check depends on the frame rate. It is reported here, not loosened. It also failed 6 times in
    the 45 Mac jobs.
  - Every shard check passed: all 123 tests ran once each, none skipped. `scripts` was green 8 of 8.
  - The same suite on the hosted Mac was green in 18 of 45 jobs.
- `budgets` on Linux was green in 4 of 8 runs, too few for a useful signal. Every red run failed the same way: the
  full-load zoom at 1920×1080 timed out waiting for `markersSettled()` (60 s). It did so on both sides, the merge base
  with its own tests included, so the fault is in the pilot's code or harness, not in the change under test. It
  happens more on some runners than others: on the AMD EPYC 7763 and 9V74 and the Xeon 8573C runners, almost every run
  of both sides failed; on the Xeon 6975P-C runners, at most one run in six. It is recorded as a follow-up.
- On commits that change the measuring code (`engine.mjs` and `perf.test.mjs` here), the budget figures are printed and
  not compared. The runs above did compare 67 figures on Linux before that change, with no regression.
- The PR checks no longer build or run anything on macOS. A macOS-only fault in packaging, signing, the LaunchAgent or
  the TCC probe shows only in a `bundle.yml` run. Until the first release, someone has to start that run.
- No free runner can link the server's native image yet, so `bundle.yml` is red today, as it already was when it ran
  on PRs. Before it can gate a release it needs a larger runner or a smaller image. In run 45324270124:
  - on Linux x64 and arm64, the native-image builder ran out of its 14 GB heap after 47 and 62 min;
  - on macOS arm64 (7 GB), the job hit its 90-minute limit.
- What would change this: platform-specific application code (then its platform gets a CI job), or a hosted runner
  that is representative of a user's device.

## Code

- `.github/workflows/ci.yml`: the jobs `scripts`, `browser`, `browser-shards` and `budgets`.
- `.github/workflows/bundle.yml`: the triggers, the heap and swap for the builder, and the `--self-check` step.
- `modules/web/test/shard-preload.mjs`, `test-shards.mjs`, `shards-check.mjs` and `test-shards.unit.test.mjs`.
- `modules/web/test/engine.mjs`: `renderMode`, `softwareRenderer` and `standInGpu`.
- `modules/web/test/browser.test.mjs`: `withPage`'s `gpu` option, and `answeredWithin`.
- `scripts/bundle/tests/relink-test.sh`: signing per platform.
- `modules/web/test/README.md`: how the shards and the stand-in work.
