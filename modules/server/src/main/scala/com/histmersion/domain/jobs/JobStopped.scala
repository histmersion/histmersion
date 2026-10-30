package com.histmersion.domain.jobs

/** Thrown out of a background job's progress report when a person asked the job to stop: the job's body is left at the
  * part boundary it reported, and the runner records the job paused or cancelled.
  */
final class JobStopped(val stop: JobStop) extends RuntimeException(s"stopped: $stop", null, false, false)
