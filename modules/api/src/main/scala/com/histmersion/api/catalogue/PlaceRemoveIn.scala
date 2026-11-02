package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.given

final case class PlaceRemoveIn(reason: Option[String] = None) derives KindlingsSchema
object PlaceRemoveIn:
  given Schema[PlaceRemoveIn] = summon[KindlingsSchema[PlaceRemoveIn]].schema
  given Codec.AsObject[PlaceRemoveIn] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceRemoveIn])
