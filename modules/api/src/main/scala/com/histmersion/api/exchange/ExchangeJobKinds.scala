package com.histmersion.api.exchange
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobDetail
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

/** Data management's exchange jobs, as it registers them: the first-run import of reference data, and a merge apply.
  */
object ExchangeJobKinds:
  type PlanHash = PlanHashValue
  export com.histmersion.api.exchange.MergePlan.{PlanHash as PlanHashValue}

  val Init: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("init"),
    _root_.com.histmersion.api.jobs.JobKinds.Wire("Import reference data"),
    _root_.com.histmersion.api.jobs.JobKindMeaning("The first-run import of reference data: the bundled gazetteer and period presets, and the sources it fetches."),
    stoppable = _root_.com.histmersion.api.jobs.JobKindEntry.OfStoppable(true),
    subject = InitPlan.Detail.of(_).map(p => _root_.com.histmersion.api.jobs.JobKinds.Subject(InitPlan.describe(p)))
  )

  val MergeApply: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("merge-apply"),
    _root_.com.histmersion.api.jobs.JobKinds.Wire("Merge another collection"),
    _root_.com.histmersion.api.jobs.JobKindMeaning("Merges another collection's bundle into this one, after taking a backup."),
    subject = PlanHash.of(_).map(h => _root_.com.histmersion.api.jobs.JobKinds.Subject(s"merge plan $h"))
  )

  /** The plan a merge apply carries out, by its hash, in the job's detail. */
  val PlanHash: JobDetail.Field[Option[com.histmersion.api.exchange.ExchangeJobKinds.PlanHash]] = new JobDetail.Field(_root_.com.histmersion.api.jobs.JobDetail.Field.Name("planHash"), None)
