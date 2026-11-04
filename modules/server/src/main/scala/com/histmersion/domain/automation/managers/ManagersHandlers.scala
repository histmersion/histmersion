package com.histmersion.domain.automation.managers

import io.scalaland.chimney.dsl.*

import com.histmersion.util.database.Transactor

import com.augustnagro.magnum.{connect, transact}

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.workflows.AutomationHost

import com.histmersion.api.{Plural, Write}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Goal.Plans.{LinkKind, Removed}
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.automation.managers.ManagerRepo

import javax.sql.DataSource
import com.histmersion.api.automation.{
  AgentMode,
  AgentsApi,
  AgentScheduler,
  ApprovalDigest,
  Autopilot,
  ManagersMarkdown,
  ModelChains,
  Stalls
}
import com.histmersion.api.Api
import com.histmersion.api.automation.Goal

/** The ONE implementation of the process-manager operations (docs/research-process-managers.md §5): REST
  * ([[AgentsRoutes]]), the MCP tool `manager` ([[AgentsTools]]) and the CLI (over REST) call these. Reading and
  * changing a manager works with agents off (nothing starts until they are on).
  */
final class ManagersHandlers(
    ds: DataSource,
    cfg: => AgentsConfig,
    limits: => Limits,
    agents: AgentsHandlers,
    supervisor: ManagerSupervisor,
    host: AutomationHost
):

  private def mcfg = cfg.managers

  /** The runs manager `id` dispatched, newest first (`status` narrows) — `run_status`'s manager filter. */
  def runs(id: String, status: Option[String], limit: Option[Int]): List[RunInfo] =
    val st = status.map(_.trim).filter(_.nonEmpty)
    agents.runs.forManager(id.trim, limit = limits.page(limit, 50)).toList.filter(r => st.forall(_ == r.status))

  private def notFound(what: String) = Api.Error(what, Some(Write.CodeNotFound))
  private def conflict(m: String) = Api.Error(m, Some(AgentsApi.CodeConflict))

  private def kindOf(m: ManagerInfo): Kind = Kind.parse(m.kind).getOrElse(Kind.Review)

  /** The settings in effect: the saved config over the server's defaults for its kind; a watcher's ingest backlog and
    * the days it takes to drain (`backlog` = one snapshot for every manager listed).
    */
  private def kinds: List[ManagerKindDefinition] = agents.workflows.managerKinds

  /** Every manager's backlog, by id, as its kind's domain counts it (one reading for every manager listed). */
  private def backlogs(): Map[String, (Int, Option[Int])] = kinds.foldLeft(Map.empty)(_ ++ _.backlog())

  private def withEffective(m: ManagerInfo, backlog: => Map[String, (Int, Option[Int])]): ManagerInfo =
    val e = m.copy(effective =
      kinds
        .find(_.kind == kindOf(m))
        .fold(ManagerConfig())(_.defaults(cfg, ManagerDefaultsViews.defaults(mcfg)))
        .merge(m.config)
    )
    if kindOf(m) != Kind.Watch then e
    else backlog.get(m.id).fold(e)((n, days) => e.copy(backlog = n, backlogDays = days))

  private def manager(id: String): Either[Api.Error, ManagerInfo] =
    agents.managerStore
      .get(id.trim)
      .map(m => withEffective(m, backlogs()))
      .toRight(notFound(s"No manager '$id'"))

  def list: List[ManagerInfo] =
    lazy val snap = backlogs()
    agents.managerStore.list().map(withEffective(_, snap))

  def get(id: String): Either[Api.Error, ManagerDetail] =
    manager(id).map { m =>
      ManagerDetail(
        m,
        withRuns(connect(ds)(ManagerRepo.withStatus(m.id, ItemStatus.Active))),
        Transactor(ds).read(ManagerRepo.hints(m.id, live = true)),
        connect(ds)(ManagerRepo.events(m.id, None, limits.page(None, 20)))
      )
    }

  private def withRuns(is: List[ManagerItem]): List[ManagerItem] =
    is.map(i => i.copy(run = i.runId.flatMap(agents.runs.get(_))))

  /** `POST /api/agents/managers` — `review` (id `review`) and `enrich` (id `enrich`) are library-wide; `seed` needs its
    * research area (`goal`; id `seed:<goal>`) and starts with the item that proposes its first articles; `watch` needs
    * its research area too (id `watch:<goal>`), gets the cron `agents.managers.watch.cron` unless `cron` names another,
    * and starts with one item per sweep (they run when it is resumed, then on its cron). Created paused unless `paused`
    * is false — or, `paused` not given, while the autopilot is on (it runs every goal's managers).
    */
  def create(
      in: ManagerCreate,
      now: java.time.Instant = java.time.Instant.now(agents.clock)
  ): Either[Api.Error, ManagerInfo] =
    // created paused unless asked otherwise — or, when nothing is asked, while the autopilot runs the goals
    val state =
      if in.paused.contains(false) || (in.paused.isEmpty && agents.autopilot.on) then State.Active else State.Paused
    val goal = in.goal.map(_.trim).filter(_.nonEmpty)
    def make(k: Kind, id: String, g: Option[String], cron: Option[String] = None): Either[Api.Error, ManagerInfo] =
      for
        _ <- validate(k, in.config)
        c <- cron
          .filter(_.trim.nonEmpty)
          .fold[Either[Api.Error, Option[Cron]]](Right(None))(t => checkCron(k, t, now).map(Some(_)))
        _ <- Either.cond(
          agents.managerStore.create(id, k, g, state, in.config, c.map(_.text), c.flatMap(_.next(now))),
          (),
          conflict(s"the manager '$id' exists already")
        )
        _ = agents.managerStore.event(
          id,
          None,
          None,
          Feed.Paused,
          s"created ${state.wire}" +
            c.fold("")(x =>
              s", cron `${x.text}` (UTC)"
            ) + (if state == State.Paused then
                   s": resume it to start ${ManagerSupervisor.purpose(k)} (nothing is spent until then)"
                 else "")
        )
        m <- manager(id)
      yield m
    Kind.parse(in.kind) match
      case None =>
        Left(Api.Error(s"unknown manager kind '${in.kind}' (one of: ${Kind.values.map(_.wire).mkString(", ")})"))
      case Some(Kind.Review) if goal.isDefined =>
        Left(Api.Error("the review manager is library-wide: give no goal (narrow it with config includeGoal)"))
      case Some(Kind.Review)                   => make(Kind.Review, ReviewId, None)
      case Some(Kind.Enrich) if goal.isDefined => Left(Api.Error("the enrich manager is library-wide: give no goal"))
      case Some(Kind.Enrich)                   => make(Kind.Enrich, EnrichId, None)
      case Some(Kind.Seed)                     =>
        for
          g <- goal.toRight(Api.Error("a seeding manager is for one research area: give its goal"))
          gl <- host.goal(g)
          m <- make(Kind.Seed, seedId(gl.slug), Some(gl.slug))
          _ <- addItem(m.id, EnqueueRequest(ProposeArticles, gl.slug))
          out <- manager(m.id)
        yield out
      case Some(Kind.Watch) =>
        for
          g <- goal.toRight(Api.Error("a watcher is for one research area: give its goal"))
          gl <- host.goal(g)
          m <- make(
            Kind.Watch,
            watchId(gl.slug),
            Some(gl.slug),
            in.cron.orElse(kinds.find(_.kind == Kind.Watch).flatMap(_.cron))
          )
          _ = Sweep
            .of(in.config)
            .foreach(sw => addItem(m.id, EnqueueRequest(sw.itemKind, gl.slug, Some(0)), ItemOrigin.Selection))
          out <- manager(m.id)
        yield out

  /** `PUT /api/agents/managers/{id}` — the config is replaced as given (an absent key = the default); `cron` set (five
    * fields in UTC, or @daily/@weekly/@monthly; no more often than `agents.scheduler.min-interval-minutes`) or cleared
    * with "" — its next firing is computed now.
    */
  def update(
      id: String,
      in: ManagerUpdate,
      now: java.time.Instant = java.time.Instant.now(agents.clock)
  ): Either[Api.Error, ManagerInfo] =
    for
      m <- manager(id)
      c = in.config.getOrElse(m.config)
      _ <- validate(kindOf(m), c)
      cron <- in.cron.map(_.trim) match
        case Some("") => Right(None)
        case Some(t)  => checkCron(kindOf(m), t, now).map(Some(_))
        case None     => Right(m.cron.flatMap(t => Cron.parse(t).toOption))
      next = cron.flatMap(x =>
        if in.cron.isDefined || m.nextTickAt.isEmpty then x.next(now)
        else m.nextTickAt.flatMap(ManagerPolicy.parseInstant)
      )
      _ = agents.managerStore.setConfig(m.id, c, cron.map(_.text), next)
      _ = agents.managerStore.event(
        m.id,
        None,
        None,
        Feed.Hint,
        s"settings changed: ${ManagersHandlers.describe(c)}" +
          (if in.cron.isDefined then
             cron.fold("; no cron")(x => s"; cron `${x.text}`, next ${next.fold("never")(_.toString)}")
           else "")
      )
      out <- manager(m.id)
    yield
      supervisor.nudge()
      out

  /** A cron a person sets on a manager of kind `k`: the enrich manager has none (ingests feed it). */
  private def checkCron(k: Kind, text: String, now: java.time.Instant): Either[Api.Error, Cron] =
    if k == Kind.Enrich then
      Left(Api.Error("the enrich manager has no cron: the ingests and the Enrich buttons feed it"))
    else ManagerPolicy.checkCron(text, now, cfg.scheduler.minIntervalMinutes).left.map(Api.Error(_))

  /** The rounds within the cap, every model override for a role the manager's pipelines use. */
  private def validate(k: Kind, c: ManagerConfig): Either[Api.Error, Unit] =
    val roles = supervisor.roles(k)
    c.models
      .getOrElse(Nil)
      .foldLeft[Either[Api.Error, Unit]](
        Either.cond(
          c.rounds.forall(r => r >= 2 && r <= agents.roundsCap),
          (),
          Api.Error(s"rounds must be between 2 and ${agents.roundsCap} (agents.review.rounds-cap)")
        )
      ) { (acc, o) =>
        acc.flatMap(_ =>
          Role
            .parse(o.role)
            .filter(roles.contains)
            .toRight(
              Api.Error(
                s"models: '${o.role}' is not a role a ${k.wire} manager runs (${roles.map(_.wire).mkString(", ")})"
              )
            )
            .flatMap(_ =>
              ModelChains
                .parseChain(o.model, agents.knownProviders)
                .left
                .map(e => Api.Error(s"models ${o.role}: $e"))
                .map(_ => ())
            )
        )
      }

  def delete(id: String): Either[Api.Error, Removed] =
    manager(id).map { m =>
      inFlight(m.id).foreach(r => agents.engine.cancel(r.id))
      Removed(Transactor(ds).transact(ManagerRepo.delete(m.id)))
    }

  private def inFlight(id: String): List[RunInfo] = agents.runs.forManager(id, active = true).toList

  /** `POST /api/agents/managers/{id}/{pause|resume|stop}[?runs=true]`. Pause = no new runs (with `runs`, its in-flight
    * runs are paused too, cooperatively); resume reverses both (only the runs paused on request); stop cancels its
    * in-flight runs and its open items.
    */
  def action(id: String, action: String, runs: Option[Boolean]): Either[Api.Error, ManagerInfo] =
    manager(id).flatMap { m =>
      val withRuns = runs.contains(true)
      action.trim.toLowerCase match
        case "pause" =>
          agents.managerStore.setState(m.id, State.Paused, withRuns)
          val paused = if withRuns then
            inFlight(m.id)
              .filter(r => r.status == RunState.Running.wire || r.status == RunState.Queued.wire)
              .count(r => agents.engine.pause(r.id).isRight)
          else 0
          agents.managerStore.event(
            m.id,
            None,
            None,
            Feed.Paused,
            "paused by a person" + (if withRuns then s", with ${Plural.count(paused, "run")} in flight"
                                    else "; its runs in flight go on")
          )
          manager(m.id)
        case "resume" =>
          agents.managerStore.setState(m.id, State.Active, pauseRuns = false)
          val resumed = if m.pauseRuns && cfg.enabled then
            inFlight(m.id)
              .filter(r => r.status == RunState.Paused.wire && r.error.exists(_.startsWith(PauseReason.Requested.wire)))
              .count(r => agents.engine.resume(r.id, None, None).isRight)
          else 0
          agents.managerStore.event(
            m.id,
            None,
            None,
            Feed.Paused,
            "resumed by a person" + (if resumed > 0 then s", with ${Plural.count(resumed, "run")} it had paused"
                                     else "")
          )
          supervisor.nudge()
          manager(m.id)
        case "stop" =>
          agents.managerStore.setState(m.id, State.Stopped, pauseRuns = false)
          val cancelled = inFlight(m.id).count(r => agents.engine.cancel(r.id).isRight)
          val items = Transactor(ds).transact(ManagerRepo.cancelOpen(m.id, "the manager was stopped"))
          agents.managerStore.event(
            m.id,
            None,
            None,
            Feed.Stopped,
            s"stopped by a person: ${Plural.count(cancelled, "run")} cancelled, ${Plural.count(items, "item")} cancelled"
          )
          manager(m.id)
        case other => Left(Api.Error(s"unknown action '$other' (one of: ${Actions.mkString(", ")})"))
    }

  // ── items ──────────────────────────────────────────────────────────────────────────────────────

  def items(id: String, status: Option[String], offset: Option[Int], limit: Option[Int]): Either[Api.Error, ItemPage] =
    for
      m <- manager(id)
      st <- status.map(_.trim).filter(_.nonEmpty) match
        case Some(s) =>
          ItemStatus
            .parse(s)
            .map(Some(_))
            .toRight(Api.Error(s"unknown item status '$s' (one of: ${ItemStatus.values.map(_.wire).mkString(", ")})"))
        case None => Right(None)
    yield
      val (off, lim) = (math.max(0, offset.getOrElse(0)), limits.page(limit, 25))
      val (rows, total) = Transactor(ds).read(ManagerRepo.itemPage(m.id, st, off, lim))
      ItemPage(withRuns(rows), total, off, lim)

  /** The item an id or a target names, in manager `m` (its default item kind first, then the others). */
  private def itemOf(m: ManagerInfo, ref: String): Either[Api.Error, ManagerItem] =
    val r = ref.trim
    r.toLongOption
      .flatMap(agents.managerStore.item(_))
      .filter(_.managerId == m.id)
      .orElse(itemKinds(kindOf(m)).iterator.map(k => agents.managerStore.itemFor(m.id, k, r)).collectFirst {
        case Some(i) =>
          i
      })
      .toRight(notFound(s"No item '$ref' of manager ${m.id}"))

  /** A target as an item of `kind` names it: an article's slug, a source's citekey, the goal (a seeding manager's own).
    * A compile item is never added by hand: it comes from an approved plan.
    */
  private def targetOf(m: ManagerInfo, kind: String, raw: String): Either[Api.Error, String] =
    kinds.find(_.items.contains(kind)) match
      case Some(k) => k.target(m, kind, raw)
      case None    => Left(Api.Error(s"unknown item kind '$kind'"))

  /** Add (or bring back) a target: `in.kind` names the item kind (`review`, `extend`, `filter`, `enrich`, `propose`, or
    * the kind itself; blank = the manager's default). An article's review whose article records no filter brings an
    * infer-filter item with it (the review manager's `inferFilter`).
    */
  def addItem(id: String, in: EnqueueRequest, origin: ItemOrigin = ItemOrigin.Person): Either[Api.Error, ManagerItem] =
    for
      m <- manager(id)
      kind <- itemKindOf(kindOf(m), in.kind).toRight(
        Api.Error(s"manager ${m.id} drives ${itemKinds(kindOf(m)).mkString(", ")} items, not '${in.kind}'")
      )
      target <- targetOf(m, kind, in.target)
      prio = in.priority.getOrElse(ManagerSupervisor.PersonPriority)
      (itemId, isNew) = agents.managerStore.upsert(m.id, kind, target, origin, prio, reopen = true)
      label = if kind == itemKinds(kindOf(m)).head then target else s"$target ($kind)"
      _ = agents.managerStore.event(
        m.id,
        Some(itemId),
        None,
        Feed.Evaluated,
        s"$label ${if isNew then "enqueued" else "enqueued again"} by a ${origin.wire} (priority $prio)"
      )
      _ = kinds.find(_.items.contains(kind)).foreach(_.added(supervisor, m, kind, target, prio))
      item <- agents.managerStore.item(itemId).toRight(notFound(s"item $itemId vanished"))
    yield
      supervisor.nudge()
      item

  /** `POST /api/agents/enqueue` — a page button: `review` or `extend` an article (the review manager), `enrich` a
    * source or the unenriched sources an article cites or a goal links (the enrich manager), `seed` a research area
    * (its seeding manager proposes its next articles). A manager that does not exist yet is created PAUSED.
    */
  def enqueue(in: EnqueueRequest): Either[Api.Error, EnqueueResult] =
    EnqueueKind.parse(in.kind) match
      case None =>
        Left(
          Api.Error(s"nothing manages '${in.kind}' items (one of: ${EnqueueKind.values.map(_.wire).mkString(", ")})")
        )
      case Some(EnqueueKind.Review) =>
        supervisor.ensureDefaults()
        addItem(ReviewId, in.copy(kind = ArticleReview)).map(i => EnqueueResult(ReviewId, List(i)))
      case Some(EnqueueKind.Extend) =>
        supervisor.ensureDefaults()
        addItem(ReviewId, in.copy(kind = ArticleExtend)).map(i => EnqueueResult(ReviewId, List(i)))
      case Some(EnqueueKind.Seed) =>
        host.goal(in.target.trim).flatMap { g =>
          val id = seedId(g.slug)
          val _ = supervisor.ensure(Kind.Seed, id, Some(g.slug))
          addItem(id, in.copy(kind = ProposeArticles, target = g.slug)).map(i => EnqueueResult(id, List(i)))
        }
      case Some(EnqueueKind.Enrich) =>
        supervisor.ensureDefaults()
        kinds
          .find(_.kind == Kind.Enrich)
          .fold(Right((List(in.target.trim), Nil)))(_.expand(supervisor, in.target.trim))
          .map { (cks, skipped) =>
            val items = cks.flatMap(ck => addItem(EnrichId, EnqueueRequest(SourceEnrich, ck, in.priority)).toOption)
            EnqueueResult(EnrichId, items, skipped)
          }

  def patchItem(id: String, ref: String, in: ItemPatch): Either[Api.Error, ManagerItem] =
    for
      m <- manager(id)
      i <- itemOf(m, ref)
      _ <- in.action.trim.toLowerCase match
        case "hold" =>
          val h = Hold(
            in.until.map(_.trim).filter(_.nonEmpty),
            in.untilSource.map(_.trim).filter(_.nonEmpty),
            in.reason.getOrElse("held by a person")
          )
          for
            _ <- h.until.fold[Either[Api.Error, Unit]](Right(()))(u =>
              ManagerPolicy
                .parseInstant(u)
                .map(_ => ())
                .toRight(Api.Error(s"'$u' is not a date (2026-11-01) or an instant"))
            )
            _ <- Either.cond(
              transact(ds)(ManagerRepo.hold(i.id, h)),
              (),
              conflict(s"item #${i.id} is ${i.status}; only a pending or held item can be held")
            )
          yield agents.managerStore.event(
            m.id,
            Some(i.id),
            None,
            Feed.Held,
            s"${i.target}: held" +
              h.until.fold("")(u => s" until $u") + h.untilSource.fold("")(s => s" until $s exists") + s" — ${h.reason}"
          )
        case "release" if i.hold.exists(_.focus.isDefined) && agents.focus.inForce =>
          val g = i.hold.flatMap(_.focus).getOrElse("")
          Left(
            conflict(
              s"item #${i.id} is held by the autopilot's focus: goal $g is not one of the active goals. Pin " +
                s"it to run it now (histmersion agent autopilot focus pin $g), or raise the cap (focus max <n>)"
            )
          )
        case "release" =>
          Either
            .cond(transact(ds)(ManagerRepo.release(i.id)), (), conflict(s"item #${i.id} is ${i.status}, not held"))
            .map(_ =>
              agents.managerStore.event(m.id, Some(i.id), None, Feed.Held, s"${i.target}: released by a person")
            )
        case "priority" =>
          in.priority.toRight(Api.Error("give `priority`")).map { p =>
            Transactor(ds).transact(ManagerRepo.setPriority(i.id, p))
            agents.managerStore.event(m.id, Some(i.id), None, Feed.Hint, s"${i.target}: priority $p")
          }
        case "skip" =>
          // an active item's run is cancelled first; the item is then stopped as skipped
          i.runId.filter(_ => i.status == ItemStatus.Active.wire).foreach(r => agents.engine.cancel(r))
          val why = in.reason.filter(_.trim.nonEmpty).fold("skipped by a person")(r => s"skipped by a person: $r")
          Either
            .cond(
              transact(ds)(
                ManagerRepo
                  .settle(i.id, ItemStatus.Stopped, why, List(ItemStatus.Pending, ItemStatus.Held, ItemStatus.Active))
              ),
              (),
              conflict(s"item #${i.id} is ${i.status} already")
            )
            .map(_ => agents.managerStore.event(m.id, Some(i.id), i.runId, Feed.Stopped, s"${i.target}: $why"))
        case "reopen" =>
          Either
            .cond(
              Transactor(ds).transact(ManagerRepo.reopen(i.id)),
              (),
              conflict(s"item #${i.id} is ${i.status}; only a settled item can be re-opened")
            )
            .map(_ =>
              agents.managerStore.event(m.id, Some(i.id), None, Feed.Evaluated, s"${i.target}: re-opened by a person")
            )
        case other => Left(Api.Error(s"unknown item action '$other' (one of: ${ItemPatch.Actions.mkString(", ")})"))
      out <- agents.managerStore.item(i.id).toRight(notFound(s"item ${i.id} vanished"))
    yield
      supervisor.nudge()
      withRuns(List(out)).head

  // ── hints ──────────────────────────────────────────────────────────────────────────────────────

  def hints(id: String, all: Boolean): Either[Api.Error, List[ManagerHint]] =
    manager(id).map(m => Transactor(ds).read(ManagerRepo.hints(m.id, live = !all)))

  /** A hint: its text reaches the next runs' fixer and skeptic as the person's notes (a once-hint the next run only).
    * Its structured effect, if any, is applied now — prioritise, hold or skip the targets it names or matches (pending
    * and held items; a named target not yet an item is added) — when it is the person's own; a MODEL's (`propose`: the
    * MCP tool, which a model drives) is recorded as a `hint-effect` approval and applied only when a person approves it
    * ([[applyProposal]]). `suggest` asks the server's planner model to read the text and propose the action (a
    * `hint-effect` run, started for the person; its proposal waits for approval the same way).
    */
  def addHint(id: String, in: HintAdd): Either[Api.Error, ManagerHint] =
    val propose = in.propose.contains(true)
    val suggest = in.suggest.contains(true)
    for
      m <- manager(id)
      text <- Option(in.text.trim).filter(_.nonEmpty).toRight(Api.Error("a hint needs text"))
      item <- in.item
        .map(_.trim)
        .filter(_.nonEmpty)
        .fold[Either[Api.Error, Option[ManagerItem]]](Right(None))(r => itemOf(m, r).map(Some(_)))
      eff <- in.effect.fold[Either[Api.Error, Option[HintEffect]]](Right(None))(e =>
        checkEffect(m, e, item, propose).map(Some(_))
      )
      _ <- Either.cond(
        !(suggest && eff.isDefined),
        (),
        Api.Error("`suggest` asks a model for the action: give no effect with it")
      )
      // a suggestion needs a run that can start: checked before the hint is recorded
      _ <- if suggest then agents.prepare(RunRequest(Pipeline.HintEffect.wire, m.id), None).map(_ => ()) else Right(())
      author = in.author.map(_.trim).filter(_.nonEmpty).getOrElse("person")
      hint = Transactor(ds).transact(
        ManagerRepo.addHint(
          m.id,
          item.map(_.id),
          text,
          if propose then None else eff,
          author,
          standing = !in.once.contains(true)
        )
      )
      applied = eff.fold("")(e =>
        if propose then proposeEffect(m, hint.id, text, e, author) else applyEffect(m, hint.id, text, e)
      )
      asked <-
        if suggest then
          agents
            .startRunWith(RunRequest(Pipeline.HintEffect.wire, m.id), Origin.Person, None, _.copy(hint = Some(hint.id)))
            .map(r => s" — a model reads it for an action (run ${r.id.take(8)}); a person approves what it proposes")
        else Right("")
      _ = agents.managerStore.event(
        m.id,
        item.map(_.id),
        None,
        Feed.Hint,
        s"hint #${hint.id} (${if hint.standing then "standing" else "once"}${item.fold("")(i => s", ${i.target}")}): ${text.take(300)}$applied$asked"
      )
      out <- Transactor(ds).read(ManagerRepo.hint(hint.id)).toRight(notFound(s"hint ${hint.id} vanished"))
    yield
      supervisor.nudge()
      out

  /** A structured effect checked against manager `m`: an action it knows (a model may also propose `config`), targets
    * (or a tag, topic, goal, or the item the hint is about), a hold's date or source, a settings change that parses and
    * that the manager's kind accepts.
    */
  private def checkEffect(
      m: ManagerInfo,
      e: HintEffect,
      item: Option[ManagerItem],
      proposal: Boolean
  ): Either[Api.Error, HintEffect] =
    val a = e.action.trim.toLowerCase
    val allowed = if proposal then HintEffect.Proposable else HintEffect.Actions
    if !allowed.contains(a) then
      Left(
        Api.Error(
          s"unknown hint action '${e.action}' (one of: ${allowed.mkString(", ")})" +
            (if !proposal && a == HintEffect.Config then " — change the settings with the manager's config instead"
             else "")
        )
      )
    else if a == HintEffect.Config then
      e.config
        .filter(c => ManagersMarkdown.configLine(c) != "the defaults")
        .toRight(Api.Error("a config action names the settings it changes"))
        .flatMap(c => validate(kindOf(m), m.config.merge(c)).map(_ => e.copy(action = a)))
    else if e.targets.isEmpty && e.tag.isEmpty && e.topic.isEmpty && e.goal.isEmpty && item.isEmpty then
      Left(Api.Error("a structured hint names its targets, a tag, a topic or a goal (or is about one item)"))
    else if a == HintEffect.HoldAction && e.until.isEmpty && e.untilSource.isEmpty then
      Left(Api.Error("a hold hint needs `until` (a date) or `untilSource` (a DOI or citekey)"))
    else if e.until.exists(u => ManagerPolicy.parseInstant(u).isEmpty) then
      Left(Api.Error(s"'${e.until.getOrElse("")}' is not a date (2026-11-01) or an instant"))
    else Right(e.copy(action = a, targets = (e.targets ++ item.map(_.target).toList).distinct))

  /** A model's effect, recorded for a person: an open `hint-effect` approval about the manager. */
  private def proposeEffect(m: ManagerInfo, hintId: Long, text: String, e: HintEffect, by: String): String =
    import io.circe.syntax.*
    val a = agents.approvalStore.openSubject(
      None,
      ApprovalKind.HintEffect,
      m.id,
      HintProposal(m.id, hintId, text, e, by).asJson.noSpaces
    )
    s" — proposed ${ManagersMarkdown.effect(e)}: approval #${a.id} waits for a person"

  /** A `hint-effect` approval a person approved: the action is checked again against the manager as it is now and
    * applied — items prioritised, held or skipped, or the settings changed — and recorded on the hint. Left = why it
    * could not be (the approval is then marked failed with it).
    */
  def applyProposal(p: HintProposal): Either[String, String] =
    for
      m <- manager(p.managerId).left.map(_.message)
      ht <- Transactor(ds)
        .read(ManagerRepo.hint(p.hint))
        .filter(_.managerId == m.id)
        .toRight(s"hint #${p.hint} of ${m.id} is gone")
      _ <- Either.cond(ht.retiredAt.isEmpty, (), s"hint #${ht.id} was retired meanwhile")
      e <- checkEffect(m, p.effect, ht.itemId.flatMap(agents.managerStore.item(_)), proposal = true).left.map(_.message)
      w <- e.action match
        case HintEffect.Config =>
          val c = m.config.merge(e.config.getOrElse(ManagerConfig()))
          val _ = agents.managerStore.setConfig(m.id, c, m.cron, m.nextTickAt.flatMap(ManagerPolicy.parseInstant))
          Right(s" — settings now: ${ManagersHandlers.describe(c)}")
        case _ => Right(applyEffect(m, ht.id, ht.text, e))
      _ = Transactor(ds).transact(ManagerRepo.setHintEffect(ht.id, Some(e)))
      _ = agents.managerStore.event(
        m.id,
        ht.itemId,
        None,
        Feed.Hint,
        s"hint #${ht.id}: the proposed ${ManagersMarkdown.effect(e)} approved by a person$w".take(2000)
      )
    yield
      supervisor.nudge()
      s"${m.id} hint #${ht.id}: ${ManagersMarkdown.effect(e)}$w"

  private def applyEffect(m: ManagerInfo, hintId: Long, text: String, e: HintEffect): String =
    // the named targets become items of the manager's default kind (a target that exists); the matched
    // ones are the open items
    val kind = itemKinds(kindOf(m)).headOption.getOrElse(ArticleReview)
    val named = e.targets.flatMap(t => targetOf(m, kind, t).toOption).distinct
    val open = connect(ds)(ManagerRepo.withStatus(m.id, ItemStatus.Pending)) ++ connect(ds)(
      ManagerRepo.withStatus(m.id, ItemStatus.Held)
    )
    val matched = open.filter { i =>
      named.contains(i.target) || ((e.tag.isDefined || e.topic.isDefined || e.goal.isDefined) && {
        val (tags, goals) = Transactor(ds).read(ManagerRepo.articleFacets(i.target))
        ManagerPolicy.matches(e.copy(targets = Nil), i.target, tags, Nil, goals)
      })
    }
    val n = e.action match
      case HintEffect.Prioritise =>
        val p = e.priority.getOrElse(ManagerSupervisor.HintPriority)
        val added = named.filterNot(t => open.exists(_.target == t)).count { t =>
          agents.managerStore.upsert(m.id, kind, t, ItemOrigin.Hint, p, reopen = true)
          true
        }
        matched.foreach(i => Transactor(ds).transact(ManagerRepo.setPriority(i.id, math.max(i.priority, p))))
        matched.size + added
      case HintEffect.HoldAction =>
        val hold = Hold(e.until, e.untilSource, s"hint #$hintId: ${text.take(200)}", Some(hintId))
        val added = named.filterNot(t => open.exists(_.target == t)).flatMap { t =>
          val (id, _) = agents.managerStore.upsert(m.id, kind, t, ItemOrigin.Hint, 0, reopen = true)
          agents.managerStore.item(id).filter(_.status == ItemStatus.Pending.wire)
        }
        (matched ++ added).count(i => transact(ds)(ManagerRepo.hold(i.id, hold)))
      case HintEffect.Skip =>
        matched.count(i =>
          transact(ds)(
            ManagerRepo.settle(
              i.id,
              ItemStatus.Stopped,
              s"skipped (hint #$hintId)",
              List(ItemStatus.Pending, ItemStatus.Held)
            )
          )
        )
      case _ => 0
    s" — ${e.action}: ${Plural.count(n, "item")}"

  def deleteHint(id: String, hint: Long): Either[Api.Error, Removed] =
    manager(id).map { m =>
      val gone = Transactor(ds).transact(ManagerRepo.retireHint(m.id, hint))
      if gone then agents.managerStore.event(m.id, None, None, Feed.Hint, s"hint #$hint retired by a person")
      Removed(gone)
    }

  // ── goal schedules: a watcher's sweeps (phase 3) ───────────────────────────────────────────────

  /** A goal's schedules (every goal's: None) as the goal surfaces show them — its watcher's sweeps
    * ([[WatchSchedules.view]]), and any `goal_schedules` row the managers have not moved yet.
    */
  def goalSchedules(goal: Option[String]): List[Goal.Plans.GoalSchedule] =
    agents.managerStore
      .list()
      .filter(m => m.kind == Kind.Watch.wire && goal.forall(g => m.goal.contains(g)))
      .flatMap(m =>
        WatchSchedules.view(m, Transactor(ds).read(ManagerRepo.itemPage(m.id, None, 0, 20))._1)
      ) ++ agents.goals.schedules(goal)

  /** `PUT /api/goals/{slug}/schedules/{kind}` — since phase 3 a goal's schedule is a sweep of its watcher
    * `watch:<goal>` (created PAUSED when it does not exist: resume it for the schedule to fire). `enabled` puts the
    * sweep in (or takes it out of) the watcher's sweeps; the cron (checked, no more often than
    * `agents.scheduler.min-interval-minutes`) is the watcher's, shared by its sweeps; a budget given is its per-run
    * cap.
    */
  def setSchedule(
      goal: String,
      kind: Goal.Plans.ScheduleKind,
      in: Goal.Plans.ScheduleSet,
      now: java.time.Instant
  ): Either[Api.Error, Goal.Plans.GoalSchedule] =
    val id = watchId(goal)
    val sw = WatchSchedules.sweepOf(kind)
    for
      cron <- ManagerPolicy.checkCron(in.cron, now, cfg.scheduler.minIntervalMinutes).left.map(Api.Error(_))
      _ = agents.managerStore.get(id) match
        case None =>
          val c = ManagerConfig(sweeps = Some(if in.enabled then List(sw.wire) else Nil), budgetUsd = in.budgetUsd)
          val _ =
            agents.managerStore.create(id, Kind.Watch, Some(goal), State.Paused, c, Some(cron.text), cron.next(now))
          agents.managerStore.event(
            id,
            None,
            None,
            Feed.Paused,
            s"created paused by the goal's ${kind.wire} schedule " +
              s"(cron `${cron.text}`): resume it for its sweeps to fire"
          )
        case Some(m) =>
          val cur = Sweep.of(m.config)
          val sweeps = Sweep.values.toList.filter(x => if x == sw then in.enabled else cur.contains(x))
          val c = m.config.copy(sweeps = Some(sweeps.map(_.wire)), budgetUsd = in.budgetUsd.orElse(m.config.budgetUsd))
          val next = if m.cron.contains(cron.text) && m.nextTickAt.isDefined then
            m.nextTickAt.flatMap(ManagerPolicy.parseInstant)
          else cron.next(now)
          val _ = agents.managerStore.setConfig(id, c, Some(cron.text), next)
          if !in.enabled then cancelSweep(m, sw, "its schedule was turned off")
          agents.managerStore.event(
            id,
            None,
            None,
            Feed.Hint,
            s"the goal's ${kind.wire} schedule set: cron `${cron.text}`, " +
              s"${if in.enabled then "on" else "off"}; sweeps ${
                  if sweeps.isEmpty then "none" else sweeps.map(_.wire).mkString(", ")
                }"
          )
      _ = agents.goals.removeSchedule(
        goal,
        kind.transformInto[com.histmersion.domain.automation.ScheduleKind]
      ) // a row the managers had not moved yet is replaced
      m <- manager(id)
    yield
      supervisor.nudge()
      WatchSchedules
        .view(m, Transactor(ds).read(ManagerRepo.itemPage(id, None, 0, 20))._1)
        .find(_.kind == kind)
        .getOrElse(Goal.Plans.GoalSchedule(goal, kind, cron.text, enabled = false, in.budgetUsd))

  /** `DELETE /api/goals/{slug}/schedules/{kind}` — the sweep leaves the watcher (its open item is cancelled); the
    * watcher itself stays.
    */
  def removeSchedule(goal: String, kind: Goal.Plans.ScheduleKind): Goal.Plans.Removed =
    val legacy = agents.goals.removeSchedule(goal, kind.transformInto[com.histmersion.domain.automation.ScheduleKind])
    val sw = WatchSchedules.sweepOf(kind)
    val fromWatcher = agents.managerStore.get(watchId(goal)).filter(m => Sweep.of(m.config).contains(sw)).exists { m =>
      val rest = Sweep.of(m.config).filterNot(_ == sw)
      val _ = agents.managerStore.setConfig(
        m.id,
        m.config.copy(sweeps = Some(rest.map(_.wire))),
        m.cron,
        m.nextTickAt.flatMap(ManagerPolicy.parseInstant)
      )
      cancelSweep(m, sw, "its schedule was removed")
      agents.managerStore.event(
        m.id,
        None,
        None,
        Feed.Hint,
        s"the goal's ${kind.wire} schedule removed; sweeps " +
          (if rest.isEmpty then "none" else rest.map(_.wire).mkString(", "))
      )
      true
    }
    Goal.Plans.Removed(legacy || fromWatcher)

  /** A sweep the watcher no longer runs: its pending or held item is cancelled (a running one finishes). */
  private def cancelSweep(m: ManagerInfo, sw: Sweep, why: String): Unit =
    m.goal.flatMap(g => agents.managerStore.itemFor(m.id, sw.itemKind, g)).foreach { i =>
      if transact(ds)(ManagerRepo.settle(i.id, ItemStatus.Cancelled, why, List(ItemStatus.Pending, ItemStatus.Held)))
      then agents.managerStore.event(m.id, Some(i.id), None, Feed.Stopped, s"${i.target} (${i.kind}): $why")
    }

  // ── the autopilot ──────────────────────────────────────────────────────────────────────────────

  /** `GET /api/agents/autopilot` — readable with agents off. */
  def autopilot: Autopilot.EngineStatus = agents.autopilot.status()

  /** `GET /api/agents/autopilot/focus` — readable with agents off (what it would focus on). */
  def focus: Autopilot.FocusInfo = agents.focus.info()

  /** `GET /api/agents/scheduler[?limit=]` — readable with agents off (a dry pass). */
  def scheduler(limit: Option[Int]): AgentScheduler.SchedulerStatus = agents.scheduler.status(limit)

  /** `POST /api/agents/scheduler {strategy}` — switch the strategy live. */
  def setScheduler(in: AgentScheduler.SchedulerSet): Either[Api.Error, AgentScheduler.SchedulerStatus] =
    agents.scheduler.set(in)

  /** `POST /api/agents/autopilot/drain {on}`. */
  def drain(in: Autopilot.DrainSet): Either[Api.Error, Autopilot.EngineStatus] = Right(agents.drain.set(in))

  /** `GET /api/agents/stalls[?days=]` — readable with agents off. */
  def stalls(days: Option[Int]): Stalls.StallReport = agents.scheduler.stalls(days)

  /** `GET /api/agents/approvals/digest`. */
  def digest: ApprovalDigest.Digest = agents.digest.digest()

  /** `POST /api/agents/approvals/decide` — a whole group at once. */
  def decideAll(in: ApprovalDigest.BulkDecide): Either[Api.Error, ApprovalDigest.BulkResult] =
    agents.digest.decideAll(in)

  /** `GET /api/agents/scheduler/why/{item}`. */
  def schedulerWhy(item: String): Either[Api.Error, AgentScheduler.WhyInfo] = agents.scheduler.why(item)

  /** `POST /api/agents/autopilot/focus {action, goal?, value?}` — pin, unpin, priority, max. */
  def setFocus(in: Autopilot.FocusSet): Either[Api.Error, Autopilot.FocusInfo] = agents.focus.set(in)

  /** `POST /api/agents/autopilot/mode {mode}` — the three-way control ([[AgentMode]]): the autopilot and the drain
    * moved, in order, to what the mode stands for. Idempotent.
    */
  def setMode(in: AgentMode.ModeSet): Either[Api.Error, Autopilot.EngineStatus] =
    AgentMode
      .parse(in.mode)
      .toRight(Api.Error(s"unknown mode '${in.mode}' — one of: ${AgentMode.values.map(_.wire).mkString(", ")}"))
      .map { to =>
        val now = agents.autopilot.status()
        val by = in.by.map(_.trim).filter(_.nonEmpty).orElse(Some("person"))
        AgentMode.steps(now.on, now.drain.isDefined, to).foldLeft(now) { (_, step) =>
          step match
            case AgentMode.Step.Autopilot(on) => agents.autopilot.set(Autopilot.AutopilotSet(on, by))
            case AgentMode.Step.Drain(on)     => agents.drain.set(Autopilot.DrainSet(on, by))
        }
      }

  /** `POST /api/agents/autopilot {on}` — idempotent; the answer says what it did. */
  def setAutopilot(in: Autopilot.AutopilotSet): Either[Api.Error, Autopilot.EngineStatus] =
    Right(agents.autopilot.set(in))

  // ── events ─────────────────────────────────────────────────────────────────────────────────────

  def events(id: String, before: Option[Long], limit: Option[Int]): Either[Api.Error, EventPage] =
    manager(id).map { m =>
      val lim = limits.page(limit, 50)
      val es = connect(ds)(ManagerRepo.events(m.id, before, lim))
      EventPage(es, Option.when(es.size == lim)(es.last.id))
    }

object ManagersHandlers:
  def describe(c: ManagerConfig): String = ManagersMarkdown.configLine(c)
