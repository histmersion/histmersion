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
    id: String,
    citekey: String,
    claim: String,
    region: Option[String],
    geo: Option[GeoPoint],
    period: Option[Period],
    periodName: Option[String],
    tags: List[String],
    confidence: Option[Confidence],
    significance: Option[String] = None
)

object Finding:

  final case class Input(
      claim: String,
      region: Option[String] = None,
      lon: Option[Double] = None,
      lat: Option[Double] = None,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None,
      periodName: Option[String] = None,
      tags: List[String] = Nil,
      confidence: Option[String] = None,
      significance: Option[String] = None
  )
  object Input:
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
      confidence = f.confidence.map(_.toString.toLowerCase),
      significance = f.significance
    )

  final case class Match(
      id: String,
      claim: String,
      regionName: Option[String],
      lon: Option[Double],
      lat: Option[Double],
      periodStart: Option[Long],
      periodEnd: Option[Long],
      periodName: Option[String],
      tags: List[String],
      why: List[String],
      places: List[String] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class Row(
      claim: String,
      regionName: Option[String],
      lon: Option[Double],
      lat: Option[Double],
      periodStart: Option[Long],
      periodEnd: Option[Long],
      periodName: Option[String],
      tags: List[String],
      confidence: Option[Confidence],
      significance: Option[String] = None,
      uid: Option[java.util.UUID] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
