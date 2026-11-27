package com.histmersion
package domain.automation.managers
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.Text.{sentence, sentences}

import hearth.kindlings.optics.syntax.*

import io.scalaland.chimney.dsl.*

import com.histmersion.util.database.{Transactor, WritePermit}
import com.histmersion.util.data.maintenance.WriteGate

import com.augustnagro.magnum.connect

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.workflows.AutomationHost

import com.histmersion.api.{Plural, Write}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.automation.agents.Halt.Stopped
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
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.domain.automation.{Goal as DomainGoal, Run as DomainRun}
import com.histmersion.domain.automation.Run
import com.histmersion.domain.automation.agents.RunQuery
import com.histmersion.domain.automation.agents.RunService
import com.histmersion.domain.research.Slug
import com.histmersion.domain.automation.ScheduleKind
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Managers
import com.histmersion.util.database.Unused
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The ONE implementation of the process-manager operations (docs/research-process-managers.md §5): REST
  * ([[AgentsRoutes]]), the MCP tool `manager` ([[AgentsTools]]) and the CLI (over REST) call these. Reading and
  * changing a manager works with agents off (nothing starts until they are on).
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final class ManagersControllers(
    ds: DataSource,
    cfg: => AgentsConfig,
    limits: => Limits,
    agents: AgentsControllers,
    supervisor: ManagerSupervisor,
    host: AutomationHost
):

  private def mcfg = cfg.managers

  /** An operation that writes, under the maintenance gate (a restore waits for it; refused, 503, while one runs). */
  private def held[A](op: WriteGate.OperationName)(f: WritePermit ?=> Either[Api.Error, A]): Either[Api.Error, A] =
    agents.maintenance.guard(op)(f)

  /** The runs manager `id` dispatched, newest first (`status` narrows) — `run_status`'s manager filter. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def runs(id: ManagerLight.Id, status: Option[Run.State.Wire], limit: Option[PageSize]): List[RunInfo] =
    val st = status.map(_.value.trim).filter(_.nonEmpty)
    agents.runs
      .forManager(ManagerLight.Id(id.value.trim), limit = RunQuery.Limit(limits.page(limit, PageSize(50)).value))
      .toList
      .filter(r => st.forall(_ == r.status.value))

  private def error(problems: Problems): Api.Error = Api.Error(problems)
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def notFound(what: Problem) = Api.Error(what, Some(Api.Error.Code(Write.CodeNotFound)))
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def conflict(m: Problem) = Api.Error(m, Some(Api.Error.Code(AgentsApi.CodeConflict)))

  /** A run id read back from the database. */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def runIdOf(r: RunInfo.Id): DomainRun.Id = DomainRun.Id.assume(r.value)

  private def lightId(m: ManagerInfo): ManagerLight.Id = m.id.transformInto[ManagerLight.Id]

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def record(
      id: ManagerInfo.Id,
      item: Option[ManagerItem.Id],
      run: Option[DomainRun.Id],
      feed: String,
      text: String
  )(using WritePermit): Unit =
    agents.managerStore.event(id, item, run, ManagerEvent.Kind(feed), ManagerEvent.Text(text))

  private def kindOf(m: ManagerInfo): Kind = Kind.parse(m.kind).getOrElse(Kind.Review)

  /** The settings in effect: the saved config over the server's defaults for its kind; a watcher's ingest backlog and
    * the days it takes to drain (`backlog` = one snapshot for every manager listed).
    */
  private def kinds: List[ManagerKindDefinition] = agents.workflows.managerKinds

  /** Every manager's backlog, by id, as its kind's domain counts it (one reading for every manager listed). */
  private def backlogs(): Map[ManagerLight.Id, (IngestBacklog.Lane.Backlog, Option[IngestBacklog.DaysToDrain])] =
    kinds.foldLeft(Map.empty)(_ ++ _.backlog())

  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def withEffective(
      m: ManagerInfo,
      backlog: => Map[ManagerLight.Id, (IngestBacklog.Lane.Backlog, Option[IngestBacklog.DaysToDrain])]
  ): ManagerInfo =
    val e = m
      .modify(_.effective)
      .setTo(
        kinds
          .find(_.kind == kindOf(m))
          .fold(ManagerConfig())(_.defaults(cfg, ManagerDefaultsViews.defaults(mcfg)))
          .merge(m.config)
      )
    if kindOf(m) != Kind.Watch then e
    else
      backlog
        .get(lightId(m))
        .fold(e)((n, days) =>
          e.copy(backlog = ManagerInfo.Backlog(n.value), backlogDays = days.map(d => ManagerInfo.BacklogDays(d.value)))
        )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def manager(id: ManagerLight.Id): Either[Api.Error, ManagerInfo] =
    agents.managerStore
      .get(ManagerInfo.Id(id.value.trim))
      .map(m => withEffective(m, backlogs()))
      .toRight(notFound(Problem.NotFound.Entity(Problem.TypeName.of(ManagerLight.Id), Problem.Raw.of(id))))

  def list: List[ManagerInfo] =
    lazy val snap = backlogs()
    agents.managerStore.list().map(withEffective(_, snap))

  def get(id: ManagerLight.Id): Either[Api.Error, ManagerDetail] =
    manager(id).map { m =>
      ManagerDetail(
        m,
        withRuns(connect(ds)(ManagerRepo.withStatus(m.id, ItemStatus.Active))),
        Transactor(ds).read(ManagerRepo.hints(m.id, live = true)),
        connect(ds)(ManagerRepo.events(m.id, None, limits.page(None, PageSize(20))))
      )
    }

  private def withRuns(is: List[ManagerItem]): List[ManagerItem] =
    is.map(i => i.modify(_.run).setTo(i.runId.flatMap(r => agents.runs.get(runIdOf(r)))))

  /** `POST /api/agents/managers` — `review` (id `review`) and `enrich` (id `enrich`) are library-wide; `seed` needs its
    * research area (`goal`; id `seed:<goal>`) and starts with the item that proposes its first articles; `watch` needs
    * its research area too (id `watch:<goal>`), gets the cron `agents.managers.watch.cron` unless `cron` names another,
    * and starts with one item per sweep (they run when it is resumed, then on its cron). Created paused unless `paused`
    * is false — or, `paused` not given, while the autopilot is on (it runs every goal's managers).
    */
  def create(
      in: ManagerCreate,
      now: java.time.Instant = java.time.Instant.now(agents.clock)
  )(using Creator.New): Either[Api.Error, ManagerInfo] =
    held(WriteGate.OperationName("create_manager"))(createHeld(in, now))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def createHeld(in: ManagerCreate, now: java.time.Instant)(using
      WritePermit
  )(using Creator.New): Either[Api.Error, ManagerInfo] =
    // created paused unless asked otherwise — or, when nothing is asked, while the autopilot runs the goals
    val state =
      if in.paused.exists(!_.value) || (in.paused.isEmpty && agents.autopilot.on) then State.Active else State.Paused
    val goal = in.goal.map(_.value.trim).filter(_.nonEmpty)
    def make(
        k: Kind,
        id: ManagerLight.Id,
        g: Option[DomainGoal.Slug],
        cron: Option[ManagerLight.Cron] = None
    )(using Creator.New): Either[Api.Error, ManagerInfo] =
      for
        _ <- validate(k, in.config)
        c <- cron
          .filter(_.value.trim.nonEmpty)
          .fold[Either[Api.Error, Option[Cron]]](Right(None))(t => checkCron(k, t, now).map(Some(_)))
        infoId = id.transformInto[ManagerInfo.Id]
        _ <- Either.cond(
          agents.managerStore
            .create(infoId, k, g, state, in.config, c.map(x => ManagerInfo.Cron(x.text.value)), c.flatMap(_.next(now))),
          (),
          conflict(Problem.Conflict.AlreadyExists(Problem.TypeName.of(ManagerLight.Id), Problem.Raw.of(id)))
        )
        _ = record(infoId, None, None, Feed.Paused, createdText(k, state, c))
        m <- manager(id)
      yield m
    Kind.parse(in.kind) match
      case None =>
        Left(
          error(
            Problem.Invalid.Unknown.here(
              Problem.TypeName.of(ManagerCreate.Kind),
              Problem.Raw.of(in.kind),
              Kind.values.toVector.map(k => Problem.Raw.of(k.wire))
            )
          )
        )
      case Some(Kind.Review) if goal.isDefined =>
        Left(
          error(
            Problem.Invalid.Broken.here(Problem.Invalid.Rule.ReviewLibraryWide)
          )
        )
      case Some(Kind.Review)                   => make(Kind.Review, ManagerLight.Id(ReviewId), None)
      case Some(Kind.Enrich) if goal.isDefined =>
        Left(Api.Error(Problem.Invalid.Broken.here(Problem.Invalid.Rule.EnrichLibraryWide)))
      case Some(Kind.Enrich) => make(Kind.Enrich, ManagerLight.Id(EnrichId), None)
      case Some(Kind.Seed)   =>
        for
          g <- goal.toRight(
            Api.Error(Problem.Invalid.Broken.here(Problem.Invalid.Rule.SeedGoal))
          )
          slug <- DomainGoal.Slug
            .option(g)
            .toRight(notFound(Problem.NotFound.Entity(Problem.TypeName.of(Goal.Slug), Problem.Raw(s"$g"))))
          gl <- host.goal(slug)
          m <- make(Kind.Seed, seedId(Goal.Ref(gl.slug.value)).transformInto[ManagerLight.Id], Some(slug))
          _ <- addItem(
            lightId(m),
            EnqueueRequest(EnqueueRequest.Kind(ProposeArticles), EnqueueRequest.Target(gl.slug.value))
          )
          out <- manager(lightId(m))
        yield out
      case Some(Kind.Watch) =>
        for
          g <- goal.toRight(
            Api.Error(Problem.Invalid.Broken.here(Problem.Invalid.Rule.WatchGoal))
          )
          slug <- DomainGoal.Slug
            .option(g)
            .toRight(notFound(Problem.NotFound.Entity(Problem.TypeName.of(Goal.Slug), Problem.Raw(s"$g"))))
          gl <- host.goal(slug)
          m <- make(
            Kind.Watch,
            watchId(Goal.Ref(gl.slug.value)).transformInto[ManagerLight.Id],
            Some(slug),
            in.cron.map(c => ManagerLight.Cron(c.value)).orElse(kinds.find(_.kind == Kind.Watch).flatMap(_.cron))
          )
          _ = Sweep
            .of(in.config)
            .foreach(sw =>
              addItem(
                lightId(m),
                EnqueueRequest(
                  EnqueueRequest.Kind(sw.itemKind.value),
                  EnqueueRequest.Target(gl.slug.value),
                  Some(EnqueueRequest.Priority(0))
                ),
                ItemOrigin.Selection
              ).left
                .foreach(e =>
                  scribe.warn(
                    s"watcher ${lightId(m)}: its first ${sw.wire} item was not queued: ${e.problems.sentences.mkString("; ")}"
                  )
                )
            )
          out <- manager(lightId(m))
        yield out

  /** A new manager's first event: how it was created, its cron, and what resuming it starts. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def createdText(k: Kind, state: State, cron: Option[Cron]): String =
    s"created ${state.wire}" + cron.fold("")(x => s", cron `${x.text}` (UTC)") +
      (if state == State.Paused then
         s": resume it to start ${ManagerSupervisor.purpose(k)} (nothing is spent until then)"
       else "")

  /** A write of [[createArea]]'s transaction that cannot be made: the transaction is rolled back, the error answered.
    */
  final private class Refused(val error: Api.Error) extends RuntimeException(error.problems.sentences.mkString("; "))

  /** `POST /api/goals/areas` — a new research area as ONE operation ([[Goal.AreaCreate]]), so that no step of it can be
    * left half done or replace what exists. Everything that can be checked is checked first: the slug, the plan run
    * (pipeline, models, budgets — as [[AgentsControllers.startRun]] checks it), the watcher's settings and cron. Then
    * one transaction writes the goal (create-only: a slug in use, or an old slug of another goal, is refused with 409),
    * the plan-goal run, the seeding manager with the item that proposes its first articles, the watcher with one item
    * per sweep, and their events; a failure of any of them leaves nothing behind. The run is handed to the engine after
    * the commit. The managers start as [[create]] starts them: paused, unless the autopilot is on.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def createArea(
      in: Goal.AreaCreate,
      now: java.time.Instant = java.time.Instant.now(agents.clock)
  )(using WritePermit)(using Creator.New): Either[Api.Error, Goal.AreaCreated] =
    val state = if agents.autopilot.on then State.Active else State.Paused
    def cronOf(k: Kind): Either[Api.Error, Option[Cron]] =
      kinds
        .find(_.kind == k)
        .flatMap(_.cron)
        .filter(_.value.trim.nonEmpty)
        .fold[Either[Api.Error, Option[Cron]]](Right(None))(t => checkCron(k, t, now).map(Some(_)))
    val seeding = in.seed.value
    for
      slug <- DomainGoal.Slug.parse(in.slug.value.trim).left.map(Api.Error(_))
      plan <-
        if !in.plan.value then Right(None)
        else
          agents
            .prepare(
              RunRequest(RunRequest.Pipeline(Pipeline.PlanGoal.wire.value), RunRequest.Target(slug.value)),
              None,
              creating = Some(slug)
            )
            .map(Some(_))
      _ <- if seeding then validate(Kind.Seed, ManagerConfig()) else Right(())
      watchCron <- in.watch.fold[Either[Api.Error, Option[Cron]]](Right(None))(c =>
        validate(Kind.Watch, c).flatMap(_ => cronOf(Kind.Watch))
      )
      ref = Goal.Ref(slug.value)
      target = ManagerItem.Target(slug.value)
      written <-
        try
          Right(Transactor(ds).write {
            agents.goals.createIn(slug, in.goal).left.foreach(e => throw Refused(e))
            val run = plan.map(pr =>
              agents.jobRuns.createIn(
                RunService.row(
                  pr.pipeline,
                  pr.target,
                  pr.model,
                  BigDecimal(pr.usd.value),
                  pr.tokens,
                  pr.params,
                  None,
                  pr.goal,
                  None,
                  Origin.Person,
                  dryRun = pr.dryRun
                )
              )
            )
            def made(id: ManagerInfo.Id, k: Kind, config: ManagerConfig, cron: Option[Cron])(using
                Creator.New
            ): ManagerInfo.Id =
              val goal = Some(slug.transformInto[Goal.Slug])
              val c = cron.map(x => ManagerInfo.Cron(x.text.value))
              if !ManagerRepo.createIn(id, k, goal, state, config, c, cron.flatMap(_.next(now))) then
                throw Refused(
                  conflict(Problem.Conflict.AlreadyExists(Problem.TypeName.of(ManagerLight.Id), Problem.Raw.of(id)))
                )
              ManagerRepo
                .eventIn(id, None, None, ManagerEvent.Kind(Feed.Paused), ManagerEvent.Text(createdText(k, state, cron)))
              id
            def enqueued(id: ManagerInfo.Id, kind: ManagerItem.Kind, origin: ItemOrigin, prio: Int, label: String) =
              val (item, _) = ManagerRepo.upsert(id, kind, target, origin, ManagerItem.Priority(prio), reopen = true)
              val text = s"$label enqueued by a ${origin.wire} (priority $prio)"
              ManagerRepo.eventIn(id, Some(item), None, ManagerEvent.Kind(Feed.Evaluated), ManagerEvent.Text(text))
            val seed = Option.when(seeding) {
              val id = made(seedId(ref), Kind.Seed, ManagerConfig(), None)
              enqueued(
                id,
                ManagerItem.Kind(ProposeArticles),
                ItemOrigin.Person,
                ManagerSupervisor.PersonPriority,
                s"$slug"
              )
              id
            }
            val watch = in.watch.map { c =>
              val id = made(watchId(ref), Kind.Watch, c, watchCron)
              Sweep.of(c).foreach(sw => enqueued(id, sw.itemKind, ItemOrigin.Selection, 0, s"$slug (${sw.itemKind})"))
              id
            }
            (run, seed, watch)
          })
        catch case r: Refused => Left(r.error)
      (run, seed, watch) = written
      _ = run.foreach(agents.engine.submit)
      _ = supervisor.nudge()
      goal <- host.goal(slug)
      seeded <- seed.fold[Either[Api.Error, Option[ManagerInfo]]](Right(None))(id => manager(lightOf(id)).map(Some(_)))
      watched <- watch.fold[Either[Api.Error, Option[ManagerInfo]]](Right(None))(id =>
        manager(lightOf(id)).map(Some(_))
      )
    yield Goal.AreaCreated(goal, run.flatMap(agents.runs.get), seeded, watched)

  private def lightOf(id: ManagerInfo.Id): ManagerLight.Id = id.transformInto[ManagerLight.Id]

  /** `PUT /api/agents/managers/{id}` — the config is replaced as given (an absent key = the default); `cron` set (five
    * fields in UTC, or @daily/@weekly/@monthly; no more often than `agents.scheduler.min-interval-minutes`) or cleared
    * with "" — its next firing is computed now.
    */
  def update(
      id: ManagerLight.Id,
      in: ManagerUpdate,
      now: java.time.Instant = java.time.Instant.now(agents.clock)
  ): Either[Api.Error, ManagerInfo] = held(WriteGate.OperationName("update_manager"))(updateHeld(id, in, now))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def updateHeld(id: ManagerLight.Id, in: ManagerUpdate, now: java.time.Instant)(using
      WritePermit
  ): Either[Api.Error, ManagerInfo] =
    for
      m <- manager(id)
      c = in.config.getOrElse(m.config)
      _ <- validate(kindOf(m), c)
      cron <- in.cron.map(_.value.trim) match
        case Some("") => Right(None)
        case Some(t)  => checkCron(kindOf(m), ManagerLight.Cron(t), now).map(Some(_))
        case None     => Right(m.cron.flatMap(t => Cron.parse(Cron.Text(t.value)).toOption))
      next = cron.flatMap(x =>
        if in.cron.isDefined || m.nextTickAt.isEmpty then x.next(now)
        else m.nextTickAt.flatMap(t => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(t.value)))
      )
      _ = agents.managerStore.setConfig(m.id, c, cron.map(x => ManagerInfo.Cron(x.text.value)), next)
      _ = record(
        m.id,
        None,
        None,
        Feed.Hint,
        s"settings changed: ${ManagersControllers.describe(c)}" +
          (if in.cron.isDefined then
             cron.fold("; no cron")(x => s"; cron `${x.text}`, next ${next.fold("never")(_.toString)}")
           else "")
      )
      out <- manager(lightId(m))
    yield
      supervisor.nudge()
      out

  /** A cron a person sets on a manager of kind `k`: the enrich manager has none (ingests feed it). */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def checkCron(k: Kind, text: ManagerLight.Cron, now: java.time.Instant): Either[Api.Error, Cron] =
    if k == Kind.Enrich then
      Left(
        Api.Error(
          Problem.Invalid.Broken.here(Problem.Invalid.Rule.EnrichCron)
        )
      )
    else
      ManagerPolicy
        .checkCron(
          Cron.Text(text.value),
          now,
          cfg.scheduler.minIntervalMinutes.transformInto[ManagerPolicy.MinIntervalMinutes]
        )
        .left
        .map(p => error(p))

  /** The rounds within the cap, every model override for a role the manager's pipelines use. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private def validate(k: Kind, c: ManagerConfig): Either[Api.Error, Unit] =
    val roles = supervisor.roles(k)
    c.models
      .getOrElse(Nil)
      .foldLeft[Either[Api.Error, Unit]](
        c.rounds
          .filterNot(r => r.value >= 2 && r.value <= agents.roundsCap.value)
          .map(r =>
            error(
              Problem.Invalid.Broken.here(
                Problem.Invalid.Rule
                  .RoundsWithin(Problem.Count.of(r), Problem.Count(2L), Problem.Count.of(agents.roundsCap))
              )
            )
          )
          .toLeft(())
      ) { (acc, o) =>
        acc.flatMap(_ =>
          Role
            .parse(o.role)
            .filter(roles.contains)
            .toRight(
              error(
                Problem.Invalid.Broken(
                  Problem.Invalid.Rule.RoleUsed(
                    Problem.Invalid.RoleOwner.Manager,
                    Problem.Raw.of(k.wire),
                    Problem.Raw.of(o.role),
                    roles.toVector.map(r => Problem.Raw.of(r.wire))
                  ),
                  Vector(Problem.Field("models"))
                )
              )
            )
            .flatMap(_ =>
              ModelChains
                .parseChain(
                  ModelChains.Chain.Spec(o.model.value),
                  agents.knownProviders.map(i => com.histmersion.api.automation.ProviderKind.Plugin.Name(i.value))
                )
                .left
                .map(e => error(e.at(Problem.Field(s"models ${o.role}"))))
                .map(_ => ())
            )
        )
      }

  def delete(id: ManagerLight.Id): Either[Api.Error, Removed] =
    held(WriteGate.OperationName("delete_manager"))(deleteHeld(id))

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def deleteHeld(id: ManagerLight.Id)(using WritePermit): Either[Api.Error, Removed] =
    manager(id).map { m =>
      inFlight(lightId(m)).foreach(r => Unused(agents.engine.cancel(runIdOf(r.id)), Unused.Why.Idempotent))
      Removed(Removed.Value(Transactor(ds).write(ManagerRepo.delete(m.id))))
    }

  private def inFlight(id: ManagerLight.Id): List[RunInfo] =
    agents.runs.forManager(id, active = RunService.Active(true)).toList

  /** `POST /api/agents/managers/{id}/{pause|resume|stop}[?runs=true]`. Pause = no new runs (with `runs`, its in-flight
    * runs are paused too, cooperatively); resume reverses both (only the runs paused on request); stop cancels its
    * in-flight runs and its open items.
    */
  def action(
      id: ManagerLight.Id,
      action: ManagersControllers.Action,
      runs: Option[ManagerInfo.PauseRuns]
  )(using Creator.New): Either[Api.Error, ManagerInfo] =
    held(WriteGate.OperationName("manager_action"))(actionHeld(id, action, runs))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def actionHeld(
      id: ManagerLight.Id,
      action: ManagersControllers.Action,
      runs: Option[ManagerInfo.PauseRuns]
  )(using WritePermit)(using Creator.New): Either[Api.Error, ManagerInfo] =
    manager(id).flatMap { m =>
      val withRuns = runs.exists(_.value)
      action.value.trim.toLowerCase match
        case "pause" =>
          Unused(agents.managerStore.setState(m.id, State.Paused, ManagerInfo.PauseRuns(withRuns)), Unused.Why.ReadBack)
          val paused = if withRuns then
            inFlight(lightId(m))
              .filter(r => r.status == RunState.Running.wire || r.status == RunState.Queued.wire)
              .count(r => agents.engine.pause(runIdOf(r.id)).isRight)
          else 0
          record(
            m.id,
            None,
            None,
            Feed.Paused,
            "paused by a person" + (if withRuns then
                                      s", with ${Plural.count(Plural.CountN(paused.toLong), Plural.CountSingular("run"))} in flight"
                                    else "; its runs in flight go on")
          )
          manager(lightId(m))
        case "resume" =>
          Unused(agents.managerStore.setState(m.id, State.Active, ManagerInfo.PauseRuns(false)), Unused.Why.ReadBack)
          val resumed = if m.pauseRuns.value && cfg.enabled.value then
            inFlight(lightId(m))
              .filter(r =>
                r.status == RunState.Paused.wire && r.error
                  .exists(e => Stopped.of(e).pausedOn(PauseReason.Requested))
              )
              .count(r => agents.engine.resume(runIdOf(r.id), None, None).isRight)
          else 0
          record(
            m.id,
            None,
            None,
            Feed.Paused,
            "resumed by a person" + (if resumed > 0 then
                                       s", with ${Plural.count(Plural.CountN(resumed.toLong), Plural.CountSingular("run"))} it had paused"
                                     else "")
          )
          supervisor.nudge()
          manager(lightId(m))
        case "stop" =>
          Unused(agents.managerStore.setState(m.id, State.Stopped, ManagerInfo.PauseRuns(false)), Unused.Why.ReadBack)
          val cancelled = inFlight(lightId(m)).count(r => agents.engine.cancel(runIdOf(r.id)).isRight)
          val items =
            Transactor(ds).write(
              ManagerRepo.cancelOpen(m.id, Problem.Run.Cancelled(Problem.Run.Actor.Person))
            )
          record(
            m.id,
            None,
            None,
            Feed.Stopped,
            s"stopped by a person: ${Plural.count(Plural.CountN(cancelled.toLong), Plural.CountSingular("run"))} cancelled, ${Plural.count(Plural.CountN(items.value.toLong), Plural.CountSingular("item"))} cancelled"
          )
          manager(lightId(m))
        case other =>
          Left(
            error(
              Problem.Invalid.Unknown
                .here(Problem.TypeName.of(ActionsValue), Problem.Raw(other), Actions.toVector.map(Problem.Raw.of(_)))
            )
          )
    }

  // ── items ──────────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def items(
      id: ManagerLight.Id,
      status: Option[domain.automation.ItemStatus.Wire],
      offset: Option[Offset],
      limit: Option[PageSize]
  ): Either[Api.Error, ItemPage] =
    for
      m <- manager(id)
      st <- status.map(_.value.trim).filter(_.nonEmpty) match
        case Some(s) =>
          ItemStatus
            .parse(com.histmersion.api.automation.ItemStatus.Wire(s))
            .map(Some(_))
            .toRight(
              error(
                Problem.Invalid.Unknown.here(
                  Problem.TypeName.of(ItemStatus.Wire),
                  Problem.Raw(s),
                  ItemStatus.values.toVector.map(v => Problem.Raw.of(v.wire))
                )
              )
            )
        case None => Right(None)
    yield
      val (off, lim) = (offset.getOrElse(Offset(0)), limits.page(limit, PageSize(25)))
      val (rows, total) = Transactor(ds).read(ManagerRepo.itemPage(m.id, st, off, lim))
      ItemPage(withRuns(rows), ItemPage.Total(total.value), ItemPage.Offset(off.value), ItemPage.Limit(lim.value))

  /** The item an id or a target names, in manager `m` (its default item kind first, then the others). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def itemOf(m: ManagerInfo, ref: ManagerItem.Ref): Either[Api.Error, ManagerItem] =
    val r = ref.value.trim
    r.toLongOption
      .flatMap(n => agents.managerStore.item(ManagerItem.Id(n)))
      .filter(_.managerId == m.id)
      .orElse(
        itemKinds(kindOf(m)).iterator
          .map(k => agents.managerStore.itemFor(m.id, k, ManagerItem.Target(r)))
          .collectFirst { case Some(i) =>
            i
          }
      )
      .toRight(notFound(Problem.NotFound.Entity(Problem.TypeName.of(ManagerItem.Ref), Problem.Raw.of(ref))))

  /** A target as an item of `kind` names it: an article's slug, a source's citekey, the goal (a seeding manager's own).
    * A compile item is never added by hand: it comes from an approved plan.
    */
  private def targetOf(
      m: ManagerInfo,
      kind: ManagerContext.ItemKind,
      raw: ManagerItem.Target
  ): Either[Api.Error, ManagerContext.ItemTarget] =
    kinds.find(_.items.contains(kind)) match
      case Some(k) => k.target(m, kind, raw)
      case None    =>
        Left(
          error(
            Problem.Invalid.Unknown
              .here(Problem.TypeName.of(ManagerContext.ItemKind), Problem.Raw.of(kind), Vector.empty)
          )
        )

  /** Add (or bring back) a target: `in.kind` names the item kind (`review`, `extend`, `filter`, `enrich`, `propose`, or
    * the kind itself; blank = the manager's default). An article's review whose article records no filter brings an
    * infer-filter item with it (the review manager's `inferFilter`).
    */
  def addItem(
      id: ManagerLight.Id,
      in: EnqueueRequest,
      origin: ItemOrigin = ItemOrigin.Person
  ): Either[Api.Error, ManagerItem] = held(WriteGate.OperationName("add_manager_item"))(addItemHeld(id, in, origin))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private def addItemHeld(id: ManagerLight.Id, in: EnqueueRequest, origin: ItemOrigin)(using
      WritePermit
  ): Either[Api.Error, ManagerItem] =
    for
      m <- manager(id)
      kind <- itemKindOf(kindOf(m), com.histmersion.api.ItemKind.Word(in.kind.value)).toRight(
        error(
          Problem.Invalid.Unknown.here(
            Problem.TypeName.of(EnqueueRequest.Kind),
            Problem.Raw.of(in.kind),
            itemKinds(kindOf(m)).toVector.map(Problem.Raw.of(_))
          )
        )
      )
      itemKind = ManagerContext.ItemKind(kind.value)
      target <- targetOf(m, itemKind, ManagerItem.Target(in.target.value))
      prio = in.priority.fold(ManagerSupervisor.PersonPriority)(_.value)
      (itemId, isNew) = agents.managerStore.upsert(
        m.id,
        kind,
        target.transformInto[ManagerItem.Target],
        origin,
        ManagerItem.Priority(prio),
        reopen = true
      )
      label = if kind == itemKinds(kindOf(m)).head then target.value else s"$target ($kind)"
      _ = record(
        m.id,
        Some(itemId),
        None,
        Feed.Evaluated,
        s"$label ${if isNew then "enqueued" else "enqueued again"} by a ${origin.wire} (priority $prio)"
      )
      _ = kinds
        .find(_.items.contains(itemKind))
        .foreach(_.added(supervisor, m, itemKind, target, ManagerContext.Priority(prio)))
      item <- agents.managerStore
        .item(itemId)
        .toRight(notFound(Problem.NotFound.Entity(Problem.TypeName.of(ManagerItem.Id), Problem.Raw.of(itemId))))
    yield
      supervisor.nudge()
      item

  /** `POST /api/agents/enqueue` — a page button: `review` or `extend` an article (the review manager), `enrich` a
    * source or the unenriched sources an article cites or a goal links (the enrich manager), `seed` a research area
    * (its seeding manager proposes its next articles). A manager that does not exist yet is created PAUSED.
    */
  def enqueue(in: EnqueueRequest)(using Creator.New): Either[Api.Error, EnqueueResult] =
    held(WriteGate.OperationName("enqueue"))(enqueueHeld(in))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def enqueueHeld(in: EnqueueRequest)(using WritePermit)(using Creator.New): Either[Api.Error, EnqueueResult] =
    EnqueueKind.parse(in.kind) match
      case None =>
        Left(
          error(
            Problem.Invalid.Unknown.here(
              Problem.TypeName.of(EnqueueRequest.Kind),
              Problem.Raw.of(in.kind),
              EnqueueKind.values.toVector.map(k => Problem.Raw.of(k.wire))
            )
          )
        )
      case Some(EnqueueKind.Review) =>
        supervisor.ensureDefaults()
        addItem(ManagerLight.Id(ReviewId), in.modify(_.kind).setTo(EnqueueRequest.Kind(ArticleReview))).map(i =>
          EnqueueResult(ManagerInfo.Id(ReviewId), List(i))
        )
      case Some(EnqueueKind.Extend) =>
        supervisor.ensureDefaults()
        addItem(ManagerLight.Id(ReviewId), in.modify(_.kind).setTo(EnqueueRequest.Kind(ArticleExtend))).map(i =>
          EnqueueResult(ManagerInfo.Id(ReviewId), List(i))
        )
      case Some(EnqueueKind.Seed) =>
        val text = in.target.value.trim
        for
          slug <- DomainGoal.Slug
            .option(text)
            .toRight(notFound(Problem.NotFound.Entity(Problem.TypeName.of(Goal.Slug), Problem.Raw(s"$text"))))
          g <- host.goal(slug)
          id = seedId(Goal.Ref(g.slug.value))
          _ = supervisor.ensure(Kind.Seed, id.transformInto[ManagerLight.Id], Some(slug))
          out <- addItem(
            id.transformInto[ManagerLight.Id],
            in.modify(_.kind)
              .setTo(EnqueueRequest.Kind(ProposeArticles))
              .modify(_.target)
              .setTo(EnqueueRequest.Target(g.slug.value))
          ).map(i => EnqueueResult(id, List(i)))
        yield out
      case Some(EnqueueKind.Enrich) =>
        supervisor.ensureDefaults()
        kinds
          .find(_.kind == Kind.Enrich)
          .fold(Right((List(ManagerContext.ItemTarget(in.target.value.trim)), Nil)))(
            _.expand(supervisor, ManagerItem.Target(in.target.value.trim))
          )
          .map { (cks, skipped) =>
            val items = cks.flatMap(ck =>
              addItem(
                ManagerLight.Id(EnrichId),
                EnqueueRequest(EnqueueRequest.Kind(SourceEnrich), EnqueueRequest.Target(ck.value), in.priority)
              ).toOption
            )
            EnqueueResult(ManagerInfo.Id(EnrichId), items, skipped.map(s => EnqueueResult.Skipped(s.value)))
          }

  def patchItem(id: ManagerLight.Id, ref: ManagerItem.Ref, in: ItemPatch): Either[Api.Error, ManagerItem] =
    held(WriteGate.OperationName("patch_manager_item"))(patchItemHeld(id, ref, in))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def patchItemHeld(id: ManagerLight.Id, ref: ManagerItem.Ref, in: ItemPatch)(using
      WritePermit
  ): Either[Api.Error, ManagerItem] =
    for
      m <- manager(id)
      i <- itemOf(m, ref)
      _ <- in.action.value.trim.toLowerCase match
        case "hold" =>
          val h = Hold(
            in.until.map(_.value.trim).filter(_.nonEmpty).map(Hold.Until(_)),
            in.untilSource.map(_.value.trim).filter(_.nonEmpty).map(Hold.UntilSource(_)),
            Managers.Rationale(in.reason.fold("held by a person")(w => s"$w"))
          )
          for
            _ <- h.until.fold[Either[Api.Error, Unit]](Right(()))(u =>
              ManagerPolicy
                .parseInstant(ManagerPolicy.InstantText(u.value))
                .map(_ => ())
                .toRight(
                  error(
                    Problem.Invalid.Unreadable
                      .here(Problem.TypeName.of(Hold.Until), Problem.Raw.of(u), Vector(Problem.Raw("2026-11-01")))
                  )
                )
            )
            _ <- Either.cond(
              Transactor(ds).write(ManagerRepo.hold(i.id, h)),
              (),
              conflict(
                Problem.Conflict.WrongState(
                  Problem.TypeName.of(ManagerItem.Id),
                  Problem.Raw.of(i.id),
                  Problem.Conflict.State.of(i.status),
                  Vector(
                    Problem.Conflict.State.of(ItemStatus.Pending.wire),
                    Problem.Conflict.State.of(ItemStatus.Held.wire)
                  )
                )
              )
            )
          yield record(
            m.id,
            Some(i.id),
            None,
            Feed.Held,
            s"${i.target}: held" +
              h.until.fold("")(u => s" until $u") + h.untilSource.fold("")(s => s" until $s exists") + s" — ${h.reason}"
          )
        case "release" if i.hold.exists(_.focus.isDefined) && agents.focus.inForce =>
          val g = i.hold.flatMap(_.focus).fold("")(_.value)
          Left(
            conflict(
              Problem.Conflict.Held(Problem.Where(s"item #${i.id}"), Problem.Conflict.Hold.Focus(Problem.Raw(g)))
            )
          )
        case "release" =>
          Either
            .cond(
              Transactor(ds).write(ManagerRepo.release(i.id)),
              (),
              conflict(
                Problem.Conflict.WrongState(
                  Problem.TypeName.of(ManagerItem.Id),
                  Problem.Raw.of(i.id),
                  Problem.Conflict.State.of(i.status),
                  Vector(Problem.Conflict.State.of(ItemStatus.Held.wire))
                )
              )
            )
            .map(_ => record(m.id, Some(i.id), None, Feed.Held, s"${i.target}: released by a person"))
        case "priority" =>
          in.priority.toRight(Api.Error(Problem.Invalid.Missing.here(Problem.Field("priority")))).flatMap { p =>
            // an item removed since it was read is not found, never a priority set on nothing
            Either.cond(
              Transactor(ds).write(ManagerRepo.setPriority(i.id, p.transformInto[ManagerItem.Priority])),
              record(m.id, Some(i.id), None, Feed.Hint, s"${i.target}: priority $p"),
              notFound(Problem.NotFound.Entity(Problem.TypeName.of(ManagerItem.Id), Problem.Raw.of(i.id)))
            )
          }
        case "skip" =>
          // an active item's run is cancelled first; the item is then stopped as skipped
          i.runId
            .filter(_ => i.status == ItemStatus.Active.wire)
            .foreach(r => Unused(agents.engine.cancel(runIdOf(r)), Unused.Why.Idempotent))
          val why = in.reason
            .filter(w => s"$w".trim.nonEmpty)
            .fold("skipped by a person")(r => s"skipped by a person: $r")
          Either
            .cond(
              Transactor(ds).write(
                ManagerRepo
                  .settle(
                    i.id,
                    ItemStatus.Stopped,
                    ManagerItem.Outcome(why),
                    List(ItemStatus.Pending, ItemStatus.Held, ItemStatus.Active)
                  )
              ),
              (),
              conflict(
                Problem.Conflict.WrongState(
                  Problem.TypeName.of(ManagerItem.Id),
                  Problem.Raw.of(i.id),
                  Problem.Conflict.State.of(i.status),
                  Vector(
                    Problem.Conflict.State.of(ItemStatus.Pending.wire),
                    Problem.Conflict.State.of(ItemStatus.Held.wire),
                    Problem.Conflict.State.of(ItemStatus.Active.wire)
                  )
                )
              )
            )
            .map(_ => record(m.id, Some(i.id), i.runId.map(runIdOf), Feed.Stopped, s"${i.target}: $why"))
        case "reopen" =>
          Either
            .cond(
              Transactor(ds).write(ManagerRepo.reopen(i.id)),
              (),
              conflict(
                Problem.Conflict.WrongState(
                  Problem.TypeName.of(ManagerItem.Id),
                  Problem.Raw.of(i.id),
                  Problem.Conflict.State.of(i.status),
                  Vector(
                    Problem.Conflict.State.of(ItemStatus.Done.wire),
                    Problem.Conflict.State.of(ItemStatus.Failed.wire),
                    Problem.Conflict.State.of(ItemStatus.Stopped.wire),
                    Problem.Conflict.State.of(ItemStatus.Cancelled.wire)
                  )
                )
              )
            )
            .map(_ => record(m.id, Some(i.id), None, Feed.Evaluated, s"${i.target}: re-opened by a person"))
        case other =>
          Left(
            error(
              Problem.Invalid.Unknown.here(
                Problem.TypeName.of(ItemPatch.Action),
                Problem.Raw(other),
                ItemPatch.Actions.toVector.map(Problem.Raw(_))
              )
            )
          )
      out <- agents.managerStore
        .item(i.id)
        .toRight(notFound(Problem.NotFound.Entity(Problem.TypeName.of(ManagerItem.Id), Problem.Raw.of(i.id))))
    yield
      supervisor.nudge()
      withRuns(List(out)).head

  // ── hints ──────────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  def hints(id: ManagerLight.Id, all: Boolean): Either[Api.Error, List[ManagerHint]] =
    manager(id).map(m => Transactor(ds).read(ManagerRepo.hints(m.id, live = !all)))

  /** A hint: its text reaches the next runs' fixer and skeptic as the person's notes (a once-hint the next run only).
    * Its structured effect, if any, is applied now — prioritise, hold or skip the targets it names or matches (pending
    * and held items; a named target not yet an item is added) — when it is the person's own; a MODEL's (`propose`: the
    * MCP tool, which a model drives) is recorded as a `hint-effect` approval and applied only when a person approves it
    * ([[applyProposal]]). `suggest` asks the server's planner model to read the text and propose the action (a
    * `hint-effect` run, started for the person; its proposal waits for approval the same way).
    */
  def addHint(id: ManagerLight.Id, in: HintAdd)(using Creator.New): Either[Api.Error, ManagerHint] =
    held(WriteGate.OperationName("add_hint"))(addHintHeld(id, in))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def addHintHeld(id: ManagerLight.Id, in: HintAdd)(using
      WritePermit
  )(using Creator.New): Either[Api.Error, ManagerHint] =
    val propose = in.propose.exists(_.value)
    val suggest = in.suggest.exists(_.value)
    for
      m <- manager(id)
      text <- Option(in.text.value.trim)
        .filter(_.nonEmpty)
        .map(ManagerHint.Text(_))
        .toRight(error(Problem.Invalid.Missing.here(Problem.Field("text"))))
      item <- in.item
        .map(_.value.trim)
        .filter(_.nonEmpty)
        .fold[Either[Api.Error, Option[ManagerItem]]](Right(None))(r => itemOf(m, ManagerItem.Ref(r)).map(Some(_)))
      eff <- in.effect.fold[Either[Api.Error, Option[HintEffect]]](Right(None))(e =>
        checkEffect(m, e, item, propose).map(Some(_))
      )
      _ <- Either.cond(
        !(suggest && eff.isDefined),
        (),
        error(
          Problem.Invalid.Inconsistent
            .here(
              NonEmptyVector.of(Problem.Field("suggest"), Problem.Field("effect")),
              Problem.Invalid.Exclusion.AtMostOne
            )
        )
      )
      // a suggestion needs a run that can start: checked before the hint is recorded
      _ <-
        if suggest then
          agents
            .prepare(
              RunRequest(RunRequest.Pipeline(Pipeline.HintEffect.wire.value), RunRequest.Target(m.id.value)),
              None
            )
            .map(_ => ())
        else Right(())
      author = ManagerHint.Author(in.author.map(_.value.trim).filter(_.nonEmpty).getOrElse("person"))
      hint = Transactor(ds).write(
        ManagerRepo.addHint(
          m.id,
          item.map(i => ManagerHint.ItemId(i.id.value)),
          text,
          if propose then None else eff,
          author,
          standing = ManagerHint.Standing(!in.once.exists(_.value))
        )
      )
      applied = eff.fold("")(e =>
        if propose then proposeEffect(m, hint.id, text, e, author) else applyEffect(m, hint.id, text, e)
      )
      asked <-
        if suggest then
          agents
            .startRunWith(
              RunRequest(RunRequest.Pipeline(Pipeline.HintEffect.wire.value), RunRequest.Target(m.id.value)),
              Origin.Person,
              None,
              _.modify(_.hint).setTo(Some(RunParams.Hint(hint.id.value)))
            )
            .map(r =>
              s" — a model reads it for an action (run ${r.id.value.take(8)}); a person approves what it proposes"
            )
        else Right("")
      _ = record(
        m.id,
        item.map(_.id),
        None,
        Feed.Hint,
        s"hint #${hint.id} (${if hint.standing.value then "standing" else "once"}${item.fold("")(i => s", ${i.target}")}): ${text.value.take(300)}$applied$asked"
      )
      out <- Transactor(ds)
        .read(ManagerRepo.hint(hint.id))
        .toRight(notFound(Problem.NotFound.Entity(Problem.TypeName.of(ManagerHint.Id), Problem.Raw.of(hint.id))))
    yield
      supervisor.nudge()
      out

  /** A structured effect checked against manager `m`: an action it knows (a model may also propose `config`), targets
    * (or a tag, topic, goal, or the item the hint is about), a hold's date or source, a settings change that parses and
    * that the manager's kind accepts.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def checkEffect(
      m: ManagerInfo,
      e: HintEffect,
      item: Option[ManagerItem],
      proposal: Boolean
  ): Either[Api.Error, HintEffect] =
    val allowed = if proposal then HintEffect.Verb.Proposable else HintEffect.Verb.Actions
    val verb = HintEffect.Action.verb(e.action)
    def oneOf = allowed.map(_.word).mkString(", ")
    verb.filter(allowed.contains) match
      case None =>
        Left(
          error(
            if !proposal && verb.contains(HintEffect.Verb.Config) then
              Problem.Invalid.Broken.here(
                Problem.Invalid.Rule.HintConfig(allowed.toVector.map(v => Problem.Raw.of(v.word)))
              )
            else
              Problem.Invalid.Unknown.here(
                Problem.TypeName.of(HintEffect.Action),
                Problem.Raw.of(e.action),
                allowed.toVector.map(v => Problem.Raw.of(v.word))
              )
          )
        )
      case Some(HintEffect.Verb.Config) =>
        e.config
          .filter(c => ManagersMarkdown.configLine(c) != "the defaults")
          .toRight(Api.Error(Problem.Invalid.Broken.here(Problem.Invalid.Rule.HintSettings)))
          .flatMap(c =>
            validate(kindOf(m), m.config.merge(c)).map(_ => e.modify(_.action).setTo(HintEffect.Verb.Config.word))
          )
      case Some(_) if e.targets.isEmpty && e.tag.isEmpty && e.topic.isEmpty && e.goal.isEmpty && item.isEmpty =>
        Left(
          error(
            Problem.Invalid.Broken.here(Problem.Invalid.Rule.HintTargets)
          )
        )
      case Some(HintEffect.Verb.Hold) if e.until.isEmpty && e.untilSource.isEmpty =>
        Left(
          Api.Error(
            Problem.Invalid.Broken.here(Problem.Invalid.Rule.HintUntil)
          )
        )
      case Some(_) if e.until.exists(u => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(u.value)).isEmpty) =>
        Left(
          error(
            Problem.Invalid.Unreadable.here(
              Problem.TypeName.of(HintEffect.Until),
              Problem.Raw(e.until.fold("")(_.value)),
              Vector(Problem.Raw("2026-11-01"))
            )
          )
        )
      case Some(v) =>
        Right(
          e.modify(_.action)
            .setTo(v.word)
            .modify(_.targets)
            .setTo((e.targets ++ item.map(i => HintEffect.Targets(i.target.value)).toList).distinct)
        )

  /** A model's effect, recorded for a person: an open `hint-effect` approval about the manager. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def proposeEffect(
      m: ManagerInfo,
      hintId: ManagerHint.Id,
      text: ManagerHint.Text,
      e: HintEffect,
      by: ManagerHint.Author
  )(using WritePermit)(using Creator.New): String =
    val a = agents.approvalStore.openSubject(
      None,
      ApprovalKind.HintEffect,
      ApprovalQuery.Subject(m.id.value),
      ApprovalService.Payload(
        HintProposal(
          m.id,
          HintProposal.Hint(hintId.value),
          HintProposal.Text(text.value),
          e,
          HintProposal.By(by.value)
        ).asJson.noSpaces
      )
    )
    s" — proposed ${ManagersMarkdown.effect(e)}: approval #${a.id} waits for a person"

  /** A `hint-effect` approval a person approved: the action is checked again against the manager as it is now and
    * applied — items prioritised, held or skipped, or the settings changed — and recorded on the hint. Left = why it
    * could not be (the approval is then marked failed with it).
    */
  def applyProposal(p: HintProposal): Either[Problems, String] = agents.maintenance.writing(applyProposalHeld(p))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def applyProposalHeld(p: HintProposal)(using WritePermit): Either[Problems, String] =
    for
      m <- manager(p.managerId.transformInto[ManagerLight.Id]).left.map(_.problems)
      ht <- Transactor(ds)
        .read(ManagerRepo.hint(ManagerHint.Id(p.hint.value)))
        .filter(_.managerId == m.id)
        .toRight(Problem.NotFound.Entity(Problem.TypeName.of(ManagerHint.Id), Problem.Raw.of(p.hint)))
      _ <- Either.cond(
        ht.retiredAt.isEmpty,
        (),
        Problem.Conflict.WrongState(
          Problem.TypeName.of(ManagerHint.Id),
          Problem.Raw.of(ht.id),
          Problem.Conflict.State("retired"),
          Vector(Problem.Conflict.State("active"))
        )
      )
      e <- checkEffect(
        m,
        p.effect,
        ht.itemId.flatMap(i => agents.managerStore.item(ManagerItem.Id(i.value))),
        proposal = true
      ).left.map(_.problems)
      w <- HintEffect.Action.verb(e.action) match
        case Some(HintEffect.Verb.Config) =>
          val c = m.config.merge(e.config.getOrElse(ManagerConfig()))
          // a manager removed since it was read is not found, never settings written to nothing
          Option
            .when(
              agents.managerStore.setConfig(
                m.id,
                c,
                m.cron,
                m.nextTickAt.flatMap(t => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(t.value)))
              )
            )(s" — settings now: ${ManagersControllers.describe(c)}")
            .toRight(Problem.NotFound.Entity(Problem.TypeName.of(ManagerLight.Id), Problem.Raw.of(m.id)))
        case _ => Right(applyEffect(m, ht.id, ht.text, e))
      _ = Transactor(ds).write(ManagerRepo.setHintEffect(ht.id, Some(e)))
      _ = record(
        m.id,
        ht.itemId.map(i => ManagerItem.Id(i.value)),
        None,
        Feed.Hint,
        s"hint #${ht.id}: the proposed ${ManagersMarkdown.effect(e)} approved by a person$w".take(2000)
      )
    yield
      supervisor.nudge()
      s"${m.id} hint #${ht.id}: ${ManagersMarkdown.effect(e)}$w"

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def applyEffect(m: ManagerInfo, hintId: ManagerHint.Id, text: ManagerHint.Text, e: HintEffect)(using
      WritePermit
  ): String =
    // the named targets become items of the manager's default kind (a target that exists); the matched
    // ones are the open items
    val kind = itemKinds(kindOf(m)).headOption.getOrElse(ManagerItem.Kind(ArticleReview))
    val named = e.targets
      .flatMap(t =>
        targetOf(m, ManagerContext.ItemKind(kind.value), ManagerItem.Target(t.value)).toOption
          .map(_.transformInto[ManagerItem.Target])
      )
      .distinct
    val open = connect(ds)(ManagerRepo.withStatus(m.id, ItemStatus.Pending)) ++ connect(ds)(
      ManagerRepo.withStatus(m.id, ItemStatus.Held)
    )
    val matched = open.filter { i =>
      named.contains(i.target) || ((e.tag.isDefined || e.topic.isDefined || e.goal.isDefined) && {
        val (tags, goals) = Slug
          .option(i.target.value)
          .fold((Nil, Nil))(slug => Transactor(ds).read(ManagerRepo.articleFacets(slug)))
        ManagerPolicy.matches(
          e.modify(_.targets).setTo(Nil),
          i.target.transformInto[ManagerPolicy.Candidate.Target],
          tags.map(t => ManagerPolicy.Tag(t.value)),
          Nil,
          goals
        )
      })
    }
    val n = HintEffect.Action.verb(e.action) match
      case Some(HintEffect.Verb.Prioritise) =>
        val p = e.priority.fold(ManagerSupervisor.HintPriority)(_.value)
        val added = named.filterNot(t => open.exists(_.target == t)).count { t =>
          agents.managerStore.upsert(m.id, kind, t, ItemOrigin.Hint, ManagerItem.Priority(p), reopen = true)
          true
        }
        matched.foreach(i =>
          Transactor(ds).write(
            Unused(
              ManagerRepo.setPriority(i.id, ManagerItem.Priority(math.max(i.priority.value, p))),
              Unused.Why.Idempotent
            )
          )
        )
        matched.size + added
      case Some(HintEffect.Verb.Hold) =>
        val hold = Hold(
          e.until.map(u => Hold.Until(u.value)),
          e.untilSource.map(u => Hold.UntilSource(u.value)),
          Managers.Rationale(s"hint #$hintId: ${text.value.take(200)}"),
          Some(Hold.Hint(hintId.value))
        )
        val added = named.filterNot(t => open.exists(_.target == t)).flatMap { t =>
          val (id, _) =
            agents.managerStore.upsert(m.id, kind, t, ItemOrigin.Hint, ManagerItem.Priority(0), reopen = true)
          agents.managerStore.item(id).filter(_.status == ItemStatus.Pending.wire)
        }
        (matched ++ added).count(i => Transactor(ds).write(ManagerRepo.hold(i.id, hold)))
      case Some(HintEffect.Verb.Skip) =>
        matched.count(i =>
          Transactor(ds).write(
            ManagerRepo.settle(
              i.id,
              ItemStatus.Stopped,
              ManagerItem.Outcome(s"skipped (hint #$hintId)"),
              List(ItemStatus.Pending, ItemStatus.Held)
            )
          )
        )
      case _ => 0
    s" — ${e.action}: ${Plural.count(Plural.CountN(n.toLong), Plural.CountSingular("item"))}"

  def deleteHint(id: ManagerLight.Id, hint: ManagerHint.Id): Either[Api.Error, Removed] =
    held(WriteGate.OperationName("delete_hint"))(deleteHintHeld(id, hint))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def deleteHintHeld(id: ManagerLight.Id, hint: ManagerHint.Id)(using WritePermit): Either[Api.Error, Removed] =
    manager(id).map { m =>
      val gone = Transactor(ds).write(ManagerRepo.retireHint(m.id, hint))
      if gone then record(m.id, None, None, Feed.Hint, s"hint #$hint retired by a person")
      Removed(Removed.Value(gone))
    }

  // ── goal schedules: a watcher's sweeps (phase 3) ───────────────────────────────────────────────

  /** A goal's schedules (every goal's: None) as the goal surfaces show them — its watcher's sweeps
    * ([[WatchSchedules.view]]), and any `goal_schedules` row the managers have not moved yet.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def goalSchedules(goal: Option[DomainGoal.Slug]): List[Goal.Plans.GoalSchedule] =
    agents.managerStore
      .list()
      .filter(m => m.kind == Kind.Watch.wire && goal.forall(g => m.goal.exists(_.value == g.value)))
      .flatMap(m =>
        WatchSchedules.view(m, Transactor(ds).read(ManagerRepo.itemPage(m.id, None, Offset(0), PageSize(20)))._1)
      ) ++ agents.goals.schedules(goal)

  /** `PUT /api/goals/{slug}/schedules/{kind}` — since phase 3 a goal's schedule is a sweep of its watcher
    * `watch:<goal>` (created PAUSED when it does not exist: resume it for the schedule to fire). `enabled` puts the
    * sweep in (or takes it out of) the watcher's sweeps; the cron (checked, no more often than
    * `agents.scheduler.min-interval-minutes`) is the watcher's, shared by its sweeps; a budget given is its per-run
    * cap.
    */
  def setSchedule(
      goal: DomainGoal.Slug,
      kind: Goal.Plans.ScheduleKind,
      in: Goal.Plans.ScheduleSet,
      now: java.time.Instant
  )(using Creator.New): Either[Api.Error, Goal.Plans.GoalSchedule] =
    held(WriteGate.OperationName("set_schedule"))(setScheduleHeld(goal, kind, in, now))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def setScheduleHeld(
      goal: DomainGoal.Slug,
      kind: Goal.Plans.ScheduleKind,
      in: Goal.Plans.ScheduleSet,
      now: java.time.Instant
  )(using WritePermit)(using Creator.New): Either[Api.Error, Goal.Plans.GoalSchedule] =
    val id = watchId(Goal.Ref(goal.value))
    val sw = WatchSchedules.sweepOf(kind)
    val budget = in.budgetUsd.map(b => ManagerConfig.BudgetUsd(b.value))
    for
      cron <- ManagerPolicy
        .checkCron(
          Cron.Text(in.cron.value),
          now,
          cfg.scheduler.minIntervalMinutes.transformInto[ManagerPolicy.MinIntervalMinutes]
        )
        .left
        .map(p => error(p))
      _ = agents.managerStore.get(id) match
        case None =>
          val c = ManagerConfig(sweeps = Some(if in.enabled.value then List(sw.wire) else Nil), budgetUsd = budget)
          Unused(
            agents.managerStore.create(
              id,
              Kind.Watch,
              Some(goal),
              State.Paused,
              c,
              Some(ManagerInfo.Cron(cron.text.value)),
              cron.next(now)
            ),
            Unused.Why.Held
          )
          record(
            id,
            None,
            None,
            Feed.Paused,
            s"created paused by the goal's ${kind.wire} schedule " +
              s"(cron `${cron.text}`): resume it for its sweeps to fire"
          )
        case Some(m) =>
          val cur = Sweep.of(m.config)
          val sweeps = Sweep.values.toList.filter(x => if x == sw then in.enabled.value else cur.contains(x))
          val c = m.config
            .modify(_.sweeps)
            .setTo(Some(sweeps.map(_.wire)))
            .modify(_.budgetUsd)
            .setTo(budget.orElse(m.config.budgetUsd))
          val next = if m.cron.exists(_.value == cron.text.value) && m.nextTickAt.isDefined then
            m.nextTickAt.flatMap(t => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(t.value)))
          else cron.next(now)
          // the manager is read back below (`manager(id)`): one removed meanwhile is not found there
          Unused(
            agents.managerStore.setConfig(id, c, Some(ManagerInfo.Cron(cron.text.value)), next),
            Unused.Why.ReadBack
          )
          if !in.enabled.value then cancelSweep(m, sw, ManagerItem.Outcome("its schedule was turned off"))
          record(
            id,
            None,
            None,
            Feed.Hint,
            s"the goal's ${kind.wire} schedule set: cron `${cron.text}`, " +
              s"${if in.enabled.value then "on" else "off"}; sweeps ${
                  if sweeps.isEmpty then "none" else sweeps.map(_.wire).mkString(", ")
                }"
          )
      _ = agents.goals.removeSchedule(
        goal,
        kind.transformInto[ScheduleKind]
      ) // a row the managers had not moved yet is replaced
      m <- manager(id.transformInto[ManagerLight.Id])
    yield
      supervisor.nudge()
      WatchSchedules
        .view(m, Transactor(ds).read(ManagerRepo.itemPage(id, None, Offset(0), PageSize(20)))._1)
        .find(_.kind == kind)
        .getOrElse(
          Goal.Plans.GoalSchedule(
            goal.transformInto[Goal.Slug],
            kind,
            Goal.Plans.GoalSchedule.Cron(cron.text.value),
            Goal.Plans.GoalSchedule.Enabled(false),
            in.budgetUsd.map(_.transformInto[Goal.Plans.GoalSchedule.BudgetUsd])
          )
        )

  /** `DELETE /api/goals/{slug}/schedules/{kind}` — the sweep leaves the watcher (its open item is cancelled); the
    * watcher itself stays.
    */
  def removeSchedule(goal: DomainGoal.Slug, kind: Goal.Plans.ScheduleKind): Goal.Plans.Removed =
    agents.maintenance.writing(removeScheduleHeld(goal, kind))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def removeScheduleHeld(goal: DomainGoal.Slug, kind: Goal.Plans.ScheduleKind)(using
      WritePermit
  ): Goal.Plans.Removed =
    val legacy = agents.goals.removeSchedule(goal, kind.transformInto[ScheduleKind])
    val sw = WatchSchedules.sweepOf(kind)
    val fromWatcher =
      agents.managerStore.get(watchId(Goal.Ref(goal.value))).filter(m => Sweep.of(m.config).contains(sw)).exists { m =>
        val rest = Sweep.of(m.config).filterNot(_ == sw)
        // a watcher removed since it was read had no schedule left to remove
        val removed = agents.managerStore.setConfig(
          m.id,
          m.config.modify(_.sweeps).setTo(Some(rest.map(_.wire))),
          m.cron,
          m.nextTickAt.flatMap(t => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(t.value)))
        )
        if removed then
          cancelSweep(m, sw, ManagerItem.Outcome("its schedule was removed"))
          record(
            m.id,
            None,
            None,
            Feed.Hint,
            s"the goal's ${kind.wire} schedule removed; sweeps " +
              (if rest.isEmpty then "none" else rest.map(_.wire).mkString(", "))
          )
        removed
      }
    Goal.Plans.Removed(Goal.Plans.Removed.Value(legacy || fromWatcher))

  /** A sweep the watcher no longer runs: its pending or held item is cancelled (a running one finishes). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def cancelSweep(m: ManagerInfo, sw: Sweep, why: ManagerItem.Outcome)(using WritePermit): Unit =
    m.goal
      .flatMap(g => agents.managerStore.itemFor(m.id, ManagerItem.Kind(sw.itemKind.value), ManagerItem.Target(g.value)))
      .foreach { i =>
        if Transactor(ds).write(
            ManagerRepo.settle(
              i.id,
              ItemStatus.Cancelled,
              why,
              List(ItemStatus.Pending, ItemStatus.Held)
            )
          )
        then record(m.id, Some(i.id), None, Feed.Stopped, s"${i.target} (${i.kind}): $why")
      }

  // ── the autopilot ──────────────────────────────────────────────────────────────────────────────

  /** `GET /api/agents/autopilot` — readable with agents off. */
  def autopilot: Autopilot.EngineStatus = agents.autopilot.status()

  /** `GET /api/agents/autopilot/focus` — readable with agents off (what it would focus on). */
  def focus: Autopilot.FocusInfo = agents.focus.info()

  /** `GET /api/agents/scheduler[?limit=]` — readable with agents off (a dry pass). */
  def scheduler(limit: Option[PageSize]): AgentScheduler.SchedulerStatus = agents.scheduler.status(limit)

  /** `POST /api/agents/scheduler {strategy}` — switch the strategy live. */
  def setScheduler(in: AgentScheduler.SchedulerSet): Either[Api.Error, AgentScheduler.SchedulerStatus] =
    held(WriteGate.OperationName("set_scheduler"))(agents.scheduler.set(in))

  /** `POST /api/agents/autopilot/drain {on}`. */
  def drain(in: Autopilot.DrainSet): Either[Problems, Autopilot.EngineStatus] =
    Right(agents.maintenance.writing(agents.drain.set(in)))

  /** `GET /api/agents/stalls[?days=]` — readable with agents off. */
  def stalls(days: Option[AgentAutopilotConfig.StallDays]): Stalls.StallReport = agents.scheduler.stalls(days)

  /** `GET /api/agents/approvals/digest`. */
  def digest: ApprovalDigest.Digest = agents.digest.digest()

  /** `POST /api/agents/approvals/decide` — a whole group at once. */
  def decideAll(in: ApprovalDigest.BulkDecide)(using Creator.New): Either[Api.Error, ApprovalDigest.BulkResult] =
    agents.digest.decideAll(in)

  /** `GET /api/agents/scheduler/why/{item}`. */
  def schedulerWhy(item: ManagerItem.Ref): Either[Api.Error, AgentScheduler.WhyInfo] = agents.scheduler.why(item)

  /** `POST /api/agents/autopilot/focus {action, goal?, value?}` — pin, unpin, priority, max. */
  def setFocus(in: Autopilot.FocusSet): Either[Api.Error, Autopilot.FocusInfo] =
    held(WriteGate.OperationName("set_focus"))(agents.focus.set(in))

  /** `POST /api/agents/autopilot/mode {mode}` — the three-way control ([[AgentMode]]): the autopilot and the drain
    * moved, in order, to what the mode stands for. Idempotent.
    */
  def setMode(in: AgentMode.ModeSet)(using Creator.New): Either[Api.Error, Autopilot.EngineStatus] =
    held(WriteGate.OperationName("set_mode"))(setModeHeld(in))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def setModeHeld(
      in: AgentMode.ModeSet
  )(using WritePermit)(using Creator.New): Either[Api.Error, Autopilot.EngineStatus] =
    AgentMode
      .parse(in.mode)
      .toRight(
        error(
          Problem.Invalid.Unknown.here(
            Problem.TypeName.of(AgentMode.ModeSet.Mode),
            Problem.Raw.of(in.mode),
            AgentMode.values.toVector.map(v => Problem.Raw.of(v.wire))
          )
        )
      )
      .map { to =>
        val now = agents.autopilot.status()
        val by = in.by.map(_.value.trim).filter(_.nonEmpty).orElse(Some("person"))
        AgentMode
          .steps(AgentMode.Step.Autopilot.On(now.on.value), AgentMode.Step.Drain.On(now.drain.isDefined), to)
          .foldLeft(now) { (_, step) =>
            step match
              case AgentMode.Step.Autopilot(on) =>
                agents.autopilot.set(
                  Autopilot.AutopilotSet(Autopilot.AutopilotSet.On(on.value), by.map(Autopilot.AutopilotSet.By(_)))
                )
              case AgentMode.Step.Drain(on) =>
                agents.drain.set(Autopilot.DrainSet(Autopilot.DrainSet.On(on.value), by.map(Autopilot.DrainSet.By(_))))
          }
      }

  /** `POST /api/agents/autopilot {on}` — idempotent; the answer says what it did. */
  def setAutopilot(in: Autopilot.AutopilotSet)(using Creator.New): Either[Problems, Autopilot.EngineStatus] =
    Right(agents.maintenance.writing(agents.autopilot.set(in)))

  // ── events ─────────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def events(
      id: ManagerLight.Id,
      before: Option[ManagerRepo.EventCursor],
      limit: Option[PageSize]
  ): Either[Api.Error, EventPage] =
    manager(id).map { m =>
      val lim = limits.page(limit, PageSize(50))
      val es = connect(ds)(ManagerRepo.events(m.id, before, lim))
      EventPage(es, Option.when(es.size == lim.value)(EventPage.Before(es.last.id.value)))
    }

object ManagersControllers:

  /** What a person does to a manager: `pause`, `resume` or `stop`. */
  type Action = Action.T
  object Action extends Newtype[String, Pure]

  def describe(c: ManagerConfig): String = ManagersMarkdown.configLine(c)
