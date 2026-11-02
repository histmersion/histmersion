package com.histmersion.util.data.maintenance

import com.histmersion.api.Vacuum
import com.histmersion.util.jobs.JobKindEntry
import com.histmersion.values.JobKind

/** Data management's upkeep jobs, as it registers them: the database's clean-up. */
object MaintenanceJobKinds:
  val Vacuum: JobKindEntry = JobKindEntry.of(
    JobKind("vacuum"),
    "Clean up the database",
    "Database clean-up (VACUUM): frees the space old row versions hold after a re-seed or a gazetteer load.",
    subject = com.histmersion.api.VacuumPlan.Detail.of(_).map(com.histmersion.api.Vacuum.describe)
  )
