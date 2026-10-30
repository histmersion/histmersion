package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class SourceWithFindings(
    source: SourceDetail,
    findings: List[FindingRow],
    formatting: List[FormatIssue] = Nil,
    illustration: Option[MediaThumb] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object SourceWithFindings:
  given Schema[SourceWithFindings] = summon[KindlingsSchema[SourceWithFindings]].schema
