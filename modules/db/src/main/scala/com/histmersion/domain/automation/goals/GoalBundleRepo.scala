package com.histmersion
package domain.automation.goals

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.domain.automation.goals.GoalRepo
import com.histmersion.util.database.{Jsonb, Sql}
import com.histmersion.util.data.exchange.{BundleFormat, BundleScope}
import com.histmersion.util.data.exchange.BundleSql.*

import com.augustnagro.magnum.*
import java.util.UUID
import com.histmersion.api.Api
import com.histmersion.api.exchange.Bundle
import com.histmersion.util.data.exchange.PlanQuestions
import com.histmersion.domain.automation.Goal

/** What a bundle carries of the goals (docs/research-merge-import.md §4), read as the bundle's lines in a deterministic
  * order (by uid; their links and schedules by their goal's uid, then their own key, in the "C" collation), and the
  * import's steps that write them: the renames the other side made, the goals with their parents, links and schedules,
  * their old slugs.
  */
private[goals] object GoalBundleRepo:

  def goals(scope: BundleScope)(using DbCon): Vector[Bundle.Goal] =
    val w = where(scope, "g.uid", "g.updated_at")
    val rows = (Sql.raw(Sql.RawText(s"""SELECT g.uid, g.origin, g.slug, g.title, g.status, g.notes, g.tags, g.regions,
                       ${rangeCols("g.period")}, p.uid, ${ts("g.created_at")}, ${ts("g.updated_at")},
                       g.plan::text, ${ts("g.plan_approved_at")}, g.budget_usd
                FROM goals g LEFT JOIN goals p ON p.slug = g.parent_slug WHERE """)) ++ w ++ " ORDER BY g.uid").frag
      .query[BGoalRow]
      .run()
    // a linked source, article or queue item travels by uid (a queue item's id is a local serial)
    val links = (Sql.raw(Sql.RawText(s"""SELECT g.uid, l.kind,
                        CASE l.kind WHEN 'queue-item' THEN coalesce(q.uid::text, l.ref) ELSE l.ref END,
                        CASE l.kind WHEN 'source' THEN s.uid WHEN 'article' THEN a.uid WHEN 'queue-item' THEN q.uid END,
                        l.note, ${ts("l.created_at")}
                 FROM goal_links l JOIN goals g ON g.slug = l.goal_slug
                 LEFT JOIN sources s ON l.kind = 'source' AND s.citekey = l.ref
                 LEFT JOIN articles a ON l.kind = 'article' AND a.slug = l.ref
                 LEFT JOIN queue_items q ON l.kind = 'queue-item' AND q.id::text = l.ref WHERE """)) ++ w ++
      """ ORDER BY g.uid, l.kind COLLATE "C", l.ref COLLATE "C"""").frag.query[BGoalLinkRow].run().groupBy(_.goalUid)
    // a goal's schedules are its watcher's sweeps since phase 3 (docs/research-process-managers.md); the rows
    // the managers have not moved into a watcher yet travel as they are. A bundle carries the cron, the
    // sweeps and the budget — the watcher's state (paused, items, events) stays here.
    val schedules = (Sql.raw(Sql.RawText("""SELECT u.uid, u.kind, u.cron, u.enabled, u.budget_usd FROM (
                       SELECT g.uid, x.kind, x.cron, x.enabled, x.budget_usd
                       FROM goal_schedules x JOIN goals g ON g.slug = x.goal_slug WHERE """)) ++ w ++
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
      Bundle.Goal(
        r.uid,
        r.origin,
        Bundle.Goal.Slug(r.slug.value),
        Bundle.Goal.Title(r.title.value),
        Bundle.Goal.Status(r.status.value),
        r.notes.map(n => Bundle.Goal.Notes(n.value)),
        r.tags.map(t => Bundle.Goal.Tags(t.value)),
        r.regions.map(g => Bundle.Goal.Regions(g.value)),
        range(
          com.histmersion.util.data.exchange.Bundle.RangeIsNull(r.pNull.value),
          r.pLo.map(l => com.histmersion.util.data.exchange.Bundle.RangeLower(l.value)),
          r.pHi.map(h => com.histmersion.util.data.exchange.Bundle.RangeUpper(h.value))
        ),
        r.parentUid,
        Bundle.Goal.CreatedAt(r.createdAt.value),
        Bundle.Goal.UpdatedAt(r.updatedAt.value),
        plan = r.plan.flatMap(p => Jsonb.read[io.circe.Json](Jsonb.JsonText(p.value))),
        planApprovedAt = r.planApprovedAt.map(a => Bundle.Goal.PlanApprovedAt(a.value)),
        budgetUsd = r.budgetUsd.map(b => Bundle.Goal.BudgetUsd(b.toDouble)),
        links = links
          .get(r.uid)
          .map(
            _.toList.map(l =>
              Bundle.GoalLink(
                Bundle.GoalLink.Kind(l.kind.value),
                Bundle.GoalLink.Ref(l.ref.value),
                l.refUid,
                l.note.map(n => Bundle.GoalLink.Note(n.value)),
                Bundle.GoalLink.CreatedAt(l.createdAt.value)
              )
            )
          )
          .filter(_.nonEmpty),
        schedules = schedules
          .get(r.uid)
          .map(
            _.toList.map(x =>
              Bundle.GoalSchedule(
                Bundle.GoalSchedule.Kind(x.kind.value),
                Bundle.GoalSchedule.Cron(x.cron.value),
                Bundle.GoalSchedule.Enabled(x.enabled.value),
                x.budgetUsd.map(b => Bundle.GoalSchedule.BudgetUsd(b.toDouble))
              )
            )
          )
          .filter(_.nonEmpty)
      )
    }

  def goalAliases(scope: BundleScope)(using DbCon): Vector[Bundle.GoalAlias] =
    (Sql.raw(Sql.RawText(s"""SELECT x.alias, g.uid, ${ts("x.created_at")}
                FROM goal_aliases x JOIN goals g ON g.slug = x.goal_slug WHERE """)) ++
      where(scope, "g.uid", "x.created_at") ++ """ ORDER BY x.alias COLLATE "C"""").frag
      .query[(Bundle.GoalAlias.Alias, UUID, Bundle.GoalAlias.CreatedAt)]
      .run()
      .map(Bundle.GoalAlias.apply)

  // ── the import's steps ──────────────────────────────────────────────────────────────────────

  def goalRenamesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.followRenames(
      w.es.goals,
      _.uid,
      _.slug,
      w.local.goals.map(g => g.uid -> g.slug).toMap,
      PlanQuestions.LabelTakenWhat("goal")
    )((old, now) =>
      GoalRepo.renameIn(
        Goal.Slug.assume(old.value),
        Goal.Slug.assume(now.value)
      )
    )

  /** A goal's links (unioned with this collection's) and schedules (this collection's kept, #881). A linked record is
    * found by its uid, so the link follows a rename or a merge; a queue item that is not here is left out. A schedule
    * brought in has no next run until it is saved here: an imported collection never starts agent runs on its own.
    */
  private def goalSets(w: BundleImport.Writer, g: Bundle.Goal)(using DbCon): Unit =
    val slug = sql"SELECT slug FROM goals WHERE uid = ${g.uid}".query[String].run().head
    g.links.getOrElse(Nil).foreach { l =>
      val ref: Option[String] = l.kind.value match
        case "source"  => l.refUid.flatMap(w.citekeyHere).map(_.value).orElse(Some(l.ref.value))
        case "article" =>
          l.refUid
            .flatMap(u => sql"SELECT slug FROM articles WHERE uid = $u".query[String].run().headOption)
            .orElse(Some(l.ref.value))
        case "queue-item" =>
          l.refUid.flatMap(u => sql"SELECT id::text FROM queue_items WHERE uid = $u".query[String].run().headOption)
        case _ => Some(l.ref.value)
      ref.foreach { r =>
        sql"""INSERT INTO goal_links (goal_slug, kind, ref, note, created_at)
              VALUES ($slug, ${l.kind}, $r, ${l.note}, ${l.createdAt}::timestamptz) ON CONFLICT DO NOTHING""".update
          .run()
      }
    }
    g.schedules.getOrElse(Nil).foreach { x =>
      val usd = x.budgetUsd.map(b => BigDecimal(b.value))
      sql"""INSERT INTO goal_schedules (goal_slug, kind, cron, enabled, budget_usd, last_note)
            VALUES ($slug, ${x.kind}, ${x.cron}, ${x.enabled}, $usd,
              'brought in by a merge: save it once here to schedule it')
            ON CONFLICT DO NOTHING""".update.run()
    }

  /** Goals: the rows, their parents, then — after every goal is in (a link can name another goal's record, a schedule
    * its own goal) — their sets.
    */
  def goalsIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    val goals = w.es.goals.filter(g => w.writes(g.uid))
    goals.foreach { g =>
      val plan = g.plan.map(BundleFormat.printer.print)
      val usd = g.budgetUsd.map(b => BigDecimal(b.value))
      sql"""INSERT INTO goals (uid, origin, slug, title, status, notes, tags, regions, period, created_at, updated_at,
              plan, plan_approved_at, budget_usd)
            VALUES (${g.uid}, ${g.origin}, ${g.slug}, ${g.title}, ${g.status}, ${g.notes}, ${g.tags}::text[],
              ${g.regions}::text[],
              ${rangeLiteral(g.period)}::int8range,
              ${g.createdAt}::timestamptz, ${g.updatedAt}::timestamptz, $plan::jsonb, ${g.planApprovedAt}::timestamptz, $usd)
            ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, title = EXCLUDED.title,
              status = EXCLUDED.status, notes = EXCLUDED.notes, tags = EXCLUDED.tags,
              regions = EXCLUDED.regions, period = EXCLUDED.period, created_at = EXCLUDED.created_at,
              updated_at = EXCLUDED.updated_at, plan = EXCLUDED.plan, plan_approved_at = EXCLUDED.plan_approved_at,
              budget_usd = EXCLUDED.budget_usd""".update.run()
    }
    goals.foreach { g =>
      val parent = g.parentUid.flatMap(goalSlugOf)
      sql"UPDATE goals SET parent_slug = $parent, updated_at = ${g.updatedAt}::timestamptz WHERE uid = ${g.uid}".update
        .run()
    }
    goals.foreach(goalSets(w, _))

  private def goalSlugOf(u: UUID)(using DbCon): Option[Goal.Slug] =
    sql"SELECT slug FROM goals WHERE uid = $u".query[Goal.Slug].run().headOption

  def goalAliasesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.es.goalAliases.foreach { a =>
      sql"""INSERT INTO goal_aliases (alias, goal_slug, created_at)
            SELECT ${a.alias}, slug, ${a.createdAt}::timestamptz FROM goals
            WHERE uid = ${a.goalUid} AND slug <> ${a.alias}
              AND NOT EXISTS (SELECT 1 FROM goals WHERE slug = ${a.alias})
            ON CONFLICT (alias) DO NOTHING""".update.run()
    }
