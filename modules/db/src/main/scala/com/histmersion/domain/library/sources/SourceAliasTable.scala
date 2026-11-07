package com.histmersion.domain.library.sources

import com.histmersion.util.identity.AliasTable
import com.histmersion.api.aliases.AliasKinds

/** Where a source's old citekeys are kept: the alias table they are followed through. */
object SourceAliasTable:
  val table: AliasTable = AliasTable(AliasKinds.SourceRecords, com.histmersion.util.identity.AliasTable.Table("sources"), com.histmersion.util.identity.AliasTable.IdColumn("citekey"), com.histmersion.util.identity.AliasTable.Value("source_aliases"), com.histmersion.util.identity.AliasTable.TargetColumn("citekey"))
