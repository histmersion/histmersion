package com.histmersion.domain.catalogue

import com.histmersion.domain.ItemKind
import com.histmersion.util.database.Transactor
import com.histmersion.util.identity.{AliasColumns, RecordTable, TableReferable}

/** Events and places as others point at them: an event by its id and uid, a place by its id; both by the ids a rename
  * left.
  */
final class CatalogueReferable(db: Transactor)
    extends TableReferable(db, List(CatalogueReferable.Events, CatalogueReferable.Places))

object CatalogueReferable:
  val EventKind: ItemKind = ItemKind("event")
  val PlaceKind: ItemKind = ItemKind("place")

  val Events: RecordTable =
    RecordTable(
      EventKind,
      com.histmersion.util.identity.RecordTable.Table("events"),
      com.histmersion.util.identity.RecordTable.Key("t.id"),
      com.histmersion.util.identity.RecordTable.Title("t.label"),
      Some(com.histmersion.util.identity.RecordTable.Uid("t.uid")),
      Some(AliasColumns(com.histmersion.util.identity.AliasColumns.Table("event_aliases"), com.histmersion.util.identity.AliasColumns.Alias("alias"), com.histmersion.util.identity.AliasColumns.Target("event_id")))
    )

  val Places: RecordTable =
    RecordTable(PlaceKind, com.histmersion.util.identity.RecordTable.Table("places"), com.histmersion.util.identity.RecordTable.Key("t.id"), com.histmersion.util.identity.RecordTable.Title("t.label"), None, Some(AliasColumns(com.histmersion.util.identity.AliasColumns.Table("place_aliases"), com.histmersion.util.identity.AliasColumns.Alias("alias"), com.histmersion.util.identity.AliasColumns.Target("place_id"))))
