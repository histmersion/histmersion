package com.histmersion.web.library

import com.histmersion.api.library.NeighborRow
import com.histmersion.api.research.Article

/** What the reading pane counts of a source's place in the citation graph: what it cites (as its page's "Cites" lists
  * it), the sources that answer it — challenge, refute, update or supersede it — and the articles that cite it. Pure.
  */
final case class CitationCounts(cites: CitationCounts.Cites, answers: CitationCounts.Answers, articles: CitationCounts.Articles)

object CitationCounts:

  /** A disagreement is stored on the critic (`challenges`, `refutes`, `updates` point at this source) and a superseding
    * on the older source (`superseded_by` points from this one to the newer).
    */
  type Cites = Cites.T
  object Cites extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Answers = Answers.T
  object Answers extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Articles = Articles.T
  object Articles extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def answering(r: NeighborRow): Boolean =
    (r.direction == "in" && Article.Coverage.DisagreementKinds.contains(r.kind) && r.kind != "superseded_by") ||
      (r.direction == "in" && r.kind == "supersedes") ||
      (r.direction == "out" && r.kind == "superseded_by")

  def of(rows: List[NeighborRow], citingArticles: Int): CitationCounts =
    CitationCounts(
      cites = rows.count(_.direction == "out"),
      answers = rows.filter(answering).map(_.citekey).distinct.size,
      articles = citingArticles
    )
