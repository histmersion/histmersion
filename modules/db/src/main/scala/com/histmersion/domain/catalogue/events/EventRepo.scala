package com.histmersion
package domain.catalogue.events

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.EventEntry

import com.histmersion.api.Write
import com.histmersion.api.catalogue.{
  EventEdge,
  EventFields,
  EventList,
  EventParticipant,
  EventPatch,
  EventPrecision,
  EventPut,
  EventScale,
  EventSummary
}
import com.histmersion.util.database.{Param, Sql}

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.given_DbCodec_List
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Correction
import com.histmersion.api.catalogue.EventGeo
import com.histmersion.api.catalogue.EventGeometry
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.personal.UnreadPage
import com.histmersion.domain.catalogue.Event
import com.histmersion.util.identity.AliasTable

/** The events (`events`) with their datings, edges and participants (`event_datings`, `event_edges`,
  * `event_participants`) and the old ids of renamed ones (`event_aliases`). Every method runs on the connection it is
  * given.
  */
private[events] object EventRepo:
  type EventCount = EventCount.T
  object EventCount extends Newtype[Long, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type Tier = Tier.T
  object Tier extends Newtype[String, Pure]

  /** How many events the catalogue holds. */

  type GeoTolerance = com.histmersion.domain.catalogue.events.GeoTolerance
  val GeoTolerance: com.histmersion.domain.catalogue.events.GeoTolerance.type =
    com.histmersion.domain.catalogue.events.GeoTolerance

  def count()(using DbCon): EventCount = EventCount(sql"SELECT count(*)::int FROM events".query[Int].run().head.toLong)

  private given DbCodec[EventEdge] = DbCodec.derived
  private given DbCodec[EventParticipant] = DbCodec.derived

  /** The columns of an [[EventRow]]: the event's own, and what its scale is derived from — its place's kind and its own
    * geometry's type — over [[EventSql.fromEvents]].
    */
  private val summaryCols =
    "e.id, e.label, e.kind, e.not_before, e.begin_by, e.end_from, e.not_after, " +
      "e.historicity, e.place_id, e.parent_id, e.description, e.citekey, " +
      "e.scale, ep.kind, GeometryType(e.geom), " + EventSql.TierSql +
      ", e.central, e.plus_minus, e.dating_method, e.uncertainty"

  private val fromEvents = EventSql.fromEvents

  // ── reads ───────────────────────────────────────────────────────────────────────────────────

  /** The events `where` selects ([[EventSql.listWhere]]), one page of them. */
  def list(where: Sql, limit: PageSize, offset: Offset)(using DbCon): EventList =
    val total = (Sql.raw(Sql.Text(s"SELECT count(*)::int FROM $fromEvents WHERE ")) ++ where).frag.query[Int].run().head
    val rows = (Sql.raw(Sql.Text(s"SELECT $summaryCols FROM $fromEvents WHERE ")) ++ where ++
      Sql.raw(Sql.Text(" ORDER BY coalesce(e.not_before, e.begin_by), e.label")) ++
      Sql.of(
        Sql.Text(" LIMIT ? OFFSET ?"),
        Param.I(Param.I.V(limit.value.max(1))),
        Param.I(Param.I.V(offset.value.max(0)))
      )).frag
      .query[EventRow]
      .run()
      .toList
      .map(_.toSummary)
    EventList.of(EventList.Total(total), rows, UnreadPage.Offset(offset.value.max(0)))

  /** The events `listed` selects that have a position, for the map's playback. The anchor is the event's own geometry,
    * else its place's: the point itself, or the point-on-surface of an area (always inside it, unlike a centroid). A
    * REGIONAL event whose anchor is an area gets that area's outline, simplified to `tolerance` degrees (a polity's
    * land-clipped shape, as the map draws it); a local one, or an area known only by a point, none. Two statements: the
    * rows with their scale, then the outlines of the regional ones alone.
    */
  def geo(listed: Sql, limit: PageSize, tolerance: GeoTolerance)(using DbCon): EventGeo.List =
    // drawn at a year only when dated, and well enough (core.EventPrecision.mapped): the imprecise are
    // counted; an undated one (its outer span unbounded) would otherwise stand in every period
    val where =
      listed ++ Sql.of(Sql.OfText(" AND e.uncertainty <= ?"), Param.L(Param.L.V(EventPrecision.ApproximateUpTo.value)))
    val rows = (geoSelect ++ where ++
      Sql.raw(Sql.Text(" ORDER BY coalesce(e.not_before, e.begin_by), e.label")) ++
      Sql.of(Sql.Text(" LIMIT ?"), Param.I(Param.I.V(limit.value.max(1))))).frag.query[EventGeoRow].run().toList
    val unplaced = (Sql.raw(
      Sql.Text(s"SELECT count(*)::int FROM $fromEvents WHERE e.geom IS NULL AND ep.geom IS NULL AND ")
    ) ++ where).frag.query[Int].run().head
    val imprecise = (Sql.raw(
      Sql.Text(s"SELECT count(*)::int FROM $fromEvents WHERE coalesce(e.geom, ep.geom) IS NOT NULL AND ")
    ) ++
      listed ++ Sql.of(
        Sql.Text(" AND e.uncertainty > ?"),
        Param.L(Param.L.V(EventPrecision.ApproximateUpTo.value))
      )).frag.query[Int].run().head
    val items = rows.map(r => (r, r.toGeo))
    val wanted = items.collect { case (r, g) if r.hasArea && g.scale.value == EventScale.Regional => r.row.id }
    val outlines: Map[BEventRow.Id, BEventRow.Geom] =
      if wanted.isEmpty then Map.empty
      else
        sql"""SELECT e.id, ST_AsGeoJSON(ST_SimplifyPreserveTopology(
                       coalesce(e.geom, ep.geom_clipped, ep.geom), $tolerance), 4)
              FROM ${SqlLiteral(fromEvents)} WHERE e.id = ANY($wanted::text[])"""
          .query[(BEventRow.Id, Option[BEventRow.Geom])]
          .run()
          .collect { case (id, Some(g)) => id -> g }
          .toMap
    EventGeo.List(
      items.map((r, g) => g.modify(_.outline).setTo(outlines.get(r.row.id).map(o => EventGeo.Outline(o.value)))),
      EventGeo.List.Unplaced(unplaced),
      EventGeo.List.Imprecise(imprecise)
    )

  /** The events of `ids` as the map's tiles draw one ([[geo]]'s rows, without an outline), by id; an id with no
    * position, or dated too loosely, is left out as [[geo]] leaves it out.
    */
  def geoOf(ids: List[BEventRow.Id])(using DbCon): Map[BEventRow.Id, EventGeo] =
    if ids.isEmpty then Map.empty
    else
      (geoSelect ++ Sql.of(
        Sql.OfText("e.id = ANY(?) AND e.uncertainty <= ?"),
        Param.A(ids.map(i => Param.A.V(i.value))),
        Param.L(Param.L.V(EventPrecision.ApproximateUpTo.value))
      )).frag.query[EventGeoRow].run().map(r => r.row.id -> r.toGeo).toMap

  /** The SELECT of the map's event rows ([[EventGeoRow]]), up to its WHERE (which the caller ends). */
  private val geoSelect: Sql = Sql.raw(Sql.Text(s"""SELECT $summaryCols,
              ST_Y(a.pt), ST_X(a.pt), s.src, ep.label, GeometryType(s.g) IN ('POLYGON', 'MULTIPOLYGON'),
              (SELECT max(c.vei) FROM event_catalogue c WHERE c.event_id = e.id),
              (SELECT max(c.magnitude) FROM event_catalogue c WHERE c.event_id = e.id)
            FROM $fromEvents ${EventSql.anchorJoins}
            WHERE s.g IS NOT NULL AND """))

  /** One event with its parent, parts, datings, edges, participants, Wikidata item and corrections by hand. */
  def get(id: BEventRow.Id)(using DbCon): Option[EventEntry] =
    sql"""SELECT ${SqlLiteral(summaryCols)}, ST_AsGeoJSON(e.geom)
          FROM ${SqlLiteral(fromEvents)} WHERE e.id = $id""".query[EventDetailRow].run().headOption.map { r =>
      val parent = r.toSummary.parentId.flatMap { pid =>
        sql"""SELECT ${SqlLiteral(summaryCols)} FROM ${SqlLiteral(fromEvents)} WHERE e.id = $pid"""
          .query[EventRow]
          .run()
          .headOption
          .map(_.toSummary)
      }
      val children = sql"""SELECT ${SqlLiteral(summaryCols)} FROM ${SqlLiteral(fromEvents)}
            WHERE e.parent_id = $id ORDER BY coalesce(e.not_before, e.begin_by), e.label"""
        .query[EventRow]
        .run()
        .toList
        .map(_.toSummary)
      val datings = sql"""SELECT event_id, citekey, not_before, begin_by, end_from, not_after,
                                 method, note
                          FROM event_datings WHERE event_id = $id ORDER BY citekey"""
        .query[EventDatingRow]
        .run()
        .toList
        .map(_.toDto)
      val edges = sql"""SELECT src_id, dst_id, kind, citekey, note
                        FROM event_edges WHERE src_id = $id OR dst_id = $id
                        ORDER BY kind, src_id, dst_id"""
        .query[EventEdge]
        .run()
        .toList
      val participants = sql"""SELECT event_id, actor, role, citekey
                              FROM event_participants WHERE event_id = $id
                              ORDER BY role, actor"""
        .query[EventParticipant]
        .run()
        .toList
      val wikidata = sql"SELECT wikidata FROM events WHERE id = $id"
        .query[Option[String]]
        .run()
        .headOption
        .flatten
        .map(EventEntry.Wikidata(_))
      EventEntry(
        r.toSummary,
        r.geometry.map(g => EventGeometry.Geojson(g.value)),
        parent,
        children,
        datings,
        edges,
        participants,
        wikidata,
        corrections = CorrectionRepo
          .of(Correction.TargetKind(Correction.Target.Event), Correction.TargetId(id.value))
          .map(_.toDto())
      )
    }

  /** The tier, other names and catalogue of one event. */
  def tierAndNames(id: BEventRow.Id)(using DbCon): Option[(Tier, List[BEventRow.Names], Option[BEventRow.Label])] =
    sql"""SELECT ${SqlLiteral(EventSql.TierSql)}, e.names, e.catalogue FROM events e WHERE e.id = $id"""
      .query[(Tier, List[BEventRow.Names], Option[BEventRow.Label])]
      .run()
      .headOption

  def exists(id: BEventRow.Id)(using DbCon): Boolean =
    sql"SELECT 1 FROM events WHERE id = $id".query[Int].run().nonEmpty

  // ── writes ──────────────────────────────────────────────────────────────────────────────────

  /** SQL, in an `ON CONFLICT` update of `events`: the four-point span is the one stored. */
  private val sameSpan = SqlLiteral(
    "(events.not_before, events.begin_by, events.end_from, events.not_after) " +
      "IS NOT DISTINCT FROM (EXCLUDED.not_before, EXCLUDED.begin_by, EXCLUDED.end_from, EXCLUDED.not_after)"
  )

  /** A person changed `fields` of an event an IMPORT created: each that now differs from what the catalogue's record
    * says is recorded as a correction, so the next import keeps it; one set back to the catalogue's value drops its
    * correction. An event compiled from sources (no catalogue) is never refreshed by an import: nothing to record.
    */
  private def recordCorrections(id: BEventRow.Id, fields: Map[String, io.circe.Json])(using DbCon): Unit =
    if fields.nonEmpty then
      EventImportRepo.upstreamOf(Event.Id.assume(id.value)).foreach { (provider, rec) =>
        val up = EventFields.upstream(rec)
        fields.foreach((f, v) =>
          up.get(f)
            .foreach(u =>
              CorrectionRepo.record(
                Correction.TargetKind(Correction.Target.Event),
                Correction.TargetId(id.value),
                Correction.Field(f),
                v,
                u,
                Some(BCatalogueRow.Provider(provider.value))
              )
            )
        )
      }

  /** Why PostGIS cannot read `geojson` as a geometry, in its own words — `None` when it can. Asked before a write, on a
    * connection of its own, so a bad geometry is a 400 rather than a failed statement.
    */
  def geometryError(geojson: BEventRow.Geom)(using con: DbCon): Option[Problem] =
    try
      val st = con.connection.prepareStatement("SELECT ST_IsEmpty(ST_GeomFromGeoJSON(?))")
      try
        st.setString(1, geojson.value)
        st.executeQuery().close()
        None
      finally st.close()
    catch
      case e: java.sql.SQLException =>
        Some(
          Problem(
            Option(e.getMessage)
              .map(_.linesIterator.next().trim)
              .filter(_.nonEmpty)
              .getOrElse(e.getClass.getSimpleName)
          )
        )

  /** Create or replace one event. On an event an import created, the fields that differ from its catalogue's are
    * recorded as corrections ([[recordCorrections]]) — they survive a re-import.
    */
  def put(id: BEventRow.Id, in: EventPut)(using DbCon): Unit =
    val geom = in.geometry.map(_.value.trim).filter(_.nonEmpty)
    val scale = in.scale.flatMap(v => EventScale.parse(v).flatten)
    sql"""INSERT INTO events (id, label, kind, not_before, begin_by, end_from, not_after,
                              historicity, geom, place_id, parent_id, description, citekey, scale)
          VALUES ($id, ${in.label}, ${in.kind},
                  ${in.timeSpan.notBefore}, ${in.timeSpan.beginBy},
                  ${in.timeSpan.endFrom}, ${in.timeSpan.notAfter},
                  ${in.historicity},
                  CASE WHEN $geom::text IS NULL THEN NULL
                       ELSE ST_SetSRID(ST_GeomFromGeoJSON($geom::text), 4326) END,
                  ${in.placeId}, ${in.parentId}, ${in.description}, ${in.citekey}, $scale)
          ON CONFLICT (id) DO UPDATE SET
            label = EXCLUDED.label, kind = EXCLUDED.kind,
            not_before = EXCLUDED.not_before, begin_by = EXCLUDED.begin_by,
            end_from = EXCLUDED.end_from, not_after = EXCLUDED.not_after,
            historicity = EXCLUDED.historicity, geom = EXCLUDED.geom,
            place_id = EXCLUDED.place_id, parent_id = EXCLUDED.parent_id,
            description = EXCLUDED.description, citekey = EXCLUDED.citekey,
            scale = EXCLUDED.scale,
            -- a catalogue's stated dating stays only while the span it explains does
            central = CASE WHEN $sameSpan THEN events.central END,
            plus_minus = CASE WHEN $sameSpan THEN events.plus_minus END,
            dating_method = CASE WHEN $sameSpan THEN events.dating_method END""".update.run()
    recordCorrections(id, EventFields.ofPut(in))

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

  /** Change the fields `p` sets. On an event an import created, each changed field is recorded as a correction
    * ([[recordCorrections]]): the next import of its catalogue keeps it (and retires it once the catalogue agrees, or
    * flags a conflict when the catalogue changes to something else).
    */
  def patch(id: BEventRow.Id, p: EventPatch)(using DbCon): Either[Api.Error, Unit] =
    if !exists(id) then Left(Api.Error(Problem(s"No event '$id'"), Some(Api.Error.Code(Write.CodeNotFound))))
    else
      p.label.foreach(v => sql"UPDATE events SET label = $v WHERE id = $id".update.run())
      p.kind.foreach(v => sql"UPDATE events SET kind = $v WHERE id = $id".update.run())
      p.historicity.foreach(v => sql"UPDATE events SET historicity = $v WHERE id = $id".update.run())
      p.placeId.foreach(v => sql"UPDATE events SET place_id = $v WHERE id = $id".update.run())
      p.parentId.foreach(v => sql"UPDATE events SET parent_id = $v WHERE id = $id".update.run())
      p.description.foreach(v => sql"UPDATE events SET description = $v WHERE id = $id".update.run())
      p.citekey.foreach(v => sql"UPDATE events SET citekey = $v WHERE id = $id".update.run())
      // `auto` clears the stated scale (derived again); a value the controller did not validate is skipped
      p.scale.flatMap(EventScale.parse).foreach(v => sql"UPDATE events SET scale = $v WHERE id = $id".update.run())
      // `""` clears the item; anything else the controller checked is a `Q…` id
      p.wikidata.map(_.value.trim).foreach { q =>
        val v = Option(q).filter(_.nonEmpty)
        sql"UPDATE events SET wikidata = $v WHERE id = $id".update.run()
      }
      // `""` clears the geometry; anything else the controller checked with [[geometryError]]
      p.geometry.map(_.value.trim).foreach { raw =>
        val g = Option(raw).filter(_.nonEmpty)
        sql"""UPDATE events SET geom = CASE WHEN $g::text IS NULL THEN NULL
              ELSE ST_SetSRID(ST_GeomFromGeoJSON($g::text), 4326) END WHERE id = $id""".update.run()
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
      // a span set by hand replaces the catalogue's dating: its stated year and ± no longer explain it
      p.timeSpan.foreach(_ =>
        sql"UPDATE events SET central = NULL, plus_minus = NULL, dating_method = NULL WHERE id = $id".update.run()
      )
      recordCorrections(id, EventFields.ofPatch(p))
      Right(())

  /** Rename an event: the id moves with one UPDATE (its datings, participants, edges and children cascade), and the
    * attestations and asset links that name it follow. What a bundle import does when an event was renamed on the other
    * side.
    */
  def rename(oldId: BEventRow.Id, newId: BEventRow.Id)(using DbCon): Either[Api.Error, Unit] =
    val aliasOf = sql"SELECT event_id FROM event_aliases WHERE alias = $newId".query[BEventRow.Id].run().headOption
    if !exists(oldId) then
      Left(Api.Error(Problem(s"No event '$oldId'"), Some(Api.Error.Code(Write.CodeNotFound))))
    else if exists(newId) then
      Left(
        Api.Error(
          Problem(s"'$newId' is already the id of another event"),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else if aliasOf.exists(_ != oldId) then
      Left(
        Api.Error(
          Problem(s"'$newId' is an old id of '${aliasOf.get}' and still resolves to it — pick another"),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else
      // renaming back to an old id: that alias goes
      sql"DELETE FROM event_aliases WHERE alias = $newId".update.run()
      sql"UPDATE events SET id = $newId WHERE id = $oldId".update.run() // its corrections follow (a trigger)
      sql"UPDATE attestations SET target_id = $newId WHERE target_type = 'event' AND target_id = $oldId".update.run()
      sql"UPDATE asset_links SET target_id = $newId WHERE target_type = 'event' AND target_id = $oldId".update.run()
      sql"""INSERT INTO event_aliases (alias, event_id) VALUES ($oldId, $newId)
            ON CONFLICT (alias) DO UPDATE SET event_id = EXCLUDED.event_id""".update.run()
      Right(())

  /** The id an OLD id was renamed to — `None` when it is a live id, or nothing. */
  def renamedTo(id: BEventRow.Id)(using DbCon): Option[BEventRow.Id] =
    EventAliasTable.table
      .renamedToIn(AliasTable.Id(id.value))
      .map(a => BEventRow.Id(a.value))

  def delete(id: BEventRow.Id)(using DbCon): Boolean =
    sql"DELETE FROM events WHERE id = $id".update.run() > 0 // its corrections go (a trigger)
