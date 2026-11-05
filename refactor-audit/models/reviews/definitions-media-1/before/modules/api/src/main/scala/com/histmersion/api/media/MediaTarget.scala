package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaTarget(
    targetType: String,
    targetId: String,
    role: String,
    property: Option[String] = None,
    item: Option[String] = None,
    basis: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
