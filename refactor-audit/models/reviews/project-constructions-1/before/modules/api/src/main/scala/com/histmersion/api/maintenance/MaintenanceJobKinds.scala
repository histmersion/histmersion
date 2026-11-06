package com.histmersion.api.maintenance
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry

/** Data management's upkeep jobs, as it registers them: the database's clean-up. */
object MaintenanceJobKinds:
  val Vacuum: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("vacuum"),
    "Clean up the database",
    "Database clean-up (VACUUM): frees the space old row versions hold after a re-seed or a gazetteer load.",
    subject =
      com.histmersion.api.maintenance.VacuumPlan.Detail.of(_).map(com.histmersion.api.maintenance.Vacuum.describe)
  )
