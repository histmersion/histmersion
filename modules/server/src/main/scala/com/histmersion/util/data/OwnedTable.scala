package com.histmersion
package util.data

import com.histmersion.lint.{tolerated, LintRule}

/** A table one owner holds: only its repositories write it, and data management sizes, vacuums and checks it as that
  * owner's.
  */
final case class OwnedTable(name: TableName)

object OwnedTable:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def all(names: String*): List[OwnedTable] = names.toList.map(n => OwnedTable(TableName(n)))
