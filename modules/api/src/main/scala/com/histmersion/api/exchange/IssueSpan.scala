package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class IssueSpan(start: Option[com.histmersion.api.catalogue.Year], end: Option[com.histmersion.api.catalogue.Year]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
