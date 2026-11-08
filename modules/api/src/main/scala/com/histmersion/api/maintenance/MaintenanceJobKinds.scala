package com.histmersion
package api.maintenance
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.jobs.JobKindMeaning

/** Data management's upkeep jobs, as it registers them: the database's clean-up. */
object MaintenanceJobKinds:
  val Vacuum: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("vacuum"),
    JobKinds.Wire("Clean up the database"),
    JobKindMeaning("Database clean-up (VACUUM): frees the space old row versions hold after a re-seed or a gazetteer load."),
    subject =
      VacuumPlan.Detail.of(_).map(p => JobKinds.Subject(com.histmersion.api.maintenance.Vacuum.describe(p)))
  )
