package com.histmersion.domain.research.articles

import com.histmersion.domain.ItemKind
import com.histmersion.util.database.Transactor
import com.histmersion.util.identity.{AliasColumns, RecordTable, TableReferable}

/** Articles as others point at them: by slug (and the slugs a rename left), a uid that survives renames. */
final class ArticleReferable(db: Transactor) extends TableReferable(db, List(ArticleReferable.Table))

object ArticleReferable:
  val Kind: ItemKind = ItemKind("article")

  val Table: RecordTable =
    RecordTable(
      Kind,
      com.histmersion.util.identity.RecordTable.Table("articles"),
      com.histmersion.util.identity.RecordTable.Key("t.slug"),
      com.histmersion.util.identity.RecordTable.Title("t.title"),
      Some(com.histmersion.util.identity.RecordTable.Uid("t.uid")),
      Some(AliasColumns(com.histmersion.util.identity.AliasColumns.Table("article_aliases"), com.histmersion.util.identity.AliasColumns.Alias("alias"), com.histmersion.util.identity.AliasColumns.Target("slug")))
    )
