package com.histmersion.api.library
import com.histmersion.api.GeoPoint
import com.histmersion.api.catalogue.Period
import com.histmersion.api.given
import com.histmersion.api.StrictJson
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import scala.annotation.tailrec
import sttp.tapir.Schema

final case class Finding(
    id: Finding.Id,
    citekey: com.histmersion.api.library.Citekey,
    claim: Finding.Claim,
    region: Option[Finding.Region],
    geo: Option[GeoPoint],
    period: Option[Period],
    periodName: Option[Finding.PeriodName],
    tags: List[Finding.Tags],
    confidence: Option[Confidence],
    significance: Option[Finding.Significance] = None
)

object Finding:

  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Claim = Claim.T
  object Claim extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Region = Region.T
  object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodName = PeriodName.T
  object PeriodName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Significance = Significance.T
  object Significance extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Input(
      claim: com.histmersion.api.library.Finding.Claim,
      region: Option[com.histmersion.api.library.Finding.Region] = None,
      lon: Option[com.histmersion.api.catalogue.Longitude] = None,
      lat: Option[com.histmersion.api.catalogue.Latitude] = None,
      periodStart: Option[com.histmersion.api.catalogue.Year] = None,
      periodEnd: Option[com.histmersion.api.catalogue.Year] = None,
      periodName: Option[com.histmersion.api.library.Finding.PeriodName] = None,
      tags: List[com.histmersion.api.library.Finding.Tags] = Nil,
      confidence: Option[Input.Confidence] = None,
      significance: Option[com.histmersion.api.library.Finding.Significance] = None
  )
  object Input:
    type Confidence = Confidence.T
    object Confidence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[Input] = KindlingsSchema
      .derived[Input]
      .schema
      .modify(_.confidence)(_.description("high | medium | low | contested — another word is refused"))

    given Codec.AsObject[Input] = StrictJson.codec(KindlingsCodecAsObject.derived[Input])

    def of(f: Finding): Input = Input(
      claim = f.claim,
      region = f.region,
      lon = f.geo.map(_.lon),
      lat = f.geo.map(_.lat),
      periodStart = f.period.map(_.start),
      periodEnd = f.period.map(_.end),
      periodName = f.periodName,
      tags = f.tags,
      confidence = f.confidence.map(_root_.com.histmersion.api.library.Finding.Input.Confidence(_.toString.toLowerCase)),
      significance = f.significance
    )

  final case class Match(
      id: com.histmersion.api.library.Finding.Id,
      claim: com.histmersion.api.library.Finding.Claim,
      regionName: Option[Match.RegionName],
      lon: Option[com.histmersion.api.catalogue.Longitude],
      lat: Option[com.histmersion.api.catalogue.Latitude],
      periodStart: Option[com.histmersion.api.catalogue.Year],
      periodEnd: Option[com.histmersion.api.catalogue.Year],
      periodName: Option[com.histmersion.api.library.Finding.PeriodName],
      tags: List[com.histmersion.api.library.Finding.Tags],
      why: List[Match.Why],
      places: List[Match.Places] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Match:
    type RegionName = RegionName.T
    object RegionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Places = Places.T
    object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Row(
      claim: com.histmersion.api.library.Finding.Claim,
      regionName: Option[Row.RegionName],
      lon: Option[com.histmersion.api.catalogue.Longitude],
      lat: Option[com.histmersion.api.catalogue.Latitude],
      periodStart: Option[com.histmersion.api.catalogue.Year],
      periodEnd: Option[com.histmersion.api.catalogue.Year],
      periodName: Option[com.histmersion.api.library.Finding.PeriodName],
      tags: List[com.histmersion.api.library.Finding.Tags],
      confidence: Option[Confidence],
      significance: Option[com.histmersion.api.library.Finding.Significance] = None,
      uid: Option[java.util.UUID] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Row:
    type RegionName = RegionName.T
    object RegionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
