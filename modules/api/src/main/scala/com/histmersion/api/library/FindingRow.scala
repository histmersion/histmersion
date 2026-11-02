package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.Confidence
import com.histmersion.api.given

final case class FindingRow(
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
      KindlingsSchema
object FindingRow:
  given Schema[FindingRow] = summon[KindlingsSchema[FindingRow]].schema
