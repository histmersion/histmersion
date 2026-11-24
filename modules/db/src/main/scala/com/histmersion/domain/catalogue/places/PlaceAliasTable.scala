package com.histmersion
package domain.catalogue.places

import com.histmersion.util.identity.AliasTable
import com.histmersion.api.aliases.AliasKinds
import com.histmersion.lint.{LintRule, tolerated}

/** Where a place's old ids are kept: the alias table they are followed through. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object PlaceAliasTable:
  val table: AliasTable = AliasTable(
    AliasKinds.PlaceRecords,
    AliasTable.Table("places"),
    AliasTable.IdColumn("id"),
    AliasTable.Value("place_aliases"),
    AliasTable.TargetColumn("place_id")
  )
