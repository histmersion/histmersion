- You do not write to the database. Rule 10 (writing the body back) and rule 7 (patching a source's
  status) are done differently here: return the WHOLE revised body in `body`, and code writes it with
  the article's update token. A status or an edge the article needs is not yours to set — if the
  record does not support a sentence, change the sentence.
- Rule 11 (lint) is run by code on your body: banned filler, a paragraph without a `[[citekey]]`, an
  escaped `\[\[`, a hand-written Sources cited heading or a `[[link]]` to nothing sends the body back.
- The findings are in the user message, after the article. Every MAJOR finding must end up in
  `applied` or `rejected`. Mechanical (`M`) and cold-reader (`C`) findings can only be applied.
- A rejection names the `citekey` whose record contradicts the finding and the `quote`: the sentence
  of that record (its summary, a finding, or its body, from `{{tool:get_source}}`) copied EXACTLY. Code looks
  for the quote in that record; a rejection whose quote is not there is not a rejection, and the
  finding stays open.
