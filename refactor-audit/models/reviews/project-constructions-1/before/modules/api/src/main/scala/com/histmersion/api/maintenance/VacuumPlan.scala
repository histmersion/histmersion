package com.histmersion.api.maintenance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class VacuumPlan(analyze: VacuumPlan.Analyze, tables: List[VacuumPlan.Tables]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
object VacuumPlan:


  /** A vacuum job's plan, in its detail. */
  type Analyze = Analyze.T
  object Analyze extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tables = Tables.T
  object Tables extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Detail: com.histmersion.api.jobs.JobDetail.Field[Option[VacuumPlan]] =
    com.histmersion.api.jobs.JobDetail.Field("vacuum", None)
