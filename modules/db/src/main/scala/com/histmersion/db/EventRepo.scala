package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

// ── row types ──────────────────────────────────────────────────────────────────────────────────

private[db] final case class EventRow(
    id: String, label: String, kind: String,
    notBefore: Option[Long], beginBy: Option[Long], endFrom: Option[Long], notAfter: Option[Long],
    historicity: String, placeId: Option[String], parentId: Option[String],
    description: Option[String], citekey: Option[String],
    scale: Option[String], placeKind: Option[String], geometryType: Option[String]):
  def toSummary: EventSummary =
    EventSummary(id, label, kind,
      TimeSpan(notBefore, beginBy, endFrom, notAfter),
      historicity, placeId, parentId, description, citekey,
      EventScale.effective(scale, kind, parentId.isDefined, placeKind, geometryType), scale.isDefined)

private[db] final case class EventDetailRow(row: EventRow, geometry: Option[String]):
  def toSummary: EventSummary = row.toSummary

/** One event on the map before its outline is read ([[EventRepo.geo]]). */
private[db] final case class EventGeoRow(row: EventRow, lat: Double, lon: Double, anchor: String,
    placeLabel: Option[String], hasArea: Boolean)

private[db] final case class EventDatingRow(
    eventId: String, citekey: String,
    notBefore: Option[Long], beginBy: Option[Long], endFrom: Option[Long], notAfter: Option[Long],
    method: Option[String], note: Option[String]):
  def toDto: EventDating =
    EventDating(eventId, citekey, TimeSpan(notBefore, beginBy, endFrom, notAfter), method, note)

given DbCodec[EventRow]        = DbCodec.derived
given DbCodec[EventDetailRow]  = DbCodec.derived
given DbCodec[EventGeoRow]     = DbCodec.derived
given DbCodec[EventDatingRow]  = DbCodec.derived
given DbCodec[EventEdge]       = DbCodec.derived
given DbCodec[EventParticipant]= DbCodec.derived

object EventRepo:

  /** The columns of an [[EventRow]]: the event's own, and what its scale is derived from — its place's
    * kind and its own geometry's type — over [[fromEvents]]. */
  private val summaryCols =
    "e.id, e.label, e.kind, e.not_before, e.begin_by, e.end_from, e.not_after, " +
      "e.historicity, e.place_id, e.parent_id, e.description, e.citekey, " +
      "e.scale, ep.kind, GeometryType(e.geom)"

  /** `events e` with its place (`ep`) joined, for [[summaryCols]]. */
  private val fromEvents = "events e LEFT JOIN places ep ON ep.id = e.place_id"

  // ── reads ───────────────────────────────────────────────────────────────────────────────────

  /** `GET /api/events` — the events related to the filter's sources ([[FilterSql.eventWhere]]: its
    * source among them, its own span in the period, its label containing the title), narrowed by
    * the event's own `text` (label/description substring), kind, historicity and place. */
  def list(ds: DataSource, filter: Filter, scope: PlaceScope, text: Option[String],
      kind: Option[String], historicity: Option[String], placeId: Option[String],
      limit: Int, offset: Int): EventList =
    val where = listWhere(filter, scope, text, kind, historicity, placeId)
    connect(ds):
      val total = (Sql.raw("SELECT count(*)::int FROM events e WHERE ") ++ where)
        .frag.query[Int].run().head
      val rows = (Sql.raw(s"SELECT $summaryCols FROM $fromEvents WHERE ") ++ where ++
        Sql.raw(" ORDER BY coalesce(e.not_before, e.begin_by), e.label") ++
        Sql.of(" LIMIT ? OFFSET ?", Param.I(limit.max(1)), Param.I(offset.max(0))))
        .frag.query[EventRow].run().toList.map(_.toSummary)
      EventList(total, rows)

  /** The WHERE of [[list]], as a value — pure, unit-tested without a database. */
  def listWhere(filter: Filter, scope: PlaceScope, text: Option[String], kind: Option[String],
      historicity: Option[String], placeId: Option[String]): Sql =
    def clean(o: Option[String]) = o.map(_.trim).filter(_.nonEmpty)
    val ps = List.newBuilder[Sql]
    val related = FilterSql.eventWhere(filter, scope)
    if related.text != "TRUE" then ps += related
    clean(text).foreach { t =>
      val c = FilterSql.contains(t)
      ps += Sql.of("(e.label ILIKE ? ESCAPE '\\' OR e.description ILIKE ? ESCAPE '\\')", Param.S(c), Param.S(c))
    }
    clean(kind).map(_.toLowerCase).foreach(k => ps += Sql.of("e.kind = ?", Param.S(k)))
    clean(historicity).map(_.toLowerCase).foreach(h => ps += Sql.of("e.historicity = ?", Param.S(h)))
    clean(placeId).foreach(p => ps += Sql.of("e.place_id = ?", Param.S(p)))
    val parts = ps.result()
    if parts.isEmpty then Sql.raw("TRUE")
    else Sql(parts.map(_.text).mkString(" AND "), parts.flatMap(_.params))

  /** `GET /api/events/geo` — the events [[list]] selects that have a position, for the map's playback
    * (#1440). The anchor is the event's own geometry, else its place's: the point itself, or the
    * point-on-surface of an area (always inside it, unlike a centroid). A REGIONAL event whose anchor
    * is an area gets that area's outline, simplified to `tolerance` degrees (a polity's land-clipped
    * shape, as the map draws it); a local one, or an area known only by a point, none. Two
    * statements: the rows with their scale, then the outlines of the regional ones alone. */
  def geo(ds: DataSource, filter: Filter, scope: PlaceScope, text: Option[String],
      kind: Option[String], historicity: Option[String], placeId: Option[String],
      limit: Int, tolerance: Double): EventGeoList =
    val where = listWhere(filter, scope, text, kind, historicity, placeId)
    connect(ds):
      val rows = (Sql.raw(s"""SELECT $summaryCols,
              ST_Y(a.pt), ST_X(a.pt), s.src, ep.label, GeometryType(s.g) IN ('POLYGON', 'MULTIPOLYGON')
            FROM $fromEvents
            CROSS JOIN LATERAL (SELECT coalesce(e.geom, ep.geom) AS g,
                                       CASE WHEN e.geom IS NOT NULL THEN 'event' ELSE 'place' END AS src) s
            CROSS JOIN LATERAL (SELECT CASE WHEN GeometryType(s.g) = 'POINT' THEN s.g
                                            ELSE ST_PointOnSurface(s.g) END AS pt) a
            WHERE s.g IS NOT NULL AND """) ++ where ++
        Sql.raw(" ORDER BY coalesce(e.not_before, e.begin_by), e.label") ++
        Sql.of(" LIMIT ?", Param.I(limit.max(1))))
        .frag.query[EventGeoRow].run().toList
      val unplaced = (Sql.raw(s"SELECT count(*)::int FROM $fromEvents WHERE e.geom IS NULL AND ep.geom IS NULL AND ") ++ where)
        .frag.query[Int].run().head
      val items = rows.map { r =>
        val e = r.row.toSummary
        (r, EventGeo(e.id, e.label, e.kind, e.scale, e.historicity, e.timeSpan, r.lat, r.lon, r.anchor,
          e.placeId, r.placeLabel, e.parentId))
      }
      val wanted = items.collect { case (r, g) if r.hasArea && g.scale == EventScale.Regional => g.id }
      val outlines: Map[String, String] =
        if wanted.isEmpty then Map.empty
        else
          sql"""SELECT e.id, ST_AsGeoJSON(ST_SimplifyPreserveTopology(
                         coalesce(e.geom, ep.geom_clipped, ep.geom), $tolerance), 4)
                FROM ${SqlLiteral(fromEvents)} WHERE e.id = ANY($wanted::text[])"""
            .query[(String, Option[String])].run().collect { case (id, Some(g)) => id -> g }.toMap
      EventGeoList(items.map((_, g) => g.copy(outline = outlines.get(g.id))), unplaced)

  def get(ds: DataSource, id: String): Option[EventDetail] =
    connect(ds):
      sql"""SELECT ${SqlLiteral(summaryCols)}, ST_AsGeoJSON(e.geom)
            FROM ${SqlLiteral(fromEvents)} WHERE e.id = $id""".query[EventDetailRow].run().headOption.map { r =>
        val parent = r.toSummary.parentId.flatMap { pid =>
          sql"""SELECT ${SqlLiteral(summaryCols)} FROM ${SqlLiteral(fromEvents)} WHERE e.id = $pid"""
            .query[EventRow].run().headOption.map(_.toSummary)
        }
        val children = sql"""SELECT ${SqlLiteral(summaryCols)} FROM ${SqlLiteral(fromEvents)}
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
      val scale = in.scale.flatMap(v => EventScale.parse(v).flatten)
      sql"""INSERT INTO events (id, label, kind, not_before, begin_by, end_from, not_after,
                                historicity, geom, place_id, parent_id, description, citekey, scale)
            VALUES ($id, ${in.label}, ${in.kind},
                    ${in.timeSpan.notBefore}, ${in.timeSpan.beginBy},
                    ${in.timeSpan.endFrom}, ${in.timeSpan.notAfter},
                    ${in.historicity},
                    CASE WHEN ${geom}::text IS NULL THEN NULL
                         ELSE ST_SetSRID(ST_GeomFromGeoJSON(${geom}::text), 4326) END,
                    ${in.placeId}, ${in.parentId}, ${in.description}, ${in.citekey}, $scale)
            ON CONFLICT (id) DO UPDATE SET
              label = EXCLUDED.label, kind = EXCLUDED.kind,
              not_before = EXCLUDED.not_before, begin_by = EXCLUDED.begin_by,
              end_from = EXCLUDED.end_from, not_after = EXCLUDED.not_after,
              historicity = EXCLUDED.historicity, geom = EXCLUDED.geom,
              place_id = EXCLUDED.place_id, parent_id = EXCLUDED.parent_id,
              description = EXCLUDED.description, citekey = EXCLUDED.citekey,
              scale = EXCLUDED.scale""".update.run()

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
        // `auto` clears the stated scale (derived again); a value [[Handlers]] did not validate is skipped
        p.scale.flatMap(EventScale.parse).foreach(v => sql"UPDATE events SET scale = $v WHERE id = $id".update.run())
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

  /** Rename an event (V042), inside the caller's transaction: the id moves with one UPDATE (its
    * datings, participants, edges and children cascade), and the attestations and asset links that
    * name it follow. What a bundle import does when an event was renamed on the other side. */
  private[db] def renameIn(oldId: String, newId: String)(using DbCon): Either[ApiError, Unit] =
    def exists(i: String) = sql"SELECT 1 FROM events WHERE id = $i".query[Int].run().nonEmpty
    val aliasOf = sql"SELECT event_id FROM event_aliases WHERE alias = $newId".query[String].run().headOption
    if !exists(oldId) then Left(ApiError(s"No event '$oldId'", Some(Write.CodeNotFound)))
    else if exists(newId) then Left(ApiError(s"'$newId' is already the id of another event", Some(Write.CodeInvalid)))
    else if aliasOf.exists(_ != oldId) then
      Left(ApiError(s"'$newId' is an old id of '${aliasOf.get}' and still resolves to it — pick another", Some(Write.CodeInvalid)))
    else
      // renaming back to an old id: that alias goes (V053)
      sql"DELETE FROM event_aliases WHERE alias = $newId".update.run()
      sql"UPDATE events SET id = $newId WHERE id = $oldId".update.run()
      sql"UPDATE attestations SET target_id = $newId WHERE target_type = 'event' AND target_id = $oldId".update.run()
      sql"UPDATE asset_links SET target_id = $newId WHERE target_type = 'event' AND target_id = $oldId".update.run()
      sql"""INSERT INTO event_aliases (alias, event_id) VALUES ($oldId, $newId)
            ON CONFLICT (alias) DO UPDATE SET event_id = EXCLUDED.event_id""".update.run()
      Right(())

  /** `histmersion events rename`: a new id, the old one kept as an alias (V053). One transaction. */
  def rename(ds: DataSource, oldId: String, newId: String): Either[ApiError, Unit] =
    transact(ds)(renameIn(oldId.trim, newId.trim))

  /** The id an OLD id was renamed to — `None` when it is a live id, or nothing. */
  def renamedTo(ds: DataSource, id: String): Option[String] =
    connect(ds):
      sql"SELECT event_id FROM event_aliases WHERE alias = $id AND NOT EXISTS (SELECT 1 FROM events WHERE id = $id)"
        .query[String].run().headOption

  def delete(ds: DataSource, id: String): Boolean =
    transact(ds):
      sql"DELETE FROM events WHERE id = $id".update.run() > 0
