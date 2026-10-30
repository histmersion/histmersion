package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

object ArticlePlans:

  final case class FilterProposal(
      slug: String,
      filter: ArticleFilter,
      rationale: String = "",
      total: Int = 0,
      cited: Int = 0,
      citedIn: Int = 0,
      missed: List[String] = Nil,
      sample: List[String] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def recall: Double = if cited == 0 then 1.0 else citedIn.toDouble / cited
  object FilterProposal:
    given Schema[FilterProposal] = summon[KindlingsSchema[FilterProposal]].schema

  final case class ProposedArticle(
      slug: String,
      title: String,
      kind: String,
      filter: ArticleFilter,
      why: String = "",
      hits: Int = 0,
      overlap: Double = 0,
      overlapWith: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ProposedArticle:
    given Schema[ProposedArticle] = summon[KindlingsSchema[ProposedArticle]].schema

  final case class ArticlePlan(
      goal: String,
      rationale: String = "",
      articles: List[ProposedArticle] = Nil,
      dropped: List[String] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def isEmpty: Boolean = articles.isEmpty
  object ArticlePlan:
    given Schema[ArticlePlan] = summon[KindlingsSchema[ArticlePlan]].schema

  def filterLine(f: ArticleFilter): String = f.line

  def filterBlock(p: FilterProposal): String =
    val missed =
      if p.missed.isEmpty then "" else s"\n- cited but not returned: ${p.missed.map(c => s"[[$c]]").mkString(", ")}"
    val sample = if p.sample.isEmpty then ""
    else s"\n- returned, not cited (a few): ${p.sample.map(c => s"[[$c]]").mkString(", ")}"
    s"${p.filter.line} → ${Plural.count(p.total, "source")}; returns ${p.citedIn} of the ${Plural.count(p.cited, "cited source")}" +
      missed + sample

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
