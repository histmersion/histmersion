package com.histmersion
package util.data.backup

import com.histmersion.util.data.TableName
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A table whose rows a restore refuses to drop unless forced: `noun` names one of them in that refusal (`source`). */
final case class GuardedTable(table: TableName, noun: GuardedTable.Noun)

object GuardedTable:
  type Noun = Noun.T
  object Noun extends RefinedType[String, Pure]
