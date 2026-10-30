package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

/** `POST /api/places/{id}/names/remove` — only a name a person added can be removed: an imported one would come back
  * with the next seed.
  */
final case class PlaceNameRemove(name: String, lang: Option[String] = None) derives KindlingsSchema
object PlaceNameRemove:
  given Schema[PlaceNameRemove] = summon[KindlingsSchema[PlaceNameRemove]].schema
  given Codec.AsObject[PlaceNameRemove] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceNameRemove])
