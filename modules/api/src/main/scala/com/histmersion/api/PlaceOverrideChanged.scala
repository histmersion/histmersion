package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What a `places set` did: the kind and period before and after, and how the place's findings moved (`findingsBefore`
  * → `findingsAfter`; `unlocated` = findings that had it and now have no place at all). `latBefore`…`lonAfter`: the
  * place's point before and after, when it is a point.
  */
final case class PlaceOverrideChanged(
    placeId: String,
    label: String,
    reset: Boolean,
    kindBefore: String,
    kindAfter: String,
    periodBefore: String,
    periodAfter: String,
    findingsBefore: Int,
    findingsAfter: Int,
    unlocated: Int,
    latBefore: Option[Double] = None,
    lonBefore: Option[Double] = None,
    latAfter: Option[Double] = None,
    lonAfter: Option[Double] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceOverrideChanged:
  given Schema[PlaceOverrideChanged] = summon[KindlingsSchema[PlaceOverrideChanged]].schema
