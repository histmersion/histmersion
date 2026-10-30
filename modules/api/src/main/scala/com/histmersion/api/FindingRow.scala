package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.Confidence

/** One geo-located, time-scoped finding row. */
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
    /** The finding's permanent identity (V030); its `<citekey>#<n>` id is a label that a rename moves. */
    uid: Option[java.util.UUID] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object FindingRow:
  given Schema[FindingRow] = summon[KindlingsSchema[FindingRow]].schema
