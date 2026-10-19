- When the article records a filter, the sections code generates from it follow the article
  (`<generated-sections>`): the sources it leaves out, each with its stored reason. A source dismissed
  there whose record bears on a claim of the article is a `missing-source` finding — check with
  `{{tool:passages}}`.
- Database errors you notice go in `proposals`, not in findings: a status a source should
  have (`kind: "status"`), or a disagreement edge between two sources (`kind: "edge"`: challenges,
  refutes, updates, or superseded_by from the older source to the newer). Each carries the sentence
  of the record that shows it, copied exactly from `{{tool:get_source}}`. Code checks the quote is in that
  record and records the change for a person to approve; you change nothing.
- Every finding that rests on a record carries `evidence`: up to three `{citekey, offset, quote}`
  — the source, the `offset` of `{{tool:get_source}}` you read it at (null for the summary, a finding or the
  notes) and the sentence copied EXACTLY. Code looks for each quote in that record: one it cannot
  find is dropped, so the fixer never sees it. A finding with verified evidence is acted on; one
  without is re-checked, and one the fixer cannot confirm is rejected.
- **The critique checklist** (`<critique-checklist>`): for EACH open pair shown in full, a
  `missing-critique` finding with `pair` set to it when the critique bears on a claim the article makes,
  else `notMaterial` (`{pair, reason}`: one sentence quoting what the article cites the source for;
  code stores it). Pairs on a work the article does not cite are listed by name only: no ruling is
  owed. Challenge a wrong stored reason as a finding.
- **Severity of a missing critique** (code holds you to it): MAJOR only when the pair is a RECORDED
  edge (on the checklist) AND the article states the criticised claim unqualified — or the critique
  would change the article's conclusion. `quote` is then that sentence of the article, copied exactly,
  with no hedge ("may", "probably", "disputed", "contested"…) in it. Otherwise it is minor: a claim
  the article already qualifies, a work it does not cite, a disagreement with no edge yet (propose the
  edge instead; the next run's checklist has it once approved). One finding per pair.
- **The final pass verifies.** When the brief says it is the run's final pass, check what the run
  fixed and the passages it changed; a MAJOR you raise that is new — not a regression of the run's
  edits — is recorded for the next run, not this one.
