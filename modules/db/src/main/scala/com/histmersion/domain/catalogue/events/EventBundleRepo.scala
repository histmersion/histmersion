package com.histmersion.domain.catalogue.events

import com.histmersion.domain.catalogue.places.PlacesCarried
import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.util.data.exchange.{BundleFormat, BundleScope}
import com.histmersion.util.data.exchange.BundleSql.*

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import java.util.UUID
import com.histmersion.api.exchange.Bundle

/** The events as a bundle carries them (docs/research-merge-import.md §4, §7.3): the events with their datings, edges
  * and participants, their old ids, what the catalogues say about them and the corrections by hand — read out in a
  * deterministic order, and written in by an import.
  */
private[events] object EventBundleRepo:


  given given_DbCodec_BundleCorrection: DbCodec[Bundle.Correction] = DbCodec.derived

  // ── out ──────────────────────────────────────────────────────────────────────────────────────

  def events(scope: BundleScope)(using DbCon): Vector[Bundle.Event] =
    val w = where(scope, "e.uid", "e.updated_at")
    val rows = (Sql.raw(s"""SELECT e.uid, e.origin, e.id, e.label, e.kind, e.not_before, e.begin_by,
                                  e.end_from, e.not_after, e.historicity, encode(ST_AsEWKB(e.geom), 'hex'),
                                  e.place_id, p.uid, e.description, e.citekey, s.uid, ${ts("e.updated_at")}, e.scale,
                                  e.catalogue, e.names, e.wikidata, e.central, e.plus_minus, e.dating_method
                           FROM events e LEFT JOIN events p ON p.id = e.parent_id
                           LEFT JOIN sources s ON s.citekey = e.citekey WHERE """) ++ w ++ " ORDER BY e.uid").frag
      .query[BEventRow]
      .run()
    val datings = (Sql.raw("""SELECT e.uid, d.citekey, s.uid, d.not_before, d.begin_by, d.end_from,
                                     d.not_after, d.method, d.note
                              FROM event_datings d JOIN events e ON e.id = d.event_id
                              LEFT JOIN sources s ON s.citekey = d.citekey WHERE """) ++ w ++
      """ ORDER BY e.uid, d.citekey COLLATE "C"""").frag.query[BDatingRow].run().groupBy(_.eventUid)
    val edges = (Sql.raw("""SELECT e.uid, b.uid, g.kind, g.citekey, s.uid, g.note
                            FROM event_edges g JOIN events e ON e.id = g.src_id JOIN events b ON b.id = g.dst_id
                            LEFT JOIN sources s ON s.citekey = g.citekey WHERE """) ++ w ++
      """ ORDER BY e.uid, b.uid, g.kind COLLATE "C"""").frag.query[BEventEdgeRow].run().groupBy(_.eventUid)
    val people = (Sql.raw("""SELECT e.uid, p.actor, p.role, p.citekey, s.uid
                             FROM event_participants p JOIN events e ON e.id = p.event_id
                             LEFT JOIN sources s ON s.citekey = p.citekey WHERE """) ++ w ++
      """ ORDER BY e.uid, p.actor COLLATE "C", p.role COLLATE "C"""").frag
      .query[BParticipantRow]
      .run()
      .groupBy(_.eventUid)
    rows.map { r =>
      Bundle.Event(
        r.uid,
        r.origin,
        r.id,
        r.label,
        r.kind,
        r.notBefore,
        r.beginBy,
        r.endFrom,
        r.notAfter,
        r.historicity,
        r.geom,
        r.placeId,
        r.parentUid,
        r.description,
        r.citekey,
        r.sourceUid,
        datings
          .getOrElse(r.uid, Vector.empty)
          .toList
          .map(d =>
            Bundle.EventDating(d.citekey, d.sourceUid, d.notBefore, d.beginBy, d.endFrom, d.notAfter, d.method, d.note)
          ),
        edges
          .getOrElse(r.uid, Vector.empty)
          .toList
          .map(g => Bundle.EventEdge(g.dstUid, g.kind, g.citekey, g.sourceUid, g.note)),
        people
          .getOrElse(r.uid, Vector.empty)
          .toList
          .map(p => Bundle.EventParticipant(p.actor, p.role, p.citekey, p.sourceUid)),
        r.updatedAt,
        scale = r.scale,
        catalogue = r.catalogue,
        names = Option(r.names).filter(_.nonEmpty),
        wikidata = r.wikidata,
        central = r.central,
        plusMinus = r.plusMinus,
        datingMethod = r.datingMethod
      )
    }

  /** Old event ids and goal slugs (V053) of the events and goals in scope. */
  def eventAliases(scope: BundleScope)(using DbCon): Vector[Bundle.EventAlias] =
    (Sql.raw(s"""SELECT x.alias, e.uid, ${ts("x.created_at")}
                FROM event_aliases x JOIN events e ON e.id = x.event_id WHERE """) ++
      where(scope, "e.uid", "x.created_at") ++ """ ORDER BY x.alias COLLATE "C"""").frag
      .query[(String, UUID, String)]
      .run()
      .map(Bundle.EventAlias.apply)

  /** What the catalogues say about the events in scope (`event_catalogue`): by event, then the record's key. */
  def eventCatalogue(scope: BundleScope)(using DbCon): Vector[Bundle.EventCatalogue] =
    (Sql.raw(s"""SELECT e.uid, c.provider, c.provider_id, c.url, c.label, c.kind, c.not_before, c.begin_by,
                       c.end_from, c.not_after, c.lat, c.lon, c.vei, c.magnitude, c.deaths, c.historicity,
                       c.record::text, ${ts("c.retrieved_at")}
                FROM event_catalogue c JOIN events e ON e.id = c.event_id WHERE """) ++
      where(scope, "e.uid", "c.retrieved_at") ++
      """ ORDER BY e.uid, c.provider COLLATE "C", c.provider_id COLLATE "C"""").frag.query[BCatalogueRow].run().map { r =>
      Bundle.EventCatalogue(
        r.eventUid,
        r.provider,
        r.providerId,
        r.url,
        r.label,
        r.kind,
        r.notBefore,
        r.beginBy,
        r.endFrom,
        r.notAfter,
        r.lat,
        r.lon,
        r.vei,
        r.magnitude,
        r.deaths,
        r.historicity,
        io.circe.parser.parse(r.record).getOrElse(io.circe.Json.obj()),
        r.retrievedAt
      )
    }

  /** The corrections by hand (`corrections`, V088); an event's carries its uid. The clock is the later of the person's
    * change and the last refresh.
    */
  def corrections(scope: BundleScope)(using DbCon): Vector[Bundle.Correction] =
    val eventUid = "(SELECT x.uid FROM events x WHERE c.target_kind = 'event' AND x.id = c.target_id)"
    (Sql.raw(s"""SELECT c.target_kind, c.target_id, $eventUid, c.field, c.value::text, c.upstream,
                       c.upstream_value::text, c.upstream_now::text, c.status, ${ts("c.created_at")},
                       ${ts("c.updated_at")}, ${ts("c.checked_at")}
                FROM corrections c WHERE """) ++
      where(scope, eventUid, "greatest(c.updated_at, coalesce(c.checked_at, c.updated_at))") ++
      """ ORDER BY c.target_kind COLLATE "C", c.target_id COLLATE "C", c.field COLLATE "C"""").frag
      .query[Bundle.Correction]
      .run()

  // ── what a merge plan compares ───────────────────────────────────────────────────────────────

  /** Who has each of `labels` here, by the query's (label, uid) rows. */
  private def owners(q: String, labels: List[com.histmersion.domain.catalogue.events.BEventRow.Label])(using DbCon): Map[com.histmersion.domain.catalogue.events.BEventRow.Id, UUID] =
    if labels.isEmpty then Map.empty
    else Sql.of(q, Param.A(labels.distinct)).frag.query[(String, UUID)].run().toMap

  /** The events here among `ids` → their uids. */
  def eventsNamed(ids: List[com.histmersion.domain.catalogue.events.BEventRow.Id])(using DbCon): Map[com.histmersion.domain.catalogue.events.BEventRow.Id, UUID] =
    owners("SELECT id, uid FROM events WHERE id = ANY(?::text[])", ids)

  /** The old event ids here among `ids` → the uid of the event each names. */
  def oldIdsNamed(ids: List[com.histmersion.domain.catalogue.events.BEventRow.Id])(using DbCon): Map[com.histmersion.domain.catalogue.events.BEventRow.Id, UUID] =
    owners(
      "SELECT x.alias, e.uid FROM event_aliases x JOIN events e ON e.id = x.event_id WHERE x.alias = ANY(?::text[])",
      ids
    )

  // ── in ───────────────────────────────────────────────────────────────────────────────────────

  /** The id an event has here now, when its uid names one. */
  private def eventIdOf(u: UUID)(using DbCon): Option[com.histmersion.domain.catalogue.events.BEventRow.Id] =
    sql"SELECT id FROM events WHERE uid = $u".query[String].run().headOption

  private def eventExists(id: com.histmersion.domain.catalogue.events.BEventRow.Id)(using DbCon): Boolean =
    sql"SELECT 1 FROM events WHERE id = $id".query[Int].run().nonEmpty

  /** Each event of the event-catalogue sets looked up once, in one step. */
  private def eventIdsOf()(using DbCon): UUID => Option[com.histmersion.domain.catalogue.events.BEventRow.Id] =
    val ids = scala.collection.mutable.Map.empty[UUID, Option[String]]
    u => ids.getOrElseUpdate(u, eventIdOf(u))

  /** An event renamed over there: the rename here first — its datings, edges and links follow, and the old id stays an
    * alias — then the row.
    */
  def eventRenames(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.followRenames(w.es.events, _.uid, _.id, w.local.events.map(e => e.uid -> e.id).toMap, com.histmersion.util.data.exchange.PlanQuestions.LabelTakenWhat("event"))(EventRepo.rename)

  /** Events: the rows, then their parents (a parent may come later in uid order), then the sets. */
  def eventsIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    val placeHere = PlacesCarried.placeHere(w)
    val events = w.es.events.filter(e => w.writes(e.uid))
    events.foreach { e =>
      val names = e.names.getOrElse(Nil)
      sql"""INSERT INTO events (uid, origin, id, label, kind, not_before, begin_by, end_from, not_after,
              historicity, geom, place_id, description, citekey, updated_at, scale,
              catalogue, names, wikidata, central, plus_minus, dating_method)
            VALUES (${e.uid}, ${e.origin}, ${e.id}, ${e.label}, ${e.kind}, ${e.notBefore}, ${e.beginBy},
              ${e.endFrom}, ${e.notAfter}, ${e.historicity}, ST_GeomFromEWKB(decode(${e.geom}, 'hex')),
              ${e.placeId.map(placeHere)}, ${e.description}, ${e.citekey}, ${e.updatedAt}::timestamptz,
              ${e.scale.flatMap(com.histmersion.api.catalogue.EventScale.parse(_).flatten)},
              ${e.catalogue}, $names::text[], ${e.wikidata}, ${e.central}, ${e.plusMinus}, ${e.datingMethod})
            ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, label = EXCLUDED.label,
              kind = EXCLUDED.kind, not_before = EXCLUDED.not_before, begin_by = EXCLUDED.begin_by,
              end_from = EXCLUDED.end_from, not_after = EXCLUDED.not_after,
              historicity = EXCLUDED.historicity, geom = EXCLUDED.geom, place_id = EXCLUDED.place_id,
              description = EXCLUDED.description, citekey = EXCLUDED.citekey,
              scale = EXCLUDED.scale, catalogue = EXCLUDED.catalogue, names = EXCLUDED.names,
              wikidata = EXCLUDED.wikidata, central = EXCLUDED.central, plus_minus = EXCLUDED.plus_minus,
              dating_method = EXCLUDED.dating_method, updated_at = EXCLUDED.updated_at""".update.run()
    }
    events.foreach { e =>
      val parent = e.parentUid.flatMap(eventIdOf)
      sql"UPDATE events SET parent_id = $parent WHERE uid = ${e.uid}".update.run()
      val id = e.id
      sql"DELETE FROM event_datings WHERE event_id = $id".update.run()
      sql"DELETE FROM event_edges WHERE src_id = $id".update.run()
      sql"DELETE FROM event_participants WHERE event_id = $id".update.run()
      e.datings.foreach { d =>
        val ck = d.sourceUid.flatMap(w.citekeyHere).getOrElse(d.citekey)
        sql"""INSERT INTO event_datings (event_id, citekey, not_before, begin_by, end_from, not_after, method, note)
              VALUES ($id, $ck, ${d.notBefore}, ${d.beginBy}, ${d.endFrom}, ${d.notAfter}, ${d.method}, ${d.note})
              ON CONFLICT DO NOTHING""".update.run()
      }
      e.edges.foreach { g =>
        val ck = g.sourceUid.flatMap(w.citekeyHere).getOrElse(g.citekey)
        eventIdOf(g.dstUid).foreach { dst =>
          sql"""INSERT INTO event_edges (src_id, dst_id, kind, citekey, note)
                VALUES ($id, $dst, ${g.kind}, $ck, ${g.note}) ON CONFLICT DO NOTHING""".update.run()
        }
      }
      e.participants.foreach { p =>
        val ck = p.sourceUid.flatMap(w.citekeyHere).orElse(p.citekey)
        sql"""INSERT INTO event_participants (event_id, actor, role, citekey)
              VALUES ($id, ${p.actor}, ${p.role}, $ck) ON CONFLICT DO NOTHING""".update.run()
      }
    }

  /** Old event ids (V053): after every event is in. */
  def eventAliasesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.es.eventAliases.foreach { a =>
      sql"""INSERT INTO event_aliases (alias, event_id, created_at)
            SELECT ${a.alias}, id, ${a.createdAt}::timestamptz FROM events
            WHERE uid = ${a.eventUid} AND id <> ${a.alias}
              AND NOT EXISTS (SELECT 1 FROM events WHERE id = ${a.alias})
            ON CONFLICT (alias) DO NOTHING""".update.run()
    }

  // ── the event-catalogue sets (#1647) ──
  // After every event, source and place is in: each line whose event (and source) is here, merged by its own rule
  // (BundleEventSets) — the newer catalogue record, the newer candidate evidence with a decision kept over a
  // proposal, the newer check, correction and site type. Like an assessment, independent of the event line's
  // decision: these are what the importers and the person found, not the event's content. Idempotent: a line the
  // collection already has, or has newer, writes nothing.

  def eventCatalogueIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    val eventIds = eventIdsOf()
    import com.histmersion.api.exchange.Bundle.EventSets.newer
    if w.es.eventCatalogue.nonEmpty then
      val here = Sql
        .raw(s"SELECT provider, provider_id, ${ts("retrieved_at")} FROM event_catalogue")
        .frag
        .query[(String, String, String)]
        .run()
        .map((p, i, at) => (p, i) -> at)
        .toMap
      w.es.eventCatalogue.foreach { c =>
        eventIds(c.eventUid).filter(_ => newer(here.get((c.provider, c.providerId)), Some(c.retrievedAt))).foreach {
          id =>
            val record = BundleFormat.printer.print(c.record)
            sql"""INSERT INTO event_catalogue (provider, provider_id, event_id, url, label, kind, not_before, begin_by,
                  end_from, not_after, lat, lon, vei, magnitude, deaths, historicity, record, retrieved_at)
                VALUES (${c.provider}, ${c.providerId}, $id, ${c.url}, ${c.label}, ${c.kind}, ${c.notBefore},
                  ${c.beginBy}, ${c.endFrom}, ${c.notAfter}, ${c.lat}, ${c.lon}, ${c.vei}, ${c.magnitude}, ${c.deaths},
                  ${c.historicity}, $record::jsonb, ${c.retrievedAt}::timestamptz)
                ON CONFLICT (provider, provider_id) DO UPDATE SET event_id = EXCLUDED.event_id, url = EXCLUDED.url,
                  label = EXCLUDED.label, kind = EXCLUDED.kind, not_before = EXCLUDED.not_before,
                  begin_by = EXCLUDED.begin_by, end_from = EXCLUDED.end_from, not_after = EXCLUDED.not_after,
                  lat = EXCLUDED.lat, lon = EXCLUDED.lon, vei = EXCLUDED.vei, magnitude = EXCLUDED.magnitude,
                  deaths = EXCLUDED.deaths, historicity = EXCLUDED.historicity, record = EXCLUDED.record,
                  retrieved_at = EXCLUDED.retrieved_at""".update.run()
        }
      }

  /** The corrections by hand: the newer change wins; an event's follows the event here, a place's the place. */
  def correctionsIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    val eventIds = eventIdsOf()
    val placeHere = PlacesCarried.placeHere(w)
    import com.histmersion.api.exchange.Bundle.EventSets.newer
    if w.es.corrections.nonEmpty then
      val clock = s"greatest(${ts("updated_at")}, coalesce(${ts("checked_at")}, ''))"
      val here = Sql
        .raw(s"SELECT target_kind, target_id, field, $clock FROM corrections")
        .frag
        .query[(String, String, String, String)]
        .run()
        .map((k, t, f, at) => (k, t, f) -> at)
        .toMap
      w.es.corrections.foreach { c =>
        // an event's correction follows the event here (by uid); a place's names a gazetteer id
        val target =
          if c.targetKind == "event" then c.targetUid.fold(Option.when(eventExists(c.targetId))(c.targetId))(eventIds)
          else Some(placeHere(c.targetId))
        val at = (c.updatedAt :: c.checkedAt.toList).max
        target.filter(t => newer(here.get((c.targetKind, t, c.field)), Some(at))).foreach { t =>
          sql"""INSERT INTO corrections (target_kind, target_id, field, value, upstream, upstream_value, upstream_now,
                  status, created_at, updated_at, checked_at)
                VALUES (${c.targetKind}, $t, ${c.field}, ${c.value}::jsonb, ${c.upstream}, ${c.upstreamValue}::jsonb,
                  ${c.upstreamNow}::jsonb, ${c.status}, ${c.createdAt}::timestamptz, ${c.updatedAt}::timestamptz,
                  ${c.checkedAt}::timestamptz)
                ON CONFLICT (target_kind, target_id, field) DO UPDATE SET value = EXCLUDED.value,
                  upstream = EXCLUDED.upstream, upstream_value = EXCLUDED.upstream_value,
                  upstream_now = EXCLUDED.upstream_now, status = EXCLUDED.status, created_at = EXCLUDED.created_at,
                  updated_at = EXCLUDED.updated_at, checked_at = EXCLUDED.checked_at""".update.run()
        }
      }
