package com.histmersion.api.maintenance
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry

/** Data management's upkeep jobs, as it registers them: the database's clean-up. */
object MaintenanceJobKinds:
  val Vacuum: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("vacuum"),
    _root_.com.histmersion.api.jobs.JobKinds.Wire("Clean up the database"),
    _root_.com.histmersion.api.jobs.JobKindMeaning("Database clean-up (VACUUM): frees the space old row versions hold after a re-seed or a gazetteer load."),
    subject =
      com.histmersion.api.maintenance.VacuumPlan.Detail.of(_).map(p => _root_.com.histmersion.api.jobs.JobKinds.Subject(com.histmersion.api.maintenance.Vacuum.describe(p)))
  )
