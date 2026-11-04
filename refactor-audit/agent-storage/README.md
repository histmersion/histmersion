# Agent storage repair

The frozen Scalafix preview changed 40 application files together: capability definitions,
implementations, composition and callers. `plan.tsv` records before/after hashes;
`applied-verification.txt` verifies that all applied files match the preview exactly.
Application changes were applied by `ApplyFrozenPreview`, not manual source edits.

AgentTrees and PluginTree now use supplied Stores; Prompts and AgentDefinitions use
supplied Resources. Manifest timestamps use Clock. Home expansion receives JvmHome,
captured by SystemContext at composition. Domain generation returns failures to the
generator command, which owns process exit. Server diagnostics use scribe.

## Verification

- All preview sources, including tests, compiled together against the evaluated server
  test classpath. Full project `compile Test/compile` with SemanticDB also passed.
- Template contracts passed for memory/disk storage, prompt overrides and fallback,
  confinement, selective cleanup, deterministic manifests, symlink isolation, resource
  closure on success and read failure, and home paths containing `$`.
- Nine selected suites ran before and after: 93 identical tests, 92 passes, one existing
  failure, no skips. The failure is the plugin tree's extra checked-in skills/files;
  those files were retained. This is not an all-green test run.
- `tests/reconciliation.txt` compares the complete failure assertion, obtained value and
  diff. It excludes source coordinates and stack frames, and hashes both original
  reports. Its first comparison incorrectly included shifted assertion line numbers;
  the saved reports were reconciled without rerunning or modifying them.
- Initial test selection named an abstract storage contract suite. The missing-suite
  guard rejected it; selecting DiskStoreSuite and MemoryStoreSuite produced the frozen
  93-test baseline. Preview verification was corrected to include `--test` before apply.

## Measurement and limits

The pre-permission all-module scan covered 2,362 production source files with none
missing: 181 raw diagnostics versus 209 previously. This comprises 29 removed findings
and one added lifecycle finding at the generator's resolved entry method. The exact
entry permission, old/new source assertions and detector-policy hashes are recorded in
`../capability-history/corrections/agent-storage/`; original baselines are retained.
The integrated post-policy assessment completed successfully: 180 diagnostics across
154 anchors, zero new or reopened identities, and zero multiplicity changes. All
2,362 production files were covered. Raw findings are not all confirmed violations.
`Reconciliation.scala` compares the frozen review's selected groups to the two history
observations; `verified-findings.tsv` records the 21 repaired anchors / 29 occurrences.
Original history records continue to call absence unverified; the separate repair event
supplies the compilation, behavior and exact-preview evidence for these identities.

The repair also handles two classloader bypasses outside current detector coverage:
CAP-GAP-0001 in Prompts and newly recorded CAP-GAP-0002 in AgentDefinitions.index.
The latter directly opened `agents/index.txt` through getResourceAsStream, read bytes
and closed the stream at predecessor commit d4510ae3. It now delegates to
`prompts.bundledText("index.txt")`. This additional discovery increases the known scope;
it does not retroactively change review-1's denominator. Classloader detection remains
unsupported project-wide, even though these two reviewed occurrences are repaired.

Remaining confirmed capability batches from review-1 are plugin-local capabilities
(25 occurrences), shared note input (3), and upload metadata (1). The 149 candidate
occurrences still require review; two non-violations retain their recorded decisions.
Transaction dynamic SQL cases remain unresolved. Copy automation remains paused.
Primitive/newtype and module-isolation inventories remain separate deliverables and
are not counted as repaired by this batch.

The model inventory increased from 30,322 to 30,347 production primitive leaves
(28,149 to 28,167 member positions); unresolved leaves across all scopes increased
from 46,650 to 46,787. These remain candidates/unresolved evidence, not confirmed
violations or claimed improvements. Production/test copy counts are unchanged.
The aggregate assessment baseline is marked incomparable after policy changes; it
was not reset. Capability comparability is established by the explicit correction
chain and stable-identity gate, not by suppressing that aggregate warning.
