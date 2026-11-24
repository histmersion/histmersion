package com.histmersion
package domain.catalogue.events

import com.histmersion.util.identity.AliasTable
import com.histmersion.api.aliases.AliasKinds
import com.histmersion.lint.{LintRule, tolerated}

/** Where an event's old ids are kept: the alias table they are followed through. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object EventAliasTable:
  val table: AliasTable = AliasTable(
    AliasKinds.EventRecords,
    AliasTable.Table("events"),
    AliasTable.IdColumn("id"),
    AliasTable.Value("event_aliases"),
    AliasTable.TargetColumn("event_id")
  )
