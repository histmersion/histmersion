package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.values.PlaceKind
import com.histmersion.api.StrictJson
import com.histmersion.api.given

final case class PlaceAdd(
    label: String,
    kind: String,
    lon: Double,
    lat: Double,
    source: String,
    id: Option[String] = None,
    names: List[String] = Nil,
    lang: Option[String] = None,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None
)
object PlaceAdd:
  given Schema[PlaceAdd] = KindlingsSchema
    .derived[PlaceAdd]
    .schema
    .modify(_.kind)(_.description(s"one of ${PlaceKind.all.mkString(", ")}"))
    .modify(_.source)(_.description("where the coordinates come from (a gazetteer id, a map, a publication)"))
  given Codec.AsObject[PlaceAdd] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceAdd])
