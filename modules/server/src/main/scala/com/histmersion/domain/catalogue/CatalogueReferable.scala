package com.histmersion.domain.catalogue

import com.histmersion.domain.ItemKind
import com.histmersion.util.database.Transactor
import com.histmersion.util.identity.{AliasColumns, RecordTable, TableReferable}

/** Events and places as others point at them: an event by its id and uid, a place by its id; both by the ids a
  * rename left.
  */
final class CatalogueReferable(db: Transactor)
    extends TableReferable(db, List(CatalogueReferable.Events, CatalogueReferable.Places))

object CatalogueReferable:
  val EventKind: ItemKind = ItemKind("event")
  val PlaceKind: ItemKind = ItemKind("place")

  val Events: RecordTable =
    RecordTable(EventKind, "events", "t.id", "t.label", Some("t.uid"), Some(AliasColumns("event_aliases", "alias", "event_id")))

  val Places: RecordTable =
    RecordTable(PlaceKind, "places", "t.id", "t.label", None, Some(AliasColumns("place_aliases", "alias", "place_id")))
