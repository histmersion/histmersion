package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class MediaSample(
    targetType: String,
    targetId: String,
    targetLabel: String,
    key: String,
    title: String,
    license: String,
    basis: String,
    url: String
) derives KindlingsCodecAsObject,
      KindlingsSchema

object MediaSample:
  given Schema[MediaSample] = summon[KindlingsSchema[MediaSample]].schema
