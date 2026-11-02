package com.histmersion.domain.catalogue.events

import com.histmersion.api.{
  CatalogRecord,
  CorrectionCounts,
  Corrections,
  CorrectionTarget,
  EventCatalogueRef,
  EventFields,
  EventTaxonomy,
  TimeSpan
}
import com.histmersion.util.database.Jsonb

import com.augustnagro.magnum.*
import com.histmersion.util.database.given_DbCodec_List

/** The event catalogues' records (`event_catalogue`) and the parts of each catalogue an import has read
  * (`event_import_parts`): what an import merges with and writes. Every method runs on the connection it is given.
  */
private[events] object EventImportRepo:

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
          CROSS JOIN LATERAL (SELECT ${SqlLiteral(EventSql.AnchorSql)} AS pt) a
          ORDER BY e.id"""
      .query[StoredEventRow]
      .run()
      .toList
      .map { r =>
        StoredEvent(
          r.id,
          r.kind,
          TimeSpan(r.notBefore, r.beginBy, r.endFrom, r.notAfter),
          r.lat,
          r.lon,
          r.wikidata,
          r.keys.flatMap(k => k.split("\\|", 2) match { case Array(p, i) => Some(p -> i); case _ => None }).toSet,
          r.catalogue.isDefined,
          r.label
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
  def save(placed: List[(CatalogRecord, CatalogMerge.Placement)])(using DbCon): EventSaved =
    // `var`s: the counts of this write
    var created, updated = 0
    var counts = CorrectionCounts()
    placed.groupBy(_._2.eventId).toList.sortBy(_._1).foreach { (eventId, group) =>
      val recs = group.map(_._1).sortBy(_.key)
      val names = recs.flatMap(r => r.label :: r.names).map(_.trim).filter(_.nonEmpty).distinct
      val wd = recs.flatMap(_.wikidata).headOption
      val catalogue = sql"SELECT catalogue FROM events WHERE id = $eventId".query[Option[String]].run().headOption
      def ownerIn(p: String): Option[CatalogRecord] =
        val storedKeys = sql"SELECT provider, provider_id FROM event_catalogue WHERE event_id = $eventId"
          .query[(String, String)]
          .run()
          .toList
        CatalogRecord.owner(eventId, p, storedKeys ++ recs.map(_.key)).flatMap(k => recs.find(_.key == k))
      def refreshFrom(r: CatalogRecord): Unit =
        counts = counts + refresh(eventId, r, names, wd)
        updated += 1
      group.head._2 match
        case CatalogMerge.Placement.New(_, _) =>
          val creator = recs.find(r => CatalogRecord.eventId(r) == eventId).getOrElse(recs.head)
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
    EventSaved(created, updated, placed.size, counts)

  /** Refresh an import's own event from its owner record `r`, its applied corrections over `r`'s values; a span kept by
    * hand drops the catalogue's stated year and ± (they do not explain it).
    */
  private def refresh(id: String, r: CatalogRecord, names: List[String], wd: Option[String])(using
      DbCon
  ): CorrectionCounts =
    val (keep, outcomes) = CorrectionRepo.reconcile(CorrectionTarget.Event, id, EventFields.upstream(r))
    def text(f: String): Option[Option[String]] = keep.get(f).map(_.asString)
    val label = text(EventFields.Label).flatten.getOrElse(r.label)
    val kind = text(EventFields.Kind).flatten.getOrElse(EventTaxonomy.canonical(r.kind))
    val hist = text(EventFields.Historicity).flatten.getOrElse(r.historicity)
    val desc = text(EventFields.Description).getOrElse(r.description)
    val spanKept = keep.contains(EventFields.Span)
    val ts = keep.get(EventFields.Span).flatMap(_.as[TimeSpan].toOption).getOrElse(r.timeSpan)
    val (central, pm, method) = if spanKept then (None, None, None) else (r.central, r.plusMinus, r.datingMethod)
    val geom: Option[String] = keep.get(EventFields.Geometry) match
      case Some(g) => Option.when(!g.isNull)(g.noSpaces)
      case None    => for la <- r.lat; lo <- r.lon yield EventFields.point(la, lo).noSpaces
    sql"""UPDATE events SET label = $label, kind = $kind,
          not_before = ${ts.notBefore}, begin_by = ${ts.beginBy}, end_from = ${ts.endFrom}, not_after = ${ts.notAfter},
          central = $central, plus_minus = $pm, dating_method = $method,
          historicity = $hist, description = $desc,
          geom = CASE WHEN $geom::text IS NULL THEN NULL ELSE ST_SetSRID(ST_GeomFromGeoJSON($geom::text), 4326) END,
          wikidata = coalesce(wikidata, $wd),
          names = (SELECT coalesce(array_agg(DISTINCT n), '{}') FROM unnest(names || $names::text[]) n)
        WHERE id = $id""".update.run()
    Corrections.counts(outcomes)

  private def pointSql(r: CatalogRecord): Option[String] =
    for la <- r.lat; lo <- r.lon yield s"SRID=4326;POINT($lo $la)"

  private def insertEvent(id: String, r: CatalogRecord, names: List[String], wd: Option[String])(using DbCon): Unit =
    val geom = pointSql(r)
    sql"""INSERT INTO events (id, label, kind, not_before, begin_by, end_from, not_after, historicity, geom,
                              description, wikidata, names, catalogue, central, plus_minus, dating_method)
          VALUES ($id, ${r.label}, ${EventTaxonomy.canonical(r.kind)}, ${r.timeSpan.notBefore}, ${r.timeSpan.beginBy},
                  ${r.timeSpan.endFrom}, ${r.timeSpan.notAfter}, ${r.historicity},
                  CASE WHEN $geom::text IS NULL THEN NULL ELSE ST_GeomFromEWKT($geom::text) END,
                  ${r.description}, $wd, $names::text[], ${r.provider}, ${r.central}, ${r.plusMinus},
                  ${r.datingMethod})""".update.run()

  /** An event the import does not own: its empty Wikidata item filled, its names added to. */
  private def fill(id: String, names: List[String], wd: Option[String])(using DbCon): Unit =
    sql"""UPDATE events SET wikidata = coalesce(wikidata, $wd),
          names = (SELECT coalesce(array_agg(DISTINCT n), '{}') FROM unnest(names || $names::text[]) n)
        WHERE id = $id AND (wikidata IS DISTINCT FROM coalesce(wikidata, $wd)
          OR NOT (names @> $names::text[]))""".update.run()

  /** The catalogue an event was created from, with its OWNER record ([[CatalogRecord.owner]]) — what the event's fields
    * are compared with when a person corrects them. `None`: not an import's event.
    */
  def upstreamOf(id: String)(using DbCon): Option[(String, CatalogRecord)] =
    sql"SELECT catalogue FROM events WHERE id = $id".query[Option[String]].run().headOption.flatten.flatMap { cat =>
      val rows = sql"""SELECT provider, provider_id, record::text FROM event_catalogue
                       WHERE event_id = $id AND provider = $cat ORDER BY provider_id"""
        .query[(String, String, String)]
        .run()
        .toList
      CatalogRecord
        .owner(id, cat, rows.map(r => (r._1, r._2)))
        .flatMap(k => rows.find(r => (r._1, r._2) == k))
        .flatMap(r => Jsonb.read[CatalogRecord](r._3))
        .map(cat -> _)
    }

  /** The stored records of `provider` (every catalogue's when `None`), as read, with the event each is of and that
    * event's kind, in key order — what `events rederive` computes again. A row whose record no longer decodes is
    * counted in the second value and left alone.
    */
  def records(provider: Option[String])(using DbCon): (List[(String, String, CatalogRecord)], Int) =
    val rows =
      sql"""SELECT c.event_id, e.kind, c.record::text FROM event_catalogue c JOIN events e ON e.id = c.event_id
                     WHERE ${provider.isEmpty} OR c.provider = ${provider.getOrElse("")}
                     ORDER BY c.provider, c.provider_id""".query[(String, String, String)].run().toList
    val decoded = rows.map((id, kind, j) => Jsonb.read[CatalogRecord](j).map((id, kind, _)))
    (decoded.flatten, decoded.count(_.isEmpty))

  /** The kind each stored record of `provider` has, by key — what an item read again in another class is compared with
    * ([[EventProviders.WikidataEvents.moreSpecific]]).
    */
  def storedKinds(provider: String)(using DbCon): Map[(String, String), String] =
    sql"SELECT provider, provider_id, kind FROM event_catalogue WHERE provider = $provider"
      .query[(String, String, String)]
      .run()
      .map((p, i, k) => (p, i) -> k)
      .toMap

  private def upsertRecord(eventId: String, r: CatalogRecord)(using DbCon): Unit =
    val json = Jsonb.write(r)
    sql"""INSERT INTO event_catalogue (provider, provider_id, event_id, url, label, kind, not_before, begin_by,
                                       end_from, not_after, lat, lon, vei, magnitude, deaths, historicity, record)
          VALUES (${r.provider}, ${r.providerId}, $eventId, ${r.url}, ${r.label}, ${EventTaxonomy.canonical(r.kind)},
                  ${r.timeSpan.notBefore}, ${r.timeSpan.beginBy}, ${r.timeSpan.endFrom}, ${r.timeSpan.notAfter},
                  ${r.lat}, ${r.lon}, ${r.vei}, ${r.magnitude}, ${r.deaths}, ${r.historicity}, $json::jsonb)
          ON CONFLICT (provider, provider_id) DO UPDATE SET
            event_id = EXCLUDED.event_id, url = EXCLUDED.url, label = EXCLUDED.label, kind = EXCLUDED.kind,
            not_before = EXCLUDED.not_before, begin_by = EXCLUDED.begin_by, end_from = EXCLUDED.end_from,
            not_after = EXCLUDED.not_after, lat = EXCLUDED.lat, lon = EXCLUDED.lon, vei = EXCLUDED.vei,
            magnitude = EXCLUDED.magnitude, deaths = EXCLUDED.deaths, historicity = EXCLUDED.historicity,
            record = EXCLUDED.record, retrieved_at = now()""".update.run()

  // ── the parts of a catalogue read ───────────────────────────────────────────────────────────

  def partsDone(provider: String)(using DbCon): Set[String] =
    sql"SELECT part FROM event_import_parts WHERE provider = $provider".query[String].run().toSet

  def markPart(provider: String, part: String, records: Int)(using DbCon): Unit =
    val _ =
      sql"""INSERT INTO event_import_parts (provider, part, records) VALUES ($provider, $part, $records)
            ON CONFLICT (provider, part) DO UPDATE SET records = EXCLUDED.records, done_at = now()""".update.run()

  def clearParts(provider: String)(using DbCon): Unit =
    val _ = sql"DELETE FROM event_import_parts WHERE provider = $provider".update.run()

  /** The catalogue rows of one event, with each provider's licence and citation. */
  def catalogueRefs(id: String)(using DbCon): List[EventCatalogueRef] =
    sql"""SELECT provider, provider_id, url, vei, magnitude, deaths, retrieved_at::text
          FROM event_catalogue WHERE event_id = $id ORDER BY provider, provider_id"""
      .query[CatalogueRefRow]
      .run()
      .toList
      .map { r =>
        val i = CatalogProvider.info(r.provider)
        EventCatalogueRef(
          r.provider,
          r.providerId,
          r.url.getOrElse(""),
          i.fold(r.provider)(_.title),
          i.fold("")(_.licence),
          i.fold("")(_.citation),
          r.vei,
          r.magnitude,
          r.deaths,
          Some(r.retrievedAt)
        )
      }
