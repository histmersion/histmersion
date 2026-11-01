package com.histmersion.domain.media

import com.histmersion.domain.media.assets.AssetsData
import com.histmersion.util.data.{DataContribution, DataOwner, OwnedTable, TableName}
import com.histmersion.util.data.checks.CheckRule
import com.histmersion.util.data.diagnostics.DataFigures
import com.histmersion.util.data.exchange.{CarriedKind, MergeRules}
import com.histmersion.util.data.maintenance.{CacheHolding, CacheName, CacheSpec}

import com.augustnagro.magnum.DbCon

/** What the media says about the data it owns: its tables, what a bundle carries of it and how a merge treats it, the
  * thumbnails it keeps, and the check of the pictures' licences.
  */
object MediaData extends DataContribution:
  val owner: DataOwner = DataOwner("media")

  val tables: List[OwnedTable] = OwnedTable.all("assets", "asset_links", "media_links", "media_checks")

  /** The figures and their links. */
  override val carried: List[CarriedKind] = MediaExchange.carried

  /** How a merge treats figures and their links. */
  override val mergeRules: List[MergeRules] = MediaExchange.mergeRules

  override val caches: List[CacheSpec] = List(
    CacheSpec(owner, CacheName("thumbnails"), "picture thumbnails", CacheHolding.InTables(List(TableName("assets"))))
  )

  override val checks: List[CheckRule] = AssetsData.checks(owner)

  def figures(using DbCon): DataFigures = DataFigures.empty
