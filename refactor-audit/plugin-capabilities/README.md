# Plugin capability batch

This batch consumes the existing review's 25 confirmed production occurrences. It is
preparation for a cross-module rewrite, not another detector refinement or a repair
claim. The original review, capability baseline and non-violation decisions remain
unchanged. `confirmed.tsv` retains stable identities and multiplicities; `calls.tsv`
records resolved source references and surrounding ASTs across all main/test modules.
References, imports and declarations are not counted as extra violations.

The planning verifier initially rejected selection of the already-adjudicated
File.pathSeparatorChar constant; explicit identity decisions now precede group
selection, as in the original review. A second check rejected absent AST coverage
because Java static Files methods use the SemanticDB `Files#` owner. The selector
was corrected to that observed owner; expected finding counts were not changed.

## Concrete rewrite scope

| Boundary | Confirmed occurrences | Required transformation |
|---|---:|---|
| KeePassXcPlugin registration | 2 filesystem | Obtain file probes from its Plugin.Context. |
| Subprocess executable lookup | 2 filesystem | Use a supplied file capability; keep PATH ordering and explicit-path errors. |
| Calibre model/settings | 4 filesystem | Supply file probes to the model and Settings.library; remove the raw default callback. |
| CalibreCollection | 10 filesystem | Delegate reads, prefix probe and scoped staging to supplied files; retain domain-specific error mapping. |
| Vault subprocess implementation | 6 process | Move process ownership behind a host-supplied process capability, including interactive sessions. |
| Calibre Shell.System | 1 process | Replace the locally chosen real backend with a Shell adapter over supplied Processes. |

Three System.nanoTime occurrences inside Subprocess.Session.await are still candidate
clock findings in the baseline. Moving session mechanics into the host also moves
those operations: reconcile their identities and reviewed adapter ownership explicitly.
Do not silently count them as three previously confirmed fixes or merely hide them
under a broader package permission.

## API and implementation decisions for the rewrite

Define LocalFiles and Processes contracts in plugin-api, with their operation result,
error and session types on their companions. Host adapters live in plugin-host;
plugins gain no dependency on server, core or db. Plugin.Context exposes the supplied
capabilities. HostServices receives implementations from composition; its consumers
include server plugin loading, relink verification and the test harness.

LocalFiles needs file/directory/executable probes, full-byte reads, bounded prefix
reads and a scoped staging operation. The latter owns creation, writing and cleanup
even when the callback fails. It returns a usable path only within that scope. Reject
staging names that escape its directory; test success, returned failure and thrown
failure. Domain-specific Calibre messages stay in Calibre. File.pathSeparatorChar and
Path construction remain pure operations and do not require capability calls.

Processes needs captured execution and interactive sessions. Preserve argv without
shell interpretation, separate stdout/stderr, environment additions, stdin close,
timeout, descendant termination, exit status, prompt readiness and reader cleanup.
Keep stdout out of vault error messages. Vault result classification, command building,
plain/value/problem helpers and YubiKey selection remain plugin logic. Calibre's
Shell adapter maps the shared process result to its existing error messages; it must
not select a global real backend. Decide the existing unbounded Calibre wait explicitly
instead of silently imposing an arbitrary timeout during this refactor.

Thread capabilities with `using` through consumers; register methods acquire them from
their context. Update definitions, constructors, tests and all call sites in the same
Scalafix preview. A compatible Context default may refuse unsupported operations; it
must never instantiate a real backend. Review SPI compatibility and API snapshots
before applying. Existing plugin jars not using the added methods must still load.

## Frozen behavioral baseline

Calibre: four suites, 45 tests, all passed, no skips (`calibre-tests/baseline.tsv`).
Vaults: 29 tests, 28 passed, no failures, one skipped (`vault-tests/baseline.tsv`).
The skipped identity is the installed-KeePassXC opt-in test controlled by
HISTMERSION_TEST_KEEPASSXC; VaultsSuite lines 534–539 show its gate. The baseline
verifier exited nonzero because it does not accept skips. Keep this evidence rather
than overwrite the baseline or claim full real-tool coverage. Fake-tool tests cover
stdin, environment, timeouts, sessions, YubiKey fallback and secret-safe errors.

Next: implement the shared contracts/adapters and one AST preview covering these
groups; compile definitions and callers together; compare the same test identities;
add host contract checks for resource ownership; apply the frozen preview and run
the project-wide identity gate. No further broad inventory expansion is needed to
start this batch. Remaining detection gaps and independent quality validation remain
separate outstanding deliverables; the overall migration goal is not complete.
