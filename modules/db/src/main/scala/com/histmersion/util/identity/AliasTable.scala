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
  def renamedTo(ds: DataSource, id: com.histmersion.util.identity.AliasTable.Id): Option[com.histmersion.util.identity.AliasTable.Id] = connect(ds)(renamedToIn(id))

  def renamedToIn(id: com.histmersion.util.identity.AliasTable.Id)(using DbCon): Option[com.histmersion.util.identity.AliasTable.Id] =
    Sql
      .of(
        Sql.Text(
          s"SELECT x.${targetColumn.value} FROM ${aliasTable.value} x WHERE x.alias = ? AND NOT EXISTS " +
            s"(SELECT 1 FROM ${table.value} r WHERE r.${idColumn.value} = ?)"
        ),
        Param.S(Param.S.V(id.value)),
        Param.S(Param.S.V(id.value))
      )
      .frag
      .query[String]
      .run()
      .headOption
      .map(AliasTable.Id(_))

  /** The identifier a record answers to NOW: the one an old `id` was renamed to, else `id` itself. */
  def current(ds: DataSource, id: com.histmersion.util.identity.AliasTable.Id): com.histmersion.util.identity.AliasTable.Id =
    val t = AliasTable.Id(id.value.trim)
    if t.value.isEmpty then t else renamedTo(ds, t).getOrElse(t)

object AliasTable:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Table = Table.T
  object Table extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdColumn = IdColumn.T
  object IdColumn extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Value = Value.T
  object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetColumn = TargetColumn.T
  object TargetColumn extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
