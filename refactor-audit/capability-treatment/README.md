# Capability treatment review 1

This bounded source review consumes the complete 209-occurrence / 175-anchor
production observation after the transaction repair. It does not narrow the scan,
change a detector, approve exceptions, or claim independent sampled precision/recall.
Every original anchor remains in findings.tsv; source fingerprint and frozen decisions
are recorded under review-1. The original identity baseline remains unchanged.

| Evidence class | Anchors | Occurrences |
|---|---:|---:|
| Confirmed supported findings | 48 | 58 |
| Adjudicated non-violations | 2 | 2 |
| Candidates awaiting review | 125 | 149 |
| Additional confirmed detector gap | 1 | 1 |

Anchor counts deduplicate identical rule/path/owner/symbol/AST matches. Repeated
expressions and local compiler symbols retain the existing identity ambiguity;
these are not 48 proven independent architectural boundaries. The 58 occurrences
remain attached to their original multiplicities. The additional resource gap has
its own persistent ID CAP-GAP-0001 and exact source/AST evidence.

File.pathSeparatorChar is a constant; File.getName extracts a name without I/O.
Their raw detector findings remain recorded, with non-violation decisions rather
than deletion. Upload's File.length is separately confirmed as direct filesystem
access. Its HandedFile alias does not make that operation a supplied capability.

The classloader stream opened by Prompts.load is a confirmed capability bypass
missing from the configured detector. Fixing only Files calls would leave that
behavior behind. This review is development evidence, not an independent holdout.
Two transaction SQL expressions remain separately unresolved; the zero unresolved
classification count among these matched anchors does not resolve those expressions.
Test-policy coverage remains unmeasured. Main-source testkit implementations are
still production inventory candidates requiring a boundary decision, not accepted
exceptions merely because their module is called testkit.

## Repair batches

| Batch | Confirmed occurrences | Treatment | Required outcome |
|---|---:|---|---|
| Agent storage and prompts | 29 supported + 1 gap | Targeted AST rewrite | AgentTrees/PluginTree use injected stores; Prompts uses supplied override and bundled-resource readers; manifest time uses supplied Clock; generation errors return to the command edge; home lookup preserves its defined source. |
| Plugin local capabilities | 25 | Design, then cross-module rewrite | plugin-api owns file/process contracts; plugin-host supplies adapters; vaults and Calibre receive them. No plugin dependency on server Storage/Processes. Preserve interactive subprocess lifetime, timeout, stderr/stdout separation, cleanup and executable lookup. |
| Shared note input | 3 | Design, then targeted rewrite | SourceNote parsing receives content through a files-compatible capability; preserve SafePath confinement, optional OCR text and API-only module dependency. |
| Upload input | 1 | Targeted rewrite | Supplied input/storage boundary snapshots file metadata; domain construction does not call File.length. |

None of these confirmed violations is a boy-scout deferral or accepted exception.
No further inventory expansion is required before starting the agent-storage batch.
Keep unsupported shapes visible while implementing that batch with definitions and
callers together. Existing generator, prompt, plugin-tree and storage tests supply
behavioral evidence; add deterministic in-memory/clock cases and preserve exact
output, selective cleanup, path confinement, atomic writes and resource closure.

The review tool rejects stale source fingerprints, changed expected multiplicities,
overlapping decisions and changed history. Explicit ID decisions precede group
decisions, which only consume remaining anchors in the frozen observation. New code
cannot inherit an old decision automatically: the source fingerprint must match.

No application changes or automatic repairs are claimed by this review. The two
allowed detector refinement rounds remain unused for capability access.
