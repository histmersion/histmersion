package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CoverageReason(citekey: String, reason: String) derives KindlingsCodecAsObject
object CoverageReason:
  given Schema[CoverageReason] = KindlingsSchema
    .derived[CoverageReason]
    .schema
    .description("Why the article does not cite one source its filter returns")
    .modify(_.citekey)(_.description("The source's citekey"))
    .modify(_.reason)(
      _.description(
        "One plain sentence: why it is left out (wrong region, period or sub-problem; no finding on the question). Empty removes it"
      )
    )

  val MaxChars = 500

  def merge(current: Map[String, String], stated: List[CoverageReason]): Either[String, Map[String, String]] =
    stated.foldLeft[Either[String, Map[String, String]]](Right(current)) { (acc, r) =>
      acc.flatMap { m =>
        val ck = Option(r.citekey).map(_.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase).getOrElse("")
        val text = Option(r.reason).map(_.trim.replaceAll("\\s+", " ")).getOrElse("")
        if !ArticleLinks.isCitekeyShaped(ck) then Left(s"reason for '${r.citekey}': not a citekey")
        else if text.length > MaxChars then
          Left(s"reason for $ck: ${text.length} characters (at most $MaxChars — one sentence)")
        else if text.isEmpty then Right(m - ck)
        else Right(m.updated(ck, text))
      }
    }

  def list(m: Map[String, String]): List[CoverageReason] = m.toList.sortBy(_._1).map(CoverageReason(_, _))
