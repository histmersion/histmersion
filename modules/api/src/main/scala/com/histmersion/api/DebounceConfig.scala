package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `previewMs`: the quiet period before a HOVERED suggestion's preview reloads the lists — shorter than `reloadMs`,
  * because a preview is read while the pointer rests and the answer takes ~20 ms.
  */
final case class DebounceConfig(suggestMs: Int, searchMs: Int, reloadMs: Int, previewMs: Int)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object DebounceConfig:
  given Schema[DebounceConfig] = summon[KindlingsSchema[DebounceConfig]].schema
