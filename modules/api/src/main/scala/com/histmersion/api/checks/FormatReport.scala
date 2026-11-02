package com.histmersion.api.checks
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class FormatReport(
    subject: String,
    issues: List[FormatIssue],
    fixed: List[String] = Nil,
    text: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def errors: Int = issues.count(_.severity == FormatSeverity.Error)
  def warnings: Int = issues.count(_.severity == FormatSeverity.Warning)
object FormatReport:
  given Schema[FormatReport] = summon[KindlingsSchema[FormatReport]].schema
