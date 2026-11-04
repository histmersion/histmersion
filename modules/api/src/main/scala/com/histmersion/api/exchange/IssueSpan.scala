package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class IssueSpan(start: Option[Long], end: Option[Long]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
