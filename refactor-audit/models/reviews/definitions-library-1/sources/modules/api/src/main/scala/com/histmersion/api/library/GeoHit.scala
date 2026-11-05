package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class GeoHit(
    citekey: com.histmersion.api.library.Citekey,
    title: GeoHit.Title,
    claim: GeoHit.Claim,
    regionName: Option[GeoHit.RegionName],
    lon: Option[com.histmersion.api.catalogue.Longitude],
    lat: Option[com.histmersion.api.catalogue.Latitude],
    periodStart: Option[com.histmersion.api.catalogue.Year],
    periodEnd: Option[com.histmersion.api.catalogue.Year],
    confidence: Option[Confidence],
    significance: Option[GeoHit.Significance] = None,
    doi: Option[com.histmersion.api.library.Doi] = None,
    sourceKind: Option[GeoHit.SourceKind] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object GeoHit:
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Claim = Claim.T
  object Claim extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionName = RegionName.T
  object RegionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Significance = Significance.T
  object Significance extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceKind = SourceKind.T
  object SourceKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
