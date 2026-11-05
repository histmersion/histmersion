# Isolation relationship history

Run `isolation-history.conf` with the Scalafix CLI after a complete architecture
inventory. The capture command refuses to overwrite the original baseline. Every
observation retains findings, additions, absences, reappearances and input hashes.
Source hashes and the complete main/test source set must match before observation.
Detector/direction-policy changes require explicit reconciliation.

The initial 788 forbidden-direction evidence rows contain 130 package qualifiers.
The remaining resolved-symbol evidence groups into 434 relationships: 245 production
and 189 test. The unit is a dependency relationship, not each reference or a confirmed
violation. Repeated references retain evidence counts but do not inflate this unit.
All 181,340 unclassified-direction rows remain unresolved inventory evidence.

The gate detects a new relationship even when aggregate counts stay constant, and
rejects reappearance after absence. It ignores line numbers. It does not claim stable
identity for local compiler owners or unowned references, distinguish identical uses,
or verify that absence is a repair. It does not cover evaluated build relationships,
published plugin dependencies or every possible policy boundary.

The first capture incorrectly labeled file-qualified `::local` owners as stable.
The following observation corrects confidence without deleting or replacing the
baseline or changing IDs. A regression assertion covers both local encodings. Those
IDs remain ambiguous and may change on unrelated compiler numbering changes.

The first source review is deliberately bounded to three files. Generic codec tests
are legitimate test consumers; the catalogue SourceReferrer adapter is an explicitly
documented contribution-interface exception in AGENTS. ApprovalService's library
types are a confirmed automation-to-library coupling, requiring design and repair;
they remain detected and must not be silently deferred or erased into primitives.
`review-1.tsv` records exact relationship IDs, treatment, rationale and source hashes.
Unreviewed relationships remain candidates. This is not an independent quality sample
and does not establish 95% precision or recall.

This adds evidence history and adjudication without changing detector coverage or
spending a detector refinement round. ProjectAssessment now runs this identity gate
after capability history; the full assessment passed across 3,138 files with no new
or reopened relationships. The actual Scalafix CLI
passed unchanged-input verification and correctly failed equal-count substitution
and reappearance scenarios, naming the expected regression identity in each case.
See `verification.txt`; the original history and application sources were untouched.

Review 1 records 32 confirmed production relationships in ApprovalService (one design
problem, not 32 independent operations), 21 explicitly accepted production adapter
relationships and 51 legitimate test relationships. The remaining 330 relationships
are candidates: 192 production and 138 test. Original raw evidence and the unreviewed
baseline remain frozen; adjudication is a separate event. The confirmed engine/library
coupling remains required repair work; it is not eligible for silent boy-scout deferral.

The subsequent caller/definition review in `../approval-isolation` confirms 22 more
relationships in Approvals and AgentRepo as part of the same design problem. Current
adjudication is therefore 54 confirmed production, 21 accepted production, 170
production candidates, 138 test candidates and 51 test non-violations. The original
review-1 ledger is unchanged. `ReviewStatus.scala` reconciles both review events with
current presence and source hashes; stale source evidence or unexplained absence fails.
It remains a separately invoked review check, not part of ProjectAssessment.
