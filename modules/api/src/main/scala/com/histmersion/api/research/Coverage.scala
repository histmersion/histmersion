package com.histmersion
package api.research

import com.histmersion.util.json.JsonCodec
import com.histmersion.util.problem.Problem
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.StatusCount
import com.histmersion.api.catalogue.ArticleEventLead
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Source
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.AuthorName
import com.histmersion.lint.{tolerated, LintRule}

final case class Coverage(
    slug: Article.Slug,
    filter: Article.Filter,
    filterLine: Coverage.FilterLine,
    total: Coverage.Total,
    checked: Coverage.Checked,
    cited: List[Coverage.Source],
    notUsed: List[Coverage.Entry],
    citedOutside: List[Citekey],
    statuses: List[StatusCount],
    edges: List[Coverage.Edge],
    searches: List[CoverageSearch],
    reasons: List[Coverage.Reason],
    drift: List[Coverage.Drift],
    eventLeads: List[ArticleEventLead] = Nil,
    critiques: List[CritiquePair] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def capped: Boolean = ((checked).value < (total).value)
  def unplaced: List[Citekey] = drift.filter(_.kind == DriftKind.Unplaced).flatMap(_.citekeys)
object Coverage:

  type FilterLine = FilterLine.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object FilterLine extends Newtype[String, Pure]
  type Total = Total.T
  object Total extends Newtype[Int, Pure]
  type Checked = Checked.T
  object Checked extends Newtype[Int, Pure]

  final case class Drift(kind: DriftKind, citekeys: List[Citekey]) derives JsonCodec, KindlingsSchema, api.json.Schema

  final case class Edge(from: Citekey, kind: Edge.Kind, to: Citekey) derives JsonCodec, KindlingsSchema, api.json.Schema

  object Edge:
    export api.library.Edge.Kind.Wire as Kind

  final case class Entry(source: Source, reason: Option[Rationale]) derives JsonCodec, KindlingsSchema, api.json.Schema

  /** Why an article leaves one source of its filter out, in the words of whoever left it out: a decision, not a
    * failure.
    */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]

  final case class Reason(citekey: Citekey, reason: Rationale) derives JsonCodec
  object Reason:
    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
    given Schema[Reason] = KindlingsSchema
      .derived[Reason]
      .schema
      .description("Why the article does not cite one source its filter returns")
      .modify(_.citekey)(_.description("The source's citekey"))
      .modify(_.reason)(
        _.description(
          "One plain sentence: why it is left out (wrong region, period or sub-problem; no finding on the question). Empty removes it"
        )
      )

    val MaxChars = 500

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def merge(current: Map[Citekey, Rationale], stated: List[Reason]): Either[Problem, Map[Citekey, Rationale]] =
      stated.foldLeft[Either[Problem, Map[Citekey, Rationale]]](Right(current)) { (acc, r) =>
        acc.flatMap { m =>
          val ck =
            Option(r.citekey).map(_.value.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase).getOrElse("")
          val text = Option(r.reason).map(w => s"$w".trim.replaceAll("\\s+", " ")).getOrElse("")
          val at = Vector(Problem.Field("reasons"))
          if !((Article.Links.isCitekeyShaped((ck))) == (true)) then
            Left(
              Problem.Invalid.Unreadable(
                Problem.TypeName.of(Citekey),
                Problem.Raw(s"${r.citekey}"),
                Vector(Problem.Raw("anthony2007")),
                at
              )
            )
          else if text.length > MaxChars then
            Left(
              Problem.Invalid.OutOfRange(
                Problem.TypeName.of(Rationale),
                Problem.Raw.of(text.length.toLong),
                Problem.Invalid.Bounds(None, Some(Problem.Raw.of(MaxChars.toLong))),
                at :+ Problem.Field(s"$ck")
              )
            )
          else if text.isEmpty then Right(m - Citekey(ck))
          else Right(m.updated(Citekey(ck), Rationale(s"$text")))
        }
      }

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def list(m: Map[Citekey, Rationale]): List[Reason] = m.toList.sortBy[String](_._1.value).map(Reason(_, _))

  final case class Source(
      citekey: Citekey,
      title: Title,
      year: Option[Year],
      status: api.library.Source.Status,
      authors: List[AuthorName] = Nil
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema
