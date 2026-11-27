package com.histmersion
package domain.automation.goals

import com.histmersion.util.identity.AliasTable
import com.histmersion.api.aliases.AliasKinds
import com.histmersion.lint.{tolerated, LintRule}

/** Where a goal's old slugs are kept: the alias table they are followed through. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object GoalAliasTable:
  val table: AliasTable = AliasTable(
    AliasKinds.GoalRecords,
    AliasTable.Table("goals"),
    AliasTable.IdColumn("slug"),
    AliasTable.Value("goal_aliases"),
    AliasTable.TargetColumn("goal_slug")
  )
