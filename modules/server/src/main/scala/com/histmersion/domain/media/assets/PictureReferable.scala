package com.histmersion.domain.media.assets

import com.histmersion.domain.ItemKind
import com.histmersion.util.database.Transactor
import com.histmersion.util.identity.{RecordTable, TableReferable}

/** Pictures as others point at them: by the asset's uid, which is also its key; shown by its caption. */
final class PictureReferable(db: Transactor) extends TableReferable(db, List(PictureReferable.Table))

object PictureReferable:
  val Kind: ItemKind = ItemKind("picture")

  val Table: RecordTable = RecordTable(Kind, com.histmersion.util.identity.RecordTable.Table("assets"), com.histmersion.util.identity.RecordTable.Key("t.uid::text"), com.histmersion.util.identity.RecordTable.Title("t.caption"), Some(com.histmersion.util.identity.RecordTable.Uid("t.uid")), None)
