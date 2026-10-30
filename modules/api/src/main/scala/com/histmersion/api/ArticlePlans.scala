package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What the managers' two proposing pipelines put before a person (docs/research-process-managers.md §4, phase 2), as
  * the approvals carry them: the coverage filter `infer-filter` inferred for an article (`article-filter`), and the new
  * articles `propose-articles` planned for a research area (`article-plan`). Both are CODE-VERIFIED before anyone sees
  * them — the filter against the corpus and the article's own citations, each proposed article's slug, source count and
  * overlap. Wire types only; the checks are pure in `core` ([[FilterInference]], [[PlanArticles]]).
  */
object ArticlePlans:

  /** A coverage filter for article `slug`: the filter, why, and what code measured — the sources it returns (`total`),
    * how many of the article's cited sources it returns (`citedIn` of `cited`), the cited ones it misses and a few of
    * the sources it returns that the article does not cite.
    */
  final case class FilterProposal(
      slug: String,
      filter: ArticleFilter,
      rationale: String = "",
      total: Int = 0,
      cited: Int = 0,
      citedIn: Int = 0,
      missed: List[String] = Nil,
      sample: List[String] = Nil
  ) derives KindlingsCodecAsObject:
    /** The share of the cited sources the filter returns (1 when the article cites none). */
    def recall: Double = if cited == 0 then 1.0 else citedIn.toDouble / cited
  object FilterProposal:
    given Schema[FilterProposal] = KindlingsSchema.derived[FilterProposal].schema

  /** One article a plan proposes: its new slug, title and kind, the filter it is compiled from, why, the sources the
    * filter returns, and its largest overlap with an article of the area (`overlap`, the share of its sources that
    * article already covers, and which one).
    */
  final case class ProposedArticle(
      slug: String,
      title: String,
      kind: String,
      filter: ArticleFilter,
      why: String = "",
      hits: Int = 0,
      overlap: Double = 0,
      overlapWith: Option[String] = None
  ) derives KindlingsCodecAsObject
  object ProposedArticle:
    given Schema[ProposedArticle] = KindlingsSchema.derived[ProposedArticle].schema

  /** The articles planned for research area `goal`, and what code dropped (with why). */
  final case class ArticlePlan(
      goal: String,
      rationale: String = "",
      articles: List[ProposedArticle] = Nil,
      dropped: List[String] = Nil
  ) derives KindlingsCodecAsObject:
    def isEmpty: Boolean = articles.isEmpty
  object ArticlePlan:
    given Schema[ArticlePlan] = KindlingsSchema.derived[ArticlePlan].schema

  /** A filter in one line, the writing guide's `search {…}` form. */
  def filterLine(f: ArticleFilter): String = f.line

  /** A filter proposal as a person reads it (Markdown). */
  def filterBlock(p: FilterProposal): String =
    val missed =
      if p.missed.isEmpty then "" else s"\n- cited but not returned: ${p.missed.map(c => s"[[$c]]").mkString(", ")}"
    val sample = if p.sample.isEmpty then ""
    else s"\n- returned, not cited (a few): ${p.sample.map(c => s"[[$c]]").mkString(", ")}"
    s"${p.filter.line} → ${Plural.count(p.total, "source")}; returns ${p.citedIn} of the ${Plural.count(p.cited, "cited source")}" +
      missed + sample

  /** A plan as a person reads it (Markdown). */
  def planBlock(p: ArticlePlan): String =
    val as = p.articles.map { a =>
      val ov = a.overlapWith.fold("")(o => f" · overlaps [[$o]] ${a.overlap * 100}%.0f%%")
      s"- `${a.slug}` — ${a.title} (${a.kind}) · ${a.filter.line} → ${Plural.count(a.hits, "source")}$ov" +
        Option(a.why).filter(_.nonEmpty).fold("")(w => s": $w")
    }
    val dropped = p.dropped.map(d => s"- dropped: $d")
    (Option(p.rationale).filter(_.nonEmpty).toList ++ List((as ++ dropped).mkString("\n")))
      .filter(_.nonEmpty)
      .mkString("\n\n")
