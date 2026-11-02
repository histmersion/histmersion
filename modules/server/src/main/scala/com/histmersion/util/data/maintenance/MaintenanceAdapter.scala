package com.histmersion.util.data.maintenance

import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.TablesAdapter.*
import com.histmersion.util.storage.{Location, StoreKey}

/** Today's stores, caches and upkeep, registered by owner until each owner registers its own. The upkeep whose code
  * lives in its owner's place (the library's reindex) is registered there and handed to the registry by the composition
  * root.
  */
object MaintenanceAdapter:

  /** The directories each owner keeps files in. The install's own (binaries, toolchain, plugins, logs) are no owner's
    * data.
    */
  def stores(owner: DataOwner): List[Location] = owner match
    case AutomationOwner => List(Location.AgentHomes, Location.CodexSessions)
    case DataArea        => List(Location.Backups, Location.Bundles)
    case DatabaseOwner   => List(Location.DatabaseCluster)
    case _               => Nil

  def caches(owner: DataOwner): List[CacheSpec] = owner match
    case DataArea =>
      List(CacheSpec(owner, CacheName("storage"), "storage figures", CacheHolding.InMemory, List(CacheEvent.Expired)))
    case _ => Nil

  def tasks(owner: DataOwner): List[MaintenanceTask] = Nil
