package com.histmersion.api.exchange
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobDetail

/** Data management's exchange jobs, as it registers them: the first-run import of reference data, and a merge apply.
  */
object ExchangeJobKinds:
  type PlanHash = PlanHash.T
  object PlanHash extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Init: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("init"),
    _root_.com.histmersion.api.jobs.JobKindEntry.OfName("Import reference data"),
    _root_.com.histmersion.api.jobs.JobKindEntry.OfMeaning("The first-run import of reference data: the bundled gazetteer and period presets, and the sources it fetches."),
    stoppable = _root_.com.histmersion.api.jobs.JobKindEntry.OfStoppable(true),
    subject = InitPlan.Detail.of(_).map(InitPlan.describe)
  )

  val MergeApply: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("merge-apply"),
    _root_.com.histmersion.api.jobs.JobKindEntry.OfName("Merge another collection"),
    _root_.com.histmersion.api.jobs.JobKindEntry.OfMeaning("Merges another collection's bundle into this one, after taking a backup."),
    subject = PlanHash.of(_).map(h => s"merge plan $h")
  )

  /** The plan a merge apply carries out, by its hash, in the job's detail. */
  val PlanHash: JobDetail.Field[Option[com.histmersion.api.exchange.ExchangeJobKinds.PlanHash]] = JobDetail.Field(_root_.com.histmersion.api.jobs.JobDetail.Field.Name("planHash"), None)
