package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The formatting report of one subject (an article slug, a citekey, or `text`). `fixed` lists the repairs `--fix`
  * applied (empty for a read-only check); `text` is the repaired text when the subject was a text sent to
  * `POST /api/lint/markdown` with `fix`.
  */
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
