package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

// ── row types ──────────────────────────────────────────────────────────────────────────────────

private[db] final case class EventRow(
    id: String, label: String, kind: String,
    notBefore: Option[Long], beginBy: Option[Long], endFrom: Option[Long], notAfter: Option[Long],
    historicity: String, placeId: Option[String], parentId: Option[String],
    description: Option[String], citekey: Option[String]):
  def toSummary: EventSummary =
    EventSummary(id, label, kind,
      TimeSpan(notBefore, beginBy, endFrom, notAfter),
      historicity, placeId, parentId, description, citekey)

private[db] final case class EventDetailRow(
    id: String, label: String, kind: String,
    notBefore: Option[Long], beginBy: Option[Long], endFrom: Option[Long], notAfter: Option[Long],
    historicity: String, placeId: Option[String], parentId: Option[String],
    description: Option[String], citekey: Option[String], geometry: Option[String]):
  def toSummary: EventSummary =
    EventSummary(id, label, kind,
      TimeSpan(notBefore, beginBy, endFrom, notAfter),
      historicity, placeId, parentId, description, citekey)

private[db] final case class EventDatingRow(
    eventId: String, citekey: String,
    notBefore: Option[Long], beginBy: Option[Long], endFrom: Option[Long], notAfter: Option[Long],
    method: Option[String], note: Option[String]):
  def toDto: EventDating =
    EventDating(eventId, citekey, TimeSpan(notBefore, beginBy, endFrom, notAfter), method, note)

given DbCodec[EventRow]        = DbCodec.derived
given DbCodec[EventDetailRow]  = DbCodec.derived
given DbCodec[EventDatingRow]  = DbCodec.derived
given DbCodec[EventEdge]       = DbCodec.derived
given DbCodec[EventParticipant]= DbCodec.derived

object EventRepo:

  private val summaryCols =
    "e.id, e.label, e.kind, e.not_before, e.begin_by, e.end_from, e.not_after, " +
      "e.historicity, e.place_id, e.parent_id, e.description, e.citekey"

  // ── reads ───────────────────────────────────────────────────────────────────────────────────

  def list(ds: DataSource, q: Option[String], kind: Option[String], historicity: Option[String],
      placeId: Option[String], from: Option[Long], to: Option[Long],
      limit: Int, offset: Int): EventList =
    val text = q.map(_.trim).filter(_.nonEmpty)
    val cont = text.map(FilterSql.contains).getOrElse("%")
    val k = kind.map(_.trim.toLowerCase).filter(_.nonEmpty)
    val h = historicity.map(_.trim.toLowerCase).filter(_.nonEmpty)
    val p = placeId.map(_.trim).filter(_.nonEmpty)
    connect(ds):
      val total = sql"""SELECT count(*)::int FROM events e
            WHERE (${text}::text IS NULL OR e.label ILIKE $cont ESCAPE '\' OR e.description ILIKE $cont ESCAPE '\')
              AND (${k}::text IS NULL OR e.kind = ${k})
              AND (${h}::text IS NULL OR e.historicity = ${h})
              AND (${p}::text IS NULL OR e.place_id = ${p})
              AND (${from}::bigint IS NULL OR e.outer_span && int8range(${from}, NULL, '[)'))
              AND (${to}::bigint IS NULL OR e.outer_span && int8range(NULL, ${to}, '(]'))
            """.query[Int].run().head
      val rows = sql"""SELECT ${SqlLiteral(summaryCols)} FROM events e
            WHERE (${text}::text IS NULL OR e.label ILIKE $cont ESCAPE '\' OR e.description ILIKE $cont ESCAPE '\')
              AND (${k}::text IS NULL OR e.kind = ${k})
              AND (${h}::text IS NULL OR e.historicity = ${h})
              AND (${p}::text IS NULL OR e.place_id = ${p})
              AND (${from}::bigint IS NULL OR e.outer_span && int8range(${from}, NULL, '[)'))
              AND (${to}::bigint IS NULL OR e.outer_span && int8range(NULL, ${to}, '(]'))
            ORDER BY coalesce(e.not_before, e.begin_by), e.label
            LIMIT ${limit.max(1)} OFFSET ${offset.max(0)}
            """.query[EventRow].run().toList.map(_.toSummary)
      EventList(total, rows)

  def get(ds: DataSource, id: String): Option[EventDetail] =
    connect(ds):
      sql"""SELECT ${SqlLiteral(summaryCols)}, ST_AsGeoJSON(e.geom)
            FROM events e WHERE e.id = $id""".query[EventDetailRow].run().headOption.map { r =>
        val parent = r.toSummary.parentId.flatMap { pid =>
          sql"""SELECT ${SqlLiteral(summaryCols)} FROM events e WHERE e.id = $pid"""
            .query[EventRow].run().headOption.map(_.toSummary)
        }
        val children = sql"""SELECT ${SqlLiteral(summaryCols)} FROM events e
              WHERE e.parent_id = $id ORDER BY coalesce(e.not_before, e.begin_by), e.label"""
          .query[EventRow].run().toList.map(_.toSummary)
        val datings = sql"""SELECT event_id, citekey, not_before, begin_by, end_from, not_after,
                                   method, note
                            FROM event_datings WHERE event_id = $id ORDER BY citekey"""
          .query[EventDatingRow].run().toList.map(_.toDto)
        val edges = sql"""SELECT src_id, dst_id, kind, citekey, note
                          FROM event_edges WHERE src_id = $id OR dst_id = $id
                          ORDER BY kind, src_id, dst_id"""
          .query[EventEdge].run().toList
        val participants = sql"""SELECT event_id, actor, role, citekey
                                FROM event_participants WHERE event_id = $id
                                ORDER BY role, actor"""
          .query[EventParticipant].run().toList
        EventDetail(r.toSummary, r.geometry, parent, children, datings, edges, participants)
      }

  // ── writes ──────────────────────────────────────────────────────────────────────────────────

  def put(ds: DataSource, id: String, in: EventPut): Unit =
    transact(ds):
      val geom = in.geometry
      sql"""INSERT INTO events (id, label, kind, not_before, begin_by, end_from, not_after,
                                historicity, geom, place_id, parent_id, description, citekey)
            VALUES ($id, ${in.label}, ${in.kind},
                    ${in.timeSpan.notBefore}, ${in.timeSpan.beginBy},
                    ${in.timeSpan.endFrom}, ${in.timeSpan.notAfter},
                    ${in.historicity},
                    CASE WHEN ${geom}::text IS NULL THEN NULL
                         ELSE ST_SetSRID(ST_GeomFromGeoJSON(${geom}::text), 4326) END,
                    ${in.placeId}, ${in.parentId}, ${in.description}, ${in.citekey})
            ON CONFLICT (id) DO UPDATE SET
              label = EXCLUDED.label, kind = EXCLUDED.kind,
              not_before = EXCLUDED.not_before, begin_by = EXCLUDED.begin_by,
              end_from = EXCLUDED.end_from, not_after = EXCLUDED.not_after,
              historicity = EXCLUDED.historicity, geom = EXCLUDED.geom,
              place_id = EXCLUDED.place_id, parent_id = EXCLUDED.parent_id,
              description = EXCLUDED.description, citekey = EXCLUDED.citekey""".update.run()

      sql"DELETE FROM event_datings WHERE event_id = $id".update.run()
      sql"DELETE FROM event_edges WHERE src_id = $id OR dst_id = $id".update.run()
      sql"DELETE FROM event_participants WHERE event_id = $id".update.run()

      val _ = batchUpdate(in.datings) { d =>
        sql"""INSERT INTO event_datings (event_id, citekey, not_before, begin_by, end_from, not_after,
                                         method, note)
              VALUES ($id, ${d.citekey}, ${d.timeSpan.notBefore}, ${d.timeSpan.beginBy},
                      ${d.timeSpan.endFrom}, ${d.timeSpan.notAfter}, ${d.method}, ${d.note})
              ON CONFLICT (event_id, citekey) DO UPDATE SET
                not_before = EXCLUDED.not_before, begin_by = EXCLUDED.begin_by,
                end_from = EXCLUDED.end_from, not_after = EXCLUDED.not_after,
                method = EXCLUDED.method, note = EXCLUDED.note""".update
      }
      val _ = batchUpdate(in.edges) { eg =>
        sql"""INSERT INTO event_edges (src_id, dst_id, kind, citekey, note)
              VALUES (${eg.srcId}, ${eg.dstId}, ${eg.kind}, ${eg.citekey}, ${eg.note})
              ON CONFLICT (src_id, dst_id, kind) DO UPDATE SET
                citekey = EXCLUDED.citekey, note = EXCLUDED.note""".update
      }
      val _ = batchUpdate(in.participants) { p =>
        sql"""INSERT INTO event_participants (event_id, actor, role, citekey)
              VALUES ($id, ${p.actor}, ${p.role}, ${p.citekey})
              ON CONFLICT (event_id, actor, role) DO UPDATE SET citekey = EXCLUDED.citekey""".update
      }

  def patch(ds: DataSource, id: String, p: EventPatch): Either[ApiError, Unit] =
    transact(ds):
      val exists = sql"SELECT 1 FROM events WHERE id = $id".query[Int].run().nonEmpty
      if !exists then Left(ApiError(s"No event '$id'", Some(Write.CodeNotFound)))
      else
        p.label.foreach(v => sql"UPDATE events SET label = $v WHERE id = $id".update.run())
        p.kind.foreach(v => sql"UPDATE events SET kind = $v WHERE id = $id".update.run())
        p.historicity.foreach(v => sql"UPDATE events SET historicity = $v WHERE id = $id".update.run())
        p.placeId.foreach(v => sql"UPDATE events SET place_id = $v WHERE id = $id".update.run())
        p.parentId.foreach(v => sql"UPDATE events SET parent_id = $v WHERE id = $id".update.run())
        p.description.foreach(v => sql"UPDATE events SET description = $v WHERE id = $id".update.run())
        p.citekey.foreach(v => sql"UPDATE events SET citekey = $v WHERE id = $id".update.run())
        p.geometry.foreach { g =>
          sql"""UPDATE events SET geom = CASE WHEN ${g}::text IS NULL THEN NULL
                ELSE ST_SetSRID(ST_GeomFromGeoJSON(${g}::text), 4326) END WHERE id = $id""".update.run()
        }
        p.timeSpan.foreach { ts =>
          sql"""UPDATE events SET not_before = ${ts.notBefore}, begin_by = ${ts.beginBy},
                end_from = ${ts.endFrom}, not_after = ${ts.notAfter} WHERE id = $id""".update.run()
        }
        p.datings.foreach { dts =>
          sql"DELETE FROM event_datings WHERE event_id = $id".update.run()
          val _ = batchUpdate(dts) { d =>
            sql"""INSERT INTO event_datings (event_id, citekey, not_before, begin_by, end_from, not_after,
                                             method, note)
                  VALUES ($id, ${d.citekey}, ${d.timeSpan.notBefore}, ${d.timeSpan.beginBy},
                          ${d.timeSpan.endFrom}, ${d.timeSpan.notAfter}, ${d.method}, ${d.note})""".update
          }
        }
        p.edges.foreach { es =>
          sql"DELETE FROM event_edges WHERE src_id = $id OR dst_id = $id".update.run()
          val _ = batchUpdate(es) { eg =>
            sql"""INSERT INTO event_edges (src_id, dst_id, kind, citekey, note)
                  VALUES (${eg.srcId}, ${eg.dstId}, ${eg.kind}, ${eg.citekey}, ${eg.note})""".update
          }
        }
        p.participants.foreach { ps =>
          sql"DELETE FROM event_participants WHERE event_id = $id".update.run()
          val _ = batchUpdate(ps) { pp =>
            sql"""INSERT INTO event_participants (event_id, actor, role, citekey)
                  VALUES ($id, ${pp.actor}, ${pp.role}, ${pp.citekey})""".update
          }
        }
        Right(())

  def delete(ds: DataSource, id: String): Boolean =
    transact(ds):
      sql"DELETE FROM events WHERE id = $id".update.run() > 0
