package com.histmersion.api.maintenance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class VacuumPlan(analyze: VacuumPlan.Analyze, tables: List[VacuumPlan.Tables]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
object VacuumPlan:


  /** A vacuum job's plan, in its detail. */
  type Analyze = com.histmersion.api.maintenance.Vacuum.Request.Analyze
  val Analyze: _root_.com.histmersion.api.maintenance.Vacuum.Request.Analyze.type = _root_.com.histmersion.api.maintenance.Vacuum.Request.Analyze
  type Tables = com.histmersion.api.maintenance.Vacuum.Request.Tables
  val Tables: _root_.com.histmersion.api.maintenance.Vacuum.Request.Tables.type = _root_.com.histmersion.api.maintenance.Vacuum.Request.Tables

  val Detail: com.histmersion.api.jobs.JobDetail.Field[Option[VacuumPlan]] =
    com.histmersion.api.jobs.JobDetail.Field(_root_.com.histmersion.api.jobs.JobDetail.Field.Name("vacuum"), None)
