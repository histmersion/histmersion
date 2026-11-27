package com.histmersion
package domain.catalogue

import com.histmersion.domain.catalogue.events.EventsData
import com.histmersion.domain.catalogue.periods.PeriodsData
import com.histmersion.domain.catalogue.places.PlacesData
import com.histmersion.util.data.{DataContribution, DataOwner, OwnedTable, TableName}
import com.histmersion.util.data.checks.CheckRule
import com.histmersion.util.data.diagnostics.DataFigures
import com.histmersion.util.data.exchange.{CarriedKind, MergeRules}
import com.histmersion.util.data.maintenance.{CacheEvent, CacheHolding, CacheName, CacheSpec}
import com.histmersion.util.storage.Location
import com.augustnagro.magnum.DbCon
import com.histmersion.util.storage.Store
import com.histmersion.lint.{tolerated, LintRule}

/** What the catalogue says about the data it owns: its tables, the gazetteer's stores and the base map's, the caches of
  * the map, what a bundle carries of it and how a merge treats it, its counts and its checks.
  */
object CatalogueData extends DataContribution:

  val owner: DataOwner = DataOwner("catalogue")

  val tables: List[OwnedTable] = OwnedTable.all(
    "places",
    "place_aliases",
    "place_descriptions",
    "place_footprints",
    "place_links",
    "place_names",
    "place_overrides",
    "place_relations",
    "place_removals",
    "place_types",
    "gazetteer_sources",
    "land",
    "periods",
    "seed_versions",
    "events",
    "event_aliases",
    "event_catalogue",
    "event_datings",
    "event_edges",
    "event_import_parts",
    "event_link_candidates",
    "event_link_checks",
    "event_participants",
    "corrections",
    "attestations",
    "site_undated_build",
    "site_undated_cells",
    "map_changes"
  )

  /** The pinned gazetteer extracts, the folder older installs kept them in, the drop folder, the base map. */
  override val stores: List[Location] =
    List(Location.GazetteerExtracts, Location.GazetteerFolder, Location.GazetteerDrop, Location.Basemap)

  /** The places, periods, events and evidence a bundle carries. */
  override val carried: List[CarriedKind] = CatalogueExchange.carried

  /** How a merge treats places, periods, events and attestations. */
  override val mergeRules: List[MergeRules] = CatalogueExchange.mergeRules

  /** The base map's tiles read on demand: the relief deeper, the map and the sea floor outside the focus region. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def onDemand(directory: String): CacheHolding =
    CacheHolding.InStore(Location.Basemap, Store.Key.applyUnsafe(directory))

  override val caches: List[CacheSpec] = List(
    CacheSpec(
      owner,
      CacheName("map-tiles"),
      CacheSpec.Label("map tiles"),
      CacheHolding.InMemory,
      List(CacheEvent.Restored)
    ),
    CacheSpec(
      owner,
      CacheName("undated-cells"),
      CacheSpec.Label("undated sites by cell"),
      CacheHolding.InTables(List(TableName("site_undated_cells"), TableName("site_undated_build")))
    ),
    CacheSpec(owner, CacheName("relief-cache"), CacheSpec.Label("relief cache"), onDemand("terrain-cache")),
    CacheSpec(owner, CacheName("map-cache"), CacheSpec.Label("map cache"), onDemand("vector-cache")),
    CacheSpec(owner, CacheName("sea-floor-cache"), CacheSpec.Label("sea-floor cache"), onDemand("sea-cache"))
  )

  override val checks: List[CheckRule] = PlacesData.checks(owner) ++ EventsData.checks(owner)

  def figures(using DbCon): DataFigures =
    DataFigures(EventsData.figures ++ PlacesData.figures ++ PeriodsData.figures)
