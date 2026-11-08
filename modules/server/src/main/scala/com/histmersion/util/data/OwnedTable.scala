package com.histmersion
package util.data

/** A table one owner holds: only its repositories write it, and data management sizes, vacuums and checks it as that
  * owner's.
  */
final case class OwnedTable(name: TableName)

object OwnedTable:
  def all(names: String*): List[OwnedTable] = names.toList.map(n => OwnedTable(TableName(n)))
