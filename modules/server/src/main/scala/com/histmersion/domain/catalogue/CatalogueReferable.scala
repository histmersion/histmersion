package com.histmersion
package domain.catalogue

import com.histmersion.domain.ItemKind
import com.histmersion.util.database.Transactor
import com.histmersion.util.identity.{AliasColumns, RecordTable, TableReferable}
import com.augustnagro.magnum.sql

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
      RecordTable.Table("events"),
      RecordTable.Key("t.id"),
      RecordTable.Title("t.label"),
      Some(RecordTable.Uid("t.uid")),
      Some(
        AliasColumns(sql"event_aliases", sql"alias", sql"event_id")
      )
    )

  val Places: RecordTable =
    RecordTable(
      PlaceKind,
      RecordTable.Table("places"),
      RecordTable.Key("t.id"),
      RecordTable.Title("t.label"),
      None,
      Some(
        AliasColumns(sql"place_aliases", sql"alias", sql"place_id")
      )
    )
