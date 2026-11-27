package com.histmersion
package util.jobs

import com.histmersion.lint.{tolerated, LintRule}

/** Thrown out of a background job's progress report when a person asked the job to stop: the job's body is left at the
  * part boundary it reported, and the runner records the job paused or cancelled.
  */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.SilentInterp, "pending G35 (a vocabulary renders through its wire string)")
final class JobStopped(val stop: JobStop) extends RuntimeException(s"stopped: $stop", null, false, false)
