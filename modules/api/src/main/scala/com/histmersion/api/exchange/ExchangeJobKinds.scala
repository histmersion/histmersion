package com.histmersion.api.exchange
import com.histmersion.api.exchange.InitPlan
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.util.jobs.Job
import com.histmersion.api.jobs.JobDetail

/** Data management's exchange jobs, as it registers them: the first-run import of reference data, and a merge apply.
  */
object ExchangeJobKinds:
  val Init: JobKindEntry = JobKindEntry.of(
    Job.Kind("init"),
    "Import reference data",
    "The first-run import of reference data: the bundled gazetteer and period presets, and the sources it fetches.",
    stoppable = true,
    subject = InitPlan.Detail.of(_).map(InitPlan.describe)
  )

  val MergeApply: JobKindEntry = JobKindEntry.of(
    Job.Kind("merge-apply"),
    "Merge another collection",
    "Merges another collection's bundle into this one, after taking a backup.",
    subject = PlanHash.of(_).map(h => s"merge plan $h")
  )

  /** The plan a merge apply carries out, by its hash, in the job's detail. */
  val PlanHash: JobDetail.Field[Option[String]] = JobDetail.Field("planHash", None)
