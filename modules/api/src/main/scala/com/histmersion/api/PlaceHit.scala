package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `GET /api/places?q=` — a search result: the row, which of its names matched, and how many findings are assigned to
  * it (through `finding_places`, so it reflects the last `places rebuild`).
  */
final case class PlaceHit(
    id: String,
    label: String,
    kind: String,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    source: Option[String],
    names: String,
    matchedName: Option[String],
    findings: Int,
    contested: Boolean = false,
    dating: PlaceDating = PlaceDating.Stated,
    importSource: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def toSummary: PlaceSummary =
    PlaceSummary(id, label, kind, periodStart, periodEnd, source, names, contested, dating, importSource)
object PlaceHit:
  given Schema[PlaceHit] = summon[KindlingsSchema[PlaceHit]].schema
