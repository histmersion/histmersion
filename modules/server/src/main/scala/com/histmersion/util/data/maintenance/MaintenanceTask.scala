package com.histmersion.util.data.maintenance

import com.histmersion.api.maintenance.ReindexResult
import com.histmersion.util.data.DataOwner

/** A piece of an owner's upkeep that runs in resumable slices: each answers how far it got and how much is left, and
  * the next one starts after the last record it did.
  */
trait MaintenanceTask:
  def owner: DataOwner
  def name: MaintenanceTaskName

  /** Run one slice of it. */
  def run(slice: MaintenanceSlice, site: MaintenanceSite): ReindexResult
