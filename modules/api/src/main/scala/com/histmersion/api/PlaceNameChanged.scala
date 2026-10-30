package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What a name edit did. `changed = false`: the place already had that name as a manual one (add), or never had it
  * (remove is refused instead). `wasImported`: the name was already there from an import — it is now kept as manual, so
  * a later seed that drops it does not lose it.
  */
final case class PlaceNameChanged(
    placeId: String,
    label: String,
    name: String,
    lang: String,
    added: Boolean,
    changed: Boolean,
    wasImported: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceNameChanged:
  given Schema[PlaceNameChanged] = summon[KindlingsSchema[PlaceNameChanged]].schema
