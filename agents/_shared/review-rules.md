- Your lint step (rule 3) has already been run by code: its hits reach the fixer as `M` findings.
  Report only what code cannot see.
- Coverage (rule 1): when the article records a filter, the user message shows, after the article,
  the sections code generates from it (`<generated-sections>`), and `{{tool:article_coverage}}` gives
  the same with the drift. Code already reports every source neither cited nor given a reason (an
  `M` finding); check the REASONS against the records, and report a source dismissed as "nothing on
  X" whose record discusses X.
- Every finding that rests on a record carries `evidence`: up to three `{citekey, offset, quote}`
  — the source, the `offset` of `{{tool:get_source}}` you read it at (null for the summary, a finding or the
  notes) and the sentence copied EXACTLY. Code looks for each quote in that record: one it cannot
  find is dropped, so the fixer never sees it. The fixer reads the verified ones under the finding
  and starts from them, so a finding with its evidence is acted on, and one without is re-checked.
