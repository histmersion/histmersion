package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*
import io.circe.syntax.*
import javax.sql.DataSource

private[db] final case class StoredEventRow(id: String, kind: String, notBefore: Option[Long], beginBy: Option[Long],
    endFrom: Option[Long], notAfter: Option[Long], lat: Option[Double], lon: Option[Double],
    wikidata: Option[String], keys: List[String], catalogue: Option[String], label: String)

private[db] final case class CatalogueRefRow(provider: String, providerId: String, url: Option[String],
    vei: Option[Int], magnitude: Option[Double], deaths: Option[Long], retrievedAt: String)

private[db] final case class NearFindingRow(id: String, citekey: String, claim: String, km: Double, yearGap: Long,
    periodStart: Option[Long], periodEnd: Option[Long])

private[db] final case class CandidateRow(eventId: String, citekey: String, score: Double, status: String,
    title: Option[String], findings: List[String], passages: String, reasons: List[String])

final case class LeadRow(id: String, label: String, kind: String, notBefore: Option[Long],
    beginBy: Option[Long], endFrom: Option[Long], notAfter: Option[Long], tier: String, vei: Option[Int],
    magnitude: Option[Double], findingsNear: Int, proposed: Int, attesting: Int)

given DbCodec[StoredEventRow]  = DbCodec.derived
given DbCodec[CatalogueRefRow] = DbCodec.derived
given DbCodec[NearFindingRow]  = DbCodec.derived
given DbCodec[CandidateRow]    = DbCodec.derived
given DbCodec[LeadRow]         = DbCodec.derived

/** Events from catalogues (#1449): what an import merges with and writes, the parts it has read, the
  * proposed links to the corpus and the facts the leads are computed from. */
object EventCatalogRepo:

  /** The SQL of an event's TIER over `events e`: attested when it names a source or an attestation
    * names it. */
  val TierSql: String =
    "CASE WHEN e.citekey IS NOT NULL OR EXISTS (SELECT 1 FROM attestations ta WHERE ta.target_type = 'event' " +
      s"AND ta.target_id = e.id) THEN '${EventTier.Attested}' ELSE '${EventTier.Catalogue}' END"

  /** An event's anchor point over `events e` with its place `ep` joined: its own geometry, else its
    * place's (a point, or the point-on-surface of an area). */
  val AnchorSql: String =
    "(SELECT CASE WHEN GeometryType(g) = 'POINT' THEN g ELSE ST_PointOnSurface(g) END " +
      "FROM (SELECT coalesce(e.geom, ep.geom) AS g) x)"

  /** SQL: the box `km` around the point `pt` in degrees — what lets the findings' geometry index answer
    * before the exact geography distance is computed (a `::geography` cast cannot use it). */
  private[db] def boxAround(pt: String, km: Double): String =
    val dy = km / 111.32
    s"ST_Expand($pt, $dy / greatest(cos(radians(ST_Y($pt))), 0.05), $dy)"

  // ── the import ──────────────────────────────────────────────────────────────────────────────

  /** Every stored event, as the merge sees it ([[CatalogMerge.place]]), in id order (the merge's
    * choice never depends on the order rows come in — it ranks candidates itself — but a stable
    * order keeps every run reproducible). */
  def stored(ds: DataSource): List[StoredEvent] =
    connect(ds):
      sql"""SELECT e.id, e.kind, e.not_before, e.begin_by, e.end_from, e.not_after,
                   ST_Y(a.pt), ST_X(a.pt), e.wikidata,
                   coalesce((SELECT array_agg(c.provider || '|' || c.provider_id) FROM event_catalogue c
                             WHERE c.event_id = e.id), '{}'::text[]),
                   e.catalogue, e.label
            FROM events e LEFT JOIN places ep ON ep.id = e.place_id
            CROSS JOIN LATERAL (SELECT ${SqlLiteral(AnchorSql)} AS pt) a
            ORDER BY e.id"""
        .query[StoredEventRow].run().toList.map { r =>
          StoredEvent(r.id, r.kind, TimeSpan(r.notBefore, r.beginBy, r.endFrom, r.notAfter), r.lat, r.lon, r.wikidata,
            r.keys.flatMap(k => k.split("\\|", 2) match { case Array(p, i) => Some(p -> i); case _ => None }).toSet,
            r.catalogue.isDefined, r.label)
        }

  /** What [[save]] did: events created, events an import made before refreshed, catalogue rows
    * written, and what became of the refreshed events' corrections by hand. */
  final case class Saved(created: Int, updated: Int, rows: Int, corrections: CorrectionCounts = CorrectionCounts())

  /** Write placed records, in one transaction. A NEW event is created from the record it is named
    * after (its label, kind, dating, point, historicity, Wikidata item). An event an import of a
    * catalogue CREATED is refreshed from its OWNER record ([[CatalogRecord.owner]]: that catalogue's
    * record it is named after) whenever that record is written again — with the corrections a person
    * made to it applied over the catalogue's values (V088, [[Corrections]]: kept, retired when the
    * catalogue now agrees, flagged when it changed to something else). An event made by hand or
    * compiled from sources is never changed, except that an empty Wikidata item is filled and the
    * names are added to. Every record is kept in `event_catalogue` under its key. */
  def save(ds: DataSource, placed: List[(CatalogRecord, CatalogMerge.Placement)]): Saved =
    transact(ds):
      // `var`s: the counts of this write
      var created, updated = 0
      var counts = CorrectionCounts()
      placed.groupBy(_._2.eventId).toList.sortBy(_._1).foreach { (eventId, group) =>
        val recs  = group.map(_._1).sortBy(_.key)
        val names = recs.flatMap(r => r.label :: r.names).map(_.trim).filter(_.nonEmpty).distinct
        val wd    = recs.flatMap(_.wikidata).headOption
        val catalogue = sql"SELECT catalogue FROM events WHERE id = $eventId".query[Option[String]].run().headOption
        def ownerIn(p: String): Option[CatalogRecord] =
          val storedKeys = sql"SELECT provider, provider_id FROM event_catalogue WHERE event_id = $eventId"
            .query[(String, String)].run().toList
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
      Saved(created, updated, placed.size, counts)

  /** Refresh an import's own event from its owner record `r`, its applied corrections over `r`'s
    * values; a span kept by hand drops the catalogue's stated year and ± (they do not explain it). */
  private def refresh(id: String, r: CatalogRecord, names: List[String], wd: Option[String])(using DbCon): CorrectionCounts =
    val (keep, outcomes) = CorrectionRepo.reconcile(CorrectionTarget.Event, id, EventFields.upstream(r))
    def text(f: String): Option[Option[String]] = keep.get(f).map(_.asString)
    val label = text(EventFields.Label).flatten.getOrElse(r.label)
    val kind  = text(EventFields.Kind).flatten.getOrElse(EventTaxonomy.canonical(r.kind))
    val hist  = text(EventFields.Historicity).flatten.getOrElse(r.historicity)
    val desc  = text(EventFields.Description).getOrElse(r.description)
    val spanKept = keep.contains(EventFields.Span)
    val ts    = keep.get(EventFields.Span).flatMap(_.as[TimeSpan].toOption).getOrElse(r.timeSpan)
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

  /** The catalogue an event was created from, with its OWNER record ([[CatalogRecord.owner]]) — what
    * the event's fields are compared with when a person corrects them. `None`: not an import's event. */
  private[db] def upstreamOf(id: String)(using DbCon): Option[(String, CatalogRecord)] =
    sql"SELECT catalogue FROM events WHERE id = $id".query[Option[String]].run().headOption.flatten.flatMap { cat =>
      val rows = sql"""SELECT provider, provider_id, record::text FROM event_catalogue
                       WHERE event_id = $id AND provider = $cat ORDER BY provider_id"""
        .query[(String, String, String)].run().toList
      CatalogRecord.owner(id, cat, rows.map(r => (r._1, r._2)))
        .flatMap(k => rows.find(r => (r._1, r._2) == k))
        .flatMap(r => io.circe.parser.decode[CatalogRecord](r._3).toOption)
        .map(cat -> _)
    }

  /** The stored records of `provider` (every catalogue's when `None`), as read, with the event each
    * is of and that event's kind, in key order — what `events rederive` computes again. A row whose record no longer
    * decodes is counted in the second value and left alone. */
  def records(ds: DataSource, provider: Option[String]): (List[(String, String, CatalogRecord)], Int) =
    connect(ds):
      val rows = sql"""SELECT c.event_id, e.kind, c.record::text FROM event_catalogue c JOIN events e ON e.id = c.event_id
                       WHERE ${provider.isEmpty} OR c.provider = ${provider.getOrElse("")}
                       ORDER BY c.provider, c.provider_id""".query[(String, String, String)].run().toList
      val decoded = rows.map((id, kind, j) => io.circe.parser.decode[CatalogRecord](j).toOption.map((id, kind, _)))
      (decoded.flatten, decoded.count(_.isEmpty))

  /** The kind each stored record of `provider` has, by key — what an item read again in another class
    * is compared with ([[EventProviders.WikidataEvents.moreSpecific]]). */
  def storedKinds(ds: DataSource, provider: String): Map[(String, String), String] =
    connect(ds):
      sql"SELECT provider, provider_id, kind FROM event_catalogue WHERE provider = $provider"
        .query[(String, String, String)].run().map((p, i, k) => (p, i) -> k).toMap

  private def upsertRecord(eventId: String, r: CatalogRecord)(using DbCon): Unit =
    val json = r.asJson.noSpaces
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

  def partsDone(ds: DataSource, provider: String): Set[String] =
    connect(ds)(sql"SELECT part FROM event_import_parts WHERE provider = $provider".query[String].run().toSet)

  def markPart(ds: DataSource, provider: String, part: String, records: Int): Unit =
    val _ = transact(ds):
      sql"""INSERT INTO event_import_parts (provider, part, records) VALUES ($provider, $part, $records)
            ON CONFLICT (provider, part) DO UPDATE SET records = EXCLUDED.records, done_at = now()""".update.run()

  def clearParts(ds: DataSource, provider: String): Unit =
    val _ = transact(ds)(sql"DELETE FROM event_import_parts WHERE provider = $provider".update.run())

  // ── an event's context (its card) ───────────────────────────────────────────────────────────

  /** The tier and names of one event. */
  def tierAndNames(ds: DataSource, id: String): Option[(String, List[String], Option[String])] =
    connect(ds):
      sql"""SELECT ${SqlLiteral(TierSql)}, e.names, e.catalogue FROM events e WHERE e.id = $id"""
        .query[(String, List[String], Option[String])].run().headOption

  /** The catalogue rows of one event, with each provider's licence and citation. */
  def catalogueRefs(ds: DataSource, id: String): List[EventCatalogueRef] =
    connect(ds):
      sql"""SELECT provider, provider_id, url, vei, magnitude, deaths, retrieved_at::text
            FROM event_catalogue WHERE event_id = $id ORDER BY provider, provider_id"""
        .query[CatalogueRefRow].run().toList.map { r =>
          val info = CatalogProvider.info(r.provider)
          EventCatalogueRef(r.provider, r.providerId, r.url.getOrElse(""), info.fold(r.provider)(_.title),
            info.fold("")(_.licence), info.fold("")(_.citation), r.vei, r.magnitude, r.deaths, Some(r.retrievedAt))
        }

  /** The sources attesting one event: its own citekey, then the attestations naming it. */
  def attestingSources(ds: DataSource, id: String): List[EventSourceRef] =
    connect(ds):
      val own = sql"""SELECT s.citekey, s.title, s.year FROM events e JOIN sources s ON s.citekey = e.citekey
                      WHERE e.id = $id""".query[(String, Option[String], Option[Int])].run().toList
        .map((ck, t, y) => EventSourceRef(ck, t, y, "citekey"))
      val att = sql"""SELECT DISTINCT ON (a.citekey) a.citekey, s.title, s.year, a.quote
                      FROM attestations a JOIN sources s ON s.citekey = a.citekey
                      WHERE a.target_type = 'event' AND a.target_id = $id ORDER BY a.citekey, a.id"""
        .query[(String, Option[String], Option[Int], Option[String])].run().toList
        .map((ck, t, y, q) => EventSourceRef(ck, t, y, "attestation", q))
      (own ++ att.filterNot(a => own.exists(_.citekey == a.citekey)))

  /** The articles citing any of `citekeys`. */
  def articlesCiting(ds: DataSource, citekeys: List[String], limit: Int): List[EventArticleRef] =
    if citekeys.isEmpty then Nil
    else connect(ds):
      sql"""SELECT DISTINCT ON (a.slug) a.slug, a.title, l.target
            FROM article_links l JOIN articles a ON a.slug = l.src_slug
            WHERE l.target_kind = 'source' AND l.target = ANY($citekeys::text[])
            ORDER BY a.slug, l.target LIMIT $limit""".query[EventArticleRef].run().toList

  /** The dated, placed findings within `km` of an event's anchor and `years` of its outer span, nearest
    * in time then space first: their distance and the years between (0 = overlapping). */
  def nearFindings(ds: DataSource, id: String, km: Double, years: Long, limit: Int): List[EventFindingRef] =
    connect(ds):
      sql"""WITH ev AS (
              SELECT ${SqlLiteral(AnchorSql)} AS pt, e.outer_span AS span
              FROM events e LEFT JOIN places ep ON ep.id = e.place_id WHERE e.id = $id)
            SELECT f.id, f.citekey, f.claim,
                   ST_Distance(f.geom::geography, ev.pt::geography) / 1000.0,
                   CASE WHEN f.period && ev.span THEN 0
                        ELSE greatest(${SqlLiteral(PeriodSql.start("f.period"))} - (${SqlLiteral(PeriodSql.end("ev.span"))}),
                                      ${SqlLiteral(PeriodSql.start("ev.span"))} - (${SqlLiteral(PeriodSql.end("f.period"))})) END,
                   ${SqlLiteral(PeriodSql.cols("f.period"))}
            FROM ev JOIN findings f
              ON f.geom IS NOT NULL AND ev.pt IS NOT NULL AND ev.span IS NOT NULL
             AND NOT lower_inf(f.period) AND NOT upper_inf(f.period)
             AND f.geom && ${SqlLiteral(boxAround("ev.pt", km))}
             AND ST_DWithin(f.geom::geography, ev.pt::geography, ${km * 1000.0})
             AND f.period && int8range(${SqlLiteral(PeriodSql.start("ev.span"))} - $years, ${SqlLiteral(PeriodSql.end("ev.span"))} + $years, '[]')
            ORDER BY 5, 4, f.id LIMIT $limit"""
        .query[NearFindingRow].run().toList
        .map(r => EventFindingRef(r.id, r.citekey, r.claim, r.km, r.yearGap.max(0), r.periodStart, r.periodEnd))

  // ── proposed links ──────────────────────────────────────────────────────────────────────────

  /** The sources whose chunks contain `name` as a phrase (the FTS index), at most `limit`. */
  def sourcesNaming(ds: DataSource, name: String, limit: Int): List[String] =
    connect(ds):
      sql"""SELECT citekey FROM source_chunks WHERE tsv @@ phraseto_tsquery('english', $name)
            GROUP BY citekey ORDER BY count(*) DESC, citekey LIMIT $limit""".query[String].run().toList

  /** The stored candidates of one event, best first; `withRejected` includes the rejected ones. */
  def candidates(ds: DataSource, id: String, withRejected: Boolean = false): List[EventLinkCandidate] =
    connect(ds):
      sql"""SELECT c.event_id, c.citekey, c.score, c.status, s.title, c.findings, c.passages::text, c.reasons
            FROM event_link_candidates c JOIN sources s ON s.citekey = c.citekey
            WHERE c.event_id = $id AND (${withRejected} OR c.status <> 'rejected')
            ORDER BY c.status = 'confirmed' DESC, c.score DESC, c.citekey"""
        .query[CandidateRow].run().toList.map { r =>
          val ps = io.circe.parser.decode[List[EventPassage]](r.passages).getOrElse(Nil)
          EventLinkCandidate(r.eventId, r.citekey, r.score, r.status, r.title, r.findings, ps, r.reasons)
        }

  /** Replace the PROPOSED candidates of one event with `cs`; a source confirmed or rejected before keeps
    * its status (its evidence is refreshed). Records the check. */
  def saveCandidates(ds: DataSource, id: String, cs: List[EventLinkCandidate]): Unit =
    transact(ds):
      sql"DELETE FROM event_link_candidates WHERE event_id = $id AND status = 'proposed'".update.run()
      cs.foreach { c =>
        val ps = c.passages.asJson.noSpaces
        sql"""INSERT INTO event_link_candidates (event_id, citekey, score, findings, passages, reasons)
              VALUES ($id, ${c.citekey}, ${c.score}, ${c.findings}::text[], $ps::jsonb, ${c.reasons}::text[])
              ON CONFLICT (event_id, citekey) DO UPDATE SET score = EXCLUDED.score, findings = EXCLUDED.findings,
                passages = EXCLUDED.passages, reasons = EXCLUDED.reasons, computed_at = now()""".update.run()
      }
      sql"""INSERT INTO event_link_checks (event_id) VALUES ($id)
            ON CONFLICT (event_id) DO UPDATE SET checked_at = now()""".update.run()

  def setStatus(ds: DataSource, id: String, citekey: String, status: String): Boolean =
    transact(ds):
      sql"UPDATE event_link_candidates SET status = $status WHERE event_id = $id AND citekey = $citekey"
        .update.run() > 0

  def isChecked(ds: DataSource, id: String): Boolean =
    connect(ds)(sql"SELECT 1 FROM event_link_checks WHERE event_id = $id".query[Int].run().nonEmpty)

  /** Events whose candidates were never computed (or every one, with `all`), catalogue ones first. */
  def eventsToCheck(ds: DataSource, all: Boolean, limit: Int): List[String] =
    connect(ds):
      sql"""SELECT e.id FROM events e
            WHERE ($all OR NOT EXISTS (SELECT 1 FROM event_link_checks k WHERE k.event_id = e.id))
            ORDER BY e.catalogue IS NULL, e.id LIMIT $limit""".query[String].run().toList

  // ── leads ───────────────────────────────────────────────────────────────────────────────────

  /** The facts of the leads of the events `where` selects (over `events e`, `ep` its place): tier,
    * size, findings near (within `km`, `years`), proposed and confirmed sources. Catalogue events
    * first, the largest first. */
  def leadRows(ds: DataSource, where: Sql, km: Double, years: Long, limit: Int, offset: Int): (Int, List[LeadRow]) =
    connect(ds):
      val total = (Sql.raw("SELECT count(*)::int FROM events e LEFT JOIN places ep ON ep.id = e.place_id WHERE ") ++ where)
        .frag.query[Int].run().head
      val rows = (Sql.raw(s"""SELECT e.id, e.label, e.kind, e.not_before, e.begin_by, e.end_from, e.not_after,
                 $TierSql AS tier,
                 (SELECT max(c.vei) FROM event_catalogue c WHERE c.event_id = e.id),
                 (SELECT max(c.magnitude) FROM event_catalogue c WHERE c.event_id = e.id),
                 (SELECT count(*)::int FROM findings f
                   WHERE a.pt IS NOT NULL AND e.outer_span IS NOT NULL AND f.geom IS NOT NULL
                     AND NOT lower_inf(f.period) AND NOT upper_inf(f.period)
                     AND f.geom && ${boxAround("a.pt", km)}
                     AND ST_DWithin(f.geom::geography, a.pt::geography, ?)
                     AND f.period && int8range(${PeriodSql.start("e.outer_span")} - ?, ${PeriodSql.end("e.outer_span")} + ?, '[]')),
                 (SELECT count(*)::int FROM event_link_candidates k WHERE k.event_id = e.id AND k.status = 'proposed'),
                 (SELECT count(*)::int FROM event_link_candidates k WHERE k.event_id = e.id AND k.status = 'confirmed')
               FROM events e LEFT JOIN places ep ON ep.id = e.place_id
               CROSS JOIN LATERAL (SELECT $AnchorSql AS pt) a WHERE """) ++ where ++
        Sql.raw(s" ORDER BY ($TierSql) = '${EventTier.Catalogue}' DESC," +
          " (SELECT coalesce(max(c.vei), 0) + coalesce(max(c.magnitude), 0) FROM event_catalogue c WHERE c.event_id = e.id) DESC," +
          " coalesce(e.not_before, e.begin_by), e.id") ++
        Sql.of(" LIMIT ? OFFSET ?", Param.I(limit.max(1)), Param.I(offset.max(0))))
      // the three `?` of the findings count come first in the text
      val withParams = Sql(rows.text, List(Param.D(km * 1000.0), Param.L(years), Param.L(years)) ++ rows.params)
      (total, withParams.frag.query[LeadRow].run().toList)

  /** Catalogue events an article's period and region hold: those overlapping `period` within `km` of
    * one of `citekeys`' dated findings of that period, and large enough (`minVei`, `minMagnitude`,
    * or a war) — with how many of those findings are near each. */
  def articleCandidates(ds: DataSource, citekeys: List[String], period: Option[(Long, Long)], km: Double,
      minVei: Int, minMagnitude: Double, limit: Int): List[(String, String, String, TimeSpan, List[String], Option[Int], Option[Double], Int, String)] =
    if citekeys.isEmpty then Nil
    else
      val (p0, p1) = period.getOrElse((Long.MinValue / 4, Long.MaxValue / 4))
      connect(ds):
        sql"""WITH fs AS (
                SELECT f.geom, f.period FROM findings f
                WHERE f.citekey = ANY($citekeys::text[]) AND f.geom IS NOT NULL
                  AND NOT lower_inf(f.period) AND NOT upper_inf(f.period)
                  AND f.period && int8range($p0, $p1, '[]'))
              SELECT e.id, e.label, e.kind, e.not_before, e.begin_by, e.end_from, e.not_after, e.names,
                     (SELECT max(c.vei) FROM event_catalogue c WHERE c.event_id = e.id),
                     (SELECT max(c.magnitude) FROM event_catalogue c WHERE c.event_id = e.id),
                     (SELECT count(*)::int FROM fs
                        WHERE ST_DWithin(fs.geom::geography, e.geom::geography, ${km * 1000.0})),
                     coalesce(e.catalogue, '')
              FROM events e
              WHERE e.catalogue IS NOT NULL AND e.geom IS NOT NULL AND e.outer_span && int8range($p0, $p1, '[]')
                AND (e.kind = 'war' OR EXISTS (SELECT 1 FROM event_catalogue c WHERE c.event_id = e.id
                       AND (c.vei >= $minVei OR c.magnitude >= $minMagnitude)))
                AND EXISTS (SELECT 1 FROM fs WHERE ST_DWithin(fs.geom::geography, e.geom::geography, ${km * 1000.0}))
              ORDER BY 11 DESC, e.id LIMIT $limit"""
          .query[(String, String, String, Option[Long], Option[Long], Option[Long], Option[Long], List[String],
            Option[Int], Option[Double], Int, String)].run().toList
          .map { case (id, label, kind, a, b, c, d, names, vei, mag, n, prov) =>
            (id, label, kind, TimeSpan(a, b, c, d), names, vei, mag, n, prov)
          }

  // ── site types ──────────────────────────────────────────────────────────────────────────────

  /** The Wikidata sites on the map (with a finding or a picture) whose type is still to be refined:
    * never asked, or (`refresh`) asked before `before` (epoch ms — the start of this run). */
  private def toTypeWhere(refresh: Boolean, before: Option[Long]): Sql =
    val asked = (refresh, before) match
      case (true, Some(ms)) => Sql.of("(p.site_type_checked_at IS NULL OR p.site_type_checked_at < to_timestamp(? / 1000.0))", Param.L(ms))
      case (true, None)     => Sql.raw("TRUE")
      case _                => Sql.raw("p.site_type_checked_at IS NULL")
    Sql.raw("p.import_source = 'wikidata' AND p.id ~ '^wikidata_q[1-9][0-9]*$' AND ") ++ asked ++
      Sql.raw(" AND (EXISTS (SELECT 1 FROM finding_places fp WHERE fp.place_id = p.id)" +
        " OR EXISTS (SELECT 1 FROM media_links m WHERE m.target_type = 'place' AND m.target_id = p.id))")

  /** (place id, Wikidata item, the class the import kept) of the next `limit` sites to refine. */
  def sitesToType(ds: DataSource, limit: Int, refresh: Boolean, before: Option[Long]): List[(String, String, Option[String])] =
    connect(ds):
      (Sql.raw("SELECT p.id, upper(substr(p.id, 10)), p.properties->>'class' FROM places p WHERE ") ++
        toTypeWhere(refresh, before) ++ Sql.of(" ORDER BY p.id LIMIT ?", Param.I(limit.max(0))))
        .frag.query[(String, String, Option[String])].run().toList

  def countSitesToType(ds: DataSource, refresh: Boolean, before: Option[Long]): Int =
    connect(ds):
      (Sql.raw("SELECT count(*)::int FROM places p WHERE ") ++ toTypeWhere(refresh, before)).frag.query[Int].run().head

  /** Record the refined types (`None` = asked, none of the curated classes). */
  def setSiteTypes(ds: DataSource, types: List[(String, Option[String])]): Unit =
    val _ = transact(ds):
      batchUpdate(types) { (id, t) =>
        sql"UPDATE places SET site_type = $t, site_type_checked_at = now() WHERE id = $id".update
      }
