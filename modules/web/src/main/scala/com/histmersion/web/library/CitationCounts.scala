package com.histmersion
package web.library

import com.histmersion.api.library.{Edge, NeighborRow}
import com.histmersion.api.research.Article

/** What the reading pane counts of a source's place in the citation graph: what it cites (as its page's "Cites" lists
  * it), the sources that answer it — challenge, refute, update or supersede it — and the articles that cite it. Pure.
  */
final case class CitationCounts(cites: Int, answers: Int, articles: Int)

object CitationCounts:

  /** A disagreement is stored on the critic (`challenges`, `refutes`, `updates` point at this source) and a superseding
    * on the older source (`superseded_by` points from this one to the newer).
    */
  def answering(r: NeighborRow): Boolean =
    val kind = Edge.Kind.wireOf(r.kind).value
    (r.direction.value == "in" && Article.Coverage.DisagreementKinds.exists(
      _.value == kind
    ) && kind != "superseded_by") ||
    (r.direction.value == "in" && kind == "supersedes") ||
    (r.direction.value == "out" && kind == "superseded_by")

  def of(rows: List[NeighborRow], citingArticles: Int): CitationCounts =
    CitationCounts(
      cites = rows.count(_.direction.value == "out"),
      answers = rows.filter(answering).map(_.citekey).distinct.size,
      articles = citingArticles
    )
