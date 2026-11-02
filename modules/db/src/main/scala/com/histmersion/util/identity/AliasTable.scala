package com.histmersion.util.identity

import com.histmersion.util.database.{Param, Sql}

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** Where an owner keeps a kind's records and their old identifiers: the table and its identifier column, the alias
  * table and its column naming the record. ONE resolution of an old identifier for every kind, so the rule cannot
  * differ between them. The names are the owner's constants, never input.
  */
final case class AliasTable(kind: AliasKind, table: String, idColumn: String, aliasTable: String, targetColumn: String):

  /** The identifier an OLD one was renamed to — `None` when `id` is a live identifier, or names nothing. */
  def renamedTo(ds: DataSource, id: String): Option[String] = connect(ds)(renamedToIn(id))

  def renamedToIn(id: String)(using DbCon): Option[String] =
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
  def current(ds: DataSource, id: String): String =
    val t = id.trim
    if t.isEmpty then t else renamedTo(ds, t).getOrElse(t)
