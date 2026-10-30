package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class MediaProviderInfo(
    name: String,
    title: String,
    over: String,
    description: String,
    plugin: String,
    builtin: Boolean,
    licences: String
) derives KindlingsCodecAsObject,
      KindlingsSchema
object MediaProviderInfo:
  given Schema[MediaProviderInfo] = summon[KindlingsSchema[MediaProviderInfo]].schema
