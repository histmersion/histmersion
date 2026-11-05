package com.histmersion.util.identity

/** An owner's alias table: the old key a rename left (`alias`) and the key it stands for now (`target`). */
final case class AliasColumns(table: String, alias: String, target: String)
