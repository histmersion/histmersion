package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One match inside a passage: `[start, end)` character offsets into [[PassageHit.text]]. */
final case class MatchSpan(start: Int, end: Int) derives KindlingsCodecAsObject, KindlingsSchema
object MatchSpan:
  given Schema[MatchSpan] = summon[KindlingsSchema[MatchSpan]].schema
