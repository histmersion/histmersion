package com.histmersion.api.library
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry

/** The library's acquisition jobs, as it registers them: the legal re-check of the to-acquire list. */
object AcquisitionJobKinds:
  val AcquireRecheck: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("acquire_recheck"),
    _root_.com.histmersion.api.jobs.JobKinds.Wire("Check what to acquire again"),
    _root_.com.histmersion.api.jobs.JobKindEntry.OfMeaning("Checks the to-acquire list again: which items have a legal open copy or are in the library now."),
    stoppable = _root_.com.histmersion.api.jobs.JobKindEntry.OfStoppable(true),
    subject = Acquire.Checks.RecheckJob.Detail.of(_).map(Acquire.Checks.RecheckJob.describe)
  )
