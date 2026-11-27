package com.histmersion
package api.exchange
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobDetail
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.jobs.JobKindMeaning
import com.histmersion.lint.{tolerated, LintRule}

/** Data management's exchange jobs, as it registers them: the first-run import of reference data, and a merge apply.
  */
object ExchangeJobKinds:
  type PlanHash = PlanHashValue
  export MergePlan.PlanHash as PlanHashValue

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val Init: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("init"),
    JobKinds.Wire("Import reference data"),
    JobKindMeaning(
      "The first-run import of reference data: the bundled gazetteer and period presets, and the sources it fetches."
    ),
    stoppable = JobKindEntry.OfStoppable(true),
    subject = InitPlan.Detail.of(_).map(p => JobKinds.Subject(InitPlan.describe(p)))
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val MergeApply: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("merge-apply"),
    JobKinds.Wire("Merge another collection"),
    JobKindMeaning("Merges another collection's bundle into this one, after taking a backup."),
    subject = PlanHash.of(_).map(h => JobKinds.Subject(s"merge plan $h"))
  )

  /** The plan a merge apply carries out, by its hash, in the job's detail. */
  val PlanHash: JobDetail.Field[Option[PlanHash]] = new JobDetail.Field(JobDetail.Field.Name("planHash"), None)
