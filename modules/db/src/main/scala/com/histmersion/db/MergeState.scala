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

  def load(es: BundleEntities, m: BundleManifest, readProblems: List[String], originals: Originals)(using DbCon): Input =
    val inst   = BundleRepo.instance
    val schema = BundleRepo.schemaVersion
    val foreignUids = (es.revs.keySet ++ es.tombstones.map(_.uid)).toList
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
      slugs = owners("SELECT slug, uid FROM articles WHERE slug = ANY(?::text[])", es.articles.map(_.slug)),
      events = owners("SELECT id, uid FROM events WHERE id = ANY(?::text[])", es.events.map(_.id)),
      goals = owners("SELECT slug, uid FROM goals WHERE slug = ANY(?::text[])", es.goals.map(_.slug)),
      assets = {
        val srcs = localized.assets.map(_.sourceUid)
        if srcs.isEmpty then Map.empty
        else sql"""SELECT s.uid, x.name, x.uid FROM assets x JOIN sources s ON s.citekey = x.citekey
                   WHERE s.uid = ANY(${ids(srcs)}::uuid[])""".query[(UUID, String, UUID)].run()
          .map((s, n, u) => (s, n) -> u).toMap
      })
    val local = BundleRepo.entities(BundleScope.Uids((localUids ++ labels.slugs.values).distinct), originals)

    val referenced = localized.references.map(_.uid)
    val asked      = (localUids ++ referenced ++ labels.citekeys.values ++ labels.aliases.values).distinct
    val tombstoned = sql"SELECT uid FROM tombstones WHERE uid = ANY(${ids(asked)}::uuid[])".query[UUID].run().toSet
    val live       = liveUids(referenced.distinct)
    val placeIds   = es.events.flatMap(_.placeId).distinct
    val places =
      if placeIds.isEmpty then Set.empty[String]
      else sql"SELECT id FROM places WHERE id = ANY($placeIds::text[])".query[String].run().toSet
    Input(m, BundleInstance(inst.id, inst.label), BundleFormat.compatible(m, schema), readProblems, es, uidAliases,
      ledger, tombstoned, local, BundleRepo.sources(BundleScope.All).toList.map(BundleRepo.sourceLine(_, Originals.none)),
      labels, live, places, BundleRepo.reference)

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
  def planFile(ds: DataSource, file: Path, resolutions: MergeResolutions, cfg: MergeConfig, originals: Originals)
      : Either[String, MergePlan] =
    val loaded = withFile(file)(BundleReader.read(_, hashBlobs = false))
    loaded.manifest match
      case None => Left(("not a readable bundle" :: loaded.problems).mkString("; "))
      case Some(m) =>
        Right(transact(ds) {
          sql"SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY".update.run()
          plan(file, m, loaded, resolutions, cfg, originals)._2.plan
        })

  /** The plan, inside the caller's transaction. */
  private[db] def plan(file: Path, m: BundleManifest, loaded: BundleReader.Loaded, resolutions: MergeResolutions,
      cfg: MergeConfig, originals: Originals)(using DbCon): (Input, MergePlanner.Result) =
    val input = load(loaded.entities, m, loaded.problems, originals)
    val sk    = sketches(file, input, MergePlanner.sketchesWanted(input, cfg), cfg.minhashSize)
    (input, MergePlanner.plan(input, sk, resolutions, cfg))

  private[db] def withFile[A](file: Path)(f: InputStream => A): A =
    val in = java.io.BufferedInputStream(Files.newInputStream(file), 1 << 16)
    try f(in) finally in.close()
