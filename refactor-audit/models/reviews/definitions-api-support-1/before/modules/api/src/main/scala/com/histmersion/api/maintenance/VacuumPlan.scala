package com.histmersion.api.maintenance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class VacuumPlan(analyze: Boolean, tables: List[String]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
object VacuumPlan:


  /** A vacuum job's plan, in its detail. */
  val Detail: com.histmersion.api.jobs.JobDetail.Field[Option[VacuumPlan]] =
    com.histmersion.api.jobs.JobDetail.Field("vacuum", None)
