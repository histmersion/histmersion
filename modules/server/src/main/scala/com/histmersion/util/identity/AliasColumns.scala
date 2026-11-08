package com.histmersion
package util.identity

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** An owner's alias table: the old key a rename left (`alias`) and the key it stands for now (`target`). */
final case class AliasColumns(table: AliasColumns.Table, alias: AliasColumns.Alias, target: AliasColumns.Target)

object AliasColumns:
  type Table = Table.T
  object Table extends RefinedType[String, Pure]
  type Alias = Alias.T
  object Alias extends RefinedType[String, Pure]
  type Target = Target.T
  object Target extends RefinedType[String, Pure]
