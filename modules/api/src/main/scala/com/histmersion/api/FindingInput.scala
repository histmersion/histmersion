package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

final case class FindingInput(
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
object FindingInput:
  given Schema[FindingInput] = KindlingsSchema
    .derived[FindingInput]
    .schema
    .modify(_.confidence)(_.description("high | medium | low | contested — another word is refused"))

  given Codec.AsObject[FindingInput] = StrictJson.codec(KindlingsCodecAsObject.derived[FindingInput])

  def of(f: Finding): FindingInput = FindingInput(
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
