package com.histmersion.domain.catalogue.events

import com.histmersion.util.identity.{AliasKinds, AliasTable}

/** Where an event's old ids are kept: the alias table they are followed through. */
object EventAliasTable:
  val table: AliasTable = AliasTable(AliasKinds.EventRecords, "events", "id", "event_aliases", "event_id")
