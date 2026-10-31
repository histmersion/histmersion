package com.histmersion.util.data.maintenance

import io.github.iltotore.iron.*

/** What a piece of upkeep is called (`reindex`): unique among the registered ones, and the name its guard refuses. */
type MaintenanceTaskName = MaintenanceTaskName.T
object MaintenanceTaskName extends RefinedType[String, Pure]
