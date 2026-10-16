- Statuses (check 2): every cited source's recorded status is in the user message after the article
  (`<cited-sources>`); read it there instead of querying.
- Your lint step (check 3) has already been run by code: its hits reach the fixer as `M` findings.
  Report only what code cannot see.
- Coverage (check 1): when the article records a filter, the user message shows, after the article,
  the sections code generates from it (`<generated-sections>`), and `{{tool:article_coverage}}` gives
  the same with the drift. Code already reports every source neither cited nor given a reason (an
  `M` finding); check the REASONS against the records, and report a source dismissed as "nothing on
  X" whose record discusses X.
- Whenever a finding rests on a record (decorative, recall, status, coverage), it carries `evidence`:
  up to three `{citekey, offset, quote}` — the source, the `offset` of `{{tool:get_source}}` you read
  it at (null for the summary, a finding or the notes) and the sentence copied EXACTLY. Code looks for
  each quote in that record: one it cannot find is dropped, so the fixer never sees it. The fixer
  starts from the verified ones instead of searching again; a finding without evidence is re-checked.
- Report checks 5–6 as `spotChecks`. Code accepts one only when a `{{tool:get_source}}` or
  `{{tool:passages}}` call of THIS step read its source, and sends back an answer with too few: read
  the records first. Neither the brief nor the ledger replaces them.
