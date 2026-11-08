package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class GeoHit(
    citekey: Citekey,
    title: GeoHit.Title,
    claim: GeoHit.Claim,
    regionName: Option[Place.Region],
    lon: Option[Longitude],
    lat: Option[Latitude],
    periodStart: Option[Year],
    periodEnd: Option[Year],
    confidence: Option[Confidence],
    significance: Option[GeoHit.Significance] = None,
    doi: Option[Doi] = None,
    sourceKind: Option[GeoHit.SourceKind] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object GeoHit:
  export com.histmersion.api.library.Title
  type Claim = Claim.T
  object Claim extends RefinedType[String, Pure]
  type Significance = Significance.T
  object Significance extends RefinedType[String, Pure]
  type SourceKind = SourceKind.T
  object SourceKind extends RefinedType[String, Pure]
