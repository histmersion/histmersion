package com.histmersion
package domain.research.articles

import com.histmersion.util.identity.AliasTable
import com.histmersion.api.aliases.AliasKinds
import com.histmersion.lint.{LintRule, tolerated}

/** Where an article's old slugs are kept: the alias table its old slugs are followed through. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object ArticleAliasTable:
  val table: AliasTable = AliasTable(
    AliasKinds.ArticleRecords,
    AliasTable.Table("articles"),
    AliasTable.IdColumn("slug"),
    AliasTable.Value("article_aliases"),
    AliasTable.TargetColumn("slug")
  )
