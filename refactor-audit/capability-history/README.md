# Capability and transaction evidence history

Current ledger: the original 228-occurrence baseline remains immutable. The
separate `adoptions/transaction-round1/` event adds three pre-existing TX-0001
occurrences in two anchors. The effective inventory is 231 occurrences in 190
anchors; 79 occurrences have ambiguous identity. None was marked fixed or
accepted. The following baseline description remains historical evidence.

`CapabilityHistory` is a separate Scalafix measurement gate. Its frozen baseline
contains 228 production diagnostics grouped into 188 anchors. There are 151
occurrences with unique non-local anchors and 77 occurrences whose identity is
ambiguous (duplicates or compiler-local owners). These are identity-confidence
counts, not detector precision or confirmed-violation counts.

An anchor uses rule, repository-relative file, semantic owner, referenced symbol
and AST. Lines and columns are deliberately excluded. Baseline IDs are SHA-256
keys over that tuple. A line-only move is stable; a file/owner/AST change requires
reconciliation and currently fails conservatively. Repeated identical anchors
retain their full multiplicity, but do not claim distinct per-occurrence IDs.
An identical replacement within the same anchor is not distinguishable. The
three confirmed transaction boundary IDs in `../transaction-findings.tsv` remain
separate from diagnostic anchors; their many-to-one mapping is not implemented.

Every check preserves a timestamped observation, new anchors, absent anchors,
reopened anchors and changed multiplicities. Observed absence is not a verified
fix. Once an anchor has been absent, reappearance fails even if it existed in
the original baseline. Failed observations are retained. No check overwrites
the frozen baseline or implicitly approves newly introduced findings.

This gate catches a new anchor replacing an old anchor at unchanged aggregate
count. Any multiplicity change requires reconciliation, including removal from
an ambiguous group. Configured detector/policy hashes and rule IDs must remain
unchanged. Source fingerprints and exact production-file coverage must match
the completed assessment. No automatic baseline-refresh mode is provided.

## Classification and limits

The baseline records detector evidence, not 228 blanket confirmed violations.
It deliberately gates new candidate evidence too, so an unreviewed addition
requires adjudication rather than passing on an aggregate count. Treatment
classification and accepted exceptions must be attached through a separate
reviewed ledger; they must not erase observations. That ledger integration and
explicit rename/reconciliation events remain required work.

Tests, configured permission occurrences, unknown dispatch, callbacks and
external helper bodies are outside this initial gate. CapabilityAccess's
ordinary-mode missing-symbol zero remains uninformative because those names
are currently collected only in best-effort mode. This history gate does not
improve detector recall or resolve that evidence gap. No detector refinement
round was consumed: matching/classification in CapabilityAccess is unchanged.

## Commands and validation

Run with the existing Scala 2.13.18 Scalafix CLI launcher:

```
--config tools/refactor/capability-history.conf --scala-version 3.10.0 --files tools/refactor/CapabilityHistory.scala
```

The normal ProjectAssessment invokes this check after generating its reports.
Failure makes the whole assessment fail. The initial capture configuration is
one-shot and refuses to overwrite the baseline. Use it only to establish an
explicitly reviewed new ledger, not to make regression failures pass.

`CapabilityHistoryVerification` uses isolated synthetic report mutations under
`target/` and verifies real command exit codes: unchanged and line-shifted
observations pass; equal-count replacement, duplicate insertion, reappearance,
missing coverage and stale evidence fail. Capture overwrite fails, and the
frozen baseline remains byte-for-byte identical. `verification.txt` records
that suite. These tests validate enforcement mechanics, not independent
positive/negative source samples for detector precision and recall.

## Explicit additive policy adoption

`capability-history-adopt-transaction-round1.conf` records the reviewed correction
without replacing the baseline or original contract. Adoption requires the exact
original application-source fingerprint, complete production coverage, every old
identity and multiplicity unchanged, and exactly the reviewed new identities and
counts. The reviewed additions include classification, treatment and owner-problem
ID. A reason and source evidence are mandatory. Existing events cannot be overwritten.

The event retains its predecessor contract, new contract, additions and evidence,
plus hashes of the unchanged baseline and reviewed additions. Ordinary checks use
the original baseline plus that event, and reject changes to either. This narrow
operation does not support arbitrary renames, baseline removals or multiple policy
transitions; those require explicit future reconciliation work.

Capability reports now carry a detector/policy-input fingerprint. Checks reject
reports emitted by a different version, even when source files are unchanged.
The fingerprint covers the standard versioned capability-rule/configuration inputs;
the normal assessment is the supported producer. Adopting a separately measured
report is allowed only in explicit adoption mode with the unchanged-source proof.

The enforcement suite additionally verifies stale detector rejection, rejected
unreviewed additions during adoption, successful reviewed-only adoption, refused
event overwrite, and rejection of an unrelated regression after adoption. The
original baseline remains byte-for-byte unchanged throughout.
