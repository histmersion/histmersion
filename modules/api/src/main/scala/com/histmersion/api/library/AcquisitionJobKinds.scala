package com.histmersion
package api.library
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.jobs.JobKindMeaning

/** The library's acquisition jobs, as it registers them: the legal re-check of the to-acquire list. */
object AcquisitionJobKinds:
  val AcquireRecheck: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("acquire_recheck"),
    JobKinds.Wire("Check what to acquire again"),
    JobKindMeaning("Checks the to-acquire list again: which items have a legal open copy or are in the library now."),
    stoppable = JobKindEntry.OfStoppable(true),
    subject = Acquire.Checks.RecheckJob.Detail.of(_).map(j => JobKinds.Subject(Acquire.Checks.RecheckJob.describe(j)))
  )
