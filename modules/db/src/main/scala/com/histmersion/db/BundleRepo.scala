package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*
import java.io.InputStream
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.UUID
import javax.sql.DataSource

/** Which rows a bundle read covers: every one, the ones changed after a moment (`--since`), or the
  * ones with the given uids (what an import compares the bundle against). */
enum BundleScope:
  case All
  case Since(at: String)
  case Uids(uids: List[UUID])

/** Where the server keeps the originals it stored at ingest — the converter's spool,
  * `<dir>/<sha256>.<ext>` (`IngestHandlers.spool`). The bundle code reaches it through this, so the
  * `db` module needs no config. */
trait Originals:
  /** The stored original with this digest: its path and extension. */
  def find(sha256: String): Option[(Path, String)]
  /** Store an original as `<sha256>.<ext>` unless one is there already; `Left` with why when it could
    * not be — including content that does not hash to `sha256`. */
  def store(sha256: String, ext: String, in: InputStream): Either[String, Unit]

object Originals:
  val none: Originals = new Originals:
    def find(sha256: String): Option[(Path, String)] = None
    def store(sha256: String, ext: String, in: InputStream): Either[String, Unit] =
      Left("this server stores no originals")

  /** A content-addressed directory: `<dir>/<sha256>.<ext>` — the converter's spool on a server. */
  def inDir(dir: Path): Originals = new Originals:
    def find(sha256: String): Option[(Path, String)] =
      scala.util.Try {
        val s = Files.list(dir)
        try s.filter(p => p.getFileName.toString.startsWith(sha256 + ".")).findFirst().orElse(null)
        finally s.close()
      }.toOption.flatMap(Option(_)).map(p => p -> p.getFileName.toString.drop(sha256.length + 1))

    /** Written to a temporary file, hashed, and moved into place only when the content IS `sha256`:
      * the directory is content-addressed, and `reocr` trusts the name. */
    def store(sha256: String, ext: String, in: InputStream): Either[String, Unit] =
      if find(sha256).isDefined then
        val _ = in.transferTo(java.io.OutputStream.nullOutputStream())
        Right(())
      else
        scala.util.Try {
          Files.createDirectories(dir)
          val tmp = Files.createTempFile(dir, "bundle-", ".part")
          try
            val md  = java.security.MessageDigest.getInstance("SHA-256")
            val out = java.security.DigestOutputStream(Files.newOutputStream(tmp), md)
            try { val _ = in.transferTo(out) } finally out.close()
            val actual = BundleFormat.hex(md.digest())
            if actual != sha256 then Left(s"its content hashes to $actual")
            else
              val _ = Files.move(tmp, dir.resolve(s"$sha256.${safeExt(ext)}"), StandardCopyOption.ATOMIC_MOVE)
              Right(())
          finally
            val _ = Files.deleteIfExists(tmp)
        }.toEither.left.map(e => s"cannot store it in $dir: ${e.getMessage}").flatten

  /** An extension safe in a file name: short and alphanumeric, else `bin`. Pure. */
  def safeExt(ext: String): String =
    val e = ext.trim.toLowerCase(java.util.Locale.ROOT)
    if e.nonEmpty && e.length <= 8 && e.forall(_.isLetterOrDigit) then e else "bin"

// ── flat rows (Magnum maps a SELECT's columns positionally) ─────────────────────────────────────────

private[db] final case class BSourceRow(
    uid: UUID, origin: UUID, citekey: String, title: String, authors: List[String], doi: Option[String],
    year: Option[Int], venue: Option[String], status: SourceStatus, topics: List[String],
    regions: List[String], tags: List[String], pNull: Boolean, pLo: Option[Long], pHi: Option[Long],
    summary: Option[String], notes: Option[String], provenance: Option[String], citedBy: Option[Int],
    influential: Option[Int], addedDate: String, bodySha: Option[String], bodyBytes: Option[Long],
    originalSha: Option[String], updatedAt: String, isbns: List[String])
private[db] final case class BFindingRow(
    uid: UUID, origin: UUID, sourceUid: UUID, id: String, claim: String, region: Option[String],
    lon: Option[Double], lat: Option[Double], pNull: Boolean, pLo: Option[Long], pHi: Option[Long],
    periodName: Option[String], tags: List[String], confidence: Option[Confidence],
    significance: Option[String], updatedAt: String)
private[db] final case class BAssessmentRow(sourceUid: UUID, checkedAt: String, retracted: Boolean,
    notices: String, citedBy: Option[Int], influential: Option[Int], verdict: String)
private[db] final case class BArticleRow(
    uid: UUID, origin: UUID, slug: String, title: String, kind: String, body: String,
    summary: Option[String], tags: List[String], regions: List[String], pNull: Boolean,
    pLo: Option[Long], pHi: Option[Long], updatedAt: String)
private[db] final case class BVersionRow(uid: UUID, origin: UUID, articleUid: UUID, bodySha: String,
    bodyBytes: Long, summary: Option[String], versionOf: String, createdAt: String)
private[db] final case class BAssetRow(
    uid: UUID, origin: UUID, sourceUid: UUID, name: String, mime: String, sha: String, bytes: Long,
    width: Option[Int], height: Option[Int], kind: Option[String], caption: Option[String],
    license: Option[String], geom: Option[String], pNull: Boolean, pLo: Option[Long], pHi: Option[Long],
    updatedAt: String)
private[db] final case class BEventRow(
    uid: UUID, origin: UUID, id: String, label: String, kind: String, notBefore: Option[Long],
    beginBy: Option[Long], endFrom: Option[Long], notAfter: Option[Long], historicity: String,
    geom: Option[String], placeId: Option[String], parentUid: Option[UUID], description: Option[String],
    citekey: Option[String], sourceUid: Option[UUID], updatedAt: String)
private[db] final case class BDatingRow(eventUid: UUID, citekey: String, sourceUid: Option[UUID],
    notBefore: Option[Long], beginBy: Option[Long], endFrom: Option[Long], notAfter: Option[Long],
    method: Option[String], note: Option[String])
private[db] final case class BEventEdgeRow(eventUid: UUID, dstUid: UUID, kind: String, citekey: String,
    sourceUid: Option[UUID], note: Option[String])
private[db] final case class BParticipantRow(eventUid: UUID, actor: String, role: String,
    citekey: Option[String], sourceUid: Option[UUID])
private[db] final case class BGoalRow(
    uid: UUID, origin: UUID, slug: String, title: String, status: String, notes: Option[String],
    tags: List[String], regions: List[String], pNull: Boolean, pLo: Option[Long], pHi: Option[Long],
    parentUid: Option[UUID], createdAt: String, updatedAt: String, plan: Option[String],
    planApprovedAt: Option[String], budgetUsd: Option[BigDecimal])
private[db] final case class BGoalLinkRow(goalUid: UUID, kind: String, ref: String, refUid: Option[UUID],
    note: Option[String], createdAt: String)
private[db] final case class BGoalScheduleRow(goalUid: UUID, kind: String, cron: String, enabled: Boolean,
    budgetUsd: Option[BigDecimal])
private[db] final case class BPlaceRow(
    uid: UUID, origin: UUID, id: String, label: String, kind: String, geom: Option[String], pNull: Boolean,
    pLo: Option[Long], pHi: Option[Long], source: Option[String], properties: Option[String], updatedAt: String)
private[db] final case class BPlaceNameRow(placeId: String, name: String, lang: String, pNull: Boolean,
    pLo: Option[Long], pHi: Option[Long])
private[db] final case class BPlaceRelRow(childId: String, parentId: String, kind: String, pNull: Boolean,
    pLo: Option[Long], pHi: Option[Long])
private[db] final case class BPlaceTypeRow(placeId: String, typeLabel: String, pNull: Boolean, pLo: Option[Long],
    pHi: Option[Long], citekey: Option[String])
private[db] final case class BPeriodRow(
    uid: UUID, origin: UUID, id: String, label: String, authority: Option[String], pNull: Boolean,
    pLo: Option[Long], pHi: Option[Long], region: Option[String], parentId: Option[String], updatedAt: String)
private[db] final case class BQueueRow(
    uid: UUID, origin: UUID, list: String, text: String, topic: Option[String], status: String,
    reason: Option[String], createdAt: String, doneAt: Option[String], updatedAt: String)
private[db] final case class BTombstoneRow(uid: UUID, kind: String, origin: UUID, deletedAt: String)

given DbCodec[BSourceRow]      = DbCodec.derived
given DbCodec[BFindingRow]     = DbCodec.derived
given DbCodec[BAssessmentRow]  = DbCodec.derived
given DbCodec[BArticleRow]     = DbCodec.derived
given DbCodec[BVersionRow]     = DbCodec.derived
given DbCodec[BAssetRow]       = DbCodec.derived
given DbCodec[BEventRow]       = DbCodec.derived
given DbCodec[BDatingRow]      = DbCodec.derived
given DbCodec[BEventEdgeRow]   = DbCodec.derived
given DbCodec[BParticipantRow] = DbCodec.derived
given DbCodec[BGoalRow]        = DbCodec.derived
given DbCodec[BGoalLinkRow]    = DbCodec.derived
given DbCodec[BGoalScheduleRow] = DbCodec.derived
given DbCodec[BPlaceRow]       = DbCodec.derived
given DbCodec[BPlaceNameRow]   = DbCodec.derived
given DbCodec[BPlaceRelRow]    = DbCodec.derived
given DbCodec[BPlaceTypeRow]   = DbCodec.derived
given DbCodec[BPeriodRow]      = DbCodec.derived
given DbCodec[BQueueRow]       = DbCodec.derived
given DbCodec[BTombstoneRow]   = DbCodec.derived
given DbCodec[InstanceInfo]    = DbCodec.derived

/** The bundle's view of the database (docs/research-merge-import.md §4): every table a bundle
  * carries, read as the [[Bundle]] line types, in a deterministic order (by uid; owned sets by their
  * owner's uid, then their own key, in the "C" collation). One read per kind; `scope` selects the rows.
  *
  * Shared by the export (`BundleExport`) and the import (`BundleImport`), which reads the LOCAL rows
  * with the uids a bundle carries through the very same code — so "the same content" means the same
  * line, and the same rev, on both sides. */
object BundleRepo:

  /** `timestamptz` as the wire carries it everywhere: UTC, microseconds, `Z`. */
  private[db] def ts(col: String): String =
    s"""to_char($col AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')"""

  /** A range as the three columns [[range]] reads back. */
  private def rangeCols(col: String): String = s"$col IS NULL, lower($col), upper($col) - 1"

  private[db] def range(isNull: Boolean, lo: Option[Long], hi: Option[Long]): Option[BundleRange] =
    Option.when(!isNull)(BundleRange(lo, hi))

  /** `WHERE` for the scope, on a table's uid and change-clock columns. */
  private def where(scope: BundleScope, uid: String, clock: String): Sql = scope match
    case BundleScope.All       => Sql.raw("TRUE")
    case BundleScope.Since(at) => Sql.of(s"$clock > ?::timestamptz", Param.S(at))
    case BundleScope.Uids(us)  => Sql.of(s"$uid = ANY(?::uuid[])", Param.A(us.map(_.toString)))


  /** The uid of whatever a label names, for a target that HAS a uid — the label of an attestation's or
    * an asset link's target is kept, and its uid travels beside it for the matcher to come (M3). */
  private def targetUid(tt: String, tid: String): String =
    s"""CASE $tt WHEN 'finding' THEN (SELECT uid FROM findings WHERE id = $tid)
                 WHEN 'source'  THEN (SELECT uid FROM sources WHERE citekey = $tid)
                 WHEN 'article' THEN (SELECT uid FROM articles WHERE slug = $tid)
                 WHEN 'event'   THEN (SELECT uid FROM events WHERE id = $tid) END"""

  // ── this instance ──────────────────────────────────────────────────────────────────────────

  def instance(ds: DataSource): InstanceInfo = connect(ds)(instance)

  private[db] def instance(using DbCon): InstanceInfo =
    Sql.raw(s"SELECT id, label, ${ts("created_at")} FROM instance").frag.query[InstanceInfo].run().head

  /** Name this instance; a blank label clears it. */
  def setLabel(ds: DataSource, label: Option[String]): InstanceInfo =
    transact(ds):
      val l = label.map(_.trim).filter(_.nonEmpty)
      sql"UPDATE instance SET label = $l".update.run()
      instance

  // ── reads, one per file ────────────────────────────────────────────────────────────────────

  private[db] def sources(scope: BundleScope)(using DbCon): Vector[BSourceRow] =
    (Sql.raw(s"""SELECT s.uid, s.origin, s.citekey, s.title, s.authors, s.doi, s.year, s.venue, s.status,
                       s.topics, s.regions, s.tags, ${rangeCols("s.period")}, s.summary, s.notes, s.source,
                       s.cited_by_count, s.influential_citations, to_char(s.added_date, 'YYYY-MM-DD'),
                       encode(sha256(convert_to(s.ocr_markdown, 'UTF8')), 'hex'),
                       octet_length(convert_to(s.ocr_markdown, 'UTF8'))::bigint,
                       nullif(lower(btrim(s.sha256)), ''), ${ts("s.updated_at")},
                       coalesce((SELECT array_agg(i.value ORDER BY i.value COLLATE "C") FROM source_identifiers i
                                 WHERE i.source_uid = s.uid AND i.scheme = 'isbn'), '{}'::text[])
                FROM sources s WHERE """) ++ where(scope, "s.uid", "s.updated_at") ++ " ORDER BY s.uid")
      .frag.query[BSourceRow].run()

  private[db] def findings(scope: BundleScope)(using DbCon): Vector[BundleFinding] =
    (Sql.raw(s"""SELECT f.uid, f.origin, s.uid, f.id, f.claim, f.region_name, ST_X(f.geom), ST_Y(f.geom),
                       ${rangeCols("f.period")}, f.period_name, f.tags, f.confidence, f.significance,
                       ${ts("f.updated_at")}
                FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE """) ++
      where(scope, "f.uid", "f.updated_at") ++ " ORDER BY f.uid")
      .frag.query[BFindingRow].run().map { r =>
        BundleFinding(r.uid, r.origin, r.sourceUid, r.id, r.claim, r.region, r.lon, r.lat,
          range(r.pNull, r.pLo, r.pHi), r.periodName, r.tags, r.confidence, r.significance, r.updatedAt)
      }

  /** The out-edges of the sources in scope — a source carries its whole set. */
  private[db] def edges(scope: BundleScope)(using DbCon): Vector[BundleEdge] =
    (Sql.raw("""SELECT a.uid, b.uid, e.kind FROM edges e
                JOIN sources a ON a.citekey = e.src JOIN sources b ON b.citekey = e.dst WHERE """) ++
      where(scope, "a.uid", "a.updated_at") ++ """ ORDER BY a.uid, b.uid, e.kind COLLATE "C"""")
      .frag.query[(UUID, UUID, String)].run().map(BundleEdge.apply)

  private[db] def unresolvedEdges(scope: BundleScope)(using DbCon): Vector[BundleUnresolvedEdge] =
    (Sql.raw("""SELECT a.uid, u.dst, u.kind FROM unresolved_edges u
                JOIN sources a ON a.citekey = u.src WHERE """) ++
      where(scope, "a.uid", "a.updated_at") ++ """ ORDER BY a.uid, u.dst COLLATE "C", u.kind COLLATE "C"""")
      .frag.query[(UUID, String, String)].run().map(BundleUnresolvedEdge.apply)

  private[db] def assessments(scope: BundleScope)(using DbCon): Vector[BundleAssessment] =
    (Sql.raw(s"""SELECT s.uid, ${ts("a.checked_at")}, a.retracted, a.notices::text, a.cited_by,
                       a.influential, a.verdict
                FROM assessments a JOIN sources s ON s.citekey = a.citekey WHERE """) ++
      where(scope, "s.uid", "a.checked_at") ++ " ORDER BY s.uid")
      .frag.query[BAssessmentRow].run().map { r =>
        BundleAssessment(r.sourceUid, r.checkedAt, r.retracted,
          io.circe.parser.parse(r.notices).getOrElse(io.circe.Json.arr()), r.citedBy, r.influential, r.verdict)
      }

  private[db] def aliases(scope: BundleScope)(using DbCon): Vector[BundleAlias] =
    (Sql.raw(s"""SELECT a.alias, s.uid, ${ts("a.created_at")}
                FROM source_aliases a JOIN sources s ON s.citekey = a.citekey WHERE """) ++
      where(scope, "s.uid", "a.created_at") ++ """ ORDER BY a.alias COLLATE "C"""")
      .frag.query[(String, UUID, String)].run().map(BundleAlias.apply)

  private[db] def articles(scope: BundleScope)(using DbCon): Vector[BundleArticle] =
    (Sql.raw(s"""SELECT a.uid, a.origin, a.slug, a.title, a.kind, a.body, a.summary, a.tags, a.regions,
                       ${rangeCols("a.period")}, ${ts("a.updated_at")}
                FROM articles a WHERE """) ++ where(scope, "a.uid", "a.updated_at") ++ " ORDER BY a.uid")
      .frag.query[BArticleRow].run().map { r =>
        BundleArticle(r.uid, r.origin, r.slug, r.title, r.kind, r.body, r.summary, r.tags, r.regions,
          range(r.pNull, r.pLo, r.pHi), r.updatedAt)
      }

  private[db] def versions(scope: BundleScope)(using DbCon): Vector[BVersionRow] =
    (Sql.raw(s"""SELECT v.uid, v.origin, a.uid, encode(sha256(convert_to(v.body, 'UTF8')), 'hex'),
                       octet_length(convert_to(v.body, 'UTF8'))::bigint, v.summary,
                       ${ts("v.updated_at")}, ${ts("v.created_at")}
                FROM article_versions v JOIN articles a ON a.slug = v.slug WHERE """) ++
      where(scope, "v.uid", "v.created_at") ++ " ORDER BY v.uid")
      .frag.query[BVersionRow].run()

  private[db] def assets(scope: BundleScope)(using DbCon): Vector[BAssetRow] =
    (Sql.raw(s"""SELECT x.uid, x.origin, s.uid, x.name, x.mime, x.sha256, length(x.bytes)::bigint,
                       x.width, x.height, x.kind, x.caption, x.license, encode(ST_AsEWKB(x.geom), 'hex'),
                       ${rangeCols("x.period")}, ${ts("x.updated_at")}
                FROM assets x JOIN sources s ON s.citekey = x.citekey WHERE """) ++
      where(scope, "x.uid", "x.updated_at") ++ " ORDER BY x.uid")
      .frag.query[BAssetRow].run()

  private[db] def assetLinks(scope: BundleScope)(using DbCon): Vector[BundleAssetLink] =
    (Sql.raw(s"""SELECT l.uid, l.origin, x.uid, l.relation, l.target_type, l.target_id,
                       ${targetUid("l.target_type", "l.target_id")}, l.xywh, l.notes, ${ts("l.updated_at")}
                FROM asset_links l JOIN assets x ON x.citekey = l.citekey AND x.name = l.asset_name WHERE """) ++
      where(scope, "l.uid", "l.updated_at") ++ " ORDER BY l.uid")
      .frag.query[(UUID, UUID, UUID, String, String, String, Option[UUID], Option[String], Option[String], String)]
      .run().map(BundleAssetLink.apply(_, _, _, _, _, _, _, _, _, _))

  private[db] def attestations(scope: BundleScope)(using DbCon): Vector[BundleAttestation] =
    (Sql.raw(s"""SELECT t.uid, t.origin, s.uid, t.target_type, t.target_id,
                       ${targetUid("t.target_type", "t.target_id")}, t.quote, t.prefix, t.suffix,
                       t.char_start, t.char_end, t.chunk_ord, t.page, t.canvas, t.xywh, t.cts_urn, t.note,
                       ${ts("t.created_at")}, ${ts("t.updated_at")}
                FROM attestations t JOIN sources s ON s.citekey = t.citekey WHERE """) ++
      where(scope, "t.uid", "t.updated_at") ++ " ORDER BY t.uid")
      .frag.query[BAttestationFullRow].run().map(r =>
        BundleAttestation(r.uid, r.origin, r.sourceUid, r.targetType, r.targetId, r.targetUid, r.quote,
          r.prefix, r.suffix, r.charStart, r.charEnd, r.chunkOrd, r.page, r.canvas, r.xywh, r.ctsUrn,
          r.note, r.createdAt, r.updatedAt))

  private[db] def events(scope: BundleScope)(using DbCon): Vector[BundleEvent] =
    val w = where(scope, "e.uid", "e.updated_at")
    val rows = (Sql.raw(s"""SELECT e.uid, e.origin, e.id, e.label, e.kind, e.not_before, e.begin_by,
                                  e.end_from, e.not_after, e.historicity, encode(ST_AsEWKB(e.geom), 'hex'),
                                  e.place_id, p.uid, e.description, e.citekey, s.uid, ${ts("e.updated_at")}
                           FROM events e LEFT JOIN events p ON p.id = e.parent_id
                           LEFT JOIN sources s ON s.citekey = e.citekey WHERE """) ++ w ++ " ORDER BY e.uid")
      .frag.query[BEventRow].run()
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
      """ ORDER BY e.uid, p.actor COLLATE "C", p.role COLLATE "C"""").frag.query[BParticipantRow].run()
      .groupBy(_.eventUid)
    rows.map { r =>
      BundleEvent(r.uid, r.origin, r.id, r.label, r.kind, r.notBefore, r.beginBy, r.endFrom, r.notAfter,
        r.historicity, r.geom, r.placeId, r.parentUid, r.description, r.citekey, r.sourceUid,
        datings.getOrElse(r.uid, Vector.empty).toList.map(d =>
          BundleEventDating(d.citekey, d.sourceUid, d.notBefore, d.beginBy, d.endFrom, d.notAfter, d.method, d.note)),
        edges.getOrElse(r.uid, Vector.empty).toList.map(g =>
          BundleEventEdge(g.dstUid, g.kind, g.citekey, g.sourceUid, g.note)),
        people.getOrElse(r.uid, Vector.empty).toList.map(p =>
          BundleEventParticipant(p.actor, p.role, p.citekey, p.sourceUid)),
        r.updatedAt)
    }

  private[db] def goals(scope: BundleScope)(using DbCon): Vector[BundleGoal] =
    val w = where(scope, "g.uid", "g.updated_at")
    val rows = (Sql.raw(s"""SELECT g.uid, g.origin, g.slug, g.title, g.status, g.notes, g.tags, g.regions,
                       ${rangeCols("g.period")}, p.uid, ${ts("g.created_at")}, ${ts("g.updated_at")},
                       g.plan::text, ${ts("g.plan_approved_at")}, g.budget_usd
                FROM goals g LEFT JOIN goals p ON p.slug = g.parent_slug WHERE """) ++ w ++ " ORDER BY g.uid")
      .frag.query[BGoalRow].run()
    // a linked source, article or queue item travels by uid (a queue item's id is a local serial)
    val links = (Sql.raw(s"""SELECT g.uid, l.kind,
                        CASE l.kind WHEN 'queue-item' THEN coalesce(q.uid::text, l.ref) ELSE l.ref END,
                        CASE l.kind WHEN 'source' THEN s.uid WHEN 'article' THEN a.uid WHEN 'queue-item' THEN q.uid END,
                        l.note, ${ts("l.created_at")}
                 FROM goal_links l JOIN goals g ON g.slug = l.goal_slug
                 LEFT JOIN sources s ON l.kind = 'source' AND s.citekey = l.ref
                 LEFT JOIN articles a ON l.kind = 'article' AND a.slug = l.ref
                 LEFT JOIN queue_items q ON l.kind = 'queue-item' AND q.id::text = l.ref WHERE """) ++ w ++
      """ ORDER BY g.uid, l.kind COLLATE "C", l.ref COLLATE "C"""").frag.query[BGoalLinkRow].run().groupBy(_.goalUid)
    val schedules = (Sql.raw("""SELECT g.uid, x.kind, x.cron, x.enabled, x.budget_usd
                     FROM goal_schedules x JOIN goals g ON g.slug = x.goal_slug WHERE """) ++ w ++
      """ ORDER BY g.uid, x.kind COLLATE "C"""").frag.query[BGoalScheduleRow].run().groupBy(_.goalUid)
    rows.map { r =>
      BundleGoal(r.uid, r.origin, r.slug, r.title, r.status, r.notes, r.tags, r.regions,
        range(r.pNull, r.pLo, r.pHi), r.parentUid, r.createdAt, r.updatedAt,
        plan = r.plan.flatMap(io.circe.parser.parse(_).toOption),
        planApprovedAt = r.planApprovedAt,
        budgetUsd = r.budgetUsd.map(_.toDouble),
        links = links.get(r.uid).map(_.toList.map(l => BundleGoalLink(l.kind, l.ref, l.refUid, l.note, l.createdAt)))
          .filter(_.nonEmpty),
        schedules = schedules.get(r.uid).map(_.toList.map(x => BundleGoalSchedule(x.kind, x.cron, x.enabled,
          x.budgetUsd.map(_.toDouble)))).filter(_.nonEmpty))
    }

  /** The places of this collection's own (`dataset = 'local'`, V052) in scope — or, with
    * `alsoIds`, any place with one of those ids as well (what a merge compares the bundle's against). */
  private[db] def places(scope: BundleScope, alsoIds: List[String] = Nil)(using DbCon): Vector[BundlePlace] =
    val own = Sql.raw("p.dataset = 'local' AND ") ++ where(scope, "p.uid", "p.updated_at")
    val sel = if alsoIds.isEmpty then own
      else Sql.raw("((") ++ own ++ Sql.of(") OR p.id = ANY(?::text[]))", Param.A(alsoIds.distinct))
    val rows = (Sql.raw(s"""SELECT p.uid, p.origin, p.id, p.label, p.kind, encode(ST_AsEWKB(p.geom), 'hex'),
                       ${rangeCols("p.period")}, p.source, p.properties::text, ${ts("p.updated_at")}
                FROM places p WHERE """) ++ sel ++ " ORDER BY p.uid").frag.query[BPlaceRow].run()
    val ids = rows.map(_.id).toList
    if ids.isEmpty then Vector.empty
    else
      val names = sql"""SELECT place_id, name, lang, period IS NULL, lower(period), upper(period) - 1 FROM place_names
                        WHERE place_id = ANY($ids::text[]) ORDER BY place_id, name COLLATE "C", lang COLLATE "C""""
        .query[BPlaceNameRow].run().groupBy(_.placeId)
      val rels = sql"""SELECT child_id, parent_id, kind, period IS NULL, lower(period), upper(period) - 1 FROM place_relations
                       WHERE child_id = ANY($ids::text[]) ORDER BY child_id, parent_id COLLATE "C", kind COLLATE "C""""
        .query[BPlaceRelRow].run().groupBy(_.childId)
      val links = sql"""SELECT place_id, scheme, external_id, relation FROM place_links WHERE place_id = ANY($ids::text[])
                        ORDER BY place_id, scheme COLLATE "C", external_id COLLATE "C""""
        .query[(String, String, String, String)].run().groupBy(_._1)
      val types = sql"""SELECT place_id, type_label, period IS NULL, lower(period), upper(period) - 1, citekey
                        FROM place_types WHERE place_id = ANY($ids::text[]) ORDER BY place_id, type_label COLLATE "C""""
        .query[BPlaceTypeRow].run().groupBy(_.placeId)
      rows.map { r =>
        BundlePlace(r.uid, r.origin, r.id, r.label, r.kind, r.geom, range(r.pNull, r.pLo, r.pHi), r.source,
          r.properties.flatMap(io.circe.parser.parse(_).toOption),
          names.getOrElse(r.id, Vector.empty).toList.map(n =>
            BundlePlaceName(n.name, Option(n.lang).filter(_.nonEmpty), range(n.pNull, n.pLo, n.pHi))),
          rels.getOrElse(r.id, Vector.empty).toList.map(x => BundlePlaceRelation(x.parentId, x.kind, range(x.pNull, x.pLo, x.pHi))),
          links.getOrElse(r.id, Vector.empty).toList.map((_, sc, ex, rel) => BundlePlaceLink(sc, ex, rel)),
          types.getOrElse(r.id, Vector.empty).toList.map(t => BundlePlaceType(t.typeLabel, range(t.pNull, t.pLo, t.pHi), t.citekey)),
          r.updatedAt)
      }

  /** The named periods of this collection's own in scope, or with one of `alsoIds`. */
  private[db] def periods(scope: BundleScope, alsoIds: List[String] = Nil)(using DbCon): Vector[BundlePeriod] =
    val own = Sql.raw("p.dataset = 'local' AND ") ++ where(scope, "p.uid", "p.updated_at")
    val sel = if alsoIds.isEmpty then own
      else Sql.raw("((") ++ own ++ Sql.of(") OR p.id = ANY(?::text[]))", Param.A(alsoIds.distinct))
    (Sql.raw(s"""SELECT p.uid, p.origin, p.id, p.label, p.authority, ${rangeCols("p.period")}, p.region, p.parent_id,
                       ${ts("p.updated_at")}
                FROM periods p WHERE """) ++ sel ++ " ORDER BY p.uid").frag.query[BPeriodRow].run().map { r =>
      BundlePeriod(r.uid, r.origin, r.id, r.label, r.authority, range(r.pNull, r.pLo, r.pHi), r.region, r.parentId, r.updatedAt)
    }

  /** Old article slugs (V042) of the articles in scope. */
  private[db] def articleAliases(scope: BundleScope)(using DbCon): Vector[BundleArticleAlias] =
    (Sql.raw(s"""SELECT x.alias, a.uid, ${ts("x.created_at")}
                FROM article_aliases x JOIN articles a ON a.slug = x.slug WHERE """) ++
      where(scope, "a.uid", "x.created_at") ++ """ ORDER BY x.alias COLLATE "C"""")
      .frag.query[(String, UUID, String)].run().map(BundleArticleAlias.apply)

  /** Old event ids and goal slugs (V053) of the events and goals in scope. */
  private[db] def eventAliases(scope: BundleScope)(using DbCon): Vector[BundleEventAlias] =
    (Sql.raw(s"""SELECT x.alias, e.uid, ${ts("x.created_at")}
                FROM event_aliases x JOIN events e ON e.id = x.event_id WHERE """) ++
      where(scope, "e.uid", "x.created_at") ++ """ ORDER BY x.alias COLLATE "C"""")
      .frag.query[(String, UUID, String)].run().map(BundleEventAlias.apply)

  private[db] def goalAliases(scope: BundleScope)(using DbCon): Vector[BundleGoalAlias] =
    (Sql.raw(s"""SELECT x.alias, g.uid, ${ts("x.created_at")}
                FROM goal_aliases x JOIN goals g ON g.slug = x.goal_slug WHERE """) ++
      where(scope, "g.uid", "x.created_at") ++ """ ORDER BY x.alias COLLATE "C"""")
      .frag.query[(String, UUID, String)].run().map(BundleGoalAlias.apply)

  /** Old place ids (V055) of the places a bundle carries — this collection's own (as [[places]]): a
    * gazetteer seed place travels as the reference fingerprint, not as a row. */
  private[db] def placeAliases(scope: BundleScope)(using DbCon): Vector[BundlePlaceAlias] =
    (Sql.raw(s"""SELECT x.alias, p.uid, ${ts("x.created_at")}
                FROM place_aliases x JOIN places p ON p.id = x.place_id WHERE p.dataset = 'local' AND """) ++
      where(scope, "p.uid", "x.created_at") ++ """ ORDER BY x.alias COLLATE "C"""")
      .frag.query[(String, UUID, String)].run().map(BundlePlaceAlias.apply)

  private[db] def queue(scope: BundleScope)(using DbCon): Vector[BundleQueueItem] =
    (Sql.raw(s"""SELECT q.uid, q.origin, q.list, q.text, q.topic, q.status, q.reason,
                       ${ts("q.created_at")}, ${ts("q.done_at")}, ${ts("q.updated_at")}
                FROM queue_items q WHERE """) ++ where(scope, "q.uid", "q.updated_at") ++ " ORDER BY q.uid")
      .frag.query[BQueueRow].run().map { r =>
        BundleQueueItem(r.uid, r.origin, r.list, r.text, r.topic, r.status, r.reason, r.createdAt,
          r.doneAt, r.updatedAt)
      }

  private[db] def tombstones(scope: BundleScope)(using DbCon): Vector[BundleTombstone] =
    (Sql.raw(s"SELECT uid, kind, origin, ${ts("deleted_at")} FROM tombstones WHERE ") ++
      where(scope, "uid", "deleted_at") ++ " ORDER BY uid")
      .frag.query[BTombstoneRow].run().flatMap { r =>
        BundleKind.fromWire(r.kind).map(BundleTombstone(r.uid, _, r.origin, r.deletedAt))
      }

  /** The gazetteer and the named periods, fingerprinted: not carried, compared ([[BundleReference]]). */
  private[db] def reference(using DbCon): BundleReference =
    // the SEED rows only: a place or period of this collection's own travels in the bundle itself
    def digest(table: String): (Int, String) =
      Sql.raw(s"""SELECT count(*)::int, coalesce(md5(string_agg(id, ',' ORDER BY id COLLATE "C")), '')
                  FROM $table WHERE dataset = 'seed'""").frag.query[(Int, String)].run().head
    val (np, dp) = digest("places")
    val (nq, dq) = digest("periods")
    BundleReference(np, dp, nq, dq)

  // ── as line types ──────────────────────────────────────────────────────────────────────────

  /** A source row as its bundle line; `originals` says whether the stored original goes along. */
  private[db] def sourceLine(r: BSourceRow, originals: Originals): BundleSource =
    BundleSource(r.uid, r.origin, r.citekey, r.title, r.authors, r.doi, r.year, r.venue, r.status, r.topics,
      r.regions, r.tags, range(r.pNull, r.pLo, r.pHi), r.summary, r.notes, r.provenance, r.citedBy,
      r.influential, r.addedDate, r.bodySha, r.originalSha,
      r.originalSha.flatMap(originals.find).map(_._2), r.updatedAt, isbns = Option.when(r.isbns.nonEmpty)(r.isbns))

  private[db] def versionLine(r: BVersionRow): BundleArticleVersion =
    BundleArticleVersion(r.uid, r.origin, r.articleUid, r.bodySha, r.summary, r.versionOf, r.createdAt)

  private[db] def assetLine(r: BAssetRow): BundleAsset =
    BundleAsset(r.uid, r.origin, r.sourceUid, r.name, r.mime, r.sha, r.width, r.height, r.kind, r.caption,
      r.license, r.geom, range(r.pNull, r.pLo, r.pHi), r.updatedAt)

  /** Everything in scope, as the bundle's lines. The three reads whose rows also locate the blobs can
    * be passed in, so an export does not run them twice. */
  private[db] def entities(scope: BundleScope, originals: Originals)(using DbCon): BundleEntities =
    entities(scope, originals, sources(scope), versions(scope), assets(scope))

  private[db] def entities(scope: BundleScope, originals: Originals, srcRows: Vector[BSourceRow],
      verRows: Vector[BVersionRow], assetRows: Vector[BAssetRow])(using DbCon): BundleEntities =
    BundleEntities(
      sources = srcRows.toList.map(sourceLine(_, originals)),
      findings = findings(scope).toList,
      edges = edges(scope).toList,
      unresolvedEdges = unresolvedEdges(scope).toList,
      assessments = assessments(scope).toList,
      aliases = aliases(scope).toList,
      articles = articles(scope).toList,
      versions = verRows.toList.map(versionLine),
      assets = assetRows.toList.map(assetLine),
      assetLinks = assetLinks(scope).toList,
      attestations = attestations(scope).toList,
      events = events(scope).toList,
      goals = goals(scope).toList,
      queue = queue(scope).toList,
      tombstones = tombstones(scope).toList,
      places = places(scope).toList,
      periods = periods(scope).toList,
      articleAliases = articleAliases(scope).toList,
      eventAliases = eventAliases(scope).toList,
      goalAliases = goalAliases(scope).toList,
      placeAliases = placeAliases(scope).toList)

  /** The Flyway version of this database, read in the caller's transaction ("33"). */
  private[db] def schemaVersion(using DbCon): Option[String] =
    sql"""SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL
          ORDER BY installed_rank DESC LIMIT 1""".query[String].run().headOption

private[db] final case class BAttestationFullRow(
    uid: UUID, origin: UUID, sourceUid: UUID, targetType: String, targetId: String, targetUid: Option[UUID],
    quote: Option[String], prefix: Option[String], suffix: Option[String], charStart: Option[Int],
    charEnd: Option[Int], chunkOrd: Option[Int], page: Option[String], canvas: Option[String],
    xywh: Option[String], ctsUrn: Option[String], note: Option[String], createdAt: String, updatedAt: String)
given DbCodec[BAttestationFullRow] = DbCodec.derived
