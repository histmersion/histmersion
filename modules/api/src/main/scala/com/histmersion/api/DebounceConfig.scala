package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class DebounceConfig(suggestMs: Int, searchMs: Int, reloadMs: Int, previewMs: Int)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object DebounceConfig:
  given Schema[DebounceConfig] = summon[KindlingsSchema[DebounceConfig]].schema
