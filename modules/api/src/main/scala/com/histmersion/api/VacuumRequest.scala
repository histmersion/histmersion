package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

/** `POST /api/maintenance/vacuum` (#1445) — `VACUUM` (with `analyze`, `VACUUM (ANALYZE)`) over the named tables, or
  * over `maintenance.vacuum-tables` (the gazetteer and finding tables a re-seed rewrites) when none is named. A re-seed
  * or a gazetteer load upserts every place, name and link, and leaves the old row versions behind as dead tuples — 1.2
  * M in `place_names` after the 2026-10-09 clean-up — which autovacuum reaches only slowly on tables that size.
  */
final case class VacuumRequest(analyze: Boolean = false, tables: List[String] = Nil)
object VacuumRequest:
  given Schema[VacuumRequest] = KindlingsSchema
    .derived[VacuumRequest]
    .schema
    .modify(_.analyze)(_.description("also refresh the planner's statistics (VACUUM (ANALYZE))"))
    .modify(_.tables)(_.description("tables to vacuum (default: config maintenance.vacuum-tables)"))
  given Codec.AsObject[VacuumRequest] = StrictJson.codec(KindlingsCodecAsObject.derived[VacuumRequest])
