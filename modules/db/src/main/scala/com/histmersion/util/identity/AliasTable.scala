package com.histmersion.util.identity

import com.histmersion.util.database.{Param, Sql}

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.api.aliases.AliasKind

/** Where an owner keeps a kind's records and their old identifiers: the table and its identifier column, the alias
  * table and its column naming the record. ONE resolution of an old identifier for every kind, so the rule cannot
  * differ between them. The names are the owner's constants, never input.
  */
final case class AliasTable(kind: AliasKind, table: AliasTable.Table, idColumn: AliasTable.IdColumn, aliasTable: AliasTable.Value, targetColumn: AliasTable.TargetColumn):

  /** The identifier an OLD one was renamed to — `None` when `id` is a live identifier, or names nothing. */
  def renamedTo(ds: DataSource, id: com.histmersion.util.identity.AliasTable.RenamedToId): Option[com.histmersion.util.identity.AliasTable.RenamedToResult] = connect(ds)(renamedToIn(id))

  def renamedToIn(id: com.histmersion.util.identity.AliasTable.RenamedToInId)(using DbCon): Option[com.histmersion.util.identity.AliasTable.RenamedToInResult] =
    Sql
      .of(
        s"SELECT x.$targetColumn FROM $aliasTable x WHERE x.alias = ? AND NOT EXISTS " +
          s"(SELECT 1 FROM $table r WHERE r.$idColumn = ?)",
        Param.S(id),
        Param.S(id)
      )
      .frag
      .query[String]
      .run()
      .headOption

  /** The identifier a record answers to NOW: the one an old `id` was renamed to, else `id` itself. */
  def current(ds: DataSource, id: com.histmersion.util.identity.AliasTable.CurrentId): com.histmersion.util.identity.AliasTable.CurrentResult =
    val t = id.trim
    if t.isEmpty then t else renamedTo(ds, t).getOrElse(t)

object AliasTable:
  type RenamedToResult = RenamedToResult.T
  object RenamedToResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenamedToId = RenamedToId.T
  object RenamedToId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenamedToInResult = RenamedToInResult.T
  object RenamedToInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenamedToInId = RenamedToInId.T
  object RenamedToInId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CurrentResult = CurrentResult.T
  object CurrentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CurrentId = CurrentId.T
  object CurrentId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Table = Table.T
  object Table extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdColumn = IdColumn.T
  object IdColumn extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Value = Value.T
  object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetColumn = TargetColumn.T
  object TargetColumn extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
