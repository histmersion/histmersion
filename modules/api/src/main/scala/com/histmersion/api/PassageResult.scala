package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The answer of `passages`: `matches` counted over the whole body (stopping at the configured cap, then `capped`),
  * merged into `total` passages of which `passages` are shown.
  */
final case class PassageResult(
    citekey: String,
    query: String,
    regex: Boolean,
    matches: Int,
    capped: Boolean,
    total: Int,
    passages: List[PassageHit],
    bodyChars: Int
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PassageResult:
  given Schema[PassageResult] = summon[KindlingsSchema[PassageResult]].schema
