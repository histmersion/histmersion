package com.histmersion.util.data.backup

import com.histmersion.util.data.TableName

/** A table whose rows a restore refuses to drop unless forced: `noun` names one of them in that refusal (`source`). */
final case class GuardedTable(table: TableName, noun: GuardedTable.Noun)

object GuardedTable:
  type Noun = Noun.T
  object Noun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
