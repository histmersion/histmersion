package com.histmersion
package domain.catalogue.places

import com.histmersion.util.identity.AliasTable
import com.histmersion.api.aliases.AliasKinds

/** Where a place's old ids are kept: the alias table they are followed through. */
object PlaceAliasTable:
  val table: AliasTable = AliasTable(
    AliasKinds.PlaceRecords,
    AliasTable.Table("places"),
    AliasTable.IdColumn("id"),
    AliasTable.Value("place_aliases"),
    AliasTable.TargetColumn("place_id")
  )
