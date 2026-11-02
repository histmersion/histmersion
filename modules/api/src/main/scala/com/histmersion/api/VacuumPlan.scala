package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class VacuumPlan(analyze: Boolean, tables: List[String]) derives KindlingsCodecAsObject, KindlingsSchema
object VacuumPlan:
  given Schema[VacuumPlan] = summon[KindlingsSchema[VacuumPlan]].schema

  /** A vacuum job's plan, in its detail. */
  val Detail: com.histmersion.util.jobs.JobDetailField[Option[VacuumPlan]] =
    com.histmersion.util.jobs.JobDetailField("vacuum", None)
