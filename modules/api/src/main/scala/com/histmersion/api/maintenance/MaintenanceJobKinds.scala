package com.histmersion
package api.maintenance
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.jobs.JobKindMeaning
import com.histmersion.lint.{tolerated, LintRule}

/** Data management's upkeep jobs, as it registers them: the database's clean-up. */
object MaintenanceJobKinds:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val Vacuum: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("vacuum"),
    JobKinds.Wire("Clean up the database"),
    JobKindMeaning(
      "Database clean-up (VACUUM): frees the space old row versions hold after a re-seed or a gazetteer load."
    ),
    subject = VacuumPlan.Detail.of(_).map(p => JobKinds.Subject(api.maintenance.Vacuum.describe(p)))
  )
