package com.histmersion.domain.automation.goals

import com.histmersion.util.identity.AliasTable
import com.histmersion.api.aliases.AliasKinds

/** Where a goal's old slugs are kept: the alias table they are followed through. */
object GoalAliasTable:
  val table: AliasTable = AliasTable(AliasKinds.GoalRecords, com.histmersion.util.identity.AliasTable.Table("goals"), com.histmersion.util.identity.AliasTable.IdColumn("slug"), com.histmersion.util.identity.AliasTable.Value("goal_aliases"), com.histmersion.util.identity.AliasTable.TargetColumn("goal_slug"))
