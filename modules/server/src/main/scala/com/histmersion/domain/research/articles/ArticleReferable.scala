package com.histmersion.domain.research.articles

import com.histmersion.domain.ItemKind
import com.histmersion.util.database.Transactor
import com.histmersion.util.identity.{AliasColumns, RecordTable, TableReferable}

/** Articles as others point at them: by slug (and the slugs a rename left), a uid that survives renames. */
final class ArticleReferable(db: Transactor) extends TableReferable(db, List(ArticleReferable.Table))

object ArticleReferable:
  val Kind: ItemKind = ItemKind("article")

  val Table: RecordTable =
    RecordTable(Kind, "articles", "t.slug", "t.title", Some("t.uid"), Some(AliasColumns("article_aliases", "alias", "slug")))
