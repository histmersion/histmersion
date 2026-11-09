package com.histmersion
package api.research

import com.histmersion.api.problem.Text.sentence
import com.histmersion.util.problem.{Legacy, Problem}

import hearth.kindlings.circederivation.KindlingsCodecAsObject
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
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema:
  def capped: Boolean = ((checked).value < (total).value)
  def unplaced: List[Citekey] = drift.filter(_.kind == DriftKind.Unplaced).flatMap(_.citekeys)
object Coverage:

  type FilterLine = FilterLine.T
  object FilterLine extends Newtype[String, Pure]
  type Total = Total.T
  object Total extends Newtype[Int, Pure]
  type Checked = Checked.T
  object Checked extends Newtype[Int, Pure]

  final case class Drift(kind: DriftKind, citekeys: List[Citekey])
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  final case class Edge(from: Citekey, kind: Edge.Kind, to: Citekey)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Edge:
    export com.histmersion.api.library.Edge.Kind.Wire as Kind

  final case class Entry(source: Source, reason: Option[Problem])
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  final case class Reason(citekey: Citekey, reason: Problem) derives KindlingsCodecAsObject
  object Reason:
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

    def merge(current: Map[Citekey, Problem], stated: List[Reason]): Either[String, Map[Citekey, Problem]] =
      stated.foldLeft[Either[String, Map[Citekey, Problem]]](Right(current)) { (acc, r) =>
        acc.flatMap { m =>
          val ck =
            Option(r.citekey).map(_.value.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase).getOrElse("")
          val text = Option(r.reason).map((_).sentence.trim.replaceAll("\\s+", " ")).getOrElse("")
          if !((Article.Links.isCitekeyShaped((ck))) == (true)) then Left(s"reason for '${r.citekey}': not a citekey")
          else if text.length > MaxChars then
            Left(s"reason for $ck: ${text.length} characters (at most $MaxChars — one sentence)")
          else if text.isEmpty then Right(m - Citekey(ck))
          else Right(m.updated(Citekey(ck), Problem.Unclassified(Legacy.Text(text))))
        }
      }

    def list(m: Map[Citekey, Problem]): List[Reason] = m.toList.sortBy[String](_._1.value).map(Reason(_, _))

  final case class Source(
      citekey: Citekey,
      title: Title,
      year: Option[Year],
      status: com.histmersion.api.library.Source.Status,
      authors: List[AuthorName] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema
