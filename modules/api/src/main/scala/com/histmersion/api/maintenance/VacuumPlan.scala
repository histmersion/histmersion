package com.histmersion.api.maintenance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class VacuumPlan(analyze: VacuumPlan.Analyze, tables: List[VacuumPlan.Tables]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
object VacuumPlan:


  /** A vacuum job's plan, in its detail. */
  export com.histmersion.api.maintenance.Vacuum.Request.{Analyze, Tables}

  val Detail: com.histmersion.api.jobs.JobDetail.Field[Option[VacuumPlan]] =
    new com.histmersion.api.jobs.JobDetail.Field(_root_.com.histmersion.api.jobs.JobDetail.Field.Name("vacuum"), None)
