package com.histmersion.util.identity

import com.histmersion.api.AliasKindsAdapter

/** Today's alias tables, registered in one table until each owner registers its own: the records' table and identifier
  * column, and the alias table and its column naming the record, of each renamed kind.
  */
object AliasTablesAdapter:
  val SourceAliases: AliasTable =
    AliasTable(AliasKindsAdapter.SourceRecords, "sources", "citekey", "source_aliases", "citekey")
  val ArticleAliases: AliasTable =
    AliasTable(AliasKindsAdapter.ArticleRecords, "articles", "slug", "article_aliases", "slug")
  val EventAliases: AliasTable = AliasTable(AliasKindsAdapter.EventRecords, "events", "id", "event_aliases", "event_id")
  val GoalAliases: AliasTable = AliasTable(AliasKindsAdapter.GoalRecords, "goals", "slug", "goal_aliases", "goal_slug")
  val PlaceAliases: AliasTable = AliasTable(AliasKindsAdapter.PlaceRecords, "places", "id", "place_aliases", "place_id")

  val all: AliasTables = AliasTables(List(SourceAliases, ArticleAliases, EventAliases, GoalAliases, PlaceAliases))
