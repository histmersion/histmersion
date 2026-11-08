package com.histmersion
package util.data

import com.histmersion.util.data.diagnostics.DataFigures
import com.histmersion.util.data.maintenance.{CacheEvent, CacheHolding, CacheName, CacheSpec}
import com.histmersion.util.storage.Location
import com.augustnagro.magnum.DbCon

/** What data management says about the data it keeps itself: the merges and their ledger, the backups and bundles it
  * writes, and the storage figures it measures for About (held in memory until they expire).
  */
object DataManagementData extends DataContribution:
  val owner: DataOwner = DataOwner("util.data")

  val tables: List[OwnedTable] = OwnedTable.all("merges", "merge_decisions", "merge_ledger", "merge_sessions")

  /** The backups and the bundles written or uploaded. */
  override val stores: List[Location] = List(Location.Backups, Location.Bundles)

  override val caches: List[CacheSpec] =
    List(
      CacheSpec(
        owner,
        CacheName("storage"),
        CacheSpec.Label("storage figures"),
        CacheHolding.InMemory,
        List(CacheEvent.Expired)
      )
    )

  def figures(using DbCon): DataFigures = DataFigures.empty
