package com.histmersion.api.checks
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class FormatIssue(
    rule: FormatRule,
    severity: FormatSeverity,
    field: FormatField,
    line: Int,
    column: Int,
    excerpt: String,
    message: String,
    fix: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object FormatIssue:
  given Schema[FormatIssue] = summon[KindlingsSchema[FormatIssue]].schema
