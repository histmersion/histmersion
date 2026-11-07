package com.histmersion.domain.catalogue.places

import com.histmersion.util.identity.AliasTable
import com.histmersion.api.aliases.AliasKinds

/** Where a place's old ids are kept: the alias table they are followed through. */
object PlaceAliasTable:
  val table: AliasTable = AliasTable(AliasKinds.PlaceRecords, com.histmersion.util.identity.AliasTable.Table("places"), com.histmersion.util.identity.AliasTable.IdColumn("id"), com.histmersion.util.identity.AliasTable.Value("place_aliases"), com.histmersion.util.identity.AliasTable.TargetColumn("place_id"))
