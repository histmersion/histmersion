package com.histmersion
package domain.research.articles

import com.histmersion.util.identity.AliasTable
import com.histmersion.api.aliases.AliasKinds

/** Where an article's old slugs are kept: the alias table its old slugs are followed through. */
object ArticleAliasTable:
  val table: AliasTable = AliasTable(AliasKinds.ArticleRecords, AliasTable.Table("articles"), AliasTable.IdColumn("slug"), AliasTable.Value("article_aliases"), AliasTable.TargetColumn("slug"))
