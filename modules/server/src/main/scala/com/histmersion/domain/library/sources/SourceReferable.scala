package com.histmersion
package domain.library.sources

import com.histmersion.domain.ItemKind
import com.histmersion.util.database.Transactor
import com.histmersion.util.identity.{AliasColumns, RecordTable, TableReferable}

/** Sources as others point at them: by citekey (and the citekeys a rename left), a uid that survives renames. */
final class SourceReferable(db: Transactor) extends TableReferable(db, List(SourceReferable.Table))

object SourceReferable:
  val Kind: ItemKind = ItemKind("source")

  val Table: RecordTable =
    RecordTable(
      Kind,
      RecordTable.Table("sources"),
      RecordTable.Key("t.citekey"),
      RecordTable.Title("t.title"),
      Some(RecordTable.Uid("t.uid")),
      Some(AliasColumns(AliasColumns.Table("source_aliases"), AliasColumns.Alias("alias"), AliasColumns.Target("citekey")))
    )
