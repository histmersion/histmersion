package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What a merge or a removal did. `into`/`intoLabel` are empty for a removal; `findingsBefore` counts the findings
  * either place had, `findingsAfter` those `into` has; `unlocated` = findings that had one of them and now have no
  * place at all.
  */
final case class PlaceGone(
    placeId: String,
    label: String,
    into: Option[String],
    intoLabel: Option[String],
    findingsBefore: Int,
    findingsAfter: Int,
    unlocated: Int
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceGone:
  given Schema[PlaceGone] = summon[KindlingsSchema[PlaceGone]].schema
