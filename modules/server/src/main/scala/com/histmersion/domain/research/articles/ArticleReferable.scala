package com.histmersion
package domain.research.articles

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
      RecordTable.Table("articles"),
      RecordTable.Key("t.slug"),
      RecordTable.Title("t.title"),
      Some(RecordTable.Uid("t.uid")),
      Some(
        AliasColumns(AliasColumns.Table("article_aliases"), AliasColumns.Alias("alias"), AliasColumns.Target("slug"))
      )
    )
