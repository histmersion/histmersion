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
      "sources",
      "t.citekey",
      "t.title",
      Some("t.uid"),
      Some(AliasColumns("source_aliases", "alias", "citekey"))
    )
