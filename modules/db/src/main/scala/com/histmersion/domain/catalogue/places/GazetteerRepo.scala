package com.histmersion
package domain.catalogue.places
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.database.given

import com.histmersion.util.database.RangeSql

import com.histmersion.api.Span
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.catalogue.{Gazetteer, GazGeometry, GazRecord}
import com.histmersion.api.library.ExternalId
import com.histmersion.api.catalogue.Place
import com.histmersion.util.database.Unused
import com.histmersion.domain.Creator
import com.histmersion.util.database.StoredCreator

/** The gazetteer's external sources in the store (#1064, V071): joining a batch of [[GazRecord]]s to the places already
  * here BY IDENTIFIER, writing what each record says (its place, if it is the first to bring it; its names and links,
  * always) with its provenance, and removing what a source no longer says.
  *
  * The write is a DIFF, so a re-import of the same version changes nothing and says so: every upsert is
  * `… WHERE (old) IS DISTINCT FROM (new)`, and the counts are rows actually inserted, changed and deleted. A record
  * owns the rows it wrote — names and links carry `(import_source, import_ref)` — so a record that drops a name drops
  * exactly that row and no other source's.
  *
  * Batches are written in the CALLER's session ([[Session]]): one connection for a whole import, a transaction per
  * batch, committed (`apply`) or rolled back (a dry run, which then reports exactly what the batch would have done).
  * The refs a run has seen go into a temporary table, so the final [[Session.sweep]] removes the rows of records the
  * source no longer has.
  */
private[places] object GazetteerRepo:

  /** A seed place's own identifiers as `place_links` (V071): Pleiades ids (a `pleiades_…` id and its `pleiades_uri`),
    * the Wikidata and GeoNames ids Natural Earth carries in `properties`, and the Wikidata item a curated place cites
    * in `source` — what the external sources are joined on. V071 wrote them for every place there was; every seed
    * import ([[PlaceRepo.upsertPlaces]]) writes them for the places it touches. Places an external source wrote are
    * left alone. Idempotent.
    */
  def linkSeedIds(ids: List[BPlaceRow.Id])(using DbCon): RowCount =
    if ids.isEmpty then RowCount(0)
    else
      RowCount(
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
      )

  // ── the registry ───────────────────────────────────────────────────────────────────────────────

  def sources()(using DbCon): List[GazetteerSourceRow] =
    sql"""SELECT key, kind, title, version, sha256, file, url, licence, citation,
                 ${RangeSql.cols(sql"period")}, places, names, links, imported_at::text
          FROM gazetteer_sources ORDER BY key""".query[GazetteerSourceRow].run().toList

  /** Record a finished import: what was read, and what it now holds in the store. */
  def recordSource(
      key: GazetteerSourceRow.Key,
      kind: Gazetteer.Kind,
      title: GazetteerSourceRow.Title,
      version: GazetteerSourceRow.Version,
      sha256: Sha256,
      file: GazetteerSourceRow.File,
      url: Option[GazetteerSourceRow.Url],
      licence: Option[GazetteerSourceRow.Licence],
      citation: Option[GazetteerSourceRow.Citation],
      span: Span
  )(using DbCon): Unit =
    val lo = span.from.map(y => DeepTime.SignedYear(y.value))
    val hi = span.to.map(y => DeepTime.SignedYear(y.value))
    Unused(
      sql"""INSERT INTO gazetteer_sources (key, kind, title, version, sha256, file, url, licence, citation, period,
                    places, names, links, imported_at)
                  VALUES ($key, ${kind.wire}, $title, $version, $sha256, $file, $url, $licence, $citation,
                    ${RangeSql.bounds(lo, hi)}::int8range,
                    (SELECT count(DISTINCT import_ref)::int FROM place_names WHERE import_source = $key),
                    (SELECT count(*)::int FROM place_names WHERE import_source = $key),
                    (SELECT count(*)::int FROM place_links WHERE import_source = $key), now())
                  ON CONFLICT (key) DO UPDATE SET kind = EXCLUDED.kind, title = EXCLUDED.title,
                    version = EXCLUDED.version, sha256 = EXCLUDED.sha256, file = EXCLUDED.file, url = EXCLUDED.url,
                    licence = EXCLUDED.licence, citation = EXCLUDED.citation, period = EXCLUDED.period,
                    places = EXCLUDED.places, names = EXCLUDED.names, links = EXCLUDED.links,
                    imported_at = EXCLUDED.imported_at""".update.run(),
      Unused.Why.Idempotent
    )

  // ── placement (pure) ───────────────────────────────────────────────────────────────────────────

  /** A stored place a lookup returned, with who wrote it. */
  final case class Stored(
      id: Place.Id,
      importSource: Option[Place.Match.ImportSource],
      importRef: Option[GazRecord.Ref]
  ):
    def toMatch: Place.Match = Place.Match(id, importSource)

  /** Where one record's names go. */
  enum Placement:
    /** A new place, the record's own. */
    case Create(placeId: Place.Id)

    /** The record's own place, from an earlier import of this source. */
    case Own(placeId: Place.Id)

    /** A place another record or source brought in, that one of the record's ids names. */
    case Join(placeId: Place.Id)

    def placeId: Place.Id

  /** The pure decision: `byOwn` are the places the record's own id names, `byLinks` those its links name, `byId` the
    * place that already has the record's would-be id, `own` the place this record created earlier
    * ([[Gazetteer.canonical]]). Returns the placement and the record's old own place when it must go (its ids now name
    * a better place).
    */
  def place(
      r: GazRecord,
      byOwn: List[Place.Match],
      byLinks: List[Place.Match],
      byId: Option[Stored],
      own: Option[Stored]
  ): (Placement, Option[Place.Id]) =
    Gazetteer.canonical(byOwn ++ byId.map(_.toMatch).toList ++ own.map(_.toMatch).toList, byLinks) match
      case None                               => (Placement.Create(r.placeId), None)
      case Some(id) if own.exists(_.id == id) => (Placement.Own(id), None)
      case Some(id)                           => (Placement.Join(id), own.map(_.id).filter(_ != id))

  // ── one import session ─────────────────────────────────────────────────────────────────────────

  def session(
      source: Gazetteer.SourceInfo.Key,
      version: Gazetteer.LoadCounts.Version,
      windowYears: Gazetteer.ValidityWindowYears =
        Gazetteer.ValidityWindowYears(Gazetteer.DefaultOpenStartWindowYears.value),
      pointAreaKinds: Set[Gazetteer.PointAreaKinds] = Gazetteer.DefaultPointAreaKinds.toSet
  )(using con: DbCon): Session =
    val _ = sql"CREATE TEMP TABLE IF NOT EXISTS gaz_seen (ref text PRIMARY KEY)".update.run()
    val _ = sql"TRUNCATE gaz_seen".update.run()
    Session(source, version, windowYears, pointAreaKinds)

  final class Session private[GazetteerRepo] (
      source: Gazetteer.SourceInfo.Key,
      version: Gazetteer.LoadCounts.Version,
      windowYears: Gazetteer.ValidityWindowYears,
      pointAreaKinds: Set[Gazetteer.PointAreaKinds]
  )(using con: DbCon):

    def writeBatch(records: List[GazRecord])(using Creator.New, CreatedAt): Gazetteer.LoadCounts = write(records)

    def recordSeen(records: List[GazRecord]): Unit =
      val refs = records.map(_.ref)
      Unused(
        sql"INSERT INTO gaz_seen SELECT unnest($refs::text[]) ON CONFLICT DO NOTHING".update.run(),
        Unused.Why.Idempotent
      )

    def pruneCandidates: List[domain.catalogue.Place.Id] =
      if pointAreaKinds.isEmpty || !Gazetteer.PointAreaSources.contains(source) then Nil
      else
        PointAreaRepo.ofSourceIn(
          PointAreas.Group.Source(source.value),
          pointAreaKinds.toList.sorted.map(k => domain.catalogue.Place.Kind.Wire(k.value))
        )

    def deletePruneChunk(ids: List[domain.catalogue.Place.Id]): RowCount =
      PointAreaRepo.deletePlacesIn(ids)

    def sweep(): Gazetteer.LoadCounts =
      val names = sql"""DELETE FROM place_names n WHERE n.import_source = $source
                        AND NOT EXISTS (SELECT 1 FROM gaz_seen s WHERE s.ref = n.import_ref)""".update.run()
      val links = sql"""DELETE FROM place_links l WHERE l.import_source = $source
                        AND NOT EXISTS (SELECT 1 FROM gaz_seen s WHERE s.ref = l.import_ref)""".update.run()
      val descs = sql"""DELETE FROM place_descriptions d WHERE d.import_source = $source
                        AND NOT EXISTS (SELECT 1 FROM gaz_seen s WHERE s.ref = d.import_ref)""".update.run()
      val places = sql"""DELETE FROM places p WHERE p.import_source = $source
                         AND NOT EXISTS (SELECT 1 FROM gaz_seen s WHERE s.ref = p.import_ref)""".update.run()
      Gazetteer.LoadCounts(
        source,
        version,
        namesRemoved = Gazetteer.LoadCounts.NamesRemoved(names.toLong),
        linksRemoved = Gazetteer.LoadCounts.LinksRemoved(links.toLong),
        placesRemoved = Gazetteer.LoadCounts.PlacesRemoved(places.toLong),
        descriptionsRemoved = Gazetteer.LoadCounts.DescriptionsRemoved(descs.toLong)
      )

    private def write(rs: List[GazRecord])(using Creator.New, CreatedAt): Gazetteer.LoadCounts =
      if rs.isEmpty then Gazetteer.LoadCounts(source, version)
      else
        val keys = Json.arr(
          rs.flatMap(_.identifiers)
            .distinct
            .map(k => Json.obj("scheme" -> Json.fromString(k.scheme.wire.value), "eid" -> Json.fromString(k.id.value)))*
        )
        val linkedRows =
          sql"""SELECT DISTINCT k.scheme, k.eid, p.id, p.import_source,
                       (l.import_source = '' OR (l.import_source = p.import_source AND l.import_ref = p.import_ref))
                FROM jsonb_to_recordset(${keys.noSpaces}::jsonb) AS k(scheme text, eid text)
                JOIN place_links l ON l.scheme = k.scheme AND l.external_id = k.eid
                JOIN places p ON p.id = l.place_id"""
            .query[(String, String, Place.Id, Option[Place.Match.ImportSource], Boolean)]
            .run()
            .toList
        val placeIds = rs.map(_.placeId).distinct
        val refs = rs.map(_.ref)
        val stored =
          sql"""SELECT id, import_source, import_ref FROM places
                WHERE id = ANY($placeIds::text[]) OR (import_source = $source AND import_ref = ANY($refs::text[]))"""
            .query[(Place.Id, Option[Place.Match.ImportSource], Option[GazRecord.Ref])]
            .run()
            .toList
            .map(Stored.apply)
        // the in-memory view grows as the batch is placed, so two records of ONE batch that share an
        // id meet exactly as they would across batches (the second joins the first's place)
        val linked = scala.collection.mutable.Map.empty[(String, String), List[Place.Match]]
        linkedRows.foreach { (s, e, id, src, native) =>
          linked((s, e)) = Place.Match(id, src, Place.Match.Native(native)) :: linked.getOrElse((s, e), Nil)
        }
        val byId = scala.collection.mutable.Map.from(stored.map(s => s.id -> s))
        val own = stored
          .filter(_.importSource.contains(Place.Match.ImportSource(source.value)))
          .flatMap(s => s.importRef.map(_ -> s))
          .toMap
        // a record that would make (or keep) a place of its own but is an area known only by a point is
        // DROPPED: no place, no names, no links, no descriptions — the place an earlier import made for
        // it (DOOMED: no record of the batch joins it, before or after) and every row it wrote anywhere
        // go now, in this batch (it still counts as seen, so the sweep has nothing left of it and a dry
        // run counts it once). A place a person's evidence names (an attestation, an asset link) stays.
        val areas = rs.filter(Gazetteer.dropsPointArea(source, _, pointAreaKinds))
        val doomed: Set[Place.Id] =
          val ids = areas.flatMap(r => own.get(r.ref)).map(_.id).distinct
          if ids.isEmpty then Set.empty
          else
            val pinned =
              sql"""SELECT target_id FROM attestations WHERE target_type = 'place' AND target_id = ANY($ids::text[])
                                UNION SELECT target_id FROM asset_links WHERE target_type = 'place' AND target_id = ANY($ids::text[])"""
                .query[Place.Id]
                .run()
                .toSet
            ids.filterNot(pinned.contains).toSet
        val decided = rs.map { r =>
          def at(k: ExternalId) =
            linked.getOrElse((k.scheme.wire.value, k.id.value), Nil).filterNot(m => doomed.contains(m.placeId))
          val (pl, stale) = place(
            r,
            r.own.toList.flatMap(at),
            r.links.flatMap(l => at(l.id)),
            byId.get(r.placeId).filterNot(s => doomed.contains(s.id)),
            own.get(r.ref).filterNot(s => doomed.contains(s.id))
          )
          if pl.isInstanceOf[Placement.Create] && Gazetteer.dropsPointArea(source, r, pointAreaKinds) then
            Left((r, stale.toList))
          else
            // what the store will say after this record: its links on its place, native where it made it
            val native = !pl.isInstanceOf[Placement.Join]
            r.placeLinks.foreach(l =>
              linked((l.id.scheme.wire.value, l.id.id.value)) =
                Place.Match(pl.placeId, Some(Place.Match.ImportSource(source.value)), Place.Match.Native(native)) ::
                  linked.getOrElse((l.id.scheme.wire.value, l.id.id.value), Nil)
            )
            if pl.isInstanceOf[Placement.Create] then
              byId(pl.placeId) = Stored(pl.placeId, Some(Place.Match.ImportSource(source.value)), Some(r.ref))
            Right((r, pl, stale))
        }
        val placed = decided.collect { case Right(x) => x }
        val dropped = decided.collect { case Left(x) => x }
        val removed = (placed.flatMap(_._3) ++ dropped.flatMap(_._2) ++ doomed).distinct
        val goneN =
          if removed.isEmpty then 0
          else sql"DELETE FROM places WHERE id = ANY($removed::text[]) AND import_source = $source".update.run()
        val droppedRefs = dropped.map(_._1.ref)
        val (dropNames, dropLinks, dropDescs) =
          if droppedRefs.isEmpty then (0, 0, 0)
          else
            (
              sql"DELETE FROM place_names WHERE import_source = $source AND import_ref = ANY($droppedRefs::text[])".update
                .run(),
              sql"DELETE FROM place_links WHERE import_source = $source AND import_ref = ANY($droppedRefs::text[])".update
                .run(),
              sql"DELETE FROM place_descriptions WHERE import_source = $source AND import_ref = ANY($droppedRefs::text[])".update
                .run()
            )
        val (created, changed) = writePlaces(placed.collect { case (r, pl: (Placement.Create | Placement.Own), _) =>
          (r, pl.placeId)
        })
        val (nAdd, nDel, nChg) = writeNames(placed.map((r, pl, _) => (r, pl.placeId)))
        val (lAdd, lDel, lChg) = writeLinks(placed.map((r, pl, _) => (r, pl.placeId)))
        val (dAdd, dDel, dChg) = writeDescriptions(placed.map((r, pl, _) => (r, pl.placeId)))
        Gazetteer.LoadCounts(
          source,
          version,
          records = Gazetteer.LoadCounts.Records(rs.size.toLong),
          created = Gazetteer.LoadCounts.Created(created.value.toLong),
          joined = Gazetteer.LoadCounts.Joined(placed.count(_._2.isInstanceOf[Placement.Join]).toLong),
          placesChanged = Gazetteer.LoadCounts.PlacesChanged(changed.value.toLong),
          namesAdded = Gazetteer.LoadCounts.NamesAdded(nAdd.value.toLong),
          namesRemoved = Gazetteer.LoadCounts.NamesRemoved(nDel.value.toLong + dropNames),
          namesChanged = Gazetteer.LoadCounts.NamesChanged(nChg.value.toLong),
          linksAdded = Gazetteer.LoadCounts.LinksAdded(lAdd.value.toLong),
          linksRemoved = Gazetteer.LoadCounts.LinksRemoved(lDel.value.toLong + dropLinks),
          placesRemoved = Gazetteer.LoadCounts.PlacesRemoved(goneN.toLong),
          descriptionsAdded = Gazetteer.LoadCounts.DescriptionsAdded(dAdd.value.toLong),
          descriptionsRemoved = Gazetteer.LoadCounts.DescriptionsRemoved(dDel.value.toLong + dropDescs),
          descriptionsChanged = Gazetteer.LoadCounts.DescriptionsChanged(dChg.value.toLong),
          linksChanged = Gazetteer.LoadCounts.LinksChanged(lChg.value.toLong),
          pointAreasSkipped = Gazetteer.LoadCounts.PointAreasSkipped(dropped.size.toLong)
        )

    private def num(v: Option[Long]): Json = v.fold(Json.Null)(Json.fromLong)
    private def txt(v: Option[String]): Json = v.fold(Json.Null)(Json.fromString)

    /** The records' own places: inserted, or updated where something differs. (inserted, updated) */
    private def writePlaces(rows: List[(GazRecord, Place.Id)])(using
        creator: Creator.New,
        at: CreatedAt
    ): (RowCount, RowCount) =
      if rows.isEmpty then (RowCount(0), RowCount(0))
      else
        val doc = Json.arr(rows.map { (r, id) =>
          val v = Gazetteer.validity(source, r.span, windowYears)
          Json.obj(
            "id" -> Json.fromString(id.value),
            "label" -> Json.fromString(r.label.value),
            "kind" -> Json.fromString(r.kind.value),
            "gjson" -> txt(r.geometry.collect { case GazGeometry.GeoJson(t) => t.value }),
            "gwkt" -> txt(r.geometry.collect { case GazGeometry.Wkt(t) => t.value }),
            "lo" -> num(v.from.map(_.value)),
            "hi" -> num(v.to.map(_.value)),
            "su" -> Json.fromBoolean(v.startUnknown.value),
            "source" -> txt(r.source.map(_.value)),
            "props" -> txt(r.properties.map(_.value)),
            "ref" -> Json.fromString(r.ref.value)
          )
        }*)
        val out =
          sql"""INSERT INTO places (id, label, kind, geom, period, source, properties, dataset,
                                    import_source, import_ref, import_version, start_unknown, creator, created_at)
                SELECT d.id, d.label, d.kind,
                       CASE WHEN d.gjson IS NOT NULL THEN ST_SetSRID(ST_GeomFromGeoJSON(d.gjson), 4326)
                            WHEN d.gwkt IS NOT NULL THEN ST_GeomFromText(d.gwkt, 4326) END,
                       ${RangeSql.range(
              sql"d.lo",
              sql"d.hi"
            )}, d.source, d.props::jsonb, 'seed', $source, d.ref, $version, d.su, ${StoredCreator.column(
              creator
            )}::jsonb, ${at.iso}::timestamptz
                FROM jsonb_to_recordset(${doc.noSpaces}::jsonb)
                  AS d(id text, label text, kind text, gjson text, gwkt text, lo bigint, hi bigint, su boolean,
                       source text, props text, ref text)
                ON CONFLICT (id) DO UPDATE SET label = EXCLUDED.label, kind = EXCLUDED.kind, geom = EXCLUDED.geom,
                  period = EXCLUDED.period, source = EXCLUDED.source, properties = EXCLUDED.properties,
                  import_source = EXCLUDED.import_source, import_ref = EXCLUDED.import_ref,
                  import_version = EXCLUDED.import_version, start_unknown = EXCLUDED.start_unknown
                WHERE (places.label, places.kind, places.period, places.source, places.properties,
                       places.import_source, places.import_ref, places.import_version, places.start_unknown)
                      IS DISTINCT FROM
                      (EXCLUDED.label, EXCLUDED.kind, EXCLUDED.period, EXCLUDED.source, EXCLUDED.properties,
                       EXCLUDED.import_source, EXCLUDED.import_ref, EXCLUDED.import_version, EXCLUDED.start_unknown)
                   OR NOT ST_OrderingEquals(coalesce(places.geom, 'POINT EMPTY'::geometry),
                                            coalesce(EXCLUDED.geom, 'POINT EMPTY'::geometry))
                RETURNING (xmax = 0)""".query[Boolean].run()
        (RowCount(out.count(identity)), RowCount(out.count(!_)))

    /** The records' names on their places, as a diff. (added, removed, changed) */
    private def writeNames(rows: List[(GazRecord, Place.Id)]): (RowCount, RowCount, RowCount) =
      val doc = Json.arr(rows.flatMap { (r, id) =>
        r.names.map(n =>
          Json.obj(
            "pid" -> Json.fromString(id.value),
            "name" -> Json.fromString(n.name.value),
            "lang" -> Json.fromString(n.lang.value),
            "lo" -> num(n.span.from.map(_.value)),
            "hi" -> num(n.span.to.map(_.value)),
            "cite" -> txt(n.citation.map(_.value)),
            "ref" -> Json.fromString(r.ref.value)
          )
        )
      }*)
      val text = doc.noSpaces
      val refs = rows.map(_._1.ref)
      val removed =
        sql"""DELETE FROM place_names n
              WHERE n.import_source = $source AND n.import_ref = ANY($refs::text[])
                AND NOT EXISTS (
                  SELECT 1 FROM jsonb_to_recordset($text::jsonb) AS d(pid text, name text, lang text, ref text)
                  WHERE d.pid = n.place_id AND d.name = n.name AND d.lang = n.lang AND d.ref = n.import_ref)""".update
          .run()
      val out =
        sql"""INSERT INTO place_names (place_id, name, lang, period, import_source, import_ref, import_version, citation)
              SELECT d.pid, d.name, d.lang, ${RangeSql.range(sql"d.lo", sql"d.hi")}, $source, d.ref, $version, d.cite
              FROM jsonb_to_recordset($text::jsonb)
                AS d(pid text, name text, lang text, lo bigint, hi bigint, cite text, ref text)
              ON CONFLICT (place_id, name, lang, import_source, import_ref) DO UPDATE SET
                period = EXCLUDED.period, import_version = EXCLUDED.import_version, citation = EXCLUDED.citation
              WHERE (place_names.period, place_names.import_version, place_names.citation)
                    IS DISTINCT FROM (EXCLUDED.period, EXCLUDED.import_version, EXCLUDED.citation)
              RETURNING (xmax = 0)""".query[Boolean].run()
      (RowCount(out.count(identity)), RowCount(removed), RowCount(out.count(!_)))

    /** The records' descriptions on their places (joined ones too), as a diff. (added, removed, changed) */
    private def writeDescriptions(rows: List[(GazRecord, Place.Id)]): (RowCount, RowCount, RowCount) =
      val doc = Json.arr(rows.flatMap { (r, id) =>
        r.descriptions
          .distinctBy(_.lang)
          .map(d =>
            Json.obj(
              "pid" -> Json.fromString(id.value),
              "lang" -> Json.fromString(d.lang.value),
              "text" -> Json.fromString(d.text.value),
              "ref" -> Json.fromString(r.ref.value)
            )
          )
      }*)
      val text = doc.noSpaces
      val refs = rows.map(_._1.ref)
      val removed =
        sql"""DELETE FROM place_descriptions x
              WHERE x.import_source = $source AND x.import_ref = ANY($refs::text[])
                AND NOT EXISTS (
                  SELECT 1 FROM jsonb_to_recordset($text::jsonb) AS d(pid text, lang text, ref text)
                  WHERE d.pid = x.place_id AND d.lang = x.lang AND d.ref = x.import_ref)""".update.run()
      val out =
        sql"""INSERT INTO place_descriptions (place_id, lang, text, import_source, import_ref, import_version)
              SELECT d.pid, d.lang, d.text, $source, d.ref, $version
              FROM jsonb_to_recordset($text::jsonb) AS d(pid text, lang text, text text, ref text)
              ON CONFLICT (place_id, lang, import_source, import_ref) DO UPDATE SET
                text = EXCLUDED.text, import_version = EXCLUDED.import_version
              WHERE (place_descriptions.text, place_descriptions.import_version)
                    IS DISTINCT FROM (EXCLUDED.text, EXCLUDED.import_version)
              RETURNING (xmax = 0)""".query[Boolean].run()
      (RowCount(out.count(identity)), RowCount(removed), RowCount(out.count(!_)))

    /** The records' links on their places, as a diff. A link another record wrote first stays its. The record's OWN
      * identifier link also carries what the record says the item is (V077): its class, its point and its stated dates
      * — on a link the seed wrote for the same id too (a curated place citing that Wikidata item), where only those
      * three columns change. (added, removed, changed)
      */
    private def writeLinks(rows: List[(GazRecord, Place.Id)]): (RowCount, RowCount, RowCount) =
      val doc = Json.arr(rows.flatMap { (r, id) =>
        r.placeLinks.map { l =>
          val own = r.own.contains(l.id)
          Json.obj(
            "pid" -> Json.fromString(id.value),
            "scheme" -> Json.fromString(l.id.scheme.wire.value),
            "eid" -> Json.fromString(l.id.id.value),
            "rel" -> Json.fromString(l.relation.wire.value),
            "ref" -> Json.fromString(r.ref.value),
            "cls" -> txt(r.itemClass.filter(_ => own).map(_.value)),
            "gjson" -> txt(r.geometry.filter(_ => own).collect { case GazGeometry.GeoJson(t) => t.value }),
            "gwkt" -> txt(r.geometry.filter(_ => own).collect { case GazGeometry.Wkt(t) => t.value }),
            "lo" -> num(r.span.from.filter(_ => own).map(_.value)),
            "hi" -> num(r.span.to.filter(_ => own).map(_.value)),
            "dated" -> Json.fromBoolean(own && !r.span.isEmpty)
          )
        }
      }*)
      val text = doc.noSpaces
      val refs = rows.map(_._1.ref)
      val removed =
        sql"""DELETE FROM place_links l
              WHERE l.import_source = $source AND l.import_ref = ANY($refs::text[])
                AND NOT EXISTS (
                  SELECT 1 FROM jsonb_to_recordset($text::jsonb) AS d(pid text, scheme text, eid text, ref text)
                  WHERE d.pid = l.place_id AND d.scheme = l.scheme AND d.eid = l.external_id AND d.ref = l.import_ref)""".update
          .run()
      // `mine`: the row is this record's (it wrote it before); `seed`: the bundled seed's bare id row
      val out =
        sql"""INSERT INTO place_links (place_id, scheme, external_id, relation, import_source, import_ref, import_version,
                                       item_class, item_geom, item_period)
              SELECT DISTINCT ON (d.pid, d.scheme, d.eid) d.pid, d.scheme, d.eid, d.rel, $source, d.ref, $version,
                     d.cls, CASE WHEN GeometryType(g.geom) = 'POINT' THEN g.geom END,
                     CASE WHEN d.dated THEN ${RangeSql.range(sql"d.lo", sql"d.hi")} END
              FROM jsonb_to_recordset($text::jsonb)
                AS d(pid text, scheme text, eid text, rel text, ref text, cls text, gjson text, gwkt text,
                     lo bigint, hi bigint, dated boolean)
              CROSS JOIN LATERAL (SELECT CASE WHEN d.gjson IS NOT NULL THEN ST_SetSRID(ST_GeomFromGeoJSON(d.gjson), 4326)
                                              WHEN d.gwkt IS NOT NULL THEN ST_GeomFromText(d.gwkt, 4326) END AS geom) g
              ORDER BY d.pid, d.scheme, d.eid, d.ref
              ON CONFLICT (place_id, scheme, external_id) DO UPDATE SET
                relation = CASE WHEN place_links.import_source = '' THEN place_links.relation ELSE EXCLUDED.relation END,
                import_version = CASE WHEN place_links.import_source = '' THEN place_links.import_version
                                      ELSE EXCLUDED.import_version END,
                item_class = EXCLUDED.item_class, item_geom = EXCLUDED.item_geom, item_period = EXCLUDED.item_period
              WHERE (place_links.import_source = EXCLUDED.import_source AND place_links.import_ref = EXCLUDED.import_ref
                     AND (place_links.relation, place_links.import_version, place_links.item_class,
                          place_links.item_geom, place_links.item_period)
                         IS DISTINCT FROM (EXCLUDED.relation, EXCLUDED.import_version, EXCLUDED.item_class,
                          EXCLUDED.item_geom, EXCLUDED.item_period))
                 OR (place_links.import_source = '' AND EXCLUDED.item_class IS NOT NULL
                     AND (place_links.item_class, place_links.item_geom, place_links.item_period)
                         IS DISTINCT FROM (EXCLUDED.item_class, EXCLUDED.item_geom, EXCLUDED.item_period))
              RETURNING (xmax = 0)""".query[Boolean].run()
      (RowCount(out.count(identity)), RowCount(removed), RowCount(out.count(!_)))
