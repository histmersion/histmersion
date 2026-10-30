package com.histmersion.web.library

import com.histmersion.api.{ArticleCoverage, NeighborRow}

/** What the reading pane counts of a source's place in the citation graph: what it cites (as its page's
  * "Cites" lists it), the sources that answer it — challenge, refute, update or supersede it — and the
  * articles that cite it. Pure. */
final case class CitationCounts(cites: Int, answers: Int, articles: Int)

object CitationCounts:

  /** A disagreement is stored on the critic (`challenges`, `refutes`, `updates` point at this source) and a
    * superseding on the older source (`superseded_by` points from this one to the newer). */
  def answering(r: NeighborRow): Boolean =
    (r.direction == "in" && ArticleCoverage.DisagreementKinds.contains(r.kind) && r.kind != "superseded_by") ||
      (r.direction == "in" && r.kind == "supersedes") ||
      (r.direction == "out" && r.kind == "superseded_by")

  def of(rows: List[NeighborRow], citingArticles: Int): CitationCounts =
    CitationCounts(
      cites = rows.count(_.direction == "out"),
      answers = rows.filter(answering).map(_.citekey).distinct.size,
      articles = citingArticles)
