package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class IssueSpan(start: Option[Long], end: Option[Long]) derives KindlingsCodecAsObject, KindlingsSchema
object IssueSpan:
  given Schema[IssueSpan] = summon[KindlingsSchema[IssueSpan]].schema
