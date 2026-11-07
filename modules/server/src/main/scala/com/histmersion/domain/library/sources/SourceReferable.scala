package com.histmersion.domain.library.sources

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
      com.histmersion.util.identity.RecordTable.Table("sources"),
      com.histmersion.util.identity.RecordTable.Key("t.citekey"),
      com.histmersion.util.identity.RecordTable.Title("t.title"),
      Some(com.histmersion.util.identity.RecordTable.Uid("t.uid")),
      Some(AliasColumns(com.histmersion.util.identity.AliasColumns.Table("source_aliases"), com.histmersion.util.identity.AliasColumns.Alias("alias"), com.histmersion.util.identity.AliasColumns.Target("citekey")))
    )
