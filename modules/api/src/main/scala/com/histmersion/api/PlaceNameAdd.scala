package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

/** `POST /api/places/{id}/names` — `lang` is a language tag (`en`, `grc`, `pl`); absent = unspecified. */
final case class PlaceNameAdd(name: String, lang: Option[String] = None) derives KindlingsSchema
object PlaceNameAdd:
  given Schema[PlaceNameAdd] = summon[KindlingsSchema[PlaceNameAdd]].schema
  given Codec.AsObject[PlaceNameAdd] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceNameAdd])
