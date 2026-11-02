package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.given

final case class PlaceNameRemove(name: String, lang: Option[String] = None) derives KindlingsSchema
object PlaceNameRemove:
  given Schema[PlaceNameRemove] = summon[KindlingsSchema[PlaceNameRemove]].schema
  given Codec.AsObject[PlaceNameRemove] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceNameRemove])
