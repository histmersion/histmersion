package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One facet bucket; `count` is always count(DISTINCT source) within the current filter. `id` is the gazetteer place
  * behind a `places` value when there is one (plan §1.6a) — pass THAT as `place=` and the filter matches through
  * `finding_places` and the hierarchy instead of by name.
  */
final case class Facet(
    value: String,
    count: Int,
    id: Option[String] = None,
    /** The gazetteer place behind `id` has a disputed shape (plan §1.6a) — rendered "(contested)". */
    contested: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object Facet:
  given Schema[Facet] = summon[KindlingsSchema[Facet]].schema
