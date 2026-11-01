package com.histmersion.db

import com.histmersion.util.database.RangeSql

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import java.util.UUID
import javax.sql.DataSource
import com.histmersion.values.{BundleKind, Confidence, SourceStatus}
import com.histmersion.util.data.exchange.BundleScope
import com.histmersion.util.data.exchange.BundleSql.*
import com.histmersion.util.storage.ContentStore

// ── flat rows (Magnum maps a SELECT's columns positionally) ─────────────────────────────────────────

final private[histmersion] case class BSourceRow(
    uid: UUID,
    origin: UUID,
    citekey: String,
    title: String,
    authors: List[String],
    doi: Option[String],
    year: Option[Int],
    venue: Option[String],
    status: SourceStatus,
    topics: List[String],
    regions: List[String],
    tags: List[String],
    pNull: Boolean,
    pLo: Option[Long],
    pHi: Option[Long],
    summary: Option[String],
    notes: Option[String],
    provenance: Option[String],
    citedBy: Option[Int],
    influential: Option[Int],
    addedDate: String,
    bodySha: Option[String],
    bodyBytes: Option[Long],
    originalSha: Option[String],
    updatedAt: String,
    isbns: List[String]
) derives DbCodec
final private[histmersion] case class BFindingRow(
    uid: UUID,
    origin: UUID,
    sourceUid: UUID,
    id: String,
    claim: String,
    region: Option[String],
    lon: Option[Double],
    lat: Option[Double],
    pNull: Boolean,
    pLo: Option[Long],
    pHi: Option[Long],
    periodName: Option[String],
    tags: List[String],
    confidence: Option[Confidence],
    significance: Option[String],
    updatedAt: String
) derives DbCodec
final private[histmersion] case class BAssessmentRow(
    sourceUid: UUID,
    checkedAt: String,
    retracted: Boolean,
    notices: String,
    citedBy: Option[Int],
    influential: Option[Int],
    verdict: String
) derives DbCodec
final private[histmersion] case class BArticleRow(
    uid: UUID,
    origin: UUID,
    slug: String,
    title: String,
    kind: String,
    body: String,
    summary: Option[String],
    tags: List[String],
    regions: List[String],
    pNull: Boolean,
    pLo: Option[Long],
    pHi: Option[Long],
    updatedAt: String
) derives DbCodec
final private[histmersion] case class BVersionRow(
    uid: UUID,
    origin: UUID,
    articleUid: UUID,
    bodySha: String,
    bodyBytes: Long,
    summary: Option[String],
    versionOf: String,
    createdAt: String
) derives DbCodec
final private[histmersion] case class BGoalRow(
    uid: UUID,
    origin: UUID,
    slug: String,
    title: String,
    status: String,
    notes: Option[String],
    tags: List[String],
    regions: List[String],
    pNull: Boolean,
    pLo: Option[Long],
    pHi: Option[Long],
    parentUid: Option[UUID],
    createdAt: String,
    updatedAt: String,
    plan: Option[String],
    planApprovedAt: Option[String],
    budgetUsd: Option[BigDecimal]
) derives DbCodec
final private[histmersion] case class BGoalLinkRow(
    goalUid: UUID,
    kind: String,
    ref: String,
    refUid: Option[UUID],
    note: Option[String],
    createdAt: String
) derives DbCodec
final private[histmersion] case class BGoalScheduleRow(
    goalUid: UUID,
    kind: String,
    cron: String,
    enabled: Boolean,
    budgetUsd: Option[BigDecimal]
) derives DbCodec
final private[histmersion] case class BQueueRow(
    uid: UUID,
    origin: UUID,
    list: String,
    text: String,
    topic: Option[String],
    status: String,
    reason: Option[String],
    createdAt: String,
    doneAt: Option[String],
    updatedAt: String
) derives DbCodec
final private[histmersion] case class BTombstoneRow(uid: UUID, kind: String, origin: UUID, deletedAt: String)
    derives DbCodec

// a finding's confidence reads through `DbCodec[Option[Confidence]]` (Codecs): an unknown word is "not stated"
given DbCodec[InstanceInfo] = DbCodec.derived

/** The bundle's view of the database (docs/research-merge-import.md §4): the tables a bundle carries for the owners
  * that do not read their own lines yet (the catalogue's are its features'), read as the [[Bundle]] line types, in a
  * deterministic order (by uid; owned sets by their owner's uid, then their own key, in the "C" collation). One read
  * per kind; `scope` selects the rows.
  *
  * Shared by the export (`BundleExport`) and the import (`BundleImport`), which reads the LOCAL rows with the uids a
  * bundle carries through the very same code — so "the same content" means the same line, and the same rev, on both
  * sides.
  */
object BundleRepo:

  // ── this instance ──────────────────────────────────────────────────────────────────────────

  def instance(ds: DataSource): InstanceInfo = connect(ds)(instance)

  private[histmersion] def instance(using DbCon): InstanceInfo =
    Sql.raw(s"SELECT id, label, ${ts("created_at")} FROM instance").frag.query[InstanceInfo].run().head

  /** Name this instance; a blank label clears it. */
  def setLabel(ds: DataSource, label: Option[String]): InstanceInfo =
    transact(ds):
      val l = label.map(_.trim).filter(_.nonEmpty)
      sql"UPDATE instance SET label = $l".update.run()
      instance

  // ── reads, one per file ────────────────────────────────────────────────────────────────────

  private[histmersion] def sources(scope: BundleScope)(using DbCon): Vector[BSourceRow] =
    (Sql.raw(
      s"""SELECT s.uid, s.origin, s.citekey, s.title, s.authors, s.doi, s.year, s.venue, s.status,
                       s.topics, s.regions, s.tags, ${rangeCols("s.period")}, s.summary, s.notes, s.source,
                       s.cited_by_count, s.influential_citations, to_char(s.added_date, 'YYYY-MM-DD'),
                       encode(sha256(convert_to(s.ocr_markdown, 'UTF8')), 'hex'),
                       octet_length(convert_to(s.ocr_markdown, 'UTF8'))::bigint,
                       nullif(lower(btrim(s.sha256)), ''), ${ts("s.updated_at")},
                       coalesce((SELECT array_agg(i.value ORDER BY i.value COLLATE "C") FROM source_identifiers i
                                 WHERE i.source_uid = s.uid AND i.scheme = 'isbn'), '{}'::text[])
                FROM sources s WHERE """
    ) ++ where(scope, "s.uid", "s.updated_at") ++ " ORDER BY s.uid").frag.query[BSourceRow].run()

  private[histmersion] def findings(scope: BundleScope)(using DbCon): Vector[BundleFinding] =
    (Sql.raw(s"""SELECT f.uid, f.origin, s.uid, f.id, f.claim, f.region_name, ST_X(f.geom), ST_Y(f.geom),
                       ${rangeCols("f.period")}, f.period_name, f.tags, f.confidence, f.significance,
                       ${ts("f.updated_at")}
                FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE """) ++
      where(scope, "f.uid", "f.updated_at") ++ " ORDER BY f.uid").frag.query[BFindingRow].run().map { r =>
      BundleFinding(
        r.uid,
        r.origin,
        r.sourceUid,
        r.id,
        r.claim,
        r.region,
        r.lon,
        r.lat,
        range(r.pNull, r.pLo, r.pHi),
        r.periodName,
        r.tags,
        r.confidence,
        r.significance,
        r.updatedAt
      )
    }

  /** The out-edges of the sources in scope — a source carries its whole set. */
  private[histmersion] def edges(scope: BundleScope)(using DbCon): Vector[BundleEdge] =
    (Sql.raw("""SELECT a.uid, b.uid, e.kind FROM edges e
                JOIN sources a ON a.citekey = e.src JOIN sources b ON b.citekey = e.dst WHERE """) ++
      where(scope, "a.uid", "a.updated_at") ++ """ ORDER BY a.uid, b.uid, e.kind COLLATE "C"""").frag
      .query[(UUID, UUID, String)]
      .run()
      .map(BundleEdge.apply)

  private[histmersion] def unresolvedEdges(scope: BundleScope)(using DbCon): Vector[BundleUnresolvedEdge] =
    (Sql.raw("""SELECT a.uid, u.dst, u.kind FROM unresolved_edges u
                JOIN sources a ON a.citekey = u.src WHERE """) ++
      where(scope, "a.uid", "a.updated_at") ++ """ ORDER BY a.uid, u.dst COLLATE "C", u.kind COLLATE "C"""").frag
      .query[(UUID, String, String)]
      .run()
      .map(BundleUnresolvedEdge.apply)

  private[histmersion] def assessments(scope: BundleScope)(using DbCon): Vector[BundleAssessment] =
    (Sql.raw(s"""SELECT s.uid, ${ts("a.checked_at")}, a.retracted, a.notices::text, a.cited_by,
                       a.influential, a.verdict
                FROM assessments a JOIN sources s ON s.citekey = a.citekey WHERE """) ++
      where(scope, "s.uid", "a.checked_at") ++ " ORDER BY s.uid").frag.query[BAssessmentRow].run().map { r =>
      BundleAssessment(
        r.sourceUid,
        r.checkedAt,
        r.retracted,
        Jsonb.read[io.circe.Json](r.notices).getOrElse(io.circe.Json.arr()),
        r.citedBy,
        r.influential,
        r.verdict
      )
    }

  private[histmersion] def aliases(scope: BundleScope)(using DbCon): Vector[BundleAlias] =
    (Sql.raw(s"""SELECT a.alias, s.uid, ${ts("a.created_at")}
                FROM source_aliases a JOIN sources s ON s.citekey = a.citekey WHERE """) ++
      where(scope, "s.uid", "a.created_at") ++ """ ORDER BY a.alias COLLATE "C"""").frag
      .query[(String, UUID, String)]
      .run()
      .map(BundleAlias.apply)

  private[histmersion] def articles(scope: BundleScope)(using DbCon): Vector[BundleArticle] =
    (Sql.raw(s"""SELECT a.uid, a.origin, a.slug, a.title, a.kind, a.body, a.summary, a.tags, a.regions,
                       ${rangeCols("a.period")}, ${ts("a.updated_at")}
                FROM articles a WHERE """) ++ where(scope, "a.uid", "a.updated_at") ++ " ORDER BY a.uid").frag
      .query[BArticleRow]
      .run()
      .map { r =>
        BundleArticle(
          r.uid,
          r.origin,
          r.slug,
          r.title,
          r.kind,
          r.body,
          r.summary,
          r.tags,
          r.regions,
          range(r.pNull, r.pLo, r.pHi),
          r.updatedAt
        )
      }

  private[histmersion] def versions(scope: BundleScope)(using DbCon): Vector[BVersionRow] =
    (Sql.raw(s"""SELECT v.uid, v.origin, a.uid, encode(sha256(convert_to(v.body, 'UTF8')), 'hex'),
                       octet_length(convert_to(v.body, 'UTF8'))::bigint, v.summary,
                       ${ts("v.updated_at")}, ${ts("v.created_at")}
                FROM article_versions v JOIN articles a ON a.slug = v.slug WHERE """) ++
      where(scope, "v.uid", "v.created_at") ++ " ORDER BY v.uid").frag.query[BVersionRow].run()

  private[histmersion] def goals(scope: BundleScope)(using DbCon): Vector[BundleGoal] =
    val w = where(scope, "g.uid", "g.updated_at")
    val rows = (Sql.raw(s"""SELECT g.uid, g.origin, g.slug, g.title, g.status, g.notes, g.tags, g.regions,
                       ${rangeCols("g.period")}, p.uid, ${ts("g.created_at")}, ${ts("g.updated_at")},
                       g.plan::text, ${ts("g.plan_approved_at")}, g.budget_usd
                FROM goals g LEFT JOIN goals p ON p.slug = g.parent_slug WHERE """) ++ w ++ " ORDER BY g.uid").frag
      .query[BGoalRow]
      .run()
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
    // a goal's schedules are its watcher's sweeps since phase 3 (docs/research-process-managers.md); the rows
    // the managers have not moved into a watcher yet travel as they are. A bundle carries the cron, the
    // sweeps and the budget — the watcher's state (paused, items, events) stays here.
    val schedules = (Sql.raw("""SELECT u.uid, u.kind, u.cron, u.enabled, u.budget_usd FROM (
                       SELECT g.uid, x.kind, x.cron, x.enabled, x.budget_usd
                       FROM goal_schedules x JOIN goals g ON g.slug = x.goal_slug WHERE """) ++ w ++
      """ UNION ALL
                       SELECT g.uid, k.kind, m.cron, true, (m.config->>'budgetUsd')::numeric
                       FROM agent_managers m JOIN goals g ON g.slug = m.goal_slug
                       CROSS JOIN LATERAL jsonb_array_elements_text(
                         coalesce(m.config->'sweeps', '["acquisition", "currency"]'::jsonb)) AS k(kind)
                       WHERE m.kind = 'watch' AND m.cron IS NOT NULL AND """ ++ w ++
      """) u ORDER BY u.uid, u.kind COLLATE "C"""").frag
      .query[BGoalScheduleRow]
      .run()
      .distinctBy(r => (r.goalUid, r.kind))
      .groupBy(_.goalUid)
    rows.map { r =>
      BundleGoal(
        r.uid,
        r.origin,
        r.slug,
        r.title,
        r.status,
        r.notes,
        r.tags,
        r.regions,
        range(r.pNull, r.pLo, r.pHi),
        r.parentUid,
        r.createdAt,
        r.updatedAt,
        plan = r.plan.flatMap(Jsonb.read[io.circe.Json]),
        planApprovedAt = r.planApprovedAt,
        budgetUsd = r.budgetUsd.map(_.toDouble),
        links = links
          .get(r.uid)
          .map(_.toList.map(l => BundleGoalLink(l.kind, l.ref, l.refUid, l.note, l.createdAt)))
          .filter(_.nonEmpty),
        schedules = schedules
          .get(r.uid)
          .map(_.toList.map(x => BundleGoalSchedule(x.kind, x.cron, x.enabled, x.budgetUsd.map(_.toDouble))))
          .filter(_.nonEmpty)
      )
    }

  /** Old article slugs (V042) of the articles in scope. */
  private[histmersion] def articleAliases(scope: BundleScope)(using DbCon): Vector[BundleArticleAlias] =
    (Sql.raw(s"""SELECT x.alias, a.uid, ${ts("x.created_at")}
                FROM article_aliases x JOIN articles a ON a.slug = x.slug WHERE """) ++
      where(scope, "a.uid", "x.created_at") ++ """ ORDER BY x.alias COLLATE "C"""").frag
      .query[(String, UUID, String)]
      .run()
      .map(BundleArticleAlias.apply)

  private[histmersion] def goalAliases(scope: BundleScope)(using DbCon): Vector[BundleGoalAlias] =
    (Sql.raw(s"""SELECT x.alias, g.uid, ${ts("x.created_at")}
                FROM goal_aliases x JOIN goals g ON g.slug = x.goal_slug WHERE """) ++
      where(scope, "g.uid", "x.created_at") ++ """ ORDER BY x.alias COLLATE "C"""").frag
      .query[(String, UUID, String)]
      .run()
      .map(BundleGoalAlias.apply)

  private[histmersion] def queue(scope: BundleScope)(using DbCon): Vector[BundleQueueItem] =
    (Sql.raw(s"""SELECT q.uid, q.origin, q.list, q.text, q.topic, q.status, q.reason,
                       ${ts("q.created_at")}, ${ts("q.done_at")}, ${ts("q.updated_at")}
                FROM queue_items q WHERE """) ++ where(scope, "q.uid", "q.updated_at") ++ " ORDER BY q.uid").frag
      .query[BQueueRow]
      .run()
      .map { r =>
        BundleQueueItem(
          r.uid,
          r.origin,
          r.list,
          r.text,
          r.topic,
          r.status,
          r.reason,
          r.createdAt,
          r.doneAt,
          r.updatedAt
        )
      }

  private[histmersion] def tombstones(scope: BundleScope)(using DbCon): Vector[BundleTombstone] =
    (Sql.raw(s"SELECT uid, kind, origin, ${ts("deleted_at")} FROM tombstones WHERE ") ++
      where(scope, "uid", "deleted_at") ++ " ORDER BY uid").frag.query[BTombstoneRow].run().flatMap { r =>
      BundleKind.fromWire(r.kind).map(BundleTombstone(r.uid, _, r.origin, r.deletedAt))
    }

  /** The gazetteer and the named periods, fingerprinted: not carried, compared ([[BundleReference]]). */
  private[histmersion] def reference(using DbCon): BundleReference =
    // the SEED rows only: a place or period of this collection's own travels in the bundle itself
    def digest(table: String): (Int, String) =
      Sql
        .raw(s"""SELECT count(*)::int, coalesce(md5(string_agg(id, ',' ORDER BY id COLLATE "C")), '')
                  FROM $table WHERE dataset = 'seed'""")
        .frag
        .query[(Int, String)]
        .run()
        .head
    val (np, dp) = digest("places")
    val (nq, dq) = digest("periods")
    BundleReference(np, dp, nq, dq)

  // ── as line types ──────────────────────────────────────────────────────────────────────────

  /** A source row as its bundle line; `originals` says whether the stored original goes along. */
  private[histmersion] def sourceLine(r: BSourceRow, originals: ContentStore): BundleSource =
    BundleSource(
      r.uid,
      r.origin,
      r.citekey,
      r.title,
      r.authors,
      r.doi,
      r.year,
      r.venue,
      r.status,
      r.topics,
      r.regions,
      r.tags,
      range(r.pNull, r.pLo, r.pHi),
      r.summary,
      r.notes,
      r.provenance,
      r.citedBy,
      r.influential,
      r.addedDate,
      r.bodySha,
      r.originalSha,
      r.originalSha.flatMap(originals.find).map(_._2),
      r.updatedAt,
      isbns = Option.when(r.isbns.nonEmpty)(r.isbns)
    )

  private[histmersion] def versionLine(r: BVersionRow): BundleArticleVersion =
    BundleArticleVersion(r.uid, r.origin, r.articleUid, r.bodySha, r.summary, r.versionOf, r.createdAt)

  /** The Flyway version of this database, read in the caller's transaction ("33"). */
  private[histmersion] def schemaVersion(using DbCon): Option[String] =
    sql"""SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL
          ORDER BY installed_rank DESC LIMIT 1""".query[String].run().headOption
