package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class IssuePlace(
    leftGeometry: Option[String] = None,
    rightGeometry: Option[String] = None,
    leftSpan: Option[IssueSpan] = None,
    rightSpan: Option[IssueSpan] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
