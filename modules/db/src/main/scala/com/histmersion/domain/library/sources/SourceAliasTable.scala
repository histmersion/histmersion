package com.histmersion
package domain.library.sources

import com.histmersion.util.identity.AliasTable
import com.histmersion.api.aliases.AliasKinds
import com.histmersion.lint.{LintRule, tolerated}

/** Where a source's old citekeys are kept: the alias table they are followed through. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object SourceAliasTable:
  val table: AliasTable = AliasTable(
    AliasKinds.SourceRecords,
    AliasTable.Table("sources"),
    AliasTable.IdColumn("citekey"),
    AliasTable.Value("source_aliases"),
    AliasTable.TargetColumn("citekey")
  )
