package com.histmersion.domain.research.articles

import com.histmersion.util.identity.AliasTable
import com.histmersion.api.aliases.AliasKinds

/** Where an article's old slugs are kept: the alias table its old slugs are followed through. */
object ArticleAliasTable:
  val table: AliasTable = AliasTable(AliasKinds.ArticleRecords, com.histmersion.util.identity.AliasTable.Table("articles"), com.histmersion.util.identity.AliasTable.IdColumn("slug"), com.histmersion.util.identity.AliasTable.Value("article_aliases"), com.histmersion.util.identity.AliasTable.TargetColumn("slug"))
