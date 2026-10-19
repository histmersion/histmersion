package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.core.GoalPlans.{LinkKind, Removed}
import com.histmersion.core.ManagerPolicy.ReviewSettings
import com.histmersion.core.Managers.*
import com.histmersion.db.{AgentRepo, GoalLinkRepo, ManagerRepo}

import javax.sql.DataSource

/** The ONE implementation of the process-manager operations (docs/research-process-managers.md §5):
  * REST ([[AgentsRoutes]]), the MCP tool `manager` ([[AgentsTools]]) and the CLI (over REST) call
  * these. Reading and changing a manager works with agents off (nothing starts until they are on). */
final class ManagersHandlers(ds: DataSource, cfg: => AgentsConfig, limits: => Limits, agents: AgentsHandlers,
    supervisor: ManagerSupervisor, h: com.histmersion.server.Handlers):

  private def mcfg = cfg.managers

  /** The runs manager `id` dispatched, newest first (`status` narrows) — `run_status`'s manager filter. */
  def runs(id: String, status: Option[String], limit: Option[Int]): List[RunInfo] =
    val st = status.map(_.trim).filter(_.nonEmpty)
    AgentRepo.forManager(ds, id.trim, limit = limits.page(limit, 50)).toList.filter(r => st.forall(_ == r.status))

  private def notFound(what: String) = ApiError(what, Some(Write.CodeNotFound))
  private def conflict(m: String)    = ApiError(m, Some(AgentsApi.CodeConflict))

  private def kindOf(m: ManagerInfo): Kind = Kind.parse(m.kind).getOrElse(Kind.Review)

  /** The settings in effect: the saved config over the server's defaults for its kind. */
  private def withEffective(m: ManagerInfo): ManagerInfo =
    m.copy(effective = ReviewSettings.defaultsFor(kindOf(m), mcfg, cfg.seed).merge(m.config))

  private def manager(id: String): Either[ApiError, ManagerInfo] =
    ManagerRepo.get(ds, id.trim).map(withEffective).toRight(notFound(s"No manager '$id'"))

  def list: List[ManagerInfo] = ManagerRepo.list(ds).map(withEffective)

  def get(id: String): Either[ApiError, ManagerDetail] =
    manager(id).map { m =>
      ManagerDetail(m, withRuns(ManagerRepo.withStatus(ds, m.id, ItemStatus.Active)), ManagerRepo.hints(ds, m.id, live = true),
        ManagerRepo.events(ds, m.id, None, limits.page(None, 20)))
    }

  private def withRuns(is: List[ManagerItem]): List[ManagerItem] =
    is.map(i => i.copy(run = i.runId.flatMap(AgentRepo.get(ds, _))))

  /** `POST /api/agents/managers` — `review` (id `review`) and `enrich` (id `enrich`) are library-wide;
    * `seed` needs its research area (`goal`; id `seed:<goal>`) and starts with the item that proposes
    * its first articles; `watch` needs its research area too (id `watch:<goal>`), gets the cron
    * `agents.managers.watch.cron` unless `cron` names another, and starts with one item per sweep (they
    * run when it is resumed, then on its cron). Created paused unless `paused` is false — or, `paused`
    * not given, while the autopilot is on (it runs every goal's managers). */
  def create(in: ManagerCreate, now: java.time.Instant = java.time.Instant.now()): Either[ApiError, ManagerInfo] =
    // created paused unless asked otherwise — or, when nothing is asked, while the autopilot runs the goals
    val state = if in.paused.contains(false) || (in.paused.isEmpty && agents.autopilot.on) then State.Active else State.Paused
    val goal  = in.goal.map(_.trim).filter(_.nonEmpty)
    def make(k: Kind, id: String, g: Option[String], cron: Option[String] = None): Either[ApiError, ManagerInfo] =
      for
        _ <- validate(k, in.config)
        c <- cron.filter(_.trim.nonEmpty).fold[Either[ApiError, Option[Cron]]](Right(None))(t => checkCron(k, t, now).map(Some(_)))
        _ <- Either.cond(ManagerRepo.create(ds, id, k, g, state, in.config, c.map(_.text), c.flatMap(_.next(now))), (),
               conflict(s"the manager '$id' exists already"))
        _  = ManagerRepo.event(ds, id, None, None, Feed.Paused, s"created ${state.wire}" +
               c.fold("")(x => s", cron `${x.text}` (UTC)") + (if state == State.Paused then
               s": resume it to start ${ManagerSupervisor.purpose(k)} (nothing is spent until then)" else ""))
        m <- manager(id)
      yield m
    Kind.parse(in.kind) match
      case None => Left(ApiError(s"unknown manager kind '${in.kind}' (one of: ${Kind.values.map(_.wire).mkString(", ")})"))
      case Some(Kind.Review) if goal.isDefined =>
        Left(ApiError("the review manager is library-wide: give no goal (narrow it with config includeGoal)"))
      case Some(Kind.Review) => make(Kind.Review, ReviewId, None)
      case Some(Kind.Enrich) if goal.isDefined => Left(ApiError("the enrich manager is library-wide: give no goal"))
      case Some(Kind.Enrich) => make(Kind.Enrich, EnrichId, None)
      case Some(Kind.Seed) =>
        for
          g <- goal.toRight(ApiError("a seeding manager is for one research area: give its goal"))
          gl <- h.getGoal(g)
          m <- make(Kind.Seed, seedId(gl.slug), Some(gl.slug))
          _ <- addItem(m.id, EnqueueRequest(ProposeArticles, gl.slug))
          out <- manager(m.id)
        yield out
      case Some(Kind.Watch) =>
        for
          g  <- goal.toRight(ApiError("a watcher is for one research area: give its goal"))
          gl <- h.getGoal(g)
          m  <- make(Kind.Watch, watchId(gl.slug), Some(gl.slug), in.cron.orElse(Some(mcfg.watch.cron)))
          _   = Sweep.of(in.config).foreach(sw => addItem(m.id, EnqueueRequest(sw.itemKind, gl.slug, Some(0)), ItemOrigin.Selection))
          out <- manager(m.id)
        yield out

  /** `PUT /api/agents/managers/{id}` — the config is replaced as given (an absent key = the default);
    * `cron` set (five fields in UTC, or @daily/@weekly/@monthly; no more often than
    * `agents.scheduler.min-interval-minutes`) or cleared with "" — its next firing is computed now. */
  def update(id: String, in: ManagerUpdate, now: java.time.Instant = java.time.Instant.now()): Either[ApiError, ManagerInfo] =
    for
      m <- manager(id)
      c  = in.config.getOrElse(m.config)
      _ <- validate(kindOf(m), c)
      cron <- in.cron.map(_.trim) match
                case Some("") => Right(None)
                case Some(t)  => checkCron(kindOf(m), t, now).map(Some(_))
                case None     => Right(m.cron.flatMap(t => Cron.parse(t).toOption))
      next = cron.flatMap(x => if in.cron.isDefined || m.nextTickAt.isEmpty then x.next(now)
                                else m.nextTickAt.flatMap(ManagerPolicy.parseInstant))
      _  = ManagerRepo.setConfig(ds, m.id, c, cron.map(_.text), next)
      _  = ManagerRepo.event(ds, m.id, None, None, Feed.Hint, s"settings changed: ${ManagersHandlers.describe(c)}" +
             (if in.cron.isDefined then cron.fold("; no cron")(x => s"; cron `${x.text}`, next ${next.fold("never")(_.toString)}") else ""))
      out <- manager(m.id)
    yield
      supervisor.nudge()
      out

  /** A cron a person sets on a manager of kind `k`: the enrich manager has none (ingests feed it). */
  private def checkCron(k: Kind, text: String, now: java.time.Instant): Either[ApiError, Cron] =
    if k == Kind.Enrich then Left(ApiError("the enrich manager has no cron: the ingests and the Enrich buttons feed it"))
    else ManagerPolicy.checkCron(text, now, cfg.scheduler.minIntervalMinutes).left.map(ApiError(_))

  /** The rounds within the cap, every model override for a role the manager's pipelines use. */
  private def validate(k: Kind, c: ManagerConfig): Either[ApiError, Unit] =
    val roles = ManagerSupervisor.roles(k)
    c.models.getOrElse(Nil).foldLeft[Either[ApiError, Unit]](
      Either.cond(c.rounds.forall(r => r >= 2 && r <= cfg.review.roundsCap), (),
        ApiError(s"rounds must be between 2 and ${cfg.review.roundsCap} (agents.review.rounds-cap)"))) { (acc, o) =>
      acc.flatMap(_ => Role.parse(o.role).filter(roles.contains)
        .toRight(ApiError(s"models: '${o.role}' is not a role a ${k.wire} manager runs (${roles.map(_.wire).mkString(", ")})"))
        .flatMap(_ => ModelChains.parseChain(o.model, agents.knownProviders).left.map(e => ApiError(s"models ${o.role}: $e")).map(_ => ())))
    }

  def delete(id: String): Either[ApiError, Removed] =
    manager(id).map { m =>
      inFlight(m.id).foreach(r => agents.engine.cancel(r.id))
      Removed(ManagerRepo.delete(ds, m.id))
    }

  private def inFlight(id: String): List[RunInfo] = AgentRepo.forManager(ds, id, active = true).toList

  /** `POST /api/agents/managers/{id}/{pause|resume|stop}[?runs=true]`. Pause = no new runs (with
    * `runs`, its in-flight runs are paused too, cooperatively); resume reverses both (only the runs
    * paused on request); stop cancels its in-flight runs and its open items. */
  def action(id: String, action: String, runs: Option[Boolean]): Either[ApiError, ManagerInfo] =
    manager(id).flatMap { m =>
      val withRuns = runs.contains(true)
      action.trim.toLowerCase match
        case "pause" =>
          ManagerRepo.setState(ds, m.id, State.Paused, withRuns)
          val paused = if withRuns then inFlight(m.id).filter(r => r.status == RunState.Running.wire || r.status == RunState.Queued.wire)
            .count(r => agents.engine.pause(r.id).isRight) else 0
          ManagerRepo.event(ds, m.id, None, None, Feed.Paused,
            "paused by a person" + (if withRuns then s", with ${Plural.count(paused, "run")} in flight" else "; its runs in flight go on"))
          manager(m.id)
        case "resume" =>
          ManagerRepo.setState(ds, m.id, State.Active, pauseRuns = false)
          val resumed = if m.pauseRuns && cfg.enabled then
            inFlight(m.id).filter(r => r.status == RunState.Paused.wire && r.error.exists(_.startsWith(PauseReason.Requested.wire)))
              .count(r => agents.engine.resume(r.id, None, None).isRight)
          else 0
          ManagerRepo.event(ds, m.id, None, None, Feed.Paused,
            "resumed by a person" + (if resumed > 0 then s", with ${Plural.count(resumed, "run")} it had paused" else ""))
          supervisor.nudge()
          manager(m.id)
        case "stop" =>
          ManagerRepo.setState(ds, m.id, State.Stopped, pauseRuns = false)
          val cancelled = inFlight(m.id).count(r => agents.engine.cancel(r.id).isRight)
          val items = ManagerRepo.cancelOpen(ds, m.id, "the manager was stopped")
          ManagerRepo.event(ds, m.id, None, None, Feed.Stopped,
            s"stopped by a person: ${Plural.count(cancelled, "run")} cancelled, ${Plural.count(items, "item")} cancelled")
          manager(m.id)
        case other => Left(ApiError(s"unknown action '$other' (one of: ${Actions.mkString(", ")})"))
    }

  // ── items ──────────────────────────────────────────────────────────────────────────────────────

  def items(id: String, status: Option[String], offset: Option[Int], limit: Option[Int]): Either[ApiError, ItemPage] =
    for
      m  <- manager(id)
      st <- status.map(_.trim).filter(_.nonEmpty) match
              case Some(s) => ItemStatus.parse(s).map(Some(_)).toRight(ApiError(
                                s"unknown item status '$s' (one of: ${ItemStatus.values.map(_.wire).mkString(", ")})"))
              case None    => Right(None)
    yield
      val (off, lim) = (math.max(0, offset.getOrElse(0)), limits.page(limit, 25))
      val (rows, total) = ManagerRepo.items(ds, m.id, st, off, lim)
      ItemPage(withRuns(rows), total, off, lim)

  /** The item an id or a target names, in manager `m` (its default item kind first, then the others). */
  private def itemOf(m: ManagerInfo, ref: String): Either[ApiError, ManagerItem] =
    val r = ref.trim
    r.toLongOption.flatMap(ManagerRepo.item(ds, _)).filter(_.managerId == m.id)
      .orElse(itemKinds(kindOf(m)).iterator.map(k => ManagerRepo.itemFor(ds, m.id, k, r)).collectFirst { case Some(i) => i })
      .toRight(notFound(s"No item '$ref' of manager ${m.id}"))

  /** A target as an item of `kind` names it: an article's slug, a source's citekey, the goal (a seeding
    * manager's own). A compile item is never added by hand: it comes from an approved plan. */
  private def targetOf(m: ManagerInfo, kind: String, raw: String): Either[ApiError, String] =
    kind match
      case ArticleReview | InferFilter | ArticleExtend => h.getArticle(raw.trim).map(_.article.slug)
      case SourceEnrich => h.getSource(raw.trim, body = false).map(_.source.citekey)
      case ProposeArticles | PlanGoalItem =>
        val g = Option(raw.trim).filter(_.nonEmpty).orElse(m.goal).getOrElse("")
        h.getGoal(g).map(_.slug).flatMap(s => Either.cond(m.goal.forall(_ == s), s,
          ApiError(s"manager ${m.id} plans the articles of ${m.goal.getOrElse("?")}, not of $s")))
      case ArticleCompile => Left(ApiError(s"'$ArticleCompile' items come from an approved article plan (propose-articles)"))
      case AcquisitionSweep | CurrencyCheck =>
        val g = Option(raw.trim).filter(_.nonEmpty).orElse(m.goal).getOrElse("")
        h.getGoal(g).map(_.slug).flatMap(s => Either.cond(m.goal.forall(_ == s), s,
          ApiError(s"manager ${m.id} watches ${m.goal.getOrElse("?")}, not $s")))
      case other => Left(ApiError(s"unknown item kind '$other'"))

  /** Add (or bring back) a target: `in.kind` names the item kind (`review`, `extend`, `filter`,
    * `enrich`, `propose`, or the kind itself; blank = the manager's default). An article's review whose
    * article records no filter brings an infer-filter item with it (the review manager's `inferFilter`). */
  def addItem(id: String, in: EnqueueRequest, origin: ItemOrigin = ItemOrigin.Person): Either[ApiError, ManagerItem] =
    for
      m      <- manager(id)
      kind   <- itemKindOf(kindOf(m), in.kind).toRight(ApiError(
                  s"manager ${m.id} drives ${itemKinds(kindOf(m)).mkString(", ")} items, not '${in.kind}'"))
      target <- targetOf(m, kind, in.target)
      prio    = in.priority.getOrElse(ManagerSupervisor.PersonPriority)
      (itemId, isNew) = ManagerRepo.upsert(ds, m.id, kind, target, origin, prio, reopen = true)
      label   = if kind == itemKinds(kindOf(m)).head then target else s"$target ($kind)"
      _       = ManagerRepo.event(ds, m.id, Some(itemId), None, Feed.Evaluated,
                  s"$label ${if isNew then "enqueued" else "enqueued again"} by a ${origin.wire} (priority $prio)")
      _       = if kind == ArticleReview then filterFirst(m, target, prio)
      item   <- ManagerRepo.item(ds, itemId).toRight(notFound(s"item $itemId vanished"))
    yield
      supervisor.nudge()
      item

  /** An article that records no filter and never had an infer-filter item gets one, at its review's
    * priority, so its review waits for it (when the manager's `inferFilter` is on). */
  private def filterFirst(m: ManagerInfo, slug: String, priority: Int): Unit =
    val on = m.config.inferFilter.getOrElse(mcfg.review.inferFilter)
    val has = com.histmersion.db.ArticleRepo.load(ds, slug).flatMap(_.filter).isDefined
    if on && !has && !ManagerRepo.hasItem(ds, m.id, InferFilter, slug) then
      val (fid, _) = ManagerRepo.upsert(ds, m.id, InferFilter, slug, ItemOrigin.Chain, priority, reopen = false)
      ManagerRepo.event(ds, m.id, Some(fid), None, Feed.Evaluated,
        s"$slug ($InferFilter): it records no coverage filter — inferred before its review")

  /** `POST /api/agents/enqueue` — a page button: `review` or `extend` an article (the review manager),
    * `enrich` a source or the unenriched sources an article cites or a goal links (the enrich manager),
    * `seed` a research area (its seeding manager proposes its next articles). A manager that does not
    * exist yet is created PAUSED. */
  def enqueue(in: EnqueueRequest): Either[ApiError, EnqueueResult] =
    EnqueueKind.parse(in.kind) match
      case None =>
        Left(ApiError(s"nothing manages '${in.kind}' items (one of: ${EnqueueKind.values.map(_.wire).mkString(", ")})"))
      case Some(EnqueueKind.Review) =>
        supervisor.ensureDefaults()
        addItem(ReviewId, in.copy(kind = ArticleReview)).map(i => EnqueueResult(ReviewId, List(i)))
      case Some(EnqueueKind.Extend) =>
        supervisor.ensureDefaults()
        addItem(ReviewId, in.copy(kind = ArticleExtend)).map(i => EnqueueResult(ReviewId, List(i)))
      case Some(EnqueueKind.Seed) =>
        h.getGoal(in.target.trim).flatMap { g =>
          val id = seedId(g.slug)
          val _  = supervisor.ensure(Kind.Seed, id, Some(g.slug))
          addItem(id, in.copy(kind = ProposeArticles, target = g.slug)).map(i => EnqueueResult(id, List(i)))
        }
      case Some(EnqueueKind.Enrich) =>
        supervisor.ensureDefaults()
        enrichTargets(in.target.trim).map { (cks, skipped) =>
          val items = cks.flatMap(ck => addItem(EnrichId, EnqueueRequest(SourceEnrich, ck, in.priority)).toOption)
          EnqueueResult(EnrichId, items, skipped)
        }

  /** What an Enrich button names: a source itself (enriched again even if it was), or the sources an
    * article cites / a goal links that are NOT enriched yet ([[AutoEnrich.enrichedBy]]; the others are
    * reported as skipped, with why). */
  private def enrichTargets(raw: String): Either[ApiError, (List[String], List[String])] =
    h.getSource(raw, body = false).map(s => (List(s.source.citekey), Nil)).orElse {
      val cited = h.getArticle(raw).map(v => h.citedSources(v.article.body))
        .orElse(h.getGoal(raw).map(g => GoalLinkRepo.links(ds, g.slug).filter(_.kind == LinkKind.Source).map(_.ref)))
      cited.left.map(_ => notFound(s"'$raw' is no source, article or goal")).map { cks =>
        val (todo, done) = cks.distinct.map(ck => ck -> enrichedWhy(ck)).partition(_._2.isEmpty)
        (todo.map(_._1), done.map((ck, why) => s"[[$ck]]: ${why.getOrElse("")}"))
      }
    }

  /** Why a source counts as enriched already (findings, notes, a live enrich run), if it does. */
  private def enrichedWhy(ck: String): Option[String] =
    h.getSource(ck, body = false).toOption match
      case None => Some("no such source")
      case Some(src) =>
        val runs = AgentRepo.forTarget(ds, Pipeline.EnrichSource, src.source.citekey).toList.filterNot(_.dryRun)
          .flatMap(r => RunState.parse(r.status).map(AutoEnrich.PriorRun(r.id, _)))
        AutoEnrich.enrichedBy(src.findings.size, src.source.notes, runs)

  /** `agents.auto-enrich` with the managers on: the ingested source becomes an item of the enrich
    * manager (created paused if missing). Answers the ingest job's progress note. */
  def ingested(citekey: String): String =
    supervisor.ensureDefaults()
    val (itemId, isNew) = ManagerRepo.upsert(ds, EnrichId, SourceEnrich, citekey, ItemOrigin.Ingest, 0, reopen = true)
    ManagerRepo.event(ds, EnrichId, Some(itemId), None, Feed.Evaluated,
      s"$citekey ${if isNew then "enqueued" else "enqueued again"} by an ingest")
    supervisor.nudge()
    val state = ManagerRepo.get(ds, EnrichId).map(_.state).getOrElse(State.Paused.wire)
    AutoEnrich.managed(itemId, state)

  def patchItem(id: String, ref: String, in: ItemPatch): Either[ApiError, ManagerItem] =
    for
      m <- manager(id)
      i <- itemOf(m, ref)
      _ <- in.action.trim.toLowerCase match
             case "hold" =>
               val h = Hold(in.until.map(_.trim).filter(_.nonEmpty), in.untilSource.map(_.trim).filter(_.nonEmpty),
                 in.reason.getOrElse("held by a person"))
               for
                 _ <- h.until.fold[Either[ApiError, Unit]](Right(()))(u => ManagerPolicy.parseInstant(u).map(_ => ())
                        .toRight(ApiError(s"'$u' is not a date (2026-11-01) or an instant")))
                 _ <- Either.cond(ManagerRepo.hold(ds, i.id, h), (), conflict(s"item #${i.id} is ${i.status}; only a pending or held item can be held"))
               yield ManagerRepo.event(ds, m.id, Some(i.id), None, Feed.Held, s"${i.target}: held" +
                 h.until.fold("")(u => s" until $u") + h.untilSource.fold("")(s => s" until $s exists") + s" — ${h.reason}")
             case "release" =>
               Either.cond(ManagerRepo.release(ds, i.id), (), conflict(s"item #${i.id} is ${i.status}, not held"))
                 .map(_ => ManagerRepo.event(ds, m.id, Some(i.id), None, Feed.Held, s"${i.target}: released by a person"))
             case "priority" =>
               in.priority.toRight(ApiError("give `priority`")).map { p =>
                 ManagerRepo.setPriority(ds, i.id, p)
                 ManagerRepo.event(ds, m.id, Some(i.id), None, Feed.Hint, s"${i.target}: priority $p")
               }
             case "skip" =>
               // an active item's run is cancelled first; the item is then stopped as skipped
               i.runId.filter(_ => i.status == ItemStatus.Active.wire).foreach(r => agents.engine.cancel(r))
               val why = in.reason.filter(_.trim.nonEmpty).fold("skipped by a person")(r => s"skipped by a person: $r")
               Either.cond(ManagerRepo.settle(ds, i.id, ItemStatus.Stopped, why,
                   List(ItemStatus.Pending, ItemStatus.Held, ItemStatus.Active)), (), conflict(s"item #${i.id} is ${i.status} already"))
                 .map(_ => ManagerRepo.event(ds, m.id, Some(i.id), i.runId, Feed.Stopped, s"${i.target}: $why"))
             case "reopen" =>
               Either.cond(ManagerRepo.reopen(ds, i.id), (), conflict(s"item #${i.id} is ${i.status}; only a settled item can be re-opened"))
                 .map(_ => ManagerRepo.event(ds, m.id, Some(i.id), None, Feed.Evaluated, s"${i.target}: re-opened by a person"))
             case other => Left(ApiError(s"unknown item action '$other' (one of: ${ItemPatch.Actions.mkString(", ")})"))
      out <- ManagerRepo.item(ds, i.id).toRight(notFound(s"item ${i.id} vanished"))
    yield
      supervisor.nudge()
      withRuns(List(out)).head

  // ── hints ──────────────────────────────────────────────────────────────────────────────────────

  def hints(id: String, all: Boolean): Either[ApiError, List[ManagerHint]] =
    manager(id).map(m => ManagerRepo.hints(ds, m.id, live = !all))

  /** A hint: its text reaches the next runs' fixer and skeptic as the person's notes (a once-hint the
    * next run only). Its structured effect, if any, is applied now — prioritise, hold or skip the targets it
    * names or matches (pending and held items; a named target not yet an item is added) — when it is the
    * person's own; a MODEL's (`propose`: the MCP tool, which a model drives) is recorded as a `hint-effect`
    * approval and applied only when a person approves it ([[applyProposal]]). `suggest` asks the server's
    * planner model to read the text and propose the action (a `hint-effect` run, started for the person;
    * its proposal waits for approval the same way). */
  def addHint(id: String, in: HintAdd): Either[ApiError, ManagerHint] =
    val propose = in.propose.contains(true)
    val suggest = in.suggest.contains(true)
    for
      m     <- manager(id)
      text  <- Option(in.text.trim).filter(_.nonEmpty).toRight(ApiError("a hint needs text"))
      item  <- in.item.map(_.trim).filter(_.nonEmpty).fold[Either[ApiError, Option[ManagerItem]]](Right(None))(r => itemOf(m, r).map(Some(_)))
      eff   <- in.effect.fold[Either[ApiError, Option[HintEffect]]](Right(None))(e => checkEffect(m, e, item, propose).map(Some(_)))
      _     <- Either.cond(!(suggest && eff.isDefined), (), ApiError("`suggest` asks a model for the action: give no effect with it"))
      // a suggestion needs a run that can start: checked before the hint is recorded
      _     <- if suggest then agents.prepare(RunRequest(Pipeline.HintEffect.wire, m.id), None).map(_ => ()) else Right(())
      author = in.author.map(_.trim).filter(_.nonEmpty).getOrElse("person")
      hint   = ManagerRepo.addHint(ds, m.id, item.map(_.id), text, if propose then None else eff, author, standing = !in.once.contains(true))
      applied = eff.fold("")(e => if propose then proposeEffect(m, hint.id, text, e, author) else applyEffect(m, hint.id, text, e))
      asked <- if suggest then agents.startRunWith(RunRequest(Pipeline.HintEffect.wire, m.id), Origin.Person, None,
                 _.copy(hint = Some(hint.id))).map(r => s" — a model reads it for an action (run ${r.id.take(8)}); a person approves what it proposes")
               else Right("")
      _      = ManagerRepo.event(ds, m.id, item.map(_.id), None, Feed.Hint,
                 s"hint #${hint.id} (${if hint.standing then "standing" else "once"}${item.fold("")(i => s", ${i.target}")}): ${text.take(300)}$applied$asked")
      out   <- ManagerRepo.hint(ds, hint.id).toRight(notFound(s"hint ${hint.id} vanished"))
    yield
      supervisor.nudge()
      out

  /** A structured effect checked against manager `m`: an action it knows (a model may also propose
    * `config`), targets (or a tag, topic, goal, or the item the hint is about), a hold's date or source,
    * a settings change that parses and that the manager's kind accepts. */
  private def checkEffect(m: ManagerInfo, e: HintEffect, item: Option[ManagerItem], proposal: Boolean): Either[ApiError, HintEffect] =
    val a = e.action.trim.toLowerCase
    val allowed = if proposal then HintEffect.Proposable else HintEffect.Actions
    if !allowed.contains(a) then
      Left(ApiError(s"unknown hint action '${e.action}' (one of: ${allowed.mkString(", ")})" +
        (if !proposal && a == HintEffect.Config then " — change the settings with the manager's config instead" else "")))
    else if a == HintEffect.Config then
      e.config.filter(c => ManagersMarkdown.configLine(c) != "the defaults").toRight(ApiError("a config action names the settings it changes"))
        .flatMap(c => validate(kindOf(m), m.config.merge(c)).map(_ => e.copy(action = a)))
    else if e.targets.isEmpty && e.tag.isEmpty && e.topic.isEmpty && e.goal.isEmpty && item.isEmpty then
      Left(ApiError("a structured hint names its targets, a tag, a topic or a goal (or is about one item)"))
    else if a == HintEffect.HoldAction && e.until.isEmpty && e.untilSource.isEmpty then
      Left(ApiError("a hold hint needs `until` (a date) or `untilSource` (a DOI or citekey)"))
    else if e.until.exists(u => ManagerPolicy.parseInstant(u).isEmpty) then
      Left(ApiError(s"'${e.until.getOrElse("")}' is not a date (2026-11-01) or an instant"))
    else Right(e.copy(action = a, targets = (e.targets ++ item.map(_.target).toList).distinct))

  /** A model's effect, recorded for a person: an open `hint-effect` approval about the manager. */
  private def proposeEffect(m: ManagerInfo, hintId: Long, text: String, e: HintEffect, by: String): String =
    import io.circe.syntax.*
    val a = AgentRepo.openSubject(ds, None, ApprovalKind.HintEffect, m.id, HintProposal(m.id, hintId, text, e, by).asJson.noSpaces)
    s" — proposed ${ManagersMarkdown.effect(e)}: approval #${a.id} waits for a person"

  /** A `hint-effect` approval a person approved: the action is checked again against the manager as it is
    * now and applied — items prioritised, held or skipped, or the settings changed — and recorded on the
    * hint. Left = why it could not be (the approval is then marked failed with it). */
  def applyProposal(p: HintProposal): Either[String, String] =
    for
      m  <- manager(p.managerId).left.map(_.message)
      ht <- ManagerRepo.hint(ds, p.hint).filter(_.managerId == m.id).toRight(s"hint #${p.hint} of ${m.id} is gone")
      _  <- Either.cond(ht.retiredAt.isEmpty, (), s"hint #${ht.id} was retired meanwhile")
      e  <- checkEffect(m, p.effect, ht.itemId.flatMap(ManagerRepo.item(ds, _)), proposal = true).left.map(_.message)
      w  <- e.action match
              case HintEffect.Config =>
                val c = m.config.merge(e.config.getOrElse(ManagerConfig()))
                val _ = ManagerRepo.setConfig(ds, m.id, c, m.cron, m.nextTickAt.flatMap(ManagerPolicy.parseInstant))
                Right(s" — settings now: ${ManagersHandlers.describe(c)}")
              case _ => Right(applyEffect(m, ht.id, ht.text, e))
      _   = ManagerRepo.setHintEffect(ds, ht.id, Some(e))
      _   = ManagerRepo.event(ds, m.id, ht.itemId, None, Feed.Hint,
              s"hint #${ht.id}: the proposed ${ManagersMarkdown.effect(e)} approved by a person$w".take(2000))
    yield
      supervisor.nudge()
      s"${m.id} hint #${ht.id}: ${ManagersMarkdown.effect(e)}$w"

  private def applyEffect(m: ManagerInfo, hintId: Long, text: String, e: HintEffect): String =
    // the named targets become items of the manager's default kind (a target that exists); the matched
    // ones are the open items
    val kind  = itemKinds(kindOf(m)).headOption.getOrElse(ArticleReview)
    val named = e.targets.flatMap(t => targetOf(m, kind, t).toOption).distinct
    val open  = ManagerRepo.withStatus(ds, m.id, ItemStatus.Pending) ++ ManagerRepo.withStatus(ds, m.id, ItemStatus.Held)
    val matched = open.filter { i =>
      named.contains(i.target) || ((e.tag.isDefined || e.topic.isDefined || e.goal.isDefined) && {
        val (tags, goals) = ManagerRepo.articleFacets(ds, i.target)
        ManagerPolicy.matches(e.copy(targets = Nil), i.target, tags, Nil, goals)
      })
    }
    val n = e.action match
      case HintEffect.Prioritise =>
        val p = e.priority.getOrElse(ManagerSupervisor.HintPriority)
        val added = named.filterNot(t => open.exists(_.target == t)).count { t =>
          ManagerRepo.upsert(ds, m.id, kind, t, ItemOrigin.Hint, p, reopen = true)
          true
        }
        matched.foreach(i => ManagerRepo.setPriority(ds, i.id, math.max(i.priority, p)))
        matched.size + added
      case HintEffect.HoldAction =>
        val hold = Hold(e.until, e.untilSource, s"hint #$hintId: ${text.take(200)}", Some(hintId))
        val added = named.filterNot(t => open.exists(_.target == t)).flatMap { t =>
          val (id, _) = ManagerRepo.upsert(ds, m.id, kind, t, ItemOrigin.Hint, 0, reopen = true)
          ManagerRepo.item(ds, id).filter(_.status == ItemStatus.Pending.wire)
        }
        (matched ++ added).count(i => ManagerRepo.hold(ds, i.id, hold))
      case HintEffect.Skip =>
        matched.count(i => ManagerRepo.settle(ds, i.id, ItemStatus.Stopped, s"skipped (hint #$hintId)",
          List(ItemStatus.Pending, ItemStatus.Held)))
      case _ => 0
    s" — ${e.action}: ${Plural.count(n, "item")}"

  def deleteHint(id: String, hint: Long): Either[ApiError, Removed] =
    manager(id).map { m =>
      val gone = ManagerRepo.retireHint(ds, m.id, hint)
      if gone then ManagerRepo.event(ds, m.id, None, None, Feed.Hint, s"hint #$hint retired by a person")
      Removed(gone)
    }

  // ── goal schedules: a watcher's sweeps (phase 3) ───────────────────────────────────────────────

  /** A goal's schedules (every goal's: None) as the goal surfaces show them — its watcher's sweeps
    * ([[WatchSchedules.view]]), and any `goal_schedules` row the managers have not moved yet. */
  def goalSchedules(goal: Option[String]): List[GoalPlans.GoalSchedule] =
    ManagerRepo.list(ds).filter(m => m.kind == Kind.Watch.wire && goal.forall(g => m.goal.contains(g)))
      .flatMap(m => WatchSchedules.view(m, ManagerRepo.items(ds, m.id, None, 0, 20)._1)) ++ GoalLinkRepo.schedules(ds, goal)

  /** `PUT /api/goals/{slug}/schedules/{kind}` — since phase 3 a goal's schedule is a sweep of its watcher
    * `watch:<goal>` (created PAUSED when it does not exist: resume it for the schedule to fire). `enabled`
    * puts the sweep in (or takes it out of) the watcher's sweeps; the cron (checked, no more often than
    * `agents.scheduler.min-interval-minutes`) is the watcher's, shared by its sweeps; a budget given is its
    * per-run cap. */
  def setSchedule(goal: String, kind: GoalPlans.ScheduleKind, in: GoalPlans.ScheduleSet, now: java.time.Instant)
      : Either[ApiError, GoalPlans.GoalSchedule] =
    val id = watchId(goal)
    val sw = WatchSchedules.sweepOf(kind)
    for
      cron <- ManagerPolicy.checkCron(in.cron, now, cfg.scheduler.minIntervalMinutes).left.map(ApiError(_))
      _ = ManagerRepo.get(ds, id) match
            case None =>
              val c = ManagerConfig(sweeps = Some(if in.enabled then List(sw.wire) else Nil), budgetUsd = in.budgetUsd)
              val _ = ManagerRepo.create(ds, id, Kind.Watch, Some(goal), State.Paused, c, Some(cron.text), cron.next(now))
              ManagerRepo.event(ds, id, None, None, Feed.Paused, s"created paused by the goal's ${kind.wire} schedule " +
                s"(cron `${cron.text}`): resume it for its sweeps to fire")
            case Some(m) =>
              val cur = Sweep.of(m.config)
              val sweeps = Sweep.values.toList.filter(x => if x == sw then in.enabled else cur.contains(x))
              val c = m.config.copy(sweeps = Some(sweeps.map(_.wire)), budgetUsd = in.budgetUsd.orElse(m.config.budgetUsd))
              val next = if m.cron.contains(cron.text) && m.nextTickAt.isDefined then m.nextTickAt.flatMap(ManagerPolicy.parseInstant)
                         else cron.next(now)
              val _ = ManagerRepo.setConfig(ds, id, c, Some(cron.text), next)
              if !in.enabled then cancelSweep(m, sw, "its schedule was turned off")
              ManagerRepo.event(ds, id, None, None, Feed.Hint, s"the goal's ${kind.wire} schedule set: cron `${cron.text}`, " +
                s"${if in.enabled then "on" else "off"}; sweeps ${if sweeps.isEmpty then "none" else sweeps.map(_.wire).mkString(", ")}")
      _ = GoalLinkRepo.removeSchedule(ds, goal, kind) // a row the managers had not moved yet is replaced
      m <- manager(id)
    yield
      supervisor.nudge()
      WatchSchedules.view(m, ManagerRepo.items(ds, id, None, 0, 20)._1).find(_.kind == kind)
        .getOrElse(GoalPlans.GoalSchedule(goal, kind, cron.text, enabled = false, in.budgetUsd))

  /** `DELETE /api/goals/{slug}/schedules/{kind}` — the sweep leaves the watcher (its open item is
    * cancelled); the watcher itself stays. */
  def removeSchedule(goal: String, kind: GoalPlans.ScheduleKind): GoalPlans.Removed =
    val legacy = GoalLinkRepo.removeSchedule(ds, goal, kind)
    val sw = WatchSchedules.sweepOf(kind)
    val fromWatcher = ManagerRepo.get(ds, watchId(goal)).filter(m => Sweep.of(m.config).contains(sw)).exists { m =>
      val rest = Sweep.of(m.config).filterNot(_ == sw)
      val _ = ManagerRepo.setConfig(ds, m.id, m.config.copy(sweeps = Some(rest.map(_.wire))), m.cron,
        m.nextTickAt.flatMap(ManagerPolicy.parseInstant))
      cancelSweep(m, sw, "its schedule was removed")
      ManagerRepo.event(ds, m.id, None, None, Feed.Hint, s"the goal's ${kind.wire} schedule removed; sweeps " +
        (if rest.isEmpty then "none" else rest.map(_.wire).mkString(", ")))
      true
    }
    GoalPlans.Removed(legacy || fromWatcher)

  /** A sweep the watcher no longer runs: its pending or held item is cancelled (a running one finishes). */
  private def cancelSweep(m: ManagerInfo, sw: Sweep, why: String): Unit =
    m.goal.flatMap(g => ManagerRepo.itemFor(ds, m.id, sw.itemKind, g)).foreach { i =>
      if ManagerRepo.settle(ds, i.id, ItemStatus.Cancelled, why, List(ItemStatus.Pending, ItemStatus.Held)) then
        ManagerRepo.event(ds, m.id, Some(i.id), None, Feed.Stopped, s"${i.target} (${i.kind}): $why")
    }

  // ── the autopilot ──────────────────────────────────────────────────────────────────────────────

  /** `GET /api/agents/autopilot` — readable with agents off. */
  def autopilot: Autopilot.AutopilotStatus = agents.autopilot.status()

  /** `POST /api/agents/autopilot {on}` — idempotent; the answer says what it did. */
  def setAutopilot(in: Autopilot.AutopilotSet): Either[ApiError, Autopilot.AutopilotStatus] =
    Right(agents.autopilot.set(in))

  // ── events ─────────────────────────────────────────────────────────────────────────────────────

  def events(id: String, before: Option[Long], limit: Option[Int]): Either[ApiError, EventPage] =
    manager(id).map { m =>
      val lim = limits.page(limit, 50)
      val es = ManagerRepo.events(ds, m.id, before, lim)
      EventPage(es, Option.when(es.size == lim)(es.last.id))
    }

object ManagersHandlers:
  def describe(c: ManagerConfig): String = ManagersMarkdown.configLine(c)
