package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.core.GoalPlans.Removed
import com.histmersion.core.ManagerPolicy.ReviewSettings
import com.histmersion.core.Managers.*
import com.histmersion.db.{AgentRepo, ManagerRepo}

import javax.sql.DataSource

/** The ONE implementation of the process-manager operations (docs/research-process-managers.md §5):
  * REST ([[AgentsRoutes]]), the MCP tool `manager` ([[AgentsTools]]) and the CLI (over REST) call
  * these. Reading and changing a manager works with agents off (nothing starts until they are on). */
final class ManagersHandlers(ds: DataSource, cfg: AgentsConfig, limits: Limits, agents: AgentsHandlers,
    supervisor: ManagerSupervisor, h: com.histmersion.server.Handlers):

  private val mcfg = cfg.managers

  private def notFound(what: String) = ApiError(what, Some(Write.CodeNotFound))
  private def conflict(m: String)    = ApiError(m, Some(AgentsApi.CodeConflict))

  /** The settings in effect: the saved config over the server's defaults. */
  private def withEffective(m: ManagerInfo): ManagerInfo =
    m.copy(effective = ReviewSettings.defaults(mcfg.review, mcfg).merge(m.config))

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

  /** `POST /api/agents/managers` — phase 1 makes the review manager (id `review`, library-wide). */
  def create(in: ManagerCreate): Either[ApiError, ManagerInfo] =
    Kind.parse(in.kind) match
      case None => Left(ApiError(s"unknown manager kind '${in.kind}' (one of: ${Kind.values.map(_.wire).mkString(", ")})"))
      case Some(Kind.Review) =>
        val state = if in.paused.contains(false) then State.Active else State.Paused
        if in.goal.exists(_.trim.nonEmpty) then Left(ApiError("the review manager is library-wide: give no goal (narrow it with config includeGoal)"))
        else if !ManagerRepo.create(ds, ReviewId, Kind.Review, None, state, in.config) then
          Left(conflict(s"the review manager '$ReviewId' exists already"))
        else
          ManagerRepo.event(ds, ReviewId, None, None, Feed.Paused, s"created ${state.wire}")
          manager(ReviewId)
      case Some(k) => Left(ApiError(s"${k.wire} managers come in a later phase (docs/research-process-managers.md §4)"))

  /** `PUT /api/agents/managers/{id}` — the config is replaced as given (an absent key = the default). */
  def update(id: String, in: ManagerUpdate): Either[ApiError, ManagerInfo] =
    for
      m <- manager(id)
      c  = in.config.getOrElse(m.config)
      _ <- validate(c)
      cron <- in.cron.map(_.trim) match
                case Some("") => Right(None)
                case Some(t)  => Cron.parse(t).map(x => Some(x.text)).left.map(ApiError(_))
                case None     => Right(m.cron)
      _  = ManagerRepo.setConfig(ds, m.id, c, cron)
      _  = ManagerRepo.event(ds, m.id, None, None, Feed.Hint, s"settings changed: ${ManagersHandlers.describe(c)}")
      out <- manager(m.id)
    yield
      supervisor.nudge()
      out

  private def validate(c: ManagerConfig): Either[ApiError, Unit] =
    val roles = Pipeline.ReviewArticle.roles
    c.models.getOrElse(Nil).foldLeft[Either[ApiError, Unit]](
      Either.cond(c.rounds.forall(r => r >= 2 && r <= cfg.review.roundsCap), (),
        ApiError(s"rounds must be between 2 and ${cfg.review.roundsCap} (agents.review.rounds-cap)"))) { (acc, o) =>
      acc.flatMap(_ => Role.parse(o.role).filter(roles.contains)
        .toRight(ApiError(s"models: '${o.role}' is not a role of review-article (${roles.map(_.wire).mkString(", ")})"))
        .flatMap(_ => ModelChains.parseChain(o.model).left.map(e => ApiError(s"models ${o.role}: $e")).map(_ => ())))
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

  /** The item an id or a target names, in manager `m`. */
  private def itemOf(m: ManagerInfo, ref: String): Either[ApiError, ManagerItem] =
    val r = ref.trim
    r.toLongOption.flatMap(ManagerRepo.item(ds, _)).filter(_.managerId == m.id)
      .orElse(ManagerRepo.itemFor(ds, m.id, itemKind(m), r))
      .toRight(notFound(s"No item '$ref' of manager ${m.id}"))

  private def itemKind(m: ManagerInfo): String = ArticleReview

  /** Add (or bring back) a target: an article slug for the review manager. */
  def addItem(id: String, in: EnqueueRequest, origin: ItemOrigin = ItemOrigin.Person): Either[ApiError, ManagerItem] =
    for
      m    <- manager(id)
      kind <- Option(in.kind.trim).filter(k => k.isEmpty || k == ArticleReview || k == "review" || k == "article")
                .map(_ => itemKind(m)).toRight(ApiError(s"manager ${m.id} drives '$ArticleReview' items, not '${in.kind}'"))
      slug <- h.getArticle(in.target.trim).map(_.article.slug)
      prio  = in.priority.getOrElse(ManagerSupervisor.PersonPriority)
      (itemId, isNew) = ManagerRepo.upsert(ds, m.id, kind, slug, origin, prio, reopen = true)
      _     = ManagerRepo.event(ds, m.id, Some(itemId), None, Feed.Evaluated,
                s"$slug ${if isNew then "enqueued" else "enqueued again"} by a ${origin.wire} (priority $prio)")
      item <- ManagerRepo.item(ds, itemId).toRight(notFound(s"item $itemId vanished"))
    yield
      supervisor.nudge()
      item

  /** `POST /api/agents/enqueue` — a page button: the target goes to the library-wide manager of its
    * kind (phase 1: an article's review → `review`, created paused if it does not exist yet). */
  def enqueue(in: EnqueueRequest): Either[ApiError, ManagerItem] =
    in.kind.trim.toLowerCase match
      case "review" | "article-review" | "article" | "" =>
        supervisor.ensureDefaults()
        addItem(ReviewId, in.copy(kind = ArticleReview))
      case other => Left(ApiError(s"nothing manages '$other' items yet (phase 1: review)"))

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
    * next run only); its structured effect, if any, is applied now — prioritise, hold or skip the targets
    * it names or matches (pending and held items; a named target not yet an item is added). */
  def addHint(id: String, in: HintAdd): Either[ApiError, ManagerHint] =
    for
      m     <- manager(id)
      text  <- Option(in.text.trim).filter(_.nonEmpty).toRight(ApiError("a hint needs text"))
      item  <- in.item.map(_.trim).filter(_.nonEmpty).fold[Either[ApiError, Option[ManagerItem]]](Right(None))(r => itemOf(m, r).map(Some(_)))
      eff   <- in.effect.fold[Either[ApiError, Option[HintEffect]]](Right(None)) { e =>
                 val a = e.action.trim.toLowerCase
                 if !HintEffect.Actions.contains(a) then Left(ApiError(s"unknown hint action '${e.action}' (one of: ${HintEffect.Actions.mkString(", ")})"))
                 else if e.targets.isEmpty && e.tag.isEmpty && e.topic.isEmpty && e.goal.isEmpty && item.isEmpty then
                   Left(ApiError("a structured hint names its targets, a tag, a topic or a goal (or is about one item)"))
                 else if a == HintEffect.HoldAction && e.until.isEmpty && e.untilSource.isEmpty then
                   Left(ApiError("a hold hint needs `until` (a date) or `untilSource` (a DOI or citekey)"))
                 else Right(Some(e.copy(action = a, targets = e.targets ++ item.map(_.target).toList)))
               }
      hint   = ManagerRepo.addHint(ds, m.id, item.map(_.id), text, eff, in.author.map(_.trim).filter(_.nonEmpty).getOrElse("person"),
                 standing = !in.once.contains(true))
      applied = eff.fold("")(e => applyEffect(m, hint.id, text, e))
      _      = ManagerRepo.event(ds, m.id, item.map(_.id), None, Feed.Hint,
                 s"hint #${hint.id} (${if hint.standing then "standing" else "once"}${item.fold("")(i => s", ${i.target}")}): ${text.take(300)}$applied")
    yield
      supervisor.nudge()
      hint

  private def applyEffect(m: ManagerInfo, hintId: Long, text: String, e: HintEffect): String =
    // the named targets become items (an article that exists); the matched ones are the open items
    val named = e.targets.flatMap(t => h.getArticle(t).toOption.map(_.article.slug)).distinct
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
          ManagerRepo.upsert(ds, m.id, ArticleReview, t, ItemOrigin.Hint, p, reopen = true)
          true
        }
        matched.foreach(i => ManagerRepo.setPriority(ds, i.id, math.max(i.priority, p)))
        matched.size + added
      case HintEffect.HoldAction =>
        val hold = Hold(e.until, e.untilSource, s"hint #$hintId: ${text.take(200)}", Some(hintId))
        val added = named.filterNot(t => open.exists(_.target == t)).flatMap { t =>
          val (id, _) = ManagerRepo.upsert(ds, m.id, ArticleReview, t, ItemOrigin.Hint, 0, reopen = true)
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

  // ── events ─────────────────────────────────────────────────────────────────────────────────────

  def events(id: String, before: Option[Long], limit: Option[Int]): Either[ApiError, EventPage] =
    manager(id).map { m =>
      val lim = limits.page(limit, 50)
      val es = ManagerRepo.events(ds, m.id, before, lim)
      EventPage(es, Option.when(es.size == lim)(es.last.id))
    }

object ManagersHandlers:
  def describe(c: ManagerConfig): String =
    val fields = List(c.maxRuns.map(n => s"maxRuns=$n"), c.maxOpen.map(n => s"maxOpen=$n"), c.rounds.map(n => s"rounds=$n"),
      c.maxRunsPerArticle.map(n => s"maxRunsPerArticle=$n"), c.noProgressRuns.map(n => s"noProgressRuns=$n"),
      c.reopenLimit.map(n => s"reopenLimit=$n"), c.cooldownMinutes.map(n => s"cooldownMinutes=$n"),
      c.lowWater.map(n => s"lowWater=$n"), c.refillBatch.map(n => s"refillBatch=$n"), c.maxFailures.map(n => s"maxFailures=$n"),
      c.budgetUsd.map(n => s"budgetUsd=$n"), c.includeTags.map(t => s"includeTags=${t.mkString(",")}"),
      c.includeGoal.map(g => s"includeGoal=$g"), c.models.map(ms => s"models=${ms.map(_.text).mkString(";")}")).flatten
    if fields.isEmpty then "the defaults" else fields.mkString(", ")
