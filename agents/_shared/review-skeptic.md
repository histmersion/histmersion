- Database errors you notice (rule 8) go in `proposals`, not in findings: a status a source should
  have (`kind: "status"`), or a disagreement edge between two sources (`kind: "edge"`: challenges,
  refutes, updates, or superseded_by from the older source to the newer). Each carries the sentence
  of the record that shows it, copied exactly from `{{tool:get_source}}`. Code checks the quote is in that
  record and records the change for a person to approve; nothing is changed by you.
