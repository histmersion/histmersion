package com.histmersion
package util.identity

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.api.aliases.AliasKind
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.NewtypeColumns.given

/** Where an owner keeps a kind's records and their old identifiers: the table and its identifier column, the alias
  * table and its column naming the record. ONE resolution of an old identifier for every kind, so the rule cannot
  * differ between them. The names are the owner's constants, never input.
  */
final case class AliasTable(
    kind: AliasKind,
    table: AliasTable.Table,
    idColumn: AliasTable.IdColumn,
    aliasTable: AliasTable.Value,
    targetColumn: AliasTable.TargetColumn
):

  /** The identifier an OLD one was renamed to — `None` when `id` is a live identifier, or names nothing. */
  def renamedTo(ds: DataSource, id: AliasTable.Id): Option[AliasTable.Id] = connect(ds)(renamedToIn(id))

  def renamedToIn(id: AliasTable.Id)(using DbCon): Option[AliasTable.Id] =
    sql"SELECT x.${SqlLiteral(targetColumn.value)} FROM ${SqlLiteral(aliasTable.value)} x WHERE x.alias = $id AND NOT EXISTS (SELECT 1 FROM ${SqlLiteral(table.value)} r WHERE r.${SqlLiteral(idColumn.value)} = $id)"
      .query[String]
      .run()
      .headOption
      .map(AliasTable.Id(_))

  /** The identifier a record answers to NOW: the one an old `id` was renamed to, else `id` itself. */
  def current(ds: DataSource, id: AliasTable.Id): AliasTable.Id =
    val t = AliasTable.Id(id.value.trim)
    if t.value.isEmpty then t else renamedTo(ds, t).getOrElse(t)

object AliasTable:
  type Id = Id.T
  object Id extends Newtype[String, Pure]

  type Table = Table.T
  object Table extends Newtype[String, Pure]
  type IdColumn = IdColumn.T
  object IdColumn extends Newtype[String, Pure]
  type Value = Value.T
  object Value extends Newtype[String, Pure]
  type TargetColumn = TargetColumn.T
  object TargetColumn extends Newtype[String, Pure]
