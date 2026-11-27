package com.histmersion
package domain.catalogue.events
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson

import com.histmersion.domain.catalogue.places.PlacesCarried
import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.exchange.{BundleFormat, BundleScope}
import com.histmersion.util.data.exchange.BundleSql.*

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.augustnagro.magnum.*
import java.util.UUID
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.util.data.exchange.PlanQuestions
import com.histmersion.domain.catalogue.places.BPlaceRow
import com.histmersion.api.catalogue.EventScale
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.util.database.Timestamps
import com.histmersion.util.database.Unused
import com.histmersion.domain.Creator
import com.histmersion.util.database.StoredCreator

/** The events as a bundle carries them (docs/research-merge-import.md §4, §7.3): the events with their datings, edges
  * and participants, their old ids, what the catalogues say about them and the corrections by hand — read out in a
  * deterministic order, and written in by an import.
  */
private[events] object EventBundleRepo:

  given given_DbCodec_BundleCorrection: DbCodec[Bundle.Correction] = DbCodec.derived

  // ── out ──────────────────────────────────────────────────────────────────────────────────────

  def events(scope: BundleScope)(using DbCon): Vector[Bundle.Event] =
    val w = where(scope, sql"e.uid", sql"e.updated_at")
    val rows = sql"""SELECT e.uid, e.origin, e.id, e.label, e.kind, e.not_before, e.begin_by,
                                  e.end_from, e.not_after, e.historicity, encode(ST_AsEWKB(e.geom), 'hex'),
                                  e.place_id, p.uid, e.description, e.citekey, s.uid, ${Timestamps.iso(
        sql"e.updated_at"
      )}, e.scale,
                                  e.catalogue, e.names, e.wikidata, e.central, e.plus_minus, e.dating_method,
                                  e.creator::text
                           FROM events e LEFT JOIN events p ON p.id = e.parent_id
                           LEFT JOIN sources s ON s.citekey = e.citekey WHERE $w ORDER BY e.uid"""
      .query[BEventRow]
      .run()
    val datings = sql"""SELECT e.uid, d.citekey, s.uid, d.not_before, d.begin_by, d.end_from,
                                     d.not_after, d.method, d.note
                              FROM event_datings d JOIN events e ON e.id = d.event_id
                              LEFT JOIN sources s ON s.citekey = d.citekey WHERE $w ORDER BY e.uid, d.citekey COLLATE "C\""""
      .query[BDatingRow]
      .run()
      .groupBy(_.eventUid)
    val edges = sql"""SELECT e.uid, b.uid, g.kind, g.citekey, s.uid, g.note
                            FROM event_edges g JOIN events e ON e.id = g.src_id JOIN events b ON b.id = g.dst_id
                            LEFT JOIN sources s ON s.citekey = g.citekey WHERE $w ORDER BY e.uid, b.uid, g.kind COLLATE "C\""""
      .query[BEventEdgeRow]
      .run()
      .groupBy(_.eventUid)
    val people = sql"""SELECT e.uid, p.actor, p.role, p.citekey, s.uid
                             FROM event_participants p JOIN events e ON e.id = p.event_id
                             LEFT JOIN sources s ON s.citekey = p.citekey WHERE $w ORDER BY e.uid, p.actor COLLATE "C", p.role COLLATE "C\""""
      .query[BParticipantRow]
      .run()
      .groupBy(_.eventUid)
    rows.map { r =>
      Bundle.Event(
        r.uid,
        r.origin,
        Bundle.Event.Id(r.id.value),
        Bundle.Event.Label(r.label.value),
        Bundle.Event.Kind(r.kind.value),
        r.notBefore.map(v => Bundle.Event.NotBefore(v.value)),
        r.beginBy.map(v => Bundle.Event.BeginBy(v.value)),
        r.endFrom.map(v => Bundle.Event.EndFrom(v.value)),
        r.notAfter.map(v => Bundle.Event.NotAfter(v.value)),
        Bundle.Event.Historicity(r.historicity.value),
        r.geom.map(g => Bundle.Event.Geom(g.value)),
        r.placeId.map(p => Bundle.Event.PlaceId(p.value)),
        r.parentUid,
        r.description.map(d => Bundle.Event.Description(d.value)),
        r.citekey.map(c => Citekey(c.value)),
        r.sourceUid,
        datings
          .getOrElse(r.uid, Vector.empty)
          .toList
          .map(d =>
            Bundle.EventDating(
              Citekey(d.citekey.value),
              d.sourceUid,
              d.notBefore.map(v => Bundle.EventDating.NotBefore(v.value)),
              d.beginBy.map(v => Bundle.EventDating.BeginBy(v.value)),
              d.endFrom.map(v => Bundle.EventDating.EndFrom(v.value)),
              d.notAfter.map(v => Bundle.EventDating.NotAfter(v.value)),
              d.method.map(m => Bundle.EventDating.Method(m.value)),
              d.note.map(n => Bundle.EventDating.Note(n.value))
            )
          ),
        edges
          .getOrElse(r.uid, Vector.empty)
          .toList
          .map(g =>
            Bundle.EventEdge(
              g.dstUid,
              Bundle.EventEdge.Kind(g.kind.value),
              Citekey(g.citekey.value),
              g.sourceUid,
              g.note.map(n => Bundle.EventEdge.Note(n.value))
            )
          ),
        people
          .getOrElse(r.uid, Vector.empty)
          .toList
          .map(p =>
            Bundle.EventParticipant(
              Bundle.EventParticipant.Actor(p.actor.value),
              Bundle.EventParticipant.Role(p.role.value),
              p.citekey.map(c => Citekey(c.value)),
              p.sourceUid
            )
          ),
        Bundle.Event.UpdatedAt(r.updatedAt.value),
        scale = r.scale.map(s => Bundle.Event.Scale(s.value)),
        catalogue = r.catalogue.map(c => Bundle.Event.Catalogue(c.value)),
        names = Option(r.names).filter(_.nonEmpty).map(_.map(n => Bundle.Event.Names(n.value))),
        wikidata = r.wikidata.map(w => Bundle.Event.Wikidata(w.value)),
        central = r.central.map(c => Bundle.Event.Central(c.value)),
        plusMinus = r.plusMinus.map(p => Bundle.Event.PlusMinus(p.value)),
        datingMethod = r.datingMethod.map(m => Bundle.Event.DatingMethod(m.value)),
        creator = StoredCreator.document(r.creator)
      )
    }

  /** Old event ids and goal slugs (V053) of the events and goals in scope. */
  def eventAliases(scope: BundleScope)(using DbCon): Vector[Bundle.EventAlias] =
    sql"""SELECT x.alias, e.uid, ${Timestamps.iso(sql"x.created_at")}
                FROM event_aliases x JOIN events e ON e.id = x.event_id WHERE ${where(
        scope,
        sql"e.uid",
        sql"x.created_at"
      )} ORDER BY x.alias COLLATE "C\""""
      .query[(String, UUID, String)]
      .run()
      .map((alias, uid, at) => Bundle.EventAlias(Bundle.EventAlias.Alias(alias), uid, Bundle.EventAlias.CreatedAt(at)))

  /** What the catalogues say about the events in scope (`event_catalogue`): by event, then the record's key. */
  def eventCatalogue(scope: BundleScope)(using DbCon): Vector[Bundle.EventCatalogue] =
    sql"""SELECT e.uid, c.provider, c.provider_id, c.url, c.label, c.kind, c.not_before, c.begin_by,
                       c.end_from, c.not_after, c.lat, c.lon, c.vei, c.magnitude, c.deaths, c.historicity,
                       c.record::text, ${Timestamps.iso(sql"c.retrieved_at")}
                FROM event_catalogue c JOIN events e ON e.id = c.event_id WHERE ${where(
        scope,
        sql"e.uid",
        sql"c.retrieved_at"
      )} ORDER BY e.uid, c.provider COLLATE "C", c.provider_id COLLATE "C\"""".query[BCatalogueRow].run().map { r =>
      Bundle.EventCatalogue(
        r.eventUid,
        Bundle.EventCatalogue.Provider(r.provider.value),
        Bundle.EventCatalogue.ProviderId(r.providerId.value),
        r.url.map(u => Bundle.EventCatalogue.Url(u.value)),
        Bundle.EventCatalogue.Label(r.label.value),
        Bundle.EventCatalogue.Kind(r.kind.value),
        r.notBefore.map(v => Bundle.EventCatalogue.NotBefore(v.value)),
        r.beginBy.map(v => Bundle.EventCatalogue.BeginBy(v.value)),
        r.endFrom.map(v => Bundle.EventCatalogue.EndFrom(v.value)),
        r.notAfter.map(v => Bundle.EventCatalogue.NotAfter(v.value)),
        r.lat.map(v => Latitude(v.value)),
        r.lon.map(v => Longitude(v.value)),
        r.vei.map(v => Bundle.EventCatalogue.Vei(v.value)),
        r.magnitude.map(v => Bundle.EventCatalogue.Magnitude(v.value)),
        r.deaths.map(v => Bundle.EventCatalogue.Deaths(v.value)),
        Bundle.EventCatalogue.Historicity(r.historicity.value),
        RawJson.parse(r.record.value).getOrElse(Json.obj()),
        Bundle.EventCatalogue.RetrievedAt(r.retrievedAt.value)
      )
    }

  /** The corrections by hand (`corrections`, V088); an event's carries its uid. The clock is the later of the person's
    * change and the last refresh.
    */
  def corrections(scope: BundleScope)(using DbCon): Vector[Bundle.Correction] =
    val eventUid = sql"(SELECT x.uid FROM events x WHERE c.target_kind = 'event' AND x.id = c.target_id)"
    sql"""SELECT c.target_kind, c.target_id, $eventUid, c.field, c.value::text, c.upstream,
                       c.upstream_value::text, c.upstream_now::text, c.status, ${Timestamps.iso(sql"c.created_at")},
                       ${Timestamps.iso(sql"c.updated_at")}, ${Timestamps.iso(sql"c.checked_at")}
                FROM corrections c WHERE ${where(
        scope,
        eventUid,
        sql"greatest(c.updated_at, coalesce(c.checked_at, c.updated_at))"
      )} ORDER BY c.target_kind COLLATE "C", c.target_id COLLATE "C", c.field COLLATE "C\""""
      .query[Bundle.Correction]
      .run()

  // ── what a merge plan compares ───────────────────────────────────────────────────────────────

  /** Who has each of `labels` here, by the (label, uid) rows of the query `q` makes of them. */
  private def owners(labels: List[BEventRow.Id])(q: List[BEventRow.Id] => Frag)(using
      DbCon
  ): Map[BEventRow.Id, UUID] =
    if labels.isEmpty then Map.empty
    else q(labels.distinct).query[(BEventRow.Id, UUID)].run().toMap

  /** The events here among `ids` → their uids. */
  def eventsNamed(ids: List[BEventRow.Id])(using DbCon): Map[BEventRow.Id, UUID] =
    owners(ids)(ls => sql"SELECT id, uid FROM events WHERE id = ANY($ls::text[])")

  /** The old event ids here among `ids` → the uid of the event each names. */
  def oldIdsNamed(ids: List[BEventRow.Id])(using DbCon): Map[BEventRow.Id, UUID] =
    owners(ids)(ls =>
      sql"SELECT x.alias, e.uid FROM event_aliases x JOIN events e ON e.id = x.event_id WHERE x.alias = ANY($ls::text[])"
    )

  // ── in ───────────────────────────────────────────────────────────────────────────────────────

  /** The id an event has here now, when its uid names one. */
  private def eventIdOf(u: UUID)(using DbCon): Option[BEventRow.Id] =
    sql"SELECT id FROM events WHERE uid = $u".query[BEventRow.Id].run().headOption

  private def eventExists(id: BEventRow.Id)(using DbCon): Boolean =
    sql"SELECT 1 FROM events WHERE id = $id".query[Int].run().nonEmpty

  /** Each event of the event-catalogue sets looked up once, in one step. */
  private def eventIdsOf()(using DbCon): UUID => Option[BEventRow.Id] =
    val ids = scala.collection.mutable.Map.empty[UUID, Option[BEventRow.Id]]
    u => ids.getOrElseUpdate(u, eventIdOf(u))

  /** An event renamed over there: the rename here first — its datings, edges and links follow, and the old id stays an
    * alias — then the row.
    */
  def eventRenames(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.followRenames(
      w.es.events,
      _.uid,
      _.id,
      w.local.events.map(e => e.uid -> e.id).toMap,
      PlanQuestions.LabelTakenWhat("event")
    )((from, to) => EventRepo.rename(BEventRow.Id(from.value), BEventRow.Id(to.value)))

  /** Events: the rows, then their parents (a parent may come later in uid order), then the sets. */
  def eventsIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    val placeHere = PlacesCarried.placeHere(w)
    val events = w.es.events.filter(e => w.writes(e.uid))
    events.foreach { e =>
      val names = e.names.getOrElse(Nil)
      sql"""INSERT INTO events (uid, origin, id, label, kind, not_before, begin_by, end_from, not_after,
              historicity, geom, place_id, description, citekey, updated_at, scale,
              catalogue, names, wikidata, central, plus_minus, dating_method, creator, created_at)
            VALUES (${e.uid}, ${e.origin}, ${e.id}, ${e.label}, ${e.kind}, ${e.notBefore}, ${e.beginBy},
              ${e.endFrom}, ${e.notAfter}, ${e.historicity}, ST_GeomFromEWKB(decode(${e.geom}, 'hex')),
              ${e.placeId.map(p =>
          placeHere(BPlaceRow.Id(p.value))
        )}, ${e.description}, ${e.citekey}, ${e.updatedAt}::timestamptz,
              ${e.scale.flatMap(s => EventScale.parse(EventSummary.Scale(s.value)).flatten)},
              ${e.catalogue}, $names::text[], ${e.wikidata}, ${e.central}, ${e.plusMinus}, ${e.datingMethod},
              ${StoredCreator.carried(e.creator)}::jsonb, ${w.at}::timestamptz)
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
      Unused(sql"UPDATE events SET parent_id = $parent WHERE uid = ${e.uid}".update.run(), Unused.Why.Held)
      val id = e.id
      Unused(sql"DELETE FROM event_datings WHERE event_id = $id".update.run(), Unused.Why.Idempotent)
      Unused(sql"DELETE FROM event_edges WHERE src_id = $id".update.run(), Unused.Why.Idempotent)
      Unused(sql"DELETE FROM event_participants WHERE event_id = $id".update.run(), Unused.Why.Idempotent)
      e.datings.foreach { d =>
        val ck = d.sourceUid.flatMap(w.citekeyHere).map(_.value).getOrElse(d.citekey.value)
        Unused(
          sql"""INSERT INTO event_datings (event_id, citekey, not_before, begin_by, end_from, not_after, method, note)
              VALUES ($id, $ck, ${d.notBefore}, ${d.beginBy}, ${d.endFrom}, ${d.notAfter}, ${d.method}, ${d.note})
              ON CONFLICT DO NOTHING""".update.run(),
          Unused.Why.Idempotent
        )
      }
      e.edges.foreach { g =>
        val ck = g.sourceUid.flatMap(w.citekeyHere).map(_.value).getOrElse(g.citekey.value)
        eventIdOf(g.dstUid).foreach { dst =>
          Unused(
            sql"""INSERT INTO event_edges (src_id, dst_id, kind, citekey, note)
                VALUES ($id, $dst, ${g.kind}, $ck, ${g.note}) ON CONFLICT DO NOTHING""".update.run(),
            Unused.Why.Idempotent
          )
        }
      }
      e.participants.foreach { p =>
        val ck = p.sourceUid.flatMap(w.citekeyHere).map(_.value).orElse(p.citekey.map(_.value))
        Unused(
          sql"""INSERT INTO event_participants (event_id, actor, role, citekey)
              VALUES ($id, ${p.actor}, ${p.role}, $ck) ON CONFLICT DO NOTHING""".update.run(),
          Unused.Why.Idempotent
        )
      }
    }

  /** Old event ids (V053): after every event is in. */
  def eventAliasesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.es.eventAliases.foreach { a =>
      Unused(
        sql"""INSERT INTO event_aliases (alias, event_id, created_at)
            SELECT ${a.alias}, id, ${a.createdAt}::timestamptz FROM events
            WHERE uid = ${a.eventUid} AND id <> ${a.alias}
              AND NOT EXISTS (SELECT 1 FROM events WHERE id = ${a.alias})
            ON CONFLICT (alias) DO NOTHING""".update.run(),
        Unused.Why.Idempotent
      )
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
      val here = sql"SELECT provider, provider_id, ${Timestamps.iso(sql"retrieved_at")} FROM event_catalogue"
        .query[(String, String, Bundle.UpdatedAt)]
        .run()
        .map((p, i, at) => (p, i) -> at)
        .toMap
      w.es.eventCatalogue.foreach { c =>
        eventIds(c.eventUid)
          .filter(_ =>
            newer(here.get((c.provider.value, c.providerId.value)), Some(Bundle.UpdatedAt(c.retrievedAt.value)))
          )
          .foreach { id =>
            val record = BundleFormat.print(c.record)
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
      val clock =
        sql"greatest(${Timestamps.iso(sql"updated_at")}, coalesce(${Timestamps.iso(sql"checked_at")}, ''))"
      val here = sql"SELECT target_kind, target_id, field, $clock FROM corrections"
        .query[(String, String, String, Bundle.UpdatedAt)]
        .run()
        .map((k, t, f, at) => (k, t, f) -> at)
        .toMap
      w.es.corrections.foreach { c =>
        // an event's correction follows the event here (by uid); a place's names a gazetteer id
        val target: Option[String] =
          if c.targetKind.value == "event" then
            c.targetUid
              .fold(Option.when(eventExists(BEventRow.Id(c.targetId.value)))(c.targetId.value))(u =>
                eventIds(u).map(_.value)
              )
          else Some(placeHere(BPlaceRow.Id(c.targetId.value)).value)
        val at = (c.updatedAt.value :: c.checkedAt.toList.map(_.value)).max
        target.filter(t => newer(here.get((c.targetKind.value, t, c.field.value)), Some(Bundle.UpdatedAt(at)))).foreach {
          t =>
            sql"""INSERT INTO corrections (target_kind, target_id, field, value, upstream, upstream_value, upstream_now,
                  status, created_at, updated_at, checked_at, creator)
                VALUES (${c.targetKind}, $t, ${c.field}, ${c.value}::jsonb, ${c.upstream}, ${c.upstreamValue}::jsonb,
                  ${c.upstreamNow}::jsonb, ${c.status}, ${c.createdAt}::timestamptz, ${c.updatedAt}::timestamptz,
                  ${c.checkedAt}::timestamptz, ${StoredCreator.write(Creator.unrecorded)}::jsonb)
                ON CONFLICT (target_kind, target_id, field) DO UPDATE SET value = EXCLUDED.value,
                  upstream = EXCLUDED.upstream, upstream_value = EXCLUDED.upstream_value,
                  upstream_now = EXCLUDED.upstream_now, status = EXCLUDED.status, created_at = EXCLUDED.created_at,
                  updated_at = EXCLUDED.updated_at, checked_at = EXCLUDED.checked_at""".update.run()
        }
      }
