package com.histmersion.domain.automation.goals

import com.histmersion.util.identity.{AliasKinds, AliasTable}

/** Where a goal's old slugs are kept: the alias table they are followed through. */
object GoalAliasTable:
  val table: AliasTable = AliasTable(AliasKinds.GoalRecords, "goals", "slug", "goal_aliases", "goal_slug")
