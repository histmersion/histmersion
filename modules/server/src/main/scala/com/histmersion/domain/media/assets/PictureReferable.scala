package com.histmersion
package domain.media.assets

import com.histmersion.domain.ItemKind
import com.histmersion.util.database.Transactor
import com.histmersion.util.identity.{RecordTable, TableReferable}

/** Pictures as others point at them: by the asset's uid, which is also its key; shown by its caption. */
final class PictureReferable(db: Transactor) extends TableReferable(db, List(PictureReferable.Table))

object PictureReferable:
  val Kind: ItemKind = ItemKind("picture")

  val Table: RecordTable = RecordTable(
    Kind,
    RecordTable.Table("assets"),
    RecordTable.Key("t.uid::text"),
    RecordTable.Title("t.caption"),
    Some(RecordTable.Uid("t.uid")),
    None
  )
