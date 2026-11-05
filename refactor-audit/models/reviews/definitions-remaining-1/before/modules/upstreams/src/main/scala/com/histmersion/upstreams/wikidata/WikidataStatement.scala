package com.histmersion.upstreams.wikidata
final case class WikidataStatement(property: String, file: String, preferred: Boolean, order: Int)

object WikidataStatement:
  /** Pure: the statements of one property to use, best first — the preferred ones when there are any (Wikidata's own
    * rule for "the" value), else the normal ones — at most `per`.
    */
  def best(statements: List[WikidataStatement], per: Int): List[WikidataStatement] =
    val preferred = statements.filter(_.preferred)
    (if preferred.nonEmpty then preferred else statements).sortBy(_.order).take(per.max(0))
