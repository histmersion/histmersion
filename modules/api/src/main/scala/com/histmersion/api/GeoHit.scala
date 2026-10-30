package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.Confidence

/** A finding plus its source, for `geo_when` and the map. */
final case class GeoHit(
    citekey: String,
    title: String,
    claim: String,
    regionName: Option[String],
    lon: Option[Double],
    lat: Option[Double],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    confidence: Option[Confidence],
    significance: Option[String] = None,
    /** The source's DOI — what a link to it is labelled with (the owner's rule: DOI when present). */
    doi: Option[String] = None,
    /** What the source is ([[SourceKinds]]): the map draws the finding with that kind's icon. */
    sourceKind: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object GeoHit:
  given Schema[GeoHit] = summon[KindlingsSchema[GeoHit]].schema
