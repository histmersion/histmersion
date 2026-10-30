package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One problem. `line`/`column` are 1-based, in the stored text of `field`; `excerpt` is the offending text (trimmed,
  * at most ~80 characters); `message` says what will go wrong; `fix`, when present, says what `--fix` would change
  * (only for repairs that never touch wording).
  */
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
