package com.histmersion.domain.catalogue

import com.histmersion.domain.catalogue.events.EventsData
import com.histmersion.domain.catalogue.periods.PeriodsData
import com.histmersion.domain.catalogue.places.PlacesData
import com.histmersion.util.data.{DataContribution, DataOwner, OwnedTable, TableName}
import com.histmersion.util.data.checks.CheckRule
import com.histmersion.util.data.diagnostics.DataFigures
import com.histmersion.util.data.exchange.{CarriedKind, CarriedKindsAdapter, MergeRules, MergeRulesAdapter}
import com.histmersion.util.data.maintenance.{CacheEvent, CacheHolding, CacheName, CacheSpec}
import com.histmersion.util.storage.{Location, StoreKey}
import com.augustnagro.magnum.DbCon

/** What the catalogue says about the data it owns: its tables, the gazetteer's stores and the base map's, the caches of
  * the map, its counts and its checks. Its carried kinds and merge rules are still the data management's per-kind code
  * (the adapters slice 6 registered), until the catalogue carries and merges its own.
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

  override val carried: List[CarriedKind] = CarriedKindsAdapter.of(owner)
  override val mergeRules: List[MergeRules] = MergeRulesAdapter.of(owner)

  /** The base map's tiles read on demand: the relief deeper, the map and the sea floor outside the focus region. */
  private def onDemand(directory: String): CacheHolding =
    CacheHolding.InStore(Location.Basemap, StoreKey.applyUnsafe(directory))

  override val caches: List[CacheSpec] = List(
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

  override val checks: List[CheckRule] = PlacesData.checks(owner) ++ EventsData.checks(owner)

  def figures(using DbCon): DataFigures =
    DataFigures(EventsData.figures ++ PlacesData.figures ++ PeriodsData.figures)
