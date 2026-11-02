package com.histmersion.domain.catalogue.places

import com.histmersion.util.identity.{AliasKinds, AliasTable}

/** Where a place's old ids are kept: the alias table they are followed through. */
object PlaceAliasTable:
  val table: AliasTable = AliasTable(AliasKinds.PlaceRecords, "places", "id", "place_aliases", "place_id")
