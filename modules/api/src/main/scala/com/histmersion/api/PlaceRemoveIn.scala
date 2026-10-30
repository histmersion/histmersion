package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

/** `POST /api/places/{id}/remove` — delete a place nothing refers to (no event, attestation, asset link, child, name
  * added by hand, nor a finding it is the only place of); no re-seed brings it back.
  */
final case class PlaceRemoveIn(reason: Option[String] = None) derives KindlingsSchema
object PlaceRemoveIn:
  given Schema[PlaceRemoveIn] = summon[KindlingsSchema[PlaceRemoveIn]].schema
  given Codec.AsObject[PlaceRemoveIn] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceRemoveIn])
