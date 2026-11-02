package com.histmersion.domain.research.articles

import com.histmersion.api.AliasKindsAdapter
import com.histmersion.util.identity.AliasTable

/** Where an article's old slugs are kept: the alias table its old slugs are followed through. */
object ArticleAliasTable:
  val table: AliasTable = AliasTable(AliasKindsAdapter.ArticleRecords, "articles", "slug", "article_aliases", "slug")
