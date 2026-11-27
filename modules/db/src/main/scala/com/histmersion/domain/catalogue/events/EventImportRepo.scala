package com.histmersion
package domain.catalogue.events
import com.histmersion.util.json.RawJson.*

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{
  CatalogRecord,
  Corrections,
  EventCatalogueRef,
  EventEntry,
  EventFields,
  EventSummary,
  EventTaxonomy
}
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.catalogue.Event
import com.histmersion.util.database.Jsonb

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.given_DbCodec_List
import com.histmersion.api.catalogue.Correction
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.Unused
import com.histmersion.domain.{CreatedAt, Creator}
import com.histmersion.util.database.StoredCreator

/** The event catalogues' records (`event_catalogue`) and the parts of each catalogue an import has read
  * (`event_import_parts`): what an import merges with and writes. Every method runs on the connection it is given.
  */
private[events] object EventImportRepo:
  type PartName = PartName.T
  object PartName extends Newtype[String, Pure]

  /** Every stored event, as the merge sees it ([[CatalogMerge.place]]), in id order (the merge's choice never depends
    * on the order rows come in — it ranks candidates itself — but a stable order keeps every run reproducible).
    */

  def stored(using DbCon): List[StoredEvent] =
    sql"""SELECT e.id, e.kind, e.not_before, e.begin_by, e.end_from, e.not_after,
                 ST_Y(a.pt), ST_X(a.pt), e.wikidata,
                 coalesce((SELECT array_agg(c.provider || '|' || c.provider_id) FROM event_catalogue c
                           WHERE c.event_id = e.id), '{}'::text[]),
                 e.catalogue, e.label
          FROM events e LEFT JOIN places ep ON ep.id = e.place_id
          CROSS JOIN LATERAL (SELECT ${EventSql.AnchorSql} AS pt) a
          ORDER BY e.id"""
      .query[StoredEventRow]
      .run()
      .toList
      .map { r =>
        StoredEvent(
          Event.Id.assume(r.id.value),
          StoredEvent.Kind(r.kind.value),
          TimeSpan(
            r.notBefore.map(v => Year(v.value)),
            r.beginBy.map(v => Year(v.value)),
            r.endFrom.map(v => Year(v.value)),
            r.notAfter.map(v => Year(v.value))
          ),
          r.lat,
          r.lon,
          r.wikidata.map(w => EventEntry.Wikidata(w.value)),
          r.keys
            .flatMap(k =>
              k.value.split("\\|", 2) match
                case Array(p, i) => Some(CatalogRecord.Provider(p) -> EventCatalogueRef.ProviderId(i))
                case _           => None
            )
            .toSet,
          StoredEvent.Catalogue(r.catalogue.isDefined),
          StoredEvent.Label(r.label.value)
        )
      }

  /** Write placed records. A NEW event is created from the record it is named after (its label, kind, dating, point,
    * historicity, Wikidata item). An event an import of a catalogue CREATED is refreshed from its OWNER record
    * ([[CatalogRecord.owner]]: that catalogue's record it is named after) whenever that record is written again — with
    * the corrections a person made to it applied over the catalogue's values ([[Corrections]]: kept, retired when the
    * catalogue now agrees, flagged when it changed to something else). An event made by hand or compiled from sources
    * is never changed, except that an empty Wikidata item is filled and the names are added to. Every record is kept in
    * `event_catalogue` under its key.
    */
  def save(placed: List[(CatalogRecord, CatalogMerge.Placement)])(using
      DbCon
  )(using
      Creator.New,
      CreatedAt
  ): EventSaved =
    // `var`s: the counts of this write
    var created, updated = 0
    var counts = Correction.Counts()
    placed.groupBy(_._2.eventId).toList.sortBy(_._1.value).foreach { (eventId, group) =>
      val recs = group.map(_._1).sortBy(r => (r.provider.value, r.providerId.value))
      val names = recs.flatMap(r => r.label.value :: r.names.map(_.value)).map(_.trim).filter(_.nonEmpty).distinct
      val wd = recs.flatMap(_.wikidata).headOption
      val catalogue =
        sql"SELECT catalogue FROM events WHERE id = $eventId".query[Option[CatalogRecord.Provider]].run().headOption
      def ownerIn(p: CatalogRecord.Provider): Option[CatalogRecord] =
        val storedKeys = sql"SELECT provider, provider_id FROM event_catalogue WHERE event_id = $eventId"
          .query[(CatalogRecord.Provider, EventCatalogueRef.ProviderId)]
          .run()
          .toList
        CatalogRecord
          .owner(CatalogRecord.EventId(eventId.value), p, storedKeys ++ recs.map(_.key))
          .flatMap(k => recs.find(_.key == k))
      def refreshFrom(r: CatalogRecord): Unit =
        counts = counts + refresh(eventId, r, names, wd)
        updated += 1
      group.head._2 match
        case CatalogMerge.Placement.New(_, _) =>
          val creator = recs.find(r => CatalogRecord.eventId(r).value == eventId.value).getOrElse(recs.head)
          catalogue match
            case None =>
              insertEvent(eventId, creator, names, wd)
              created += 1
            case Some(Some(p)) => ownerIn(p).fold(fill(eventId, names, wd))(refreshFrom)
            case Some(None)    => fill(eventId, names, wd)
        case CatalogMerge.Placement.Existing(_, _) =>
          catalogue.flatten match
            case Some(p) => ownerIn(p).fold(fill(eventId, names, wd))(refreshFrom)
            case None    => fill(eventId, names, wd)
      recs.foreach(r => upsertRecord(eventId, r))
    }
    EventSaved(EventSaved.Created(created), EventSaved.Updated(updated), EventSaved.Rows(placed.size), counts)

  /** Refresh an import's own event from its owner record `r`, its applied corrections over `r`'s values; a span kept by
    * hand drops the catalogue's stated year and ± (they do not explain it).
    */
  private def refresh(id: Event.Id, r: CatalogRecord, names: List[String], wd: Option[EventEntry.Wikidata])(using
      DbCon
  ): Correction.Counts =
    val (keep, outcomes) = CorrectionRepo.reconcile(
      Correction.TargetKind(Correction.Target.Event),
      Correction.TargetId(id.value),
      EventFields.upstream(r).map((f, v) => Correction.Field(f) -> v)
    )
    def text(f: String): Option[Option[String]] = keep.get(Correction.Field(f)).map(_.asString)
    val label: String = text(EventFields.Label).flatten.getOrElse(r.label.value)
    val kind: String =
      text(EventFields.Kind).flatten.getOrElse(EventTaxonomy.canonical(EventSummary.Kind(r.kind.value)).value)
    val hist: String = text(EventFields.Historicity).flatten.getOrElse(r.historicity.value)
    val desc: Option[String] = text(EventFields.Description).getOrElse(r.description.map(_.value))
    val spanKept = keep.contains(Correction.Field(EventFields.Span))
    val ts = keep.get(Correction.Field(EventFields.Span)).flatMap(_.as[TimeSpan].toOption).getOrElse(r.timeSpan)
    val (central, pm, method) = if spanKept then (None, None, None) else (r.central, r.plusMinus, r.datingMethod)
    val geom: Option[String] = keep.get(Correction.Field(EventFields.Geometry)) match
      case Some(g) => Option.when(!g.isNull)(g.noSpaces)
      case None    => for la <- r.lat; lo <- r.lon yield EventFields.point(la, lo).noSpaces
    Unused(
      sql"""UPDATE events SET label = $label, kind = $kind,
          not_before = ${ts.notBefore}, begin_by = ${ts.beginBy}, end_from = ${ts.endFrom}, not_after = ${ts.notAfter},
          central = $central, plus_minus = $pm, dating_method = $method,
          historicity = $hist, description = $desc,
          geom = CASE WHEN $geom::text IS NULL THEN NULL ELSE ST_SetSRID(ST_GeomFromGeoJSON($geom::text), 4326) END,
          wikidata = coalesce(wikidata, $wd),
          names = (SELECT coalesce(array_agg(DISTINCT n), '{}') FROM unnest(names || $names::text[]) n)
        WHERE id = $id""".update.run(),
      Unused.Why.Held
    )
    Corrections.counts(outcomes)

  private def pointSql(r: CatalogRecord): Option[String] =
    for la <- r.lat; lo <- r.lon yield s"SRID=4326;POINT($lo $la)"

  private def insertEvent(id: Event.Id, r: CatalogRecord, names: List[String], wd: Option[EventEntry.Wikidata])(using
      DbCon
  )(using creator: Creator.New, at: CreatedAt): Unit =
    val geom = pointSql(r)
    sql"""INSERT INTO events (id, label, kind, not_before, begin_by, end_from, not_after, historicity, geom,
                              description, wikidata, names, catalogue, central, plus_minus, dating_method, creator,
                              created_at)
          VALUES ($id, ${r.label}, ${EventTaxonomy.canonical(
        EventSummary.Kind(r.kind.value)
      )}, ${r.timeSpan.notBefore}, ${r.timeSpan.beginBy},
                  ${r.timeSpan.endFrom}, ${r.timeSpan.notAfter}, ${r.historicity},
                  CASE WHEN $geom::text IS NULL THEN NULL ELSE ST_GeomFromEWKT($geom::text) END,
                  ${r.description}, $wd, $names::text[], ${r.provider}, ${r.central}, ${r.plusMinus},
                  ${r.datingMethod}, ${StoredCreator.column(creator)}::jsonb, ${at.iso}::timestamptz)""".update.run()

  /** An event the import does not own: its empty Wikidata item filled, its names added to. */
  private def fill(id: Event.Id, names: List[String], wd: Option[EventEntry.Wikidata])(using DbCon): Unit =
    Unused(
      sql"""UPDATE events SET wikidata = coalesce(wikidata, $wd),
          names = (SELECT coalesce(array_agg(DISTINCT n), '{}') FROM unnest(names || $names::text[]) n)
        WHERE id = $id AND (wikidata IS DISTINCT FROM coalesce(wikidata, $wd)
          OR NOT (names @> $names::text[]))""".update.run(),
      Unused.Why.Idempotent
    )

  /** The catalogue an event was created from, with its OWNER record ([[CatalogRecord.owner]]) — what the event's fields
    * are compared with when a person corrects them. `None`: not an import's event.
    */
  def upstreamOf(id: Event.Id)(using DbCon): Option[(CatalogRecord.Provider, CatalogRecord)] =
    sql"SELECT catalogue FROM events WHERE id = $id"
      .query[Option[CatalogRecord.Provider]]
      .run()
      .headOption
      .flatten
      .flatMap { cat =>
        val rows = sql"""SELECT provider, provider_id, record::text FROM event_catalogue
                       WHERE event_id = $id AND provider = $cat ORDER BY provider_id"""
          .query[(CatalogRecord.Provider, EventCatalogueRef.ProviderId, Jsonb.JsonText)]
          .run()
          .toList
        CatalogRecord
          .owner(CatalogRecord.EventId(id.value), cat, rows.map(r => (r._1, r._2)))
          .flatMap(k => rows.find(r => (r._1, r._2) == k))
          .flatMap(r => Jsonb.read[CatalogRecord](r._3))
          .map(cat -> _)
      }

  /** The stored records of `provider` (every catalogue's when `None`), as read, with the event each is of and that
    * event's kind, in key order — what `events rederive` computes again. A row whose record no longer decodes is
    * counted in the second value and left alone.
    */
  def records(provider: Option[CatalogRecord.Provider])(using
      DbCon
  ): (List[(Event.Id, EventSummary.Kind, CatalogRecord)], RowCount) =
    val rows =
      sql"""SELECT c.event_id, e.kind, c.record::text FROM event_catalogue c JOIN events e ON e.id = c.event_id
                     WHERE ${provider.isEmpty} OR c.provider = ${provider.fold("")(_.value)}
                     ORDER BY c.provider, c.provider_id""".query[(String, String, String)].run().toList
    val decoded =
      rows.map((id, kind, j) =>
        Jsonb.read[CatalogRecord](Jsonb.JsonText(j)).map((Event.Id.assume(id), EventSummary.Kind(kind), _))
      )
    (decoded.flatten, RowCount(decoded.count(_.isEmpty)))

  /** The kind each stored record of `provider` has, by key — what an item read again in another class is compared with
    * ([[EventProviders.WikidataEvents.moreSpecific]]).
    */
  def storedKinds(
      provider: CatalogRecord.Provider
  )(using DbCon): Map[(CatalogRecord.Provider, EventCatalogueRef.ProviderId), EventSummary.Kind] =
    sql"SELECT provider, provider_id, kind FROM event_catalogue WHERE provider = $provider"
      .query[(CatalogRecord.Provider, EventCatalogueRef.ProviderId, EventSummary.Kind)]
      .run()
      .map((p, i, k) => (p, i) -> k)
      .toMap

  private def upsertRecord(eventId: Event.Id, r: CatalogRecord)(using DbCon): Unit =
    val json = Jsonb.write(r)
    sql"""INSERT INTO event_catalogue (provider, provider_id, event_id, url, label, kind, not_before, begin_by,
                                       end_from, not_after, lat, lon, vei, magnitude, deaths, historicity, record)
          VALUES (${r.provider}, ${r.providerId}, $eventId, ${r.url}, ${r.label}, ${EventTaxonomy.canonical(
        EventSummary.Kind(r.kind.value)
      )},
                  ${r.timeSpan.notBefore}, ${r.timeSpan.beginBy}, ${r.timeSpan.endFrom}, ${r.timeSpan.notAfter},
                  ${r.lat}, ${r.lon}, ${r.vei}, ${r.magnitude}, ${r.deaths}, ${r.historicity}, $json::jsonb)
          ON CONFLICT (provider, provider_id) DO UPDATE SET
            event_id = EXCLUDED.event_id, url = EXCLUDED.url, label = EXCLUDED.label, kind = EXCLUDED.kind,
            not_before = EXCLUDED.not_before, begin_by = EXCLUDED.begin_by, end_from = EXCLUDED.end_from,
            not_after = EXCLUDED.not_after, lat = EXCLUDED.lat, lon = EXCLUDED.lon, vei = EXCLUDED.vei,
            magnitude = EXCLUDED.magnitude, deaths = EXCLUDED.deaths, historicity = EXCLUDED.historicity,
            record = EXCLUDED.record, retrieved_at = now()""".update.run()

  // ── the parts of a catalogue read ───────────────────────────────────────────────────────────

  def partsDone(provider: CatalogRecord.Provider)(using DbCon): Set[PartName] =
    sql"SELECT part FROM event_import_parts WHERE provider = $provider".query[PartName].run().toSet

  def markPart(provider: CatalogRecord.Provider, part: PartName, records: RowCount)(using DbCon): Unit =
    val _ =
      sql"""INSERT INTO event_import_parts (provider, part, records) VALUES ($provider, $part, $records)
            ON CONFLICT (provider, part) DO UPDATE SET records = EXCLUDED.records, done_at = now()""".update.run()

  def clearParts(provider: CatalogRecord.Provider)(using DbCon): Unit =
    Unused(sql"DELETE FROM event_import_parts WHERE provider = $provider".update.run(), Unused.Why.Idempotent)

  /** The catalogue rows of one event, with each provider's licence and citation. */
  def catalogueRefs(id: Event.Id)(using DbCon): List[EventCatalogueRef] =
    sql"""SELECT provider, provider_id, url, vei, magnitude, deaths, retrieved_at::text
          FROM event_catalogue WHERE event_id = $id ORDER BY provider, provider_id"""
      .query[CatalogueRefRow]
      .run()
      .toList
      .map { r =>
        val provider = CatalogRecord.Provider(r.provider.value)
        val i = CatalogProvider.info(provider)
        EventCatalogueRef(
          provider,
          EventCatalogueRef.ProviderId(r.providerId.value),
          EventCatalogueRef.Url(r.url.fold("")(_.value)),
          i.fold(EventCatalogueRef.Title(r.provider.value))(_.title),
          i.fold(EventCatalogueRef.Licence(""))(_.licence),
          i.fold(EventCatalogueRef.Citation(""))(_.citation),
          r.vei.map(v => EventCatalogueRef.Vei(v.value)),
          r.magnitude.map(m => EventCatalogueRef.Magnitude(m.value)),
          r.deaths.map(d => EventCatalogueRef.Deaths(d.value)),
          Some(EventCatalogueRef.RetrievedAt(r.retrievedAt.value))
        )
      }
