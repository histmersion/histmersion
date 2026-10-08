package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*
import io.circe.Json
import javax.sql.DataSource

/** One row of `gazetteer_sources` (V071). */
final case class GazetteerSourceRow(key: String, kind: String, title: String, version: String, sha256: String,
    file: String, url: Option[String], licence: Option[String], citation: Option[String],
    periodStart: Option[Long], periodEnd: Option[Long], places: Int, names: Int, links: Int, importedAt: String)
given DbCodec[GazetteerSourceRow] = DbCodec.derived

/** The gazetteer's external sources in the store (#1064, V071): joining a batch of [[GazRecord]]s
  * to the places already here BY IDENTIFIER, writing what each record says (its place, if it is the
  * first to bring it; its names and links, always) with its provenance, and removing what a source
  * no longer says.
  *
  * The write is a DIFF, so a re-import of the same version changes nothing and says so: every
  * upsert is `… WHERE (old) IS DISTINCT FROM (new)`, and the counts are rows actually inserted,
  * changed and deleted. A record owns the rows it wrote — names and links carry `(import_source,
  * import_ref)` — so a record that drops a name drops exactly that row and no other source's.
  *
  * Batches are written in the CALLER's session ([[Session]]): one connection for a whole import, a
  * transaction per batch, committed (`apply`) or rolled back (a dry run, which then reports exactly
  * what the batch would have done). The refs a run has seen go into a temporary table, so the final
  * [[Session.sweep]] removes the rows of records the source no longer has. */
object GazetteerRepo:

  private val Json0 = io.circe.Printer.noSpaces

  /** A seed place's own identifiers as `place_links` (V071): Pleiades ids (a `pleiades_…` id and
    * its `pleiades_uri`), the Wikidata and GeoNames ids Natural Earth carries in `properties`, and the
    * Wikidata item a curated place cites in `source` — what the external sources are joined on.
    * V071 wrote them for every place there was; every seed import ([[PlaceRepo.upsertPlaces]]) writes
    * them for the places it touches. Places an external source wrote are left alone. Idempotent. */
  def linkSeedIds(ids: List[String])(using DbCon): Int =
    if ids.isEmpty then 0
    else
      sql"""INSERT INTO place_links (place_id, scheme, external_id, relation)
            SELECT id, scheme, ext, rel FROM (
              SELECT id, 'pleiades' AS scheme, substring(properties->>'pleiades_uri' FROM '/places/([0-9]+)') AS ext,
                     'exact_match' AS rel FROM places WHERE id = ANY($ids::text[]) AND import_source IS NULL
              UNION ALL
              SELECT id, 'pleiades', CASE WHEN id ~ '^pleiades_[0-9]' AND substring(id FROM 10) !~ '[^0-9]' THEN substring(id FROM 10) END, 'exact_match' FROM places
                WHERE id = ANY($ids::text[]) AND import_source IS NULL
              UNION ALL
              SELECT id, 'wikidata', CASE WHEN properties->>'wikidata' ~* '^Q[0-9]' AND substring(properties->>'wikidata' FROM 2) !~ '[^0-9]' THEN upper(properties->>'wikidata') END, 'exact_match' FROM places
                WHERE id = ANY($ids::text[]) AND import_source IS NULL
              UNION ALL
              SELECT id, 'geonames', CASE WHEN properties->>'geonames' ~ '^[0-9]' AND properties->>'geonames' !~ '[^0-9]' THEN properties->>'geonames' END, 'exact_match' FROM places
                WHERE id = ANY($ids::text[]) AND import_source IS NULL
              UNION ALL
              SELECT id, 'wikidata', substring(source FROM 'Wikidata (Q[0-9]+)'), 'close_match' FROM places
                WHERE id = ANY($ids::text[]) AND import_source IS NULL
            ) s WHERE ext IS NOT NULL
            ON CONFLICT DO NOTHING""".update.run()

  // ── the registry ───────────────────────────────────────────────────────────────────────────────

  def sources(ds: DataSource): List[GazetteerSourceRow] =
    connect(ds)(sources())

  def sources()(using DbCon): List[GazetteerSourceRow] =
    sql"""SELECT key, kind, title, version, sha256, file, url, licence, citation,
                 lower(period), upper(period) - 1, places, names, links, imported_at::text
          FROM gazetteer_sources ORDER BY key""".query[GazetteerSourceRow].run().toList

  /** Record a finished import: what was read, and what it now holds in the store. */
  def recordSource(key: String, kind: GazetteerKind, title: String, version: String, sha256: String, file: String,
      url: Option[String], licence: Option[String], citation: Option[String], span: Span)(using DbCon): Unit =
    val lo = span.from
    val hi = span.to
    val _ = sql"""INSERT INTO gazetteer_sources (key, kind, title, version, sha256, file, url, licence, citation, period,
                    places, names, links, imported_at)
                  VALUES ($key, ${kind.wire}, $title, $version, $sha256, $file, $url, $licence, $citation,
                    int8range(${lo}::bigint, ${hi}::bigint + 1, '[)'),
                    (SELECT count(DISTINCT import_ref)::int FROM place_names WHERE import_source = $key),
                    (SELECT count(*)::int FROM place_names WHERE import_source = $key),
                    (SELECT count(*)::int FROM place_links WHERE import_source = $key), now())
                  ON CONFLICT (key) DO UPDATE SET kind = EXCLUDED.kind, title = EXCLUDED.title,
                    version = EXCLUDED.version, sha256 = EXCLUDED.sha256, file = EXCLUDED.file, url = EXCLUDED.url,
                    licence = EXCLUDED.licence, citation = EXCLUDED.citation, period = EXCLUDED.period,
                    places = EXCLUDED.places, names = EXCLUDED.names, links = EXCLUDED.links,
                    imported_at = EXCLUDED.imported_at""".update.run()

  // ── placement (pure) ───────────────────────────────────────────────────────────────────────────

  /** A stored place a lookup returned, with who wrote it. */
  final case class Stored(id: String, importSource: Option[String], importRef: Option[String]):
    def toMatch: PlaceMatch = PlaceMatch(id, importSource)

  /** Where one record's names go. */
  enum Placement:
    /** A new place, the record's own. */
    case Create(placeId: String)
    /** The record's own place, from an earlier import of this source. */
    case Own(placeId: String)
    /** A place another record or source brought in, that one of the record's ids names. */
    case Join(placeId: String)

    def placeId: String

  /** The pure decision: `byOwn` are the places the record's own id names, `byLinks` those its links
    * name, `byId` the place that already has the record's would-be id, `own` the place this record
    * created earlier ([[Gazetteer.canonical]]). Returns the placement and the record's old own place
    * when it must go (its ids now name a better place). */
  def place(r: GazRecord, byOwn: List[PlaceMatch], byLinks: List[PlaceMatch], byId: Option[Stored],
      own: Option[Stored]): (Placement, Option[String]) =
    Gazetteer.canonical(byOwn ++ byId.map(_.toMatch).toList ++ own.map(_.toMatch).toList, byLinks) match
      case None                                => (Placement.Create(r.placeId), None)
      case Some(id) if own.exists(_.id == id)  => (Placement.Own(id), None)
      case Some(id)                            => (Placement.Join(id), own.map(_.id).filter(_ != id))

  // ── one import session ─────────────────────────────────────────────────────────────────────────

  /** Start an import of `source` at `version` on the caller's connection (an empty `gaz_seen`). */
  def session(source: String, version: String, apply: Boolean)(using con: DbCon): Session =
    val _ = sql"CREATE TEMP TABLE IF NOT EXISTS gaz_seen (ref text PRIMARY KEY)".update.run()
    val _ = sql"TRUNCATE gaz_seen".update.run()
    Session(source, version, apply)

  /** An import of one source on ONE connection ([[session]]). `apply = false` rolls every batch back. */
  final class Session private[GazetteerRepo] (source: String, version: String, apply: Boolean)(using con: DbCon):
    private val jdbc = con.connection

    /** One batch, in its own transaction. */
    def batch(records: List[GazRecord]): GazetteerLoadCounts =
      val (uniq, dup) = records.foldLeft((Vector.empty[GazRecord], List.empty[String])) { case ((ok, d), r) =>
        if ok.exists(_.ref == r.ref) then (ok, d :+ s"${r.ref}: repeated") else (ok :+ r, d)
      }
      val rs = uniq.toList
      jdbc.setAutoCommit(false)
      val counts =
        try
          val c = write(rs)
          if apply then jdbc.commit() else jdbc.rollback()
          c
        catch
          case e: Throwable =>
            jdbc.rollback()
            throw e
        finally jdbc.setAutoCommit(true)
      val refs = rs.map(_.ref)
      val _ = sql"INSERT INTO gaz_seen SELECT unnest($refs::text[]) ON CONFLICT DO NOTHING".update.run()
      counts.copy(skipped = (dup ++ counts.skipped).take(20))

    /** Remove what the source no longer says: the rows of records this run has not seen. Run once,
      * after the last batch, and only when the whole source was read. */
    def sweep(): GazetteerLoadCounts =
      jdbc.setAutoCommit(false)
      try
        val names = sql"""DELETE FROM place_names n WHERE n.import_source = $source
                          AND NOT EXISTS (SELECT 1 FROM gaz_seen s WHERE s.ref = n.import_ref)""".update.run()
        val links = sql"""DELETE FROM place_links l WHERE l.import_source = $source
                          AND NOT EXISTS (SELECT 1 FROM gaz_seen s WHERE s.ref = l.import_ref)""".update.run()
        val places = sql"""DELETE FROM places p WHERE p.import_source = $source
                           AND NOT EXISTS (SELECT 1 FROM gaz_seen s WHERE s.ref = p.import_ref)""".update.run()
        if apply then jdbc.commit() else jdbc.rollback()
        GazetteerLoadCounts(source, version, namesRemoved = names, linksRemoved = links, placesRemoved = places)
      catch
        case e: Throwable =>
          jdbc.rollback()
          throw e
      finally jdbc.setAutoCommit(true)

    private def write(rs: List[GazRecord]): GazetteerLoadCounts =
      if rs.isEmpty then GazetteerLoadCounts(source, version)
      else
        val keys = Json.arr(rs.flatMap(_.identifiers).distinct.map(k =>
          Json.obj("scheme" -> Json.fromString(k.scheme.wire), "eid" -> Json.fromString(k.id)))*)
        val linkedRows =
          sql"""SELECT DISTINCT k.scheme, k.eid, p.id, p.import_source,
                       (l.import_source = '' OR (l.import_source = p.import_source AND l.import_ref = p.import_ref))
                FROM jsonb_to_recordset(${Json0.print(keys)}::jsonb) AS k(scheme text, eid text)
                JOIN place_links l ON l.scheme = k.scheme AND l.external_id = k.eid
                JOIN places p ON p.id = l.place_id""".query[(String, String, String, Option[String], Boolean)].run().toList
        val placeIds = rs.map(_.placeId).distinct
        val refs     = rs.map(_.ref)
        val stored =
          sql"""SELECT id, import_source, import_ref FROM places
                WHERE id = ANY($placeIds::text[]) OR (import_source = $source AND import_ref = ANY($refs::text[]))"""
            .query[(String, Option[String], Option[String])].run().toList.map(Stored.apply)
        // the in-memory view grows as the batch is placed, so two records of ONE batch that share an
        // id meet exactly as they would across batches (the second joins the first's place)
        val linked = scala.collection.mutable.Map.empty[(String, String), List[PlaceMatch]]
        linkedRows.foreach { (s, e, id, src, native) => linked((s, e)) = PlaceMatch(id, src, native) :: linked.getOrElse((s, e), Nil) }
        val byId = scala.collection.mutable.Map.from(stored.map(s => s.id -> s))
        val own  = stored.filter(_.importSource.contains(source)).flatMap(s => s.importRef.map(_ -> s)).toMap
        val placed = rs.map { r =>
          def at(k: ExternalId) = linked.getOrElse((k.scheme.wire, k.id), Nil)
          val (pl, stale) = place(r, r.own.toList.flatMap(at), r.links.flatMap(l => at(l.id)),
            byId.get(r.placeId), own.get(r.ref))
          // what the store will say after this record: its links on its place, native where it made it
          val native = !pl.isInstanceOf[Placement.Join]
          r.placeLinks.foreach(l => linked((l.id.scheme.wire, l.id.id)) =
            PlaceMatch(pl.placeId, Some(source), native) :: linked.getOrElse((l.id.scheme.wire, l.id.id), Nil))
          if pl.isInstanceOf[Placement.Create] then byId(pl.placeId) = Stored(pl.placeId, Some(source), Some(r.ref))
          (r, pl, stale)
        }
        val removed = placed.flatMap(_._3).distinct
        val goneN =
          if removed.isEmpty then 0
          else sql"DELETE FROM places WHERE id = ANY($removed::text[]) AND import_source = $source".update.run()
        val (created, changed) = writePlaces(placed.collect { case (r, pl: (Placement.Create | Placement.Own), _) => (r, pl.placeId) })
        val (nAdd, nDel, nChg) = writeNames(placed.map((r, pl, _) => (r, pl.placeId)))
        val (lAdd, lDel)       = writeLinks(placed.map((r, pl, _) => (r, pl.placeId)))
        GazetteerLoadCounts(source, version, records = rs.size, created = created,
          joined = placed.count(_._2.isInstanceOf[Placement.Join]), placesChanged = changed,
          namesAdded = nAdd, namesRemoved = nDel, namesChanged = nChg, linksAdded = lAdd, linksRemoved = lDel,
          placesRemoved = goneN)

    private def num(v: Option[Long]): Json = v.fold(Json.Null)(Json.fromLong)
    private def txt(v: Option[String]): Json = v.fold(Json.Null)(Json.fromString)

    /** The records' own places: inserted, or updated where something differs. (inserted, updated) */
    private def writePlaces(rows: List[(GazRecord, String)]): (Int, Int) =
      if rows.isEmpty then (0, 0)
      else
        val doc = Json.arr(rows.map { (r, id) =>
          Json.obj(
            "id" -> Json.fromString(id), "label" -> Json.fromString(r.label), "kind" -> Json.fromString(r.kind),
            "gjson" -> txt(r.geometry.collect { case GazGeometry.GeoJson(t) => t }),
            "gwkt" -> txt(r.geometry.collect { case GazGeometry.Wkt(t) => t }),
            "lo" -> num(r.span.from), "hi" -> num(r.span.to),
            "source" -> txt(r.source), "props" -> txt(r.properties), "ref" -> Json.fromString(r.ref))
        }*)
        val out =
          sql"""INSERT INTO places (id, label, kind, geom, period, source, properties, dataset,
                                    import_source, import_ref, import_version)
                SELECT d.id, d.label, d.kind,
                       CASE WHEN d.gjson IS NOT NULL THEN ST_SetSRID(ST_GeomFromGeoJSON(d.gjson), 4326)
                            WHEN d.gwkt IS NOT NULL THEN ST_GeomFromText(d.gwkt, 4326) END,
                       int8range(d.lo, d.hi + 1, '[)'), d.source, d.props::jsonb, 'seed', $source, d.ref, $version
                FROM jsonb_to_recordset(${Json0.print(doc)}::jsonb)
                  AS d(id text, label text, kind text, gjson text, gwkt text, lo bigint, hi bigint, source text, props text, ref text)
                ON CONFLICT (id) DO UPDATE SET label = EXCLUDED.label, kind = EXCLUDED.kind, geom = EXCLUDED.geom,
                  period = EXCLUDED.period, source = EXCLUDED.source, properties = EXCLUDED.properties,
                  import_source = EXCLUDED.import_source, import_ref = EXCLUDED.import_ref,
                  import_version = EXCLUDED.import_version
                WHERE (places.label, places.kind, places.period, places.source, places.properties,
                       places.import_source, places.import_ref, places.import_version)
                      IS DISTINCT FROM
                      (EXCLUDED.label, EXCLUDED.kind, EXCLUDED.period, EXCLUDED.source, EXCLUDED.properties,
                       EXCLUDED.import_source, EXCLUDED.import_ref, EXCLUDED.import_version)
                   OR NOT ST_OrderingEquals(coalesce(places.geom, 'POINT EMPTY'::geometry),
                                            coalesce(EXCLUDED.geom, 'POINT EMPTY'::geometry))
                RETURNING (xmax = 0)""".query[Boolean].run()
        (out.count(identity), out.count(!_))

    /** The records' names on their places, as a diff. (added, removed, changed) */
    private def writeNames(rows: List[(GazRecord, String)]): (Int, Int, Int) =
      val doc = Json.arr(rows.flatMap { (r, id) =>
        r.names.map(n => Json.obj(
          "pid" -> Json.fromString(id), "name" -> Json.fromString(n.name), "lang" -> Json.fromString(n.lang),
          "lo" -> num(n.span.from), "hi" -> num(n.span.to), "cite" -> txt(n.citation), "ref" -> Json.fromString(r.ref)))
      }*)
      val text = Json0.print(doc)
      val refs = rows.map(_._1.ref)
      val removed =
        sql"""DELETE FROM place_names n
              WHERE n.import_source = $source AND n.import_ref = ANY($refs::text[])
                AND NOT EXISTS (
                  SELECT 1 FROM jsonb_to_recordset($text::jsonb) AS d(pid text, name text, lang text, ref text)
                  WHERE d.pid = n.place_id AND d.name = n.name AND d.lang = n.lang AND d.ref = n.import_ref)""".update.run()
      val out =
        sql"""INSERT INTO place_names (place_id, name, lang, period, import_source, import_ref, import_version, citation)
              SELECT d.pid, d.name, d.lang, int8range(d.lo, d.hi + 1, '[)'), $source, d.ref, $version, d.cite
              FROM jsonb_to_recordset($text::jsonb)
                AS d(pid text, name text, lang text, lo bigint, hi bigint, cite text, ref text)
              ON CONFLICT (place_id, name, lang, import_source, import_ref) DO UPDATE SET
                period = EXCLUDED.period, import_version = EXCLUDED.import_version, citation = EXCLUDED.citation
              WHERE (place_names.period, place_names.import_version, place_names.citation)
                    IS DISTINCT FROM (EXCLUDED.period, EXCLUDED.import_version, EXCLUDED.citation)
              RETURNING (xmax = 0)""".query[Boolean].run()
      (out.count(identity), removed, out.count(!_))

    /** The records' links on their places, as a diff. A link another record wrote first stays its.
      * (added, removed) */
    private def writeLinks(rows: List[(GazRecord, String)]): (Int, Int) =
      val doc = Json.arr(rows.flatMap { (r, id) =>
        r.placeLinks.map(l => Json.obj(
          "pid" -> Json.fromString(id), "scheme" -> Json.fromString(l.id.scheme.wire),
          "eid" -> Json.fromString(l.id.id), "rel" -> Json.fromString(l.relation.wire), "ref" -> Json.fromString(r.ref)))
      }*)
      val text = Json0.print(doc)
      val refs = rows.map(_._1.ref)
      val removed =
        sql"""DELETE FROM place_links l
              WHERE l.import_source = $source AND l.import_ref = ANY($refs::text[])
                AND NOT EXISTS (
                  SELECT 1 FROM jsonb_to_recordset($text::jsonb) AS d(pid text, scheme text, eid text, ref text)
                  WHERE d.pid = l.place_id AND d.scheme = l.scheme AND d.eid = l.external_id AND d.ref = l.import_ref)""".update.run()
      val out =
        sql"""INSERT INTO place_links (place_id, scheme, external_id, relation, import_source, import_ref, import_version)
              SELECT DISTINCT ON (d.pid, d.scheme, d.eid) d.pid, d.scheme, d.eid, d.rel, $source, d.ref, $version
              FROM jsonb_to_recordset($text::jsonb) AS d(pid text, scheme text, eid text, rel text, ref text)
              ORDER BY d.pid, d.scheme, d.eid, d.ref
              ON CONFLICT (place_id, scheme, external_id) DO UPDATE SET
                relation = EXCLUDED.relation, import_version = EXCLUDED.import_version
              WHERE place_links.import_source = EXCLUDED.import_source AND place_links.import_ref = EXCLUDED.import_ref
                AND (place_links.relation, place_links.import_version)
                    IS DISTINCT FROM (EXCLUDED.relation, EXCLUDED.import_version)
              RETURNING (xmax = 0)""".query[Boolean].run()
      (out.count(identity), removed)
