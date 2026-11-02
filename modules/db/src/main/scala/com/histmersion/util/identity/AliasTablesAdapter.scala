package com.histmersion.util.identity

import com.histmersion.api.AliasKindsAdapter

/** Today's alias tables, registered in one table until each owner registers its own (research registers its own): the
  * records' table and identifier column, and the alias table and its column naming the record, of each renamed kind.
  */
object AliasTablesAdapter:
  val SourceAliases: AliasTable =
    AliasTable(AliasKindsAdapter.SourceRecords, "sources", "citekey", "source_aliases", "citekey")
  val EventAliases: AliasTable = AliasTable(AliasKindsAdapter.EventRecords, "events", "id", "event_aliases", "event_id")
  val GoalAliases: AliasTable = AliasTable(AliasKindsAdapter.GoalRecords, "goals", "slug", "goal_aliases", "goal_slug")
  val PlaceAliases: AliasTable = AliasTable(AliasKindsAdapter.PlaceRecords, "places", "id", "place_aliases", "place_id")

  /** Today's tables. */
  val all: List[AliasTable] = List(SourceAliases, EventAliases, GoalAliases, PlaceAliases)

  /** Today's tables with the ones the owners that register their own hand in (research's), in the order of the kinds.
    */
  def withOwn(own: List[AliasTable]): AliasTables =
    val tables = all ++ own
    AliasTables(AliasKindsAdapter.all.flatMap(k => tables.find(_.kind == k)))
