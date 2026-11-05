package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.StatusCount
import com.histmersion.api.catalogue.ArticleEventLead
import com.histmersion.api.given
import com.histmersion.api.library.Source

final case class Coverage(
    slug: String,
    filter: Article.Filter,
    filterLine: String,
    total: Int,
    checked: Int,
    cited: List[Coverage.Source],
    notUsed: List[Coverage.Entry],
    citedOutside: List[String],
    statuses: List[StatusCount],
    edges: List[Coverage.Edge],
    searches: List[CoverageSearch],
    reasons: List[Coverage.Reason],
    drift: List[Coverage.Drift],
    eventLeads: List[ArticleEventLead] = Nil,
    critiques: List[CritiquePair] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def capped: Boolean = checked < total
  def unplaced: List[String] = drift.filter(_.kind == DriftKind.Unplaced).flatMap(_.citekeys)
object Coverage:


  final case class Drift(kind: DriftKind, citekeys: List[String]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  final case class Edge(from: String, kind: String, to: String) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  final case class Entry(source: Source, reason: Option[String]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  final case class Reason(citekey: String, reason: String) derives KindlingsCodecAsObject
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

    def merge(current: Map[String, String], stated: List[Reason]): Either[String, Map[String, String]] =
      stated.foldLeft[Either[String, Map[String, String]]](Right(current)) { (acc, r) =>
        acc.flatMap { m =>
          val ck = Option(r.citekey).map(_.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase).getOrElse("")
          val text = Option(r.reason).map(_.trim.replaceAll("\\s+", " ")).getOrElse("")
          if !Article.Links.isCitekeyShaped(ck) then Left(s"reason for '${r.citekey}': not a citekey")
          else if text.length > MaxChars then
            Left(s"reason for $ck: ${text.length} characters (at most $MaxChars — one sentence)")
          else if text.isEmpty then Right(m - ck)
          else Right(m.updated(ck, text))
        }
      }

    def list(m: Map[String, String]): List[Reason] = m.toList.sortBy(_._1).map(Reason(_, _))

  final case class Source(
      citekey: String,
      title: String,
      year: Option[Int],
      status: com.histmersion.api.library.Source.Status,
      authors: List[String] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
