package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

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
