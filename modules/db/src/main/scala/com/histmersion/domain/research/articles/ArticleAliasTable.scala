package com.histmersion.domain.research.articles

import com.histmersion.util.identity.{AliasKinds, AliasTable}

/** Where an article's old slugs are kept: the alias table its old slugs are followed through. */
object ArticleAliasTable:
  val table: AliasTable = AliasTable(AliasKinds.ArticleRecords, "articles", "slug", "article_aliases", "slug")
