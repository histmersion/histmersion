package com.histmersion.api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class DebounceConfig(suggestMs: Int, searchMs: Int, reloadMs: Int, previewMs: Int)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object DebounceConfig:
  given Schema[DebounceConfig] = summon[KindlingsSchema[DebounceConfig]].schema
