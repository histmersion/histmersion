package com.histmersion.db

import com.histmersion.util.database.RangeSql

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.MergePlanner.{Input, Labels, LedgerEntry}
import com.histmersion.domain.MergeText.MinHash

import com.augustnagro.magnum.*
import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.util.UUID
import javax.sql.DataSource
import com.histmersion.util.data.TableName
import com.histmersion.util.data.exchange.{
  BundleFormat,
  BundleReader,
  BundleScope,
  BundleTar,
  CarriedKinds,
  MergePlans,
  MergeRuleSet
}
import com.histmersion.util.storage.{ByteSource, ContentStore}

/** What this collection knows that a merge plan needs (docs/research-merge-import.md §5–§6, milestone M3): the plan's
  * input stage — its rows, labels, ledger, redirects, matched places and periods, article histories and OCR bodies.
  * [[MergePlanner]] is the pure rest; [[MergePlans]] puts the two together.
  */
object MergeState:

  private def ids(us: Iterable[UUID]): List[String] = us.iterator.map(_.toString).toList.distinct

  def load(
      es: BundleEntities,
      m: BundleManifest,
      readProblems: List[String],
      originals: ContentStore,
      chunkChars: Int = 0,
      cfg: Option[MergeSettings] = None,
      referable: List[TableName] = MergeRuleSet.transition.referableTables(CarriedKinds.transition)
  )(using DbCon): Input =
    val inst = BundleRepo.instance
    val schema = BundleRepo.schemaVersion
    // the bundle's own uids and those it refers to (an incremental bundle names records it does not carry)
    val foreignUids = (es.revs.keySet ++ es.tombstones.map(_.uid) ++ es.references.map(_.uid)).toList.distinct
    // the redirects are few (one per merged-away record): all of them, so chains resolve
    val uidAliases = sql"SELECT alias_uid, uid FROM uid_aliases".query[(UUID, UUID)].run().toMap
    val ledger = sql"""SELECT foreign_uid, foreign_rev, local_uid, local_rev_after FROM merge_ledger
                       WHERE foreign_uid = ANY(${ids(foreignUids)}::uuid[])"""
      .query[(UUID, String, UUID, Option[String])]
      .run()
      .map((f, r, l, a) => f -> LedgerEntry(r, l, a))
      .toMap
    val idOf = MergePlanner.mapUid(uidAliases, ledger)
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
        .query[(String, UUID)]
        .run()
        .toMap,
      findings = owners("SELECT id, uid FROM findings WHERE id = ANY(?::text[])", es.findings.map(_.id)),
      slugs = owners(
        "SELECT slug, uid FROM articles WHERE slug = ANY(?::text[])",
        es.articles.map(_.slug) ++ es.articleAliases.map(_.alias)
      ),
      events = owners(
        "SELECT id, uid FROM events WHERE id = ANY(?::text[])",
        es.events.map(_.id) ++ es.eventAliases.map(_.alias)
      ),
      goals = owners(
        "SELECT slug, uid FROM goals WHERE slug = ANY(?::text[])",
        es.goals.map(_.slug) ++ es.goalAliases.map(_.alias)
      ),
      assets = {
        val srcs = localized.assets.map(_.sourceUid)
        if srcs.isEmpty then Map.empty
        else
          sql"""SELECT s.uid, x.name, x.uid FROM assets x JOIN sources s ON s.citekey = x.citekey
                   WHERE s.uid = ANY(${ids(srcs)}::uuid[])"""
            .query[(UUID, String, UUID)]
            .run()
            .map((s, n, u) => (s, n) -> u)
            .toMap
      },
      articleAliases = owners(
        "SELECT x.alias, a.uid FROM article_aliases x JOIN articles a ON a.slug = x.slug WHERE x.alias = ANY(?::text[])",
        es.articles.map(_.slug) ++ es.articleAliases.map(_.alias)
      ),
      eventAliases = owners(
        "SELECT x.alias, e.uid FROM event_aliases x JOIN events e ON e.id = x.event_id WHERE x.alias = ANY(?::text[])",
        es.events.map(_.id) ++ es.eventAliases.map(_.alias)
      ),
      goalAliases = owners(
        "SELECT x.alias, g.uid FROM goal_aliases x JOIN goals g ON g.slug = x.goal_slug WHERE x.alias = ANY(?::text[])",
        es.goals.map(_.slug) ++ es.goalAliases.map(_.alias)
      ),
      places = owners("SELECT id, uid FROM places WHERE id = ANY(?::text[])", es.placeAliases.map(_.alias)),
      placeAliases = owners(
        "SELECT x.alias, p.uid FROM place_aliases x JOIN places p ON p.id = x.place_id WHERE x.alias = ANY(?::text[])",
        es.places.map(_.id) ++ es.placeAliases.map(_.alias)
      )
    )
    // every row here: a merged pair needs the survivor's findings, assets, attestations and edges, a
    // label taken needs the record that has it (only metadata — bodies and bytes stay in the database)
    val local0 = BundleRepo.entities(BundleScope.All, originals)

    // places and named periods: the bundle's that have no uid here are matched by id, else by label
    // and geometry (span); the rows here they could be one with are read too, whatever their dataset
    val ownPlaceUids = local0.places.map(_.uid).toSet
    val ownPeriodUids = local0.periods.map(_.uid).toSet
    val newPlaces = localized.places.filterNot(p => ownPlaceUids(p.uid))
    val newPeriods = localized.periods.filterNot(p => ownPeriodUids(p.uid))
    // …and a place of ours the bundle renamed: whether its new id is some other place's here (#888)
    val ownPlaceIds = local0.places.map(p => p.uid -> p.id).toMap
    val renamedIds = localized.places.filter(p => ownPlaceIds.get(p.uid).exists(_ != p.id)).map(_.id)
    val placeIdsHere = idsPresent("places", newPlaces.map(_.id) ++ renamedIds)
    val placeMatches =
      cfg.fold(Map.empty[UUID, String])(c => matchPlaces(newPlaces.filterNot(p => placeIdsHere(p.id)), c))
    val periodIdsHere = idsPresent("periods", newPeriods.map(_.id))
    val periodMatches = matchPeriods(newPeriods.filterNot(p => periodIdsHere(p.id)))
    val placeRows = BundleRepo.places(BundleScope.All, (placeIdsHere ++ placeMatches.values).toList).toList
    val periodRows = BundleRepo.periods(BundleScope.All, (periodIdsHere ++ periodMatches.values).toList).toList
    val local = local0.copy(places = placeRows, periods = periodRows)

    val referenced = localized.references.map(_.uid)
    val asked = (localUids ++ referenced ++ labels.citekeys.values ++ labels.aliases.values).distinct
    val tombstoned = sql"SELECT uid FROM tombstones WHERE uid = ANY(${ids(asked)}::uuid[])".query[UUID].run().toSet
    val live = liveUids(referenced.distinct, referable)
    val placeIds = (es.events.flatMap(_.placeId) ++ placeMatches.values).distinct
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
      .query[(UUID, String, String)]
      .run()
      .toList
      .groupMap(_._1)((_, sha, body) => (sha, body))
    val remembered = sql"SELECT issue_id, decision FROM merge_decisions"
      .query[(String, String)]
      .run()
      .flatMap((id, d) => MergeDecision.parse(d).toOption.map(id -> _))
      .toMap
    Input(
      m,
      BundleInstance(inst.id, inst.label),
      BundleFormat.compatible(m, schema),
      readProblems,
      es,
      uidAliases,
      ledger,
      tombstoned,
      local,
      local.sources,
      labels,
      live,
      places,
      BundleRepo.reference,
      // the same rendering as BundleRepo.ts, so a bundle alias's createdAt compares as a string
      aliasTombstones = sql"""SELECT alias, to_char(deleted_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
               FROM alias_tombstones""".query[(String, String)].run().toMap,
      articleHistory = history,
      remembered = remembered,
      chunkChars = chunkChars,
      placeMatches = placeMatches,
      periodMatches = periodMatches
    )

  /** Which of `ids` name a row of `table` here. */
  private def idsPresent(table: String, ids: List[String])(using DbCon): Set[String] =
    if ids.isEmpty then Set.empty
    else
      Sql.of(s"SELECT id FROM $table WHERE id = ANY(?::text[])", Param.A(ids.distinct)).frag.query[String].run().toSet

  /** A bundle place with no id here that names a place here: the same label (reduced as the gazetteer reduces it: case,
    * filler words, punctuation) and kind, and — when both have a geometry — points within `merge.place-within-km` or
    * shapes overlapping by `merge.place-overlap-at-least` (the intersection over the union); two places without a
    * geometry match on the label alone. The nearest (most overlapping) place wins; a local place is preferred to a seed
    * one.
    */
  private[db] def matchPlaces(ps: List[BundlePlace], cfg: MergeSettings)(using DbCon): Map[UUID, String] =
    val norm = PlaceRepo.normLabelSql("p.label")
    ps.flatMap { b =>
      val within = cfg.placeWithinKm * 1000.0
      Sql
        .of(
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
          Param.S(b.geom.getOrElse("")),
          Param.S(b.label),
          Param.S(b.kind),
          Param.D(within),
          Param.D(cfg.placeOverlapAtLeast)
        )
        .frag
        .query[String]
        .run()
        .headOption
        .map(b.uid -> _)
    }.toMap

  /** A bundle period with no id here that names a period here: the same label (reduced) and spans sharing a year.
    */
  private[db] def matchPeriods(ps: List[BundlePeriod])(using DbCon): Map[UUID, String] =
    val norm = PlaceRepo.normLabelSql("p.label")
    ps.flatMap { b =>
      val (lo, hi) = (b.period.flatMap(_.start), b.period.flatMap(_.end))
      Sql
        .of(
          s"""SELECT p.id FROM periods p
            WHERE $norm = ${PlaceRepo.normLabelSql("?")}
              AND p.period && ${RangeSql.range("nullif(?, '')::bigint", "nullif(?, '')::bigint")}
            ORDER BY CASE WHEN p.dataset = 'local' THEN 0 ELSE 1 END, p.id LIMIT 1""",
          Param.S(b.label),
          Param.S(lo.fold("")(_.toString)),
          Param.S(hi.fold("")(_.toString))
        )
        .frag
        .query[String]
        .run()
        .headOption
        .map(b.uid -> _)
    }.toMap

  /** The plan's issues with their geometries (hex EWKB from the planner) turned into GeoJSON, which the review screen
    * draws — one query for every geometry of the plan.
    */
  def withGeoJson(p: MergePlan)(using DbCon): MergePlan =
    val hexes =
      p.issues.flatMap(_.detail.flatMap(_.place)).flatMap(x => x.leftGeometry.toList ++ x.rightGeometry.toList).distinct
    if hexes.isEmpty then p
    else
      val json = Sql
        .of(
          """SELECT h, ST_AsGeoJSON(ST_GeomFromEWKB(decode(h, 'hex')), 6) FROM unnest(?::text[]) AS h""",
          Param.A(hexes)
        )
        .frag
        .query[(String, Option[String])]
        .run()
        .flatMap((h, j) => j.map(h -> _))
        .toMap
      def conv(g: Option[String]) = g.flatMap(json.get)
      p.copy(issues =
        p.issues.map(i =>
          i.copy(detail =
            i.detail.map(d =>
              d.copy(place =
                d.place.map(x => x.copy(leftGeometry = conv(x.leftGeometry), rightGeometry = conv(x.rightGeometry)))
              )
            )
          )
        )
      )

  /** The uids, among `uids`, of rows that exist here, in the tables of the kinds a line can refer to. */
  private[db] def liveUids(uids: List[UUID], tables: List[TableName])(using DbCon): Set[UUID] =
    if uids.isEmpty || tables.isEmpty then Set.empty
    else
      val us = ids(uids)
      Sql
        .of(
          tables.map(t => s"SELECT uid FROM ${t.value} WHERE uid = ANY(?::uuid[])").mkString(" UNION ALL "),
          tables.map(_ => Param.A(us))*
        )
        .frag
        .query[UUID]
        .run()
        .toSet

  /** MinHash signatures of the OCR bodies `wanted` names (by sha256): this collection's from the database, the bundle's
    * from one pass over its blobs. Only bodies of pairs whose score they can change are asked for
    * ([[MergePlanner.sketchesWanted]]).
    */
  def sketches(bundle: ByteSource, in: Input, wanted: Set[String], size: Int)(using DbCon): Map[String, MinHash] =
    if wanted.isEmpty then Map.empty
    else
      val here = in.sources
        .filter(s => s.bodySha256.exists(wanted))
        .distinctBy(_.bodySha256)
        .flatMap { s =>
          sql"SELECT ocr_markdown FROM sources WHERE uid = ${s.uid}"
            .query[Option[String]]
            .run()
            .headOption
            .flatten
            .map(b => s.bodySha256.get -> MergeText.minHash(b, size))
        }
        .toMap
      val there = wanted -- here.keySet
      here ++ (if there.isEmpty then Map.empty else bundleSketches(bundle, there, size))

  /** The OCR bodies `wanted` names (by sha256), as text: this collection's from the database, the bundle's from one
    * pass over its blobs ([[MergePlanner.bodiesWanted]]).
    */
  def bodies(bundle: ByteSource, in: Input, wanted: Set[String])(using DbCon): Map[String, String] =
    if wanted.isEmpty then Map.empty
    else
      val here = in.sources
        .filter(s => s.bodySha256.exists(wanted))
        .distinctBy(_.bodySha256)
        .flatMap { s =>
          sql"SELECT ocr_markdown FROM sources WHERE uid = ${s.uid}"
            .query[Option[String]]
            .run()
            .headOption
            .flatten
            .map(b => s.bodySha256.get -> b)
        }
        .toMap
      val there = wanted -- here.keySet
      here ++ (if there.isEmpty then Map.empty else blobTexts(bundle, there))

  private def blobTexts(bundle: ByteSource, wanted: Set[String]): Map[String, String] =
    bundle.read { in =>
      val tar = BundleTar.Reader(in)
      // a bounded walk: one step per tar entry, and the reader ends (or fails) at the end of the file
      @annotation.tailrec
      def loop(acc: Map[String, String]): Map[String, String] =
        if acc.size == wanted.size then acc
        else
          tar.next() match
            case Left(_) | Right(None) => acc
            case Right(Some(e))        =>
              Bundle.blobSha(e.name).filter(wanted) match
                case Some(sha) => loop(acc + (sha -> String(tar.readAll(), UTF_8)))
                case None      => loop(acc)
      loop(Map.empty)
    }

  private def bundleSketches(bundle: ByteSource, wanted: Set[String], size: Int): Map[String, MinHash] =
    bundle.read { in =>
      val tar = BundleTar.Reader(in)
      // a bounded walk: one step per tar entry, and the reader ends (or fails) at the end of the file
      @annotation.tailrec
      def loop(acc: Map[String, MinHash]): Map[String, MinHash] =
        if acc.size == wanted.size then acc
        else
          tar.next() match
            case Left(_) | Right(None) => acc
            case Right(Some(e))        =>
              Bundle.blobSha(e.name).filter(wanted) match
                case Some(sha) => loop(acc + (sha -> MergeText.minHash(String(tar.readAll(), UTF_8), size)))
                case None      => loop(acc)
      loop(Map.empty)
    }

  /** `merge plan` of a bundle on disk ([[MergePlans.planFile]]). */
  def planFile(
      ds: DataSource,
      file: Path,
      resolutions: MergeResolutions,
      cfg: MergeSettings,
      originals: ContentStore,
      chunkChars: Int = 0
  ): Either[String, MergePlan] =
    MergePlans.planFile(ds, ByteSource.file(file), resolutions, cfg, originals, chunkChars)

  private[db] def withFile[A](file: Path)(f: InputStream => A): A = ByteSource.file(file).read(f)
