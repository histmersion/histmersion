package com.histmersion.db

import com.histmersion.core.*
import com.histmersion.core.MergePlanner.{Input, Labels, LedgerEntry}
import com.histmersion.core.MergeText.MinHash

import com.augustnagro.magnum.*
import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.UUID
import javax.sql.DataSource

/** What this collection knows that a merge plan needs (docs/research-merge-import.md §5–§6,
  * milestone M3): the plan's input stage. [[MergePlanner]] is the pure rest.
  *
  * Read inside the caller's transaction — `merge plan` in a read-only `REPEATABLE READ` snapshot
  * ([[planFile]]), `bundle import` in its own write transaction, so the plan it checks is the plan of
  * the state it writes over. */
object MergeState:

  private def ids(us: Iterable[UUID]): List[String] = us.iterator.map(_.toString).toList.distinct

  def load(es: BundleEntities, m: BundleManifest, readProblems: List[String], originals: Originals,
      chunkChars: Int = 0, cfg: Option[MergeConfig] = None)(using DbCon): Input =
    val inst   = BundleRepo.instance
    val schema = BundleRepo.schemaVersion
    // the bundle's own uids and those it refers to (an incremental bundle names records it does not carry)
    val foreignUids = (es.revs.keySet ++ es.tombstones.map(_.uid) ++ es.references.map(_.uid)).toList.distinct
    // the redirects are few (one per merged-away record): all of them, so chains resolve
    val uidAliases = sql"SELECT alias_uid, uid FROM uid_aliases".query[(UUID, UUID)].run().toMap
    val ledger = sql"""SELECT foreign_uid, foreign_rev, local_uid, local_rev_after FROM merge_ledger
                       WHERE foreign_uid = ANY(${ids(foreignUids)}::uuid[])"""
      .query[(UUID, String, UUID, Option[String])].run().map((f, r, l, a) => f -> LedgerEntry(r, l, a)).toMap
    val idOf   = MergePlanner.mapUid(uidAliases, ledger)
    val mapped = foreignUids.map(f => f -> idOf(f)).filter((f, l) => f != l).toMap
    val localUids = foreignUids.map(idOf)

    // who has the bundle's labels here
    def owners(q: String, labels: List[String]): Map[String, UUID] =
      if labels.isEmpty then Map.empty
      else Sql.of(q, Param.A(labels.distinct)).frag.query[(String, UUID)].run().toMap
    val localized = MergePlanner.localize(es, mapped, Map.empty)
    val labels = Labels(
      citekeys = sql"SELECT citekey, uid FROM sources".query[(String, UUID)].run().toMap,
      aliases = sql"SELECT a.alias, s.uid FROM source_aliases a JOIN sources s ON s.citekey = a.citekey"
        .query[(String, UUID)].run().toMap,
      findings = owners("SELECT id, uid FROM findings WHERE id = ANY(?::text[])", es.findings.map(_.id)),
      slugs = owners("SELECT slug, uid FROM articles WHERE slug = ANY(?::text[])",
        es.articles.map(_.slug) ++ es.articleAliases.map(_.alias)),
      events = owners("SELECT id, uid FROM events WHERE id = ANY(?::text[])", es.events.map(_.id) ++ es.eventAliases.map(_.alias)),
      goals = owners("SELECT slug, uid FROM goals WHERE slug = ANY(?::text[])", es.goals.map(_.slug) ++ es.goalAliases.map(_.alias)),
      assets = {
        val srcs = localized.assets.map(_.sourceUid)
        if srcs.isEmpty then Map.empty
        else sql"""SELECT s.uid, x.name, x.uid FROM assets x JOIN sources s ON s.citekey = x.citekey
                   WHERE s.uid = ANY(${ids(srcs)}::uuid[])""".query[(UUID, String, UUID)].run()
          .map((s, n, u) => (s, n) -> u).toMap
      },
      articleAliases = owners("SELECT x.alias, a.uid FROM article_aliases x JOIN articles a ON a.slug = x.slug WHERE x.alias = ANY(?::text[])",
        es.articles.map(_.slug) ++ es.articleAliases.map(_.alias)),
      eventAliases = owners("SELECT x.alias, e.uid FROM event_aliases x JOIN events e ON e.id = x.event_id WHERE x.alias = ANY(?::text[])",
        es.events.map(_.id) ++ es.eventAliases.map(_.alias)),
      goalAliases = owners("SELECT x.alias, g.uid FROM goal_aliases x JOIN goals g ON g.slug = x.goal_slug WHERE x.alias = ANY(?::text[])",
        es.goals.map(_.slug) ++ es.goalAliases.map(_.alias)),
      places = owners("SELECT id, uid FROM places WHERE id = ANY(?::text[])", es.placeAliases.map(_.alias)),
      placeAliases = owners("SELECT x.alias, p.uid FROM place_aliases x JOIN places p ON p.id = x.place_id WHERE x.alias = ANY(?::text[])",
        es.places.map(_.id) ++ es.placeAliases.map(_.alias)))
    // every row here: a merged pair needs the survivor's findings, assets, attestations and edges, a
    // label taken needs the record that has it (only metadata — bodies and bytes stay in the database)
    val local0 = BundleRepo.entities(BundleScope.All, originals)

    // places and named periods: the bundle's that have no uid here are matched by id, else by label
    // and geometry (span); the rows here they could be one with are read too, whatever their dataset
    val ownPlaceUids  = local0.places.map(_.uid).toSet
    val ownPeriodUids = local0.periods.map(_.uid).toSet
    val newPlaces  = localized.places.filterNot(p => ownPlaceUids(p.uid))
    val newPeriods = localized.periods.filterNot(p => ownPeriodUids(p.uid))
    // …and a place of ours the bundle renamed: whether its new id is some other place's here (#888)
    val ownPlaceIds  = local0.places.map(p => p.uid -> p.id).toMap
    val renamedIds   = localized.places.filter(p => ownPlaceIds.get(p.uid).exists(_ != p.id)).map(_.id)
    val placeIdsHere = idsPresent("places", newPlaces.map(_.id) ++ renamedIds)
    val placeMatches = cfg.fold(Map.empty[UUID, String])(c => matchPlaces(newPlaces.filterNot(p => placeIdsHere(p.id)), c))
    val periodIdsHere = idsPresent("periods", newPeriods.map(_.id))
    val periodMatches = matchPeriods(newPeriods.filterNot(p => periodIdsHere(p.id)))
    val placeRows  = BundleRepo.places(BundleScope.All, (placeIdsHere ++ placeMatches.values).toList).toList
    val periodRows = BundleRepo.periods(BundleScope.All, (periodIdsHere ++ periodMatches.values).toList).toList
    val local = local0.copy(places = placeRows, periods = periodRows)

    val referenced = localized.references.map(_.uid)
    val asked      = (localUids ++ referenced ++ labels.citekeys.values ++ labels.aliases.values).distinct
    val tombstoned = sql"SELECT uid FROM tombstones WHERE uid = ANY(${ids(asked)}::uuid[])".query[UUID].run().toSet
    val live       = liveUids(referenced.distinct)
    val placeIds   = (es.events.flatMap(_.placeId) ++ placeMatches.values).distinct
    val places =
      if placeIds.isEmpty then Set.empty[String]
      else sql"SELECT id FROM places WHERE id = ANY($placeIds::text[])".query[String].run().toSet
    // the recorded versions of the articles the bundle names (by uid or by slug): where a three-way
    // merge finds its common base
    val articleUids = (localUids ++ labels.slugs.values).distinct
    val history = sql"""SELECT a.uid, encode(sha256(convert_to(v.body, 'UTF8')), 'hex'), v.body
                        FROM article_versions v JOIN articles a ON a.slug = v.slug
                        WHERE a.uid = ANY(${ids(articleUids)}::uuid[])
                        ORDER BY a.uid, v.created_at DESC, v.id DESC"""
      .query[(UUID, String, String)].run().toList.groupMap(_._1)((_, sha, body) => (sha, body))
    val remembered = sql"SELECT issue_id, decision FROM merge_decisions".query[(String, String)].run()
      .flatMap((id, d) => MergeDecision.parse(d).toOption.map(id -> _)).toMap
    Input(m, BundleInstance(inst.id, inst.label), BundleFormat.compatible(m, schema), readProblems, es, uidAliases,
      ledger, tombstoned, local, local.sources, labels, live, places, BundleRepo.reference,
      // the same rendering as BundleRepo.ts, so a bundle alias's createdAt compares as a string
      aliasTombstones =
        sql"""SELECT alias, to_char(deleted_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
               FROM alias_tombstones""".query[(String, String)].run().toMap,
      articleHistory = history, remembered = remembered, chunkChars = chunkChars,
      placeMatches = placeMatches, periodMatches = periodMatches)

  /** Which of `ids` name a row of `table` here. */
  private def idsPresent(table: String, ids: List[String])(using DbCon): Set[String] =
    if ids.isEmpty then Set.empty
    else Sql.of(s"SELECT id FROM $table WHERE id = ANY(?::text[])", Param.A(ids.distinct)).frag.query[String].run().toSet

  /** A bundle place with no id here that names a place here: the same label (reduced as the gazetteer
    * reduces it: case, filler words, punctuation) and kind, and — when both have a geometry — points
    * within `merge.place-within-km` or shapes overlapping by `merge.place-overlap-at-least` (the
    * intersection over the union); two places without a geometry match on the label alone. The
    * nearest (most overlapping) place wins; a local place is preferred to a seed one. */
  private[db] def matchPlaces(ps: List[BundlePlace], cfg: MergeConfig)(using DbCon): Map[UUID, String] =
    val norm = PlaceRepo.normLabelSql("p.label")
    ps.flatMap { b =>
      val within = cfg.placeWithinKm * 1000.0
      Sql.of(
        s"""WITH b AS (SELECT ST_GeomFromEWKB(decode(nullif(?, ''), 'hex')) AS g)
            SELECT p.id FROM places p, b
            WHERE $norm = ${PlaceRepo.normLabelSql("?")} AND p.kind = ?
              AND CASE WHEN p.geom IS NULL OR b.g IS NULL THEN p.geom IS NULL AND b.g IS NULL
                       WHEN GeometryType(p.geom) = 'POINT' AND GeometryType(b.g) = 'POINT'
                         THEN ST_DWithin(p.geom::geography, b.g::geography, ?)
                       WHEN ST_Area(p.geom) > 0 AND ST_Area(b.g) > 0
                         THEN ST_Area(ST_Intersection(p.geom, b.g)) / NULLIF(ST_Area(ST_Union(p.geom, b.g)), 0) >= ?
                       ELSE false END
            ORDER BY CASE WHEN p.dataset = 'local' THEN 0 ELSE 1 END,
                     CASE WHEN p.geom IS NULL OR b.g IS NULL THEN 0 ELSE ST_Distance(ST_Centroid(p.geom), ST_Centroid(b.g)) END,
                     p.id
            LIMIT 1""",
        Param.S(b.geom.getOrElse("")), Param.S(b.label), Param.S(b.kind), Param.D(within), Param.D(cfg.placeOverlapAtLeast))
        .frag.query[String].run().headOption.map(b.uid -> _)
    }.toMap

  /** A bundle period with no id here that names a period here: the same label (reduced) and spans
    * sharing a year. */
  private[db] def matchPeriods(ps: List[BundlePeriod])(using DbCon): Map[UUID, String] =
    val norm = PlaceRepo.normLabelSql("p.label")
    ps.flatMap { b =>
      val (lo, hi) = (b.period.flatMap(_.start), b.period.flatMap(_.end))
      Sql.of(
        s"""SELECT p.id FROM periods p
            WHERE $norm = ${PlaceRepo.normLabelSql("?")}
              AND p.period && int8range(nullif(?, '')::bigint, nullif(?, '')::bigint + 1, '[)')
            ORDER BY CASE WHEN p.dataset = 'local' THEN 0 ELSE 1 END, p.id LIMIT 1""",
        Param.S(b.label), Param.S(lo.fold("")(_.toString)), Param.S(hi.fold("")(_.toString)))
        .frag.query[String].run().headOption.map(b.uid -> _)
    }.toMap

  /** The plan's issues with their geometries (hex EWKB from the planner) turned into GeoJSON, which
    * the review screen draws — one query for every geometry of the plan. */
  private[db] def withGeoJson(p: MergePlan)(using DbCon): MergePlan =
    val hexes = p.issues.flatMap(_.detail.flatMap(_.place)).flatMap(x => x.leftGeometry.toList ++ x.rightGeometry.toList).distinct
    if hexes.isEmpty then p
    else
      val json = Sql.of("""SELECT h, ST_AsGeoJSON(ST_GeomFromEWKB(decode(h, 'hex')), 6) FROM unnest(?::text[]) AS h""",
        Param.A(hexes)).frag.query[(String, Option[String])].run().flatMap((h, j) => j.map(h -> _)).toMap
      def conv(g: Option[String]) = g.flatMap(json.get)
      p.copy(issues = p.issues.map(i => i.copy(detail = i.detail.map(d => d.copy(place = d.place.map(x =>
        x.copy(leftGeometry = conv(x.leftGeometry), rightGeometry = conv(x.rightGeometry))))))))

  /** The uids, among `uids`, of rows that exist here (of the kinds a line can refer to). */
  private[db] def liveUids(uids: List[UUID])(using DbCon): Set[UUID] =
    if uids.isEmpty then Set.empty
    else
      val us = ids(uids)
      sql"""SELECT uid FROM sources WHERE uid = ANY($us::uuid[]) UNION ALL
            SELECT uid FROM articles WHERE uid = ANY($us::uuid[]) UNION ALL
            SELECT uid FROM assets WHERE uid = ANY($us::uuid[]) UNION ALL
            SELECT uid FROM events WHERE uid = ANY($us::uuid[]) UNION ALL
            SELECT uid FROM goals WHERE uid = ANY($us::uuid[])""".query[UUID].run().toSet

  /** MinHash signatures of the OCR bodies `wanted` names (by sha256): this collection's from the
    * database, the bundle's from one pass over its blobs. Only bodies of pairs whose score they can
    * change are asked for ([[MergePlanner.sketchesWanted]]). */
  def sketches(file: Path, in: Input, wanted: Set[String], size: Int)(using DbCon): Map[String, MinHash] =
    if wanted.isEmpty then Map.empty
    else
      val here = in.sources.filter(s => s.bodySha256.exists(wanted)).distinctBy(_.bodySha256).flatMap { s =>
        sql"SELECT ocr_markdown FROM sources WHERE uid = ${s.uid}".query[Option[String]].run().headOption.flatten
          .map(b => s.bodySha256.get -> MergeText.minHash(b, size))
      }.toMap
      val there = wanted -- here.keySet
      here ++ (if there.isEmpty then Map.empty else bundleSketches(file, there, size))

  /** The OCR bodies `wanted` names (by sha256), as text: this collection's from the database, the
    * bundle's from one pass over its blobs ([[MergePlanner.bodiesWanted]]). */
  def bodies(file: Path, in: Input, wanted: Set[String])(using DbCon): Map[String, String] =
    if wanted.isEmpty then Map.empty
    else
      val here = in.sources.filter(s => s.bodySha256.exists(wanted)).distinctBy(_.bodySha256).flatMap { s =>
        sql"SELECT ocr_markdown FROM sources WHERE uid = ${s.uid}".query[Option[String]].run().headOption.flatten
          .map(b => s.bodySha256.get -> b)
      }.toMap
      val there = wanted -- here.keySet
      here ++ (if there.isEmpty then Map.empty else blobTexts(file, there))

  private def blobTexts(file: Path, wanted: Set[String]): Map[String, String] =
    val in = java.io.BufferedInputStream(Files.newInputStream(file), 1 << 16)
    try
      val tar = BundleTar.Reader(in)
      // a bounded walk: one step per tar entry, and the reader ends (or fails) at the end of the file
      @annotation.tailrec
      def loop(acc: Map[String, String]): Map[String, String] =
        if acc.size == wanted.size then acc
        else tar.next() match
          case Left(_) | Right(None) => acc
          case Right(Some(e)) =>
            Bundle.blobSha(e.name).filter(wanted) match
              case Some(sha) => loop(acc + (sha -> String(tar.readAll(), UTF_8)))
              case None      => loop(acc)
      loop(Map.empty)
    finally in.close()

  private def bundleSketches(file: Path, wanted: Set[String], size: Int): Map[String, MinHash] =
    val in = java.io.BufferedInputStream(Files.newInputStream(file), 1 << 16)
    try
      val tar = BundleTar.Reader(in)
      // a bounded walk: one step per tar entry, and the reader ends (or fails) at the end of the file
      @annotation.tailrec
      def loop(acc: Map[String, MinHash]): Map[String, MinHash] =
        if acc.size == wanted.size then acc
        else tar.next() match
          case Left(_) | Right(None) => acc
          case Right(Some(e)) =>
            Bundle.blobSha(e.name).filter(wanted) match
              case Some(sha) => loop(acc + (sha -> MergeText.minHash(String(tar.readAll(), UTF_8), size)))
              case None      => loop(acc)
      loop(Map.empty)
    finally in.close()

  /** `merge plan`: read the bundle, gather this collection's state in one read-only snapshot, plan. */
  def planFile(ds: DataSource, file: Path, resolutions: MergeResolutions, cfg: MergeConfig, originals: Originals,
      chunkChars: Int = 0): Either[String, MergePlan] =
    val loaded = withFile(file)(BundleReader.read(_, hashBlobs = false))
    loaded.manifest match
      case None => Left(("not a readable bundle" :: loaded.problems).mkString("; "))
      case Some(m) =>
        Right(transact(ds) {
          sql"SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY".update.run()
          withGeoJson(plan(file, m, loaded, resolutions, cfg, originals, chunkChars)._2.plan)
        })

  /** The plan, inside the caller's transaction. `chunkChars` places a re-anchored attestation in its
    * FTS chunk; `merge plan` and `merge apply` must pass the same value, or their plan hashes differ. */
  private[db] def plan(file: Path, m: BundleManifest, loaded: BundleReader.Loaded, resolutions: MergeResolutions,
      cfg: MergeConfig, originals: Originals, chunkChars: Int = 0)(using DbCon): (Input, MergePlanner.Result) =
    val input0 = load(loaded.entities, m, loaded.problems, originals, chunkChars, Some(cfg))
    val sk     = sketches(file, input0, MergePlanner.sketchesWanted(input0, cfg), cfg.minhashSize)
    val input  = input0.copy(bodies = bodies(file, input0, MergePlanner.bodiesWanted(input0, sk, resolutions, cfg)))
    (input, MergePlanner.plan(input, sk, resolutions, cfg))

  private[db] def withFile[A](file: Path)(f: InputStream => A): A =
    val in = java.io.BufferedInputStream(Files.newInputStream(file), 1 << 16)
    try f(in) finally in.close()
