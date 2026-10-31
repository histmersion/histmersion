package com.histmersion.util.data.maintenance

import com.histmersion.util.data.DataOwner

/** A piece of an owner's upkeep. */
trait MaintenanceTask:
  def owner: DataOwner
