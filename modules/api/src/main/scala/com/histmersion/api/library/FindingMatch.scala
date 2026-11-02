package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class FindingMatch(
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
      KindlingsSchema
object FindingMatch:
  given Schema[FindingMatch] = summon[KindlingsSchema[FindingMatch]].schema
