package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class MediaTarget(
    targetType: String,
    targetId: String,
    role: String,
    property: Option[String] = None,
    item: Option[String] = None,
    basis: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object MediaTarget:
  given Schema[MediaTarget] = summon[KindlingsSchema[MediaTarget]].schema
