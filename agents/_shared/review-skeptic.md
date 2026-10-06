- When the article records a filter, the sections code generates from it follow the article
  (`<generated-sections>`): the sources it leaves out, each with its stored reason. A source dismissed
  there whose record bears on a claim of the article is a `missing-source` finding — check with
  `{{tool:passages}}`.
- Database errors you notice (rule 8) go in `proposals`, not in findings: a status a source should
  have (`kind: "status"`), or a disagreement edge between two sources (`kind: "edge"`: challenges,
  refutes, updates, or superseded_by from the older source to the newer). Each carries the sentence
  of the record that shows it, copied exactly from `{{tool:get_source}}`. Code checks the quote is in that
  record and records the change for a person to approve; nothing is changed by you.
- Every finding that rests on a record carries `evidence`: up to three `{citekey, offset, quote}`
  — the source, the `offset` of `{{tool:get_source}}` you read it at (null for the summary, a finding or the
  notes) and the sentence copied EXACTLY. Code looks for each quote in that record: one it cannot
  find is dropped, so the fixer never sees it. The fixer reads the verified ones under the finding
  and starts from them, so a finding with its evidence is acted on, and one without is re-checked.
