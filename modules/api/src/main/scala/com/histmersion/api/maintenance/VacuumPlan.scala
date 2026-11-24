package com.histmersion
package api.maintenance
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.jobs.JobDetail
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class VacuumPlan(analyze: VacuumPlan.Analyze, tables: List[VacuumPlan.Tables])
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema
object VacuumPlan:

  /** A vacuum job's plan, in its detail. */
  export Vacuum.Request.{Analyze, Tables}

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  val Detail: JobDetail.Field[Option[VacuumPlan]] =
    new com.histmersion.api.jobs.JobDetail.Field(JobDetail.Field.Name("vacuum"), None)
