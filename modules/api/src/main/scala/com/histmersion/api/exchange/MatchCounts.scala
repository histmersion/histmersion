package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MatchCounts(
    sources: Int = 0,
    byUid: Int = 0,
    byLedger: Int = 0,
    byStrongKey: Int = 0,
    byScore: Int = 0,
    undecided: Int = 0,
    unmatched: Int = 0,
    deletedHere: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema
object MatchCounts:
  given Schema[MatchCounts] = summon[KindlingsSchema[MatchCounts]].schema
