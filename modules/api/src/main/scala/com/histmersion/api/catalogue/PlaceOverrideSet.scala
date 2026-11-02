package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.given

final case class PlaceOverrideSet(
    kind: Option[String] = None,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    reason: Option[String] = None,
    reset: Boolean = false,
    pointLat: Option[Double] = None,
    pointLon: Option[Double] = None
)
object PlaceOverrideSet:
  given Schema[PlaceOverrideSet] = KindlingsSchema
    .derived[PlaceOverrideSet]
    .schema
    .modify(_.kind)(_.description(s"one of ${com.histmersion.domain.catalogue.Place.Kind.all.mkString(", ")}"))
    .modify(_.reason)(
      _.description("why: the evidence the decision rests on (required with a point: where the point comes from)")
    )
    .modify(_.pointLat)(
      _.description("latitude of the place's corrected point (with pointLon); only for a place known by a point")
    )
    .modify(_.pointLon)(_.description("longitude of the place's corrected point (with pointLat)"))
  given Codec.AsObject[PlaceOverrideSet] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceOverrideSet])
