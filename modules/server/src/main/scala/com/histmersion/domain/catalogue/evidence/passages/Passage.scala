package com.histmersion.domain.catalogue.evidence.passages

import com.histmersion.api.catalogue.MatchSpan

/** A passage of a source's text a search found: where it starts in the body, its text, and where in it the matches are.
  */
final case class Passage(offset: Int, text: String, matches: List[MatchSpan])
