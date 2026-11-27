package com.histmersion
package domain.catalogue.events

import com.histmersion.api.library.{DeleteBlocker, DeleteCount, DeletePart}
import com.histmersion.domain.library.sources.referrers.*

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given

import com.augustnagro.magnum.*
import com.histmersion.api.library.Rename
import com.histmersion.util.database.Unused
import com.histmersion.domain.{CreatedAt, Creator}

/** The events a source is the primary source of, the relations and participants sourced to it (they refuse a delete),
  * and the datings it gives (they go with it).
  */
final class EventSourceReferrer extends SourceReferrer:

  override def renamed(rename: SourceRename)(using DbCon)(using Creator.New, CreatedAt): RenamedReferences =
    val (from, to) = (rename.from, rename.to)
    RenamedReferences(
      List(
        "events.citekey" -> sql"UPDATE events SET citekey = $to WHERE citekey = $from".update.run(),
        // (event_id, citekey) is the key: a dating already recorded under the new name wins
        "event_datings.citekey" ->
          sql"""UPDATE event_datings d SET citekey = $to WHERE d.citekey = $from
                AND NOT EXISTS (SELECT 1 FROM event_datings o WHERE o.event_id = d.event_id AND o.citekey = $to)""".update
            .run(),
        "event_edges.citekey" -> sql"UPDATE event_edges SET citekey = $to WHERE citekey = $from".update.run(),
        "event_participants.citekey" ->
          sql"UPDATE event_participants SET citekey = $to WHERE citekey = $from".update.run()
      ).collect { case (t, n) if n > 0 => Rename.Count(Rename.Count.Table(t), Rename.Count.Rows(n)) }
    )

  override def report(source: SourceKeys)(using DbCon): DeleteReport =
    val keys = source.keys
    val events = sql"SELECT id, label FROM events WHERE citekey = ANY($keys::text[]) ORDER BY id"
      .query[(String, String)]
      .run()
      .toList
      .map((id, l) => DeleteBlocker(DeleteBlocker.Kind.Event, DeleteBlocker.Id(id), Some(DeleteBlocker.Title(l))))
    val relations = sql"""SELECT src_id, kind, dst_id FROM event_edges WHERE citekey = ANY($keys::text[])
                          ORDER BY src_id, kind, dst_id"""
      .query[(String, String, String)]
      .run()
      .toList
      .map((s, k, d) => DeleteBlocker(DeleteBlocker.Kind.EventEdge, DeleteBlocker.Id(s"$s $k $d")))
    val participants = sql"""SELECT event_id, actor FROM event_participants WHERE citekey = ANY($keys::text[])
                             ORDER BY event_id, actor, role"""
      .query[(String, String)]
      .run()
      .toList
      .map((e, a) =>
        DeleteBlocker(DeleteBlocker.Kind.EventParticipant, DeleteBlocker.Id(e), Some(DeleteBlocker.Title(a)))
      )
    val datings = sql"SELECT count(*)::int FROM event_datings WHERE citekey = ANY($keys::text[])"
      .query[Int]
      .run()
      .headOption
      .getOrElse(0)
    DeleteReport(
      blockers = events ++ relations ++ participants,
      counts = List(DeletePart.EventDatings -> datings).collect {
        case (p, rows) if rows > 0 => DeleteCount(p, DeleteCount.Rows(rows))
      }
    )

  override def deleting(source: SourceKeys, report: DeleteReport)(using DbCon): Unit =
    Unused(
      sql"DELETE FROM event_datings WHERE citekey = ANY(${source.keys}::text[])".update.run(),
      Unused.Why.Idempotent
    )
