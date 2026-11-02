package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.given

final case class PlaceNameAdd(name: String, lang: Option[String] = None) derives KindlingsSchema
object PlaceNameAdd:
  given Schema[PlaceNameAdd] = summon[KindlingsSchema[PlaceNameAdd]].schema
  given Codec.AsObject[PlaceNameAdd] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceNameAdd])
