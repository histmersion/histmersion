package com.histmersion.util.data.exchange

import com.histmersion.api.InitPlan
import com.histmersion.util.jobs.JobKindEntry
import com.histmersion.values.JobKind

/** Data management's exchange jobs, as it registers them: the first-run import of reference data, and a merge apply.
  */
object ExchangeJobKinds:
  val Init: JobKindEntry = JobKindEntry.of(
    JobKind("init"),
    "Import reference data",
    "The first-run import of reference data: the bundled gazetteer and period presets, and the sources it fetches.",
    stoppable = true,
    subject = _.init.map(InitPlan.describe)
  )

  val MergeApply: JobKindEntry = JobKindEntry.of(
    JobKind("merge-apply"),
    "Merge another collection",
    "Merges another collection's bundle into this one, after taking a backup.",
    subject = _.planHash.map(h => s"merge plan $h")
  )
