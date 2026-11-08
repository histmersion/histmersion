package com.histmersion
package util.data.maintenance

import com.histmersion.api.maintenance.ReindexResult
import com.histmersion.util.data.DataOwner
import io.github.iltotore.iron.*

/** A piece of an owner's upkeep that runs in resumable slices: each answers how far it got and how much is left, and
  * the next one starts after the last record it did.
  */
trait MaintenanceTask:
  def owner: DataOwner
  def name: MaintenanceTask.Name

  /** Run one slice of it. */
  def run(slice: MaintenanceSlice, site: MaintenanceSite): ReindexResult

object MaintenanceTask:

  /** What a piece of upkeep is called (`reindex`): unique among the registered ones, and the name its guard refuses. */
  type Name = Name.T
  object Name extends Newtype[String, Pure]
