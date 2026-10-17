package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** Where each [[AliasKind]]'s rows and old identifiers live (#1799): ONE resolution of an old
  * identifier for every kind, so the rule ([[Aliases]]) cannot differ between them. */
object AliasRepo:

  /** The kind's table and identifier column, and its alias table and the column naming the record. */
  final case class Tables(table: String, idCol: String, aliasTable: String, targetCol: String)

  def tables(k: AliasKind): Tables = k match
    case AliasKind.Source  => Tables("sources", "citekey", "source_aliases", "citekey")
    case AliasKind.Article => Tables("articles", "slug", "article_aliases", "slug")
    case AliasKind.Event   => Tables("events", "id", "event_aliases", "event_id")
    case AliasKind.Goal    => Tables("goals", "slug", "goal_aliases", "goal_slug")
    case AliasKind.Place   => Tables("places", "id", "place_aliases", "place_id")

  /** The identifier an OLD one was renamed to — `None` when `id` is a live identifier, or names nothing. */
  def renamedTo(ds: DataSource, k: AliasKind, id: String): Option[String] =
    connect(ds)(renamedToIn(k, id))

  private[db] def renamedToIn(k: AliasKind, id: String)(using DbCon): Option[String] =
    val t = tables(k)
    Sql.of(s"SELECT x.${t.targetCol} FROM ${t.aliasTable} x WHERE x.alias = ? AND NOT EXISTS " +
        s"(SELECT 1 FROM ${t.table} r WHERE r.${t.idCol} = ?)", Param.S(id), Param.S(id))
      .frag.query[String].run().headOption

  /** The identifier a record answers to NOW: the one an old `id` was renamed to, else `id` itself. */
  def current(ds: DataSource, k: AliasKind, id: String): String =
    val t = id.trim
    if t.isEmpty then t else renamedTo(ds, k, t).getOrElse(t)
