package com.histmersion.api.library
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry

/** The library's acquisition jobs, as it registers them: the legal re-check of the to-acquire list. */
object AcquisitionJobKinds:
  val AcquireRecheck: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("acquire_recheck"),
    "Check what to acquire again",
    "Checks the to-acquire list again: which items have a legal open copy or are in the library now.",
    stoppable = true,
    subject = Acquire.Checks.RecheckJob.Detail.of(_).map(Acquire.Checks.RecheckJob.describe)
  )
