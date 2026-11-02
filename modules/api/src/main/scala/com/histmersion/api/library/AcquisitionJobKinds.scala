package com.histmersion.api.library
import com.histmersion.api.library.AcquireChecks
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.util.jobs.Job

/** The library's acquisition jobs, as it registers them: the legal re-check of the to-acquire list. */
object AcquisitionJobKinds:
  val AcquireRecheck: JobKindEntry = JobKindEntry.of(
    Job.Kind("acquire_recheck"),
    "Check what to acquire again",
    "Checks the to-acquire list again: which items have a legal open copy or are in the library now.",
    stoppable = true,
    subject = AcquireChecks.RecheckJob.Detail.of(_).map(AcquireChecks.RecheckJob.describe)
  )
