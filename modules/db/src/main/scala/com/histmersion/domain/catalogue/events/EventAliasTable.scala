package com.histmersion.domain.catalogue.events

import com.histmersion.util.identity.AliasTable
import com.histmersion.api.aliases.AliasKinds

/** Where an event's old ids are kept: the alias table they are followed through. */
object EventAliasTable:
  val table: AliasTable = AliasTable(AliasKinds.EventRecords, com.histmersion.util.identity.AliasTable.Table("events"), com.histmersion.util.identity.AliasTable.IdColumn("id"), com.histmersion.util.identity.AliasTable.Value("event_aliases"), com.histmersion.util.identity.AliasTable.TargetColumn("event_id"))
