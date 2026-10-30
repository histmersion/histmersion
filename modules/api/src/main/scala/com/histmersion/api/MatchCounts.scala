package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The bundle's sources, by how they were matched. */
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
