package com.histmersion.api.maintenance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class VacuumPlan(analyze: Boolean, tables: List[String]) derives KindlingsCodecAsObject, KindlingsSchema
object VacuumPlan:
  given Schema[VacuumPlan] = summon[KindlingsSchema[VacuumPlan]].schema

  /** A vacuum job's plan, in its detail. */
  val Detail: com.histmersion.api.jobs.JobDetailField[Option[VacuumPlan]] =
    com.histmersion.api.jobs.JobDetailField("vacuum", None)
