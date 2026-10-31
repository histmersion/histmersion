package com.histmersion.util.data.maintenance

import com.histmersion.util.data.{DataOwner, TableName}
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
    case CatalogueOwner  =>
      List(Location.GazetteerExtracts, Location.GazetteerFolder, Location.GazetteerDrop, Location.Basemap)
    case LibraryOwner    => List(Location.IngestOriginals, Location.Spool, Location.CorpusFolder)
    case DataArea        => List(Location.Backups, Location.Bundles)
    case DatabaseOwner   => List(Location.DatabaseCluster)
    case _               => Nil

  def caches(owner: DataOwner): List[CacheSpec] = owner match
    case CatalogueOwner =>
      List(
        CacheSpec(owner, CacheName("map-tiles"), "map tiles", CacheHolding.InMemory, List(CacheEvent.Restored)),
        CacheSpec(
          owner,
          CacheName("undated-cells"),
          "undated sites by cell",
          CacheHolding.InTables(List(TableName("site_undated_cells"), TableName("site_undated_build")))
        ),
        CacheSpec(owner, CacheName("relief-cache"), "relief cache", onDemand("terrain-cache")),
        CacheSpec(owner, CacheName("map-cache"), "map cache", onDemand("vector-cache")),
        CacheSpec(owner, CacheName("sea-floor-cache"), "sea-floor cache", onDemand("sea-cache"))
      )
    case MediaOwner =>
      List(
        CacheSpec(owner, CacheName("thumbnails"), "picture thumbnails", CacheHolding.InTables(List(TableName("assets"))))
      )
    case DataArea =>
      List(CacheSpec(owner, CacheName("storage"), "storage figures", CacheHolding.InMemory, List(CacheEvent.Expired)))
    case _ => Nil

  /** The base map's tiles read on demand: the relief deeper, the map and the sea floor outside the focus region. */
  private def onDemand(directory: String): CacheHolding =
    CacheHolding.InStore(Location.Basemap, StoreKey.applyUnsafe(directory))

  def tasks(owner: DataOwner): List[MaintenanceTask] = Nil
