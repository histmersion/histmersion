package com.histmersion.util.identity

import com.histmersion.util.database.{Param, Sql}
import com.histmersion.domain.{Described, ItemKey, ItemKind, ItemRef, ItemUid, RecordState}
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.Title

import com.augustnagro.magnum.*

import java.time.Instant

/** How an owner's table names its records, so that references to them are resolved and described in one place: the
  * table (aliased `t` in the expressions), the expression of a record's key and title, its uid column where the kind
  * has uids, and its alias table where renames leave one. A uid that is gone is followed through the merges' redirects
  * (`uid_aliases`) and then the tombstones. Takes the connection it runs on; the names are the owner's constants, never
  * input.
  */
final case class RecordTable(
    kind: ItemKind,
    table: RecordTable.Table,
    key: RecordTable.Key,
    title: RecordTable.Title,
    uid: Option[RecordTable.Uid],
    aliases: Option[AliasColumns]
):
  private val uidCol = uid.fold("NULL::uuid")(_.value)
  private val keyCol = key.value
  private val titleCol = title.value
  private val tableName = table.value

  private def byKeys(keys: List[ItemKey])(using DbCon): List[(ItemKey, Option[ItemUid], Option[Title])] =
    if keys.isEmpty then Nil
    else
      Sql
        .of(
          Sql.Text(s"SELECT $keyCol, $uidCol, $titleCol FROM $tableName t WHERE $keyCol = ANY(?::text[])"),
          Param.A(keys.map(k => Param.A.V(k.value)))
        )
        .frag
        .query[(ItemKey, Option[ItemUid], Option[Title])]
        .run()
        .toList

  /** Old keys → (the key now, uid, title). */
  private def byAliases(keys: List[ItemKey])(using DbCon): Map[ItemKey, (ItemKey, Option[ItemUid], Option[Title])] =
    aliases.filter(_ => keys.nonEmpty).fold(Map.empty) { a =>
      Sql
        .of(
          Sql.Text(
            s"""SELECT a.${a.alias}, $keyCol, $uidCol, $titleCol FROM ${a.table} a JOIN $tableName t ON $keyCol = a.${a.target}
              WHERE a.${a.alias} = ANY(?::text[])"""
          ),
          Param.A(keys.map(k => Param.A.V(k.value)))
        )
        .frag
        .query[(ItemKey, ItemKey, Option[ItemUid], Option[Title])]
        .run()
        .map((old, now, u, t) => old -> (now, u, t))
        .toMap
    }

  private def byUids(uids: Set[ItemUid])(using DbCon): Map[ItemUid, (ItemKey, Option[Title])] =
    uid.filter(_ => uids.nonEmpty).fold(Map.empty) { col =>
      Sql
        .of(
          Sql.Text(s"SELECT ${col.value}, $keyCol, $titleCol FROM $tableName t WHERE ${col.value} = ANY(?::uuid[])"),
          Param.A(uids.toList.map(u => Param.A.V(u.toString)))
        )
        .frag
        .query[(ItemUid, ItemKey, Option[Title])]
        .run()
        .map((u, k, t) => u -> (k, t))
        .toMap
    }

  /** Merged-away uid → the uid that answers for it now, through every merge since. */
  private def redirects(uids: Set[ItemUid])(using DbCon): Map[ItemUid, ItemUid] =
    if uids.isEmpty then Map.empty
    else
      Sql
        .of(
          Sql.Text(
            s"""WITH RECURSIVE r(start, uid, hops) AS (
                SELECT alias_uid, uid, 1 FROM uid_aliases WHERE alias_uid = ANY(?::uuid[])
                UNION ALL
                SELECT r.start, a.uid, r.hops + 1 FROM r JOIN uid_aliases a ON a.alias_uid = r.uid
                 WHERE r.hops < ${RecordTable.MaxHops})
              SELECT DISTINCT ON (start) start, uid FROM r ORDER BY start, hops DESC"""
          ),
          Param.A(uids.toList.map(u => Param.A.V(u.toString)))
        )
        .frag
        .query[(ItemUid, ItemUid)]
        .run()
        .toMap

  private def tombstones(uids: Set[ItemUid])(using DbCon): Map[ItemUid, Instant] =
    if uids.isEmpty then Map.empty
    else
      Sql
        .of(
          Sql.Text(
            s"""SELECT uid, to_char(deleted_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') FROM tombstones
              WHERE uid = ANY(?::uuid[])"""
          ),
          Param.A(uids.toList.map(u => Param.A.V(u.toString)))
        )
        .frag
        .query[(ItemUid, String)]
        .run()
        .map((u, at) => u -> Instant.parse(at))
        .toMap

  def resolve(k: ItemKey)(using DbCon): Either[com.histmersion.util.identity.RecordTable.Problem, ItemRef] =
    byKeys(List(k)).headOption
      .map((_, u, _) => ItemRef(kind, k, u))
      .orElse(byAliases(List(k)).get(k).map((_, u, _) => ItemRef(kind, k, u)))
      .toRight(RecordTable.Problem(s"no ${kind.value} '${k.value}'"))

  def describe(refs: Set[ItemRef])(using DbCon): Map[ItemRef, Described] =
    val (withUid, withoutUid) = refs.filter(_.kind == kind).partition(_.uid.isDefined)
    val uids = withUid.flatMap(_.uid)
    val live = byUids(uids)
    val gone = uids -- live.keySet
    val redirected = redirects(gone)
    val survivors = byUids(redirected.values.toSet)
    val deleted = tombstones(gone ++ redirected.values.toSet.filterNot(survivors.contains))
    val byUid = withUid.toList.flatMap { ref =>
      val u = ref.uid.get
      live.get(u) match
        case Some((k, t)) =>
          Some(ref -> Described(k, t, if k == ref.key then RecordState.Live else RecordState.Renamed(k)))
        case None =>
          redirected.get(u).flatMap(to => survivors.get(to).map(to -> _)) match
            case Some((to, (k, t))) => Some(ref -> Described(k, t, RecordState.MergedInto(ItemRef(kind, k, Some(to)))))
            case None               =>
              (redirected.get(u).toList :+ u)
                .flatMap(deleted.get)
                .headOption
                .map(at => ref -> Described(ref.key, None, RecordState.Deleted(at)))
    }
    val keys = withoutUid.map(_.key).toList
    val found = byKeys(keys).map((k, _, t) => k -> t).toMap
    val renamed = byAliases(keys.filterNot(k => found.contains(k)))
    val byKey = withoutUid.toList.flatMap { ref =>
      found
        .get(ref.key)
        .map(t => ref -> Described(ref.key, t, RecordState.Live))
        .orElse(renamed.get(ref.key).map((now, _, t) => ref -> Described(now, t, RecordState.Renamed(now))))
    }
    (byUid ++ byKey).toMap

object RecordTable:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** How many merges in a row are followed. */

  type Table = Table.T
  object Table extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Uid = Uid.T
  object Uid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val MaxHops = 16
