package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── an import ───────────────────────────────────────────────────────────────────────────────────

/** `POST /api/events/import` (a job): read `provider`'s catalogue — `bbox` (`minLon,minLat,maxLon,maxLat`), the years
  * `from`/`to` (signed), `minVei` (eruptions) and `minMagnitude` (earthquakes) narrow it further than the configured
  * defaults — and, unless `dryRun`, store what it finds, merged with what is there. `limit` caps the records taken;
  * `refresh` asks again for the parts already read.
  */
final case class EventImportRequest(
    provider: String,
    dryRun: Boolean = true,
    limit: Option[Int] = None,
    bbox: Option[String] = None,
    from: Option[Long] = None,
    to: Option[Long] = None,
    minVei: Option[Int] = None,
    minMagnitude: Option[Double] = None,
    refresh: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventImportRequest:
  given Schema[EventImportRequest] = summon[KindlingsSchema[EventImportRequest]].schema
