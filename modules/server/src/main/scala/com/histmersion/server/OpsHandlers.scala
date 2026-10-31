package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Lint.*
import com.histmersion.api.Query.*
import com.histmersion.api.Queue.*
import com.histmersion.api.GoalPlans.{GoalView, LinkKind, Plan}
import com.histmersion.db.{AcquireCheckRepo, ArticleRepo, GoalLinkRepo, GoalRepo, QueryRepo, QueueRepo}
import com.histmersion.util.data.checks.{CheckContext, CheckRulesAdapter, CheckRunner, GuardedWrite}

/** The ONE implementation of the stream-2.8 operations — lint, the bookkeeping queues and the read-only query (plan
  * §5.2). Reached by REST through [[OpsRoutes]] and by MCP through [[OpsTools]]; the CLI reaches them over HTTP, like
  * every other command.
  *
  * Held as `Handlers.ops` so the composition root still builds exactly one object graph.
  */
final class OpsHandlers(deps: Deps):
  private val ds = deps.ds
  private def cfg = deps.cfg
  private val maintenance = deps.maintenance

  // ── lint ─────────────────────────────────────────────────────────────────────────────────────

  /** The registered checks, run by util.data's lint runner in today's order. */
  private val checks = CheckRunner(Deps.data.checks, CheckRulesAdapter.order, deps.transactor)

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

  /** `article-critique-pairing`'s repair writes each article through the article patch, guarded by its token. */
  private val articleWrite: GuardedWrite = (slug, body, token) =>
    ArticleRepo.patch(ds, slug, ArticlePatch(body = Some(body), ifUpdatedAt = token)).isRight

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

  private def kindOf(list: String): Either[ApiError, Kind] =
    Kind.parse(list).toRight(ApiError(s"unknown queue list '$list' — one of: ${Kind.all.mkString(", ")}"))

  def queueList(
      list: String,
      all: Boolean,
      goal: Option[String] = None,
      job: Option[String] = None
  ): Either[ApiError, QueueListing] =
    kindOf(list).map { k =>
      val items = (goal.map(_.trim).filter(_.nonEmpty), job.map(_.trim).filter(_.nonEmpty)) match
        case (_, Some(j))    => QueueRepo.forJob(ds, k, j).toList
        case (Some(g), None) => QueueRepo.forGoal(ds, k, g, all).toList
        case (None, None)    => QueueRepo.list(ds, k, all).toList
      val checks =
        if k != Kind.Acquire then Nil
        else
          AcquireCheckRepo
            .of(ds, items.map(_.id))
            .values
            .toList
            .sortBy(_.item)
            .map(r => AcquireChecks.AcquireCheck(r.item, r.at.toString, r.outcome, r.reason, r.checks))
      val linked = com.histmersion.db.GoalLinkRepo
        .goalsOfItems(ds, items.map(_.id))
        .map((id, slug, title) => (id, GoalRef(slug, title)))
      val titles = items.flatMap(_.goal).distinct.flatMap(s => GoalRepo.get(ds, s).map(g => s -> g.title)).toMap
      val own = items.flatMap(i => i.goal.map(s => (i.id, GoalRef(s, titles.getOrElse(s, s)))))
      QueueListing(k.label, items, checks, Queue.itemGoals(own, linked))
    }

  /** A LOG item with no topic joins today's entry (`## 2026-08-25`), which is how an appended worklog line behaves;
    * ACQUIRE and TASKS items simply have no topic until one is given.
    */
  def queueAdd(list: String, in: QueueAdd): Either[ApiError, QueueItem] =
    maintenance.guard("queue/add")(queueAddNow(list, in))

  private def queueAddNow(list: String, in: QueueAdd): Either[ApiError, QueueItem] =
    kindOf(list).flatMap { k =>
      if in.text.trim.isEmpty then Left(ApiError("queue item text is empty"))
      else
        val topic = in.topic.map(_.trim).filter(_.nonEmpty) match
          case some @ Some(_)        => some
          case None if k == Kind.Log => Some(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString)
          case None                  => None
        // both are 400s, not 500s: `created_at` reaches Postgres as `?::timestamptz` (a malformed
        // one used to surface as a driver error) and `status` was written unchecked.
        for
          status <- Queue.statusOf(in.status).left.map(ApiError(_))
          createdAt <- Queue.createdAtOf(in.createdAt).left.map(ApiError(_))
        yield QueueRepo.add(ds, k, in.copy(topic = topic, status = status, createdAt = createdAt))
    }

  def queuePatch(id: Int, p: QueuePatch): Either[ApiError, QueueItem] =
    maintenance.guard("queue/patch"):
      val checked =
        for
          status <- Queue.statusOf(p.status)
          _ <- Queue.textOf(p.text)
        yield p.copy(status = status)
      checked.left.map(ApiError(_)).flatMap(QueueRepo.patch(ds, id, _))

  def queueDelete(id: Int): Either[ApiError, QueueItem] =
    maintenance.guard("queue/delete")(QueueRepo.delete(ds, id))

  /** The list rendered as its `meta/<FILE>.md`. Notes are included — they ARE the file's prose. */
  def queueExport(list: String): Either[ApiError, String] =
    kindOf(list).map(k => Queue.render(k, QueueRepo.list(ds, k, all = true).toList))

  def queueImport(list: String, req: QueueImport): Either[ApiError, QueueImported] =
    maintenance.guard("queue/import")(queueImportNow(list, req))

  private def queueImportNow(list: String, req: QueueImport): Either[ApiError, QueueImported] =
    kindOf(list).map { k =>
      val rows = Queue.parse(k, req.text)
      val (added, gone) = QueueRepo.replaceAll(ds, k, rows, req.replace)
      val notes = rows.count(_.status.contains(Queue.Note))
      QueueImported(k.label, added - notes, notes, gone)
    }

  /** Every list at once — what `histmersion export <dir>` writes alongside the source notes. */
  def queueFiles(): List[(String, String)] =
    Kind.values.toList.map(k => k.file -> Queue.render(k, QueueRepo.list(ds, k, all = true).toList))

  /** `meta/GOALS.md`: every goal as its view (links, schedules, approved plan, run cap — the same parts
    * `GET /api/goals/{slug}/view` returns), plus the state of each linked queue item so the file can show it done or
    * open. Read-only; the rendering is [[GoalsExport.render]].
    */
  def goalsExport(): String =
    val schedules = GoalLinkRepo.schedules(ds).groupBy(_.goal)
    val views = GoalRepo.all(ds).map { g =>
      val (plan, budget) = GoalLinkRepo.planAndBudget(ds, g.slug).getOrElse((None, None))
      GoalView(
        g,
        Nil,
        GoalLinkRepo.links(ds, g.slug),
        schedules.getOrElse(g.slug, Nil),
        plan.flatMap(j => io.circe.parser.decode[Plan](j).toOption),
        budget.map(_.toDouble)
      )
    }
    val itemIds = views.flatMap(_.links).filter(_.kind == LinkKind.QueueItem).flatMap(_.ref.toIntOption).distinct
    val status = QueueRepo.byIds(ds, itemIds).map(i => i.id -> i.status).toMap
    GoalsExport.render(views, status)

  // ── read-only query + read-only backup listing ────────────────────────────────────────────────

  /** Read-only backup listing — the `backups` MCP tool. */
  def backups: Either[ApiError, List[BackupInfo]] = BackupService(cfg).list

  /** The ONLY caller that runs on `deps.queryDs` — the dedicated read-only role. Everything else in this file uses the
    * application `ds`, and must: the escape hatch is the one place arbitrary SQL arrives.
    */
  def query(req: QueryRequest): Either[ApiError, QueryResult] =
    QueryRepo
      .run(deps.queryDs, req.sql, cfg.query.timeoutMs, cfg.query.maxRows, cfg.query.maxCellChars)
      .left
      .map(m => ApiError(m))

  def schemaTables: SchemaTables = SchemaTables(QueryRepo.tables(ds))

  /** The `query` tool's description, generated from the live schema. Falls back to a schema-less description if the
    * database cannot be read while the tool list is being built — a server that cannot answer `information_schema` has
    * bigger problems than a thin tool description.
    */
  def queryToolDescription: String =
    Query.toolDescription(scala.util.Try(schemaTables).getOrElse(SchemaTables(Nil)))
