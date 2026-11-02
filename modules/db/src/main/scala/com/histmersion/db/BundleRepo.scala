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
  * that do not read their own lines yet (automation's goals and work lists, the tombstones), read as the [[Bundle]]
  * line types, in a deterministic order (by uid; owned sets by their owner's uid, then their own key, in the "C"
  * collation). One read per kind; `scope` selects the rows.
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

  // ── the schema ─────────────────────────────────────────────────────────────────────────────

  /** The Flyway version of this database, read in the caller's transaction ("33"). */
  private[histmersion] def schemaVersion(using DbCon): Option[String] =
    sql"""SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL
          ORDER BY installed_rank DESC LIMIT 1""".query[String].run().headOption
