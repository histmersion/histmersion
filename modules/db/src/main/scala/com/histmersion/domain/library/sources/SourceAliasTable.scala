package com.histmersion.domain.library.sources

import com.histmersion.util.identity.{AliasKinds, AliasTable}

/** Where a source's old citekeys are kept: the alias table they are followed through. */
object SourceAliasTable:
  val table: AliasTable = AliasTable(AliasKinds.SourceRecords, "sources", "citekey", "source_aliases", "citekey")
