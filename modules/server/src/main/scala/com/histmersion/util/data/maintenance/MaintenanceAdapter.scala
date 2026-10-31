package com.histmersion.util.data.maintenance

import com.histmersion.util.data.DataOwner
import com.histmersion.util.storage.Location

/** Today's stores, caches and upkeep, registered by owner until each owner registers its own. */
object MaintenanceAdapter:
  def stores(owner: DataOwner): List[Location] = Nil
  def caches(owner: DataOwner): List[CacheSpec] = Nil
  def tasks(owner: DataOwner): List[MaintenanceTask] = Nil
