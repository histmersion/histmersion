package com.histmersion.util.identity

/** An owner's alias table: the old key a rename left (`alias`) and the key it stands for now (`target`). */
final case class AliasColumns(table: AliasColumns.Table, alias: AliasColumns.Alias, target: AliasColumns.Target)

object AliasColumns:
  type Table = Table.T
  object Table extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Alias = Alias.T
  object Alias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Target = Target.T
  object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
