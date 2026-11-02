package com.histmersion.server

import com.augustnagro.magnum.connect

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Lint.*
import com.histmersion.api.Query.*
import com.histmersion.api.Queue.*
import com.histmersion.api.GoalPlans.{GoalView, LinkKind, Plan}
import com.histmersion.db.{GoalLinkRepo, GoalRepo}
import com.histmersion.domain.automation.worklists.{QueueItemId, QueueScope}
import com.histmersion.util.data.checks.{CheckContext, CheckOrder, CheckRunner, GuardedWrite}

/** The ONE implementation of the stream-2.8 operations — lint, the bookkeeping queues and the read-only query (plan
  * §5.2). Reached by REST through [[OpsRoutes]] and by MCP through [[OpsTools]]; the CLI reaches them over HTTP, like
  * every other command.
  *
  * Held as `Handlers.ops` so the composition root still builds exactly one object graph.
  */
final class OpsHandlers(deps: Deps, articleWrite: GuardedWrite):
  private val ds = deps.ds
  private def cfg = deps.cfg
  private val maintenance = deps.maintenance

  /** The acquisition list's checks, shown with its items. */
  private val acquireChecks = com.histmersion.domain.library.acquisition.AcquireCheckService(deps.transactor)

  // ── lint ─────────────────────────────────────────────────────────────────────────────────────

  /** The registered checks, run by util.data's lint runner in today's order. */
  private val checks = CheckRunner(Deps.data.checks, CheckOrder(Lint.names), deps.transactor)

  /** The rule names, in report order. */
  def lintRules: List[String] = checks.names

  /** `GET /api/lint` — read-only. An unknown rule name is a 400: a typo must never read as a clean corpus.
    */
  def lint(rules: List[String]): Either[ApiError, LintReport] =
    checks
      .select(rules)
      .left
      .map(m => ApiError(m))
      .map(rs => checks.report(rs, checkContext, cfg.lint.maxItems))

  private def checkContext: CheckContext = CheckContext(cfg, ds, formatChecker, articleWrite)

  /** The formatting rules' check: the corpus-wide pass reports bare citekeys against every stored one, and leaves
    * unresolved `[[links]]` to `dangling-article-links` (one rule per fact).
    */
  private def formatChecker(isCitekey: String => Boolean): FormatChecker =
    (text, field, article) =>
      MarkdownLint.issues(text, field, MarkdownLint.Context(article = article, isCitekey = Some(isCitekey)))

  /** `POST /api/lint/fix` — the two repairs that are safe to make without a human: unify unambiguous short edge
    * targets, and re-chunk sources whose full-text index is missing. Both are bounded per call; `remaining > 0` means
    * "call me again". When `rules` names `finding-geocode`, the findings that rule lists are moved too, and when it
    * names `finding-unplaced`, the findings that rule lists get their point (only then: both change the map).
    * `place-kind` reclassifies and merges the places it lists, `place-grid-point` moves the places it lists to their
    * linked record's point; both then re-match findings to places. `place-imported-point-area` removes the places it
    * lists, bounded per call (`pointAreasRemaining > 0`: call again), and re-matches the affected sources' findings.
    */
  def lintFix(rules: List[String] = Nil): LintFixResult = lintFixNow(rules)

  /** `lint --fix` rewrites edges and re-chunks sources — a WRITE, and it was the one repair path that ran straight
    * through a restore.
    */
  def guardedLintFix(rules: List[String] = Nil): Either[ApiError, LintFixResult] =
    maintenance.guard("lint/fix")(Right(lintFixNow(rules)))

  private def lintFixNow(rules: List[String]): LintFixResult = checks.fix(rules, checkContext)

  /** Lint, optionally after applying the safe fixes — what `histmersion lint --fix` and the MCP tool do, so the report
    * the caller sees is the state AFTER the repair.
    */
  def lintWithFix(rules: List[String], fix: Boolean): Either[ApiError, LintReport] =
    if !fix then lint(rules)
    else guardedLintFix(rules).flatMap(f => lint(rules).map(_.copy(fixed = f.applied)))

  // ── queue ────────────────────────────────────────────────────────────────────────────────────

  private def kindOf(list: String): Either[ApiError, Kind] = deps.workLists.kindOf(list)

  def queueList(
      list: String,
      all: Boolean,
      goal: Option[String] = None,
      job: Option[String] = None
  ): Either[ApiError, QueueListing] =
    kindOf(list).map { k =>
      val scope = (goal.map(_.trim).filter(_.nonEmpty), job.map(_.trim).filter(_.nonEmpty)) match
        case (_, Some(j))    => QueueScope.ForJob(j)
        case (Some(g), None) =>
          QueueScope.ForGoal(g, GoalLinkRepo.refs(ds, g, LinkKind.QueueItem).toSet, withDone = all)
        case (None, None) => if all then QueueScope.All else QueueScope.Open
      val items = deps.queues.list(k, scope)
      val checks =
        if k != Kind.Acquire then Nil
        else
          acquireChecks
            .of(items.map(_.id))
            .values
            .toList
            .sortBy(_.item)
            .map(r => AcquireChecks.AcquireCheck(r.item, r.at.toString, r.outcome, r.reason, r.checks))
      val linked = com.histmersion.db.GoalLinkRepo
        .goalsOfItems(ds, items.map(_.id))
        .map((id, slug, title) => (id, GoalRef(slug, title)))
      val titles =
        items.flatMap(_.goal).distinct.flatMap(s => connect(ds)(GoalRepo.get(s)).map(g => s -> g.title)).toMap
      val own = items.flatMap(i => i.goal.map(s => (i.id, GoalRef(s, titles.getOrElse(s, s)))))
      QueueListing(k.label, items, checks, Queue.itemGoals(own, linked))
    }

  def queueAdd(list: String, in: QueueAdd): Either[ApiError, QueueItem] = deps.workLists.add(list, in)

  def queuePatch(id: Int, p: QueuePatch): Either[ApiError, QueueItem] = deps.workLists.patch(id, p)

  def queueDelete(id: Int): Either[ApiError, QueueItem] = deps.workLists.delete(id)

  def queueExport(list: String): Either[ApiError, String] = deps.workLists.exportText(list)

  def queueImport(list: String, req: QueueImport): Either[ApiError, QueueImported] =
    deps.workLists.importText(list, req)

  def queueFiles(): List[(String, String)] = deps.workLists.files()

  /** `meta/GOALS.md`: every goal as its view (links, schedules, approved plan, run cap — the same parts
    * `GET /api/goals/{slug}/view` returns), plus the state of each linked queue item so the file can show it done or
    * open. Read-only; the rendering is [[GoalsExport.render]].
    */
  def goalsExport(): String =
    val schedules = connect(ds)(GoalLinkRepo.schedules()).groupBy(_.goal)
    val views = connect(ds)(GoalRepo.all()).map { g =>
      val (plan, budget) = connect(ds)(GoalLinkRepo.planAndBudget(g.slug)).getOrElse((None, None))
      GoalView(
        g,
        Nil,
        connect(ds)(GoalLinkRepo.links(g.slug)),
        schedules.getOrElse(g.slug, Nil),
        plan.flatMap(j => io.circe.parser.decode[Plan](j).toOption),
        budget.map(_.toDouble)
      )
    }
    val itemIds = views.flatMap(_.links).filter(_.kind == LinkKind.QueueItem).flatMap(_.ref.toIntOption).distinct
    val status = deps.queues.byIds(itemIds.map(QueueItemId(_))).map(i => i.id -> i.status).toMap
    GoalsExport.render(views, status)

  // ── read-only backup listing ────────────────────────────────────────────────

  /** Read-only backup listing — the `backups` MCP tool. */
  def backups: Either[ApiError, List[BackupInfo]] = BackupWiring.service(deps).list
