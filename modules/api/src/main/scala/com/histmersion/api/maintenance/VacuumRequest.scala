package com.histmersion.api.maintenance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.given

/** A re-seed or a gazetteer load leaves the old row versions behind as dead tuples, which autovacuum reaches only
  * slowly on tables that size.
  */
final case class VacuumRequest(analyze: Boolean = false, tables: List[String] = Nil)
object VacuumRequest:
  given Schema[VacuumRequest] = KindlingsSchema
    .derived[VacuumRequest]
    .schema
    .modify(_.analyze)(_.description("also refresh the planner's statistics (VACUUM (ANALYZE))"))
    .modify(_.tables)(_.description("tables to vacuum (default: config maintenance.vacuum-tables)"))
  given Codec.AsObject[VacuumRequest] = StrictJson.codec(KindlingsCodecAsObject.derived[VacuumRequest])
