package com.histmersion
package domain.automation.scheduler
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.managers.{ManagerDefaultsViews, ManagerLight, ManagerSupervisor}

import com.histmersion.api.Write
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.{ApprovalState, Billing, PauseReason, Pipeline, Role, RunState}
import com.histmersion.api.automation.Managers.*
import com.histmersion.api.automation.Queue.QueueAdd
import com.histmersion.domain.SchedulerPolicy.*
import com.histmersion.api.automation.AgentScheduler.*

import java.time.Instant
import javax.sql.DataSource
import com.histmersion.api.automation.{Agents, AgentScheduler, ApprovalDigest, PlanQuota, Queue, Stalls}
import com.histmersion.api.Api
import com.histmersion.api.automation.Goal
import com.histmersion.domain.automation.autopilot.AutopilotRepo
import com.histmersion.domain.automation.managers.ManagerContext
import com.histmersion.domain.automation.{Goal as DomainGoal, Run as DomainRun}
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import com.histmersion.util.jobs.Job
import com.histmersion.util.jobs.RunRow
import com.histmersion.api.automation.Run
import com.histmersion.domain.automation.worklists.WorkListControllers

/** The stall report tells a wait by its kind of reason only; the sentence is made from the case. */
private given waitWhyToStall: Transformer[SchedulerPolicy.Block.Why, Stalls.SchedWait.Why] =
  why =>
    why match
      case SchedulerPolicy.Block.Why.Budget(cap) => Stalls.SchedWait.Why.Budget(Stalls.SchedWait.Detail(cap.render))
      case SchedulerPolicy.Block.Why.Starved(c)  => Stalls.SchedWait.Why.Starved(Stalls.SchedWait.Detail(s"$c"))
      case other                                 => Stalls.SchedWait.Why.Other(Stalls.SchedWait.Detail(other.render))

/** The central agent scheduler (docs/research-goal-advancement.md "As built"): the managers PRODUCE pending items; once
  * per managers' tick [[pass]] reads them all — with each goal's priority and age, the plan pools each would draw on,
  * what blocks it, the runs that occupy a place — and [[SchedulerPolicy.run]] decides which start, calling the
  * supervisor's dispatch for each. The pass is kept in memory for the explanations ([[status]], [[why]]); with no pass
  * yet (agents off, a fresh start) a DRY pass shows what one would do, starting nothing.
  *
  * It replaces the per-manager slot caps (a manager's own saved `maxRuns` still caps it, counting only the runs that
  * run or are queued — never one waiting for a person) and the focus's slots (the focus no longer holds anything; its
  * pins and priorities are the goals' priorities here).
  */
final class SchedulerService(
    ds: DataSource,
    cfg: => AgentsConfig,
    agents: AgentsControllers
):

  /** A person's choice of strategy (name, by, at), if any. */
  def chosen: Option[(AutopilotRepo.StrategyName, AutopilotRepo.Row.ChangedBy, AutopilotRepo.Row.ChangedAt)] =
    agents.autopilotRow.strategy()

  private def instantOf(text: String): Option[Instant] = ManagerPolicy.parseInstant(ManagerPolicy.InstantText(text))

  /** The model a manager's override names for `role`, if any. */
  private def modelOf(over: List[Agents.ModelOverride], role: Role): Option[AgentsControllers.ModelName] =
    over.find(o => Role.parse(o.role).contains(role)).map(o => AgentsControllers.ModelName(o.model.value))

  /** The settings in effect now. */
  def settings: Settings = SchedulerPolicy.settings(
    ManagerDefaultsViews.scheduler(cfg.scheduler),
    chosen.map(c => SchedulerPolicy.StrategyName(c._1.value))
  )

  /** One pass as shown: when, whether it was dry, its inputs and its decision. */
  final private case class Snapshot(at: Instant, dry: Boolean, in: Inputs, s: Settings, d: Decision)

  /** The last real pass (a `var`: the supervisor's tick writes it, the status reads it). */
  @volatile private var last: Option[Snapshot] = None

  /** One goal as the scheduler sees it: its priority (a pin added), when it was created, whether it is still gathering
    * its sources.
    */
  final private case class GoalInfo(
      priority: Int,
      created: Option[Instant],
      gathering: Boolean,
      inProgress: Option[String]
  )

  private def goals(): Map[DomainGoal.Slug, GoalInfo] =
    agents.focus
      .facts()
      .map(f =>
        f.slug -> GoalInfo(
          f.priority.value + (if f.pinned.value then PinBoost else 0),
          instantOf(f.createdAt.value),
          !GoalFocus.finished(f.status) && GoalFocus
            .stage(f, GoalFocus.Settings.MinSources(agents.sourcesToPlan.value)) == GoalFocus.Stage.Gathering,
          AutopilotPolicy
            .articlesInProgress(
              AutopilotPolicy.NextFacts.Unreviewed(f.unreviewed.value),
              AutopilotPolicy.NextFacts.CompileOpen(f.compileOpen.value)
            )
            .map(_.value)
        )
      )
      .toMap

  /** The inputs of one pass: the candidates (with their manager and item), the resources. */
  final case class Inputs(cands: List[Candidate], items: Map[Candidate.Id, (ManagerInfo, ManagerItem)], res: Resources)

  /** Why nothing may start for any manager now (a spend cap reached), if so. */
  def budgetBlock(): Option[Block] =
    val (day, month) = agents.runs.spendDayMonth()
    if day >= BigDecimal(cfg.budgets.dailyUsd.value) then
      Some(
        Block(Block.Why.Budget(Block.SpendCap.Daily(Block.Usd(BigDecimal(cfg.budgets.dailyUsd.value)))))
      )
    else if month >= BigDecimal(cfg.budgets.monthlyUsd.value) then
      Some(
        Block(Block.Why.Budget(Block.SpendCap.Monthly(Block.Usd(BigDecimal(cfg.budgets.monthlyUsd.value)))))
      )
    else None

  /** The plan pools the roles of `pipelines` draw on with `over` (a manager's model overrides) — picked without
    * spending (no plan reading is taken): Left = a role's chain cannot run now (why); Right(Nil) = a metered model in
    * the mix, or nothing known (no pool limit).
    */
  def poolIds(
      over: List[Agents.ModelOverride],
      now: Instant,
      pipelines: List[Pipeline]
  ): Either[Block.Chain, List[PlanQuota.PoolId]] =
    val roles = pipelines.flatMap(_.roles).distinct
    val picks = roles.map { r =>
      r -> agents.picker.pick(r, modelOf(over, r), PlanQuotaRepo.EpochMillis(now.toEpochMilli), refresh = None)
    }
    picks.collectFirst { case (r, Left(f)) => (r, f) } match
      case Some((r, PickFailure.Exhausted(_, at, why))) =>
        Left(
          Block.Chain(
            s"${r.wire} chain ${if why == PauseReason.PlanQuota then "below its plan floor" else "out of allowance"}" +
              at.fold("")(t => s"; frees ~$t")
          )
        )
      case Some((r, PickFailure.Unavailable(msg))) => Left(Block.Chain(s"${r.wire}: ${msg.sentence.take(200)}"))
      case _                                       =>
        val plans = picks.collect { case (_, Right(p)) => p.billing }
        val pools = plans.collect { case Billing.Plan(p) => p }.distinct
        Right(if pools.nonEmpty && plans.forall(_.isInstanceOf[Billing.Plan]) then pools else Nil)

  private def poolCap(p: PlanQuota.PoolId): Int =
    val calls = p match
      case PlanQuota.PoolId.ClaudePlan  => cfg.quota.claudePlan.maxCallsInFlight.value
      case PlanQuota.PoolId.ChatgptPlan => cfg.quota.chatgptPlan.maxCallsInFlight.value
    math.max(1, calls) * math.max(1, cfg.scheduler.poolOvercommit.value)

  /** What one pass reads: every due pending item of `ms` (the active managers that may start runs) at `now`, the goals,
    * the blocks, the runs that occupy a place; `stop` = why nothing starts at all (draining).
    */
  def inputs(ms: List[ManagerInfo], now: Instant, stop: Option[Block]): Inputs =
    val gs = goals()
    val perManager = ms.map { m =>
      m -> agents.managerStore
        .pending(m.id)
        .filterNot(_.kind.value == OaIngest)
        .filter(i => i.notBefore.flatMap(n => instantOf(n.value)).forall(!_.isAfter(now)))
    }
    def targetsOf(id: ManagerInfo.Id) =
      perManager.filter(_._1.id == id).flatMap(_._2.map(t => Goal.Plans.GoalLink.Ref(t.target.value)))
    val articleGoals = agents.focusStore.goalsOf(DomainGoal.LinkKind.Article.wire, targetsOf(ManagerInfo.Id(ReviewId)))
    val sourceGoals = agents.focusStore.goalsOf(DomainGoal.LinkKind.Source.wire, targetsOf(ManagerInfo.Id(EnrichId)))

    /** The goal an item advances: its manager's, else the best of the goals its target is linked to. */
    def goalOf(m: ManagerInfo, i: ManagerItem): Option[DomainGoal.Slug] =
      m.goal.map(g => DomainGoal.Slug.assume(g.value)).orElse {
        val ref = Goal.Plans.GoalLink.Ref(i.target.value)
        val linked = if m.id.value == ReviewId then articleGoals.getOrElse(ref, Nil)
        else if m.id.value == EnrichId then sourceGoals.getOrElse(ref, Nil)
        else Nil
        linked
          .filter(gs.contains)
          .minByOption(g => (-gs(g).priority, gs(g).created.fold(Long.MaxValue)(_.toEpochMilli), g.value))
          .orElse(linked.headOption)
      }
    val plansWait =
      if perManager.exists(_._2.exists(_.kind.value == PlanGoalItem)) then agents.focus.goalPlansWait() else None
    val cands = perManager.flatMap { (m, items) =>
      val daily = ManagerPolicy.dailyBlock(
        ManagerPolicy.dailyCap(m.config, ManagerDefaultsViews.defaults(cfg.managers)),
        BigDecimal(m.spentTodayUsd.value)
      )
      val open =
        if items.exists(i => i.kind.value == ArticleReview || i.kind.value == ProposeArticles) then
          agents.managerStore
            .openKeys(m.id)
            .map((k, t) => (ManagerPolicy.ItemKind(k.value), t.transformInto[ManagerPolicy.Candidate.Target]))
        else Nil
      val over = m.config.models.getOrElse(Nil)
      val memo =
        collection.mutable.Map.empty[Pipeline, Either[Block.Chain, List[PlanQuota.PoolId]]] // per manager and pass
      items.map { i =>
        val itemKind = ManagerContext.ItemKind(i.kind.value)
        val pipeline = agents.managers.pipelineOf(itemKind)
        val pools =
          if daily.isDefined then Right(Nil) else memo.getOrElseUpdate(pipeline, poolIds(over, now, List(pipeline)))
        val block = daily
          .map(d => Block(d))
          .orElse(pools.left.toOption.map(chain => Block(Block.Why.Starved(chain))))
          .orElse(
            Option.when(ManagerPolicy.waitsFor(itemKind, i.target.transformInto[ManagerPolicy.Candidate.Target], open))(
              Block(
                Block.Why.WaitsFor(
                  if i.kind.value == ProposeArticles then Block.Precondition.Plan else Block.Precondition.Filter
                )
              )
            )
          )
          // #3763: no new proposal while the goal's articles are in progress (a person's enqueue goes all the same)
          .orElse(
            Option
              .when(i.kind.value == ProposeArticles && i.origin != ItemOrigin.Person.wire)(
                goalOf(m, i).flatMap(gs.get).flatMap(_.inProgress)
              )
              .flatten
              .map(d => Block(Block.Why.ArticlesInProgress(Block.Detail(d))))
          )
          // no new goal plan while too many planned goals are unfinished (a person's enqueue goes all the same)
          .orElse(
            Option
              .when(i.kind.value == PlanGoalItem && i.origin != ItemOrigin.Person.wire)(plansWait)
              .flatten
              .map(d => Block(Block.Why.UnfinishedGoals(Block.Detail(d.value))))
          )
        val goal = goalOf(m, i)
        val info = goal.flatMap(gs.get)
        val since = List(
          instantOf(i.updatedAt.value),
          i.notBefore.flatMap(n => instantOf(n.value))
        ).flatten.maxOption
        Candidate(
          Candidate.Id(i.id.value),
          Candidate.ManagerId(m.id.value),
          Candidate.Kind(i.kind.value),
          i.target.transformInto[Candidate.Target],
          goal.map(g => Candidate.Goal(g.value)),
          Candidate.GoalPriority(info.fold(0)(_.priority)),
          info.flatMap(_.created),
          Candidate.Gathering(info.exists(_.gathering)),
          Candidate.ItemPriority(i.priority.value),
          since.getOrElse(now),
          pools.toOption.getOrElse(Nil).map(p => Candidate.Pools(p.wire.value)),
          block,
          Candidate.Person(i.origin == ItemOrigin.Person.wire)
        )
      }
    }
    val runs = agents.managerStore.occupyingRuns()
    val pipePools = collection.mutable.Map.empty[Pipeline, List[PlanQuota.PoolId]] // per pass
    def poolsOfRun(w: Pipeline.Wire): List[PlanQuota.PoolId] =
      Pipeline.parse(w).toList.flatMap(p => pipePools.getOrElseUpdate(p, poolIds(Nil, now, List(p)).getOrElse(Nil)))
    val caps = ms.flatMap(m => m.config.maxRuns.map(r => m.id -> Resources.ManagerCapValue(r.value))).toMap
    val res = Resources(
      maxRuns = Resources.MaxRuns(cfg.maxConcurrentRuns.value),
      occupied = Resources.Occupied(
        agents.jobRuns
          .count(
            Job.Kind.assume(Agents.JobKind),
            (
              RunRow.Status(Agents.RunState.Queued.wire.value),
              RunRow.Status(Agents.RunState.Running.wire.value)
            )
          )
          .value
      ),
      poolCap = PlanQuota.PoolId.values.toList
        .map(p => Candidate.Pools(p.wire.value) -> Resources.PoolCapValue(poolCap(p)))
        .toMap,
      poolUsed = runs
        .flatMap((p, _) => poolsOfRun(p))
        .groupMapReduce(p => Candidate.Pools(p.wire.value))(_ => 1)(_ + _)
        .view
        .mapValues(Resources.PoolUsedValue(_))
        .toMap,
      managerCap = caps.map((id, cap) => Candidate.ManagerId(id.value) -> cap),
      managerUsed = caps.keys
        .map(id => Candidate.ManagerId(id.value) -> Resources.ManagerUsedValue(agents.managerStore.running(id).value))
        .toMap,
      gatheringUsed = Resources.GatheringUsed(
        runs.count(_._2.exists(g => gs.get(DomainGoal.Slug.assume(g.value)).exists(_.gathering)))
      ),
      stop = stop.orElse(budgetBlock()),
      recent = agents.managerStore
        .recentKinds(PageSize.assume(math.max(1, cfg.scheduler.shareWindow.value)))
        .map(k => JobType.of(Candidate.Kind(k.value)))
    )
    Inputs(cands, perManager.flatMap((m, is) => is.map(i => Candidate.Id(i.id.value) -> (m, i))).toMap, res)

  /** One pass of the supervisor's tick: decide, start through `start` (the supervisor's dispatch of one item: Right
    * when its run was created, Left why not), and keep the pass for the explanations.
    */
  def pass(ms: List[ManagerInfo], now: Instant, stop: Option[Block])(
      start: (ManagerInfo, ManagerItem) => Either[Block.Refusal, Unit]
  ): Decision =
    val in = inputs(ms, now, stop)
    val s = settings
    val d =
      SchedulerPolicy.run(in.cands, in.res, s, now)(c => in.items.get(c.id).fold(Left(Block.Refusal.Gone))(start(_, _)))
    last = Some(Snapshot(now, dry = false, in, s, d))
    d

  /** Read the plan pools on their schedules (`agents.quota.*.refresh-every-minutes`): while the autopilot is on and the
    * last pass left work waiting on a pool, so the next passes decide on current numbers. Nothing while draining. One
    * line per reading taken.
    */
  def refreshPlans(now: Instant): List[String] =
    val wanted = if agents.drain.draining then Set.empty[Candidate.Pools] else wantedPools()
    val autopilotOn = agents.autopilot.on
    PlanQuota.PoolId.values.toList.flatMap { p =>
      agents.pools
        .refreshOnSchedule(
          p,
          PlanReadSchedule.AutopilotOn(autopilotOn),
          PlanReadSchedule.Wanted(wanted.contains(Candidate.Pools(p.wire.value))),
          now
        )
        .toOption
        .map(r => s"plan refresh ${p.wire} (${PlanQuota.RefreshTrigger.Schedule.wire}): ${r.text}")
    }

  private def wantedPools(): Set[Candidate.Pools] =
    last.fold(Set.empty[Candidate.Pools])(snap =>
      SchedulerPolicy.poolsWanted(snap.d.waiting.map(_._1.c), chainPools(snap.in, _))
    )

  /** The pools every model of `c`'s role chains draws on, whether or not it is usable now. */
  private def chainPools(in: Inputs, c: Candidate): List[Candidate.Pools] =
    val over = in.items.get(c.id).flatMap(_._1.config.models).getOrElse(Nil)
    agents.managers
      .pipelineOf(ManagerContext.ItemKind(c.kind.value))
      .roles
      .flatMap { r =>
        agents.picker.chainFor(r, modelOf(over, r)).getOrElse(Nil)
      }
      .flatMap(agents.pools.poolOf)
      .map(p => Candidate.Pools(p.wire.value))
      .distinct

  /** The managers that may start runs (active, of a kind the supervisor runs). */
  def live(): List[ManagerInfo] =
    agents.managerStore
      .list()
      .filter(m =>
        State.parse(m.state).contains(State.Active) &&
          Kind.parse(m.kind).exists(k => k == Kind.Review || k == Kind.Enrich || k == Kind.Seed || k == Kind.Watch)
      )

  /** A pass that starts nothing: what one would do now. */
  private def dry(now: Instant): Snapshot =
    val in = inputs(live(), now, agents.drain.block)
    val s = settings
    Snapshot(now, dry = true, in, s, SchedulerPolicy.run(in.cands, in.res, s, now)(_ => Right(())))

  // ── what it shows ──────────────────────────────────────────────────────────────────────────────

  /** The status: the last pass (or a dry one), the first `limit` waiting items (default 50). */
  def status(
      limit: Option[PageSize] = None,
      now: Instant = Instant.now(agents.clock),
      changes: List[SchedulerStatus.Changes] = Nil
  ): SchedulerStatus =
    val snap = last.getOrElse(dry(now))
    val s = snap.s
    val shown = math.max(0, limit.fold(50)(_.value))
    val waits = ordered(snap.d)
    val res = snap.in.res
    SchedulerStatus(
      StrategyLine.Name(s.strategy.name.value),
      if chosen.isDefined then AgentScheduler.SchedulerStatus.StrategyFrom("person")
      else AgentScheduler.SchedulerStatus.StrategyFrom("config"),
      Strategies.all.map(x => StrategyLine(StrategyLine.Name(x.name.value), StrategyLine.Describe(x.describe))),
      Some(SchedulerStatus.At(snap.at.toString)),
      SchedulerStatus.Dry(snap.dry),
      SchedulerStatus.MaxRuns(res.maxRuns.value),
      SchedulerStatus.Occupied(res.occupied.value),
      res.poolCap.toList
        .sortBy(_._1.value)
        .map((p, cap) =>
          PoolUse(PoolUse.Pool(p.value), PoolUse.Used(res.poolUsed.get(p).fold(0)(_.value)), PoolUse.Cap(cap.value))
        ),
      JobType.values.toList.map(t =>
        TypeLine(
          TypeLine.JobType(t.wire.value),
          TypeLine.Label(t.label.value),
          TypeLine.Weight(s.weight(t).value),
          TypeLine.Priority(s.priorityOf(t).value),
          TypeLine.Recent(res.recent.count(_ == t)),
          TypeLine.Waiting(waits.count(_._1.c.jobType == t))
        )
      ),
      snap.d.started.map((x, why) =>
        Started(
          Started.Item(x.c.id.value),
          Started.Manager(x.c.managerId.value),
          Started.Kind(x.c.kind.value),
          Started.JobType(x.c.jobType.wire.value),
          Started.Target(x.c.target.value),
          x.c.goal.map(g => Goal.Slug(g.value)),
          Problem.Unclassified(Legacy.Text(why.value))
        )
      ),
      waits.take(shown).map(wire),
      SchedulerStatus.WaitingTotal(waits.size),
      SchedulerStatus.ForPerson(agents.managerStore.forPerson().value),
      res.stop.map(b => SchedulerStatus.Stop(b.why.render)),
      List(
        s"aging: 1 level per ${s.agingHours} h",
        s"max wait ${s.maxWaitHours} h",
        s"min share ${s.minShare}",
        s"gathering places ${s.gatheringSlots}",
        s"pool overcommit ${math.max(1, cfg.scheduler.poolOvercommit.value)}"
      ).map(SchedulerStatus.Settings(_)),
      changes
    )

  /** The waiting items in the order shown: the ones in the order first (by place), then the rest. */
  private def ordered(d: Decision): List[(Scored, SchedulerPolicy.Wait)] =
    val (inOrder, rest) = d.waiting.partition(_._2.position.value > 0)
    inOrder.sortBy(w => (w._2.position.value: Int)) ++ rest

  private def wire(x: (Scored, SchedulerPolicy.Wait)): Waiting =
    val (sc, w) = x
    Waiting(
      Waiting.Item(sc.c.id.value),
      Waiting.Manager(sc.c.managerId.value),
      Waiting.Kind(sc.c.kind.value),
      Waiting.JobType(sc.c.jobType.wire.value),
      Waiting.Target(sc.c.target.value),
      sc.c.goal.map(g => Goal.Slug(g.value)),
      Waiting.Priority(sc.effective.value),
      Waiting.WaitedMinutes(sc.waitedMinutes.value),
      Waiting.Code(w.code.value),
      Problem.Unclassified(Legacy.Text(w.why.render)),
      w.behind.map(b => Waiting.Behind(b.value)),
      Waiting.Position(w.position.value)
    )

  private def say(base: WhyInfo, code: String, why: String): WhyInfo =
    base.modify(_.code).setTo(WhyInfo.Code(code)).modify(_.why).setTo(Problem.Unclassified(Legacy.Text(why)))

  /** Why one item (its id, or a target among the open items) is where it is. */
  def why(ref: ManagerItem.Ref, now: Instant = Instant.now(agents.clock)): Either[Api.Error, WhyInfo] =
    val r = ref.value.trim
    val item = r
      .stripPrefix("#")
      .toLongOption
      .flatMap(n => agents.managerStore.item(ManagerItem.Id(n)))
      .orElse(agents.managerStore.openFor(ManagerItem.Target(r)).headOption)
    item
      .toRight(
        Api.Error(
          Problem.Unclassified(Legacy.Text(s"no manager item '$r' (an item id, or the target of an open item)")),
          Some(Api.Error.Code(Write.CodeNotFound))
        )
      )
      .map { i =>
        val base = WhyInfo(
          WhyInfo.Item(i.id.value),
          WhyInfo.Manager(i.managerId.value),
          WhyInfo.Kind(i.kind.value),
          WhyInfo.Target(i.target.value),
          WhyInfo.Status(i.status.value),
          run = i.runId.map(r => WhyInfo.Run(r.value))
        )
        ItemStatus.parse(i.status) match
          case Some(ItemStatus.Pending) =>
            val snap = last.getOrElse(dry(now))
            snap.d.started.find(_._1.c.id.value == i.id.value) match
              case Some((_, w)) => say(base, AgentScheduler.Code.Running, s"started by the last pass: $w")
              case None         =>
                snap.d.waiting.find(_._1.c.id.value == i.id.value) match
                  case Some((_, w)) =>
                    say(base, w.code.value, w.why.render)
                      .modify(_.behind)
                      .setTo(w.behind.map(b => WhyInfo.Behind(b.value)))
                      .modify(_.position)
                      .setTo(WhyInfo.Position(w.position.value))
                  case None =>
                    val notBefore = i.notBefore.flatMap(n => instantOf(n.value)).filter(_.isAfter(now))
                    val paused = agents.managerStore.get(i.managerId).filterNot(_.state == State.Active.wire)
                    if i.kind.value == OaIngest then
                      say(
                        base,
                        AgentScheduler.Code.NotDue,
                        "a legal open-access ingest: the ingest backlog drains it within the day's ingest allowance (no AI allowance)"
                      )
                    else
                      notBefore match
                        case Some(t) =>
                          say(
                            base,
                            AgentScheduler.Code.NotDue,
                            s"not due before $t (a cooldown or a failed run's backoff)"
                          )
                        case None =>
                          paused match
                            case Some(m) =>
                              say(
                                base,
                                AgentScheduler.Code.Paused,
                                s"its manager ${m.id} is ${m.state}: it starts nothing"
                              )
                            case None =>
                              base
                                .modify(_.why)
                                .setTo(Problem.Unclassified(Legacy.Text("not looked at yet: the next pass decides it")))
          case Some(ItemStatus.Held) =>
            i.hold match
              case Some(hd) if hd.untilApproval.isDefined =>
                say(
                  base,
                  AgentScheduler.Code.Person,
                  s"waits for a person to decide approval #${hd.untilApproval.get}; it holds no place"
                ).modify(_.approval).setTo(hd.untilApproval.map(a => WhyInfo.Approval(a.value)))
              case Some(hd) =>
                say(
                  base,
                  AgentScheduler.Code.Held,
                  s"held: ${hd.reason.sentence}" +
                    hd.until.fold("")(u => s" (until $u)") + hd.untilSource
                      .fold("")(u => s" (until $u is in the corpus)")
                )
              case None => say(base, AgentScheduler.Code.Held, "held")
          case Some(ItemStatus.Active) =>
            val run = i.runId.flatMap(r => agents.runs.get(DomainRun.Id.assume(r.value)))
            run.flatMap(r => RunState.parse(Run.State.Wire(r.status.value))) match
              case Some(RunState.WaitingApproval) =>
                val open = run.toList
                  .flatMap(r => agents.approvalStore.approvalsOf(Job.Id.assume(r.id.value), None))
                  .filter(_.state == ApprovalState.Open.wire)
                say(
                  base,
                  AgentScheduler.Code.Person,
                  s"its run waits for a person to decide ${open.lastOption
                      .fold("an approval")(a => s"approval #${a.id} (${a.kind})")}; " +
                    "it holds no place until then"
                ).modify(_.approval).setTo(open.lastOption.map(a => WhyInfo.Approval(a.id.value)))
              case Some(RunState.Paused) =>
                say(
                  base,
                  AgentScheduler.Code.Paused,
                  s"its run is paused: ${run.flatMap(_.error).fold("")(_.sentence)}".trim
                )
              case Some(st) => say(base, AgentScheduler.Code.Running, s"its run is ${st.wire}")
              case None     => say(base, AgentScheduler.Code.Running, "its run is being evaluated")
          case _ => say(base, AgentScheduler.Code.Settled, s"settled: ${i.outcome.fold(i.status.value)(_.value)}")
      }

  // ── the stall report ───────────────────────────────────────────────────────────────────────────

  /** Every unfinished goal with no progress in `days` days (default `agents.autopilot.stall-days`; a goal's creation
    * counts as progress), the one reason it stands still and what unblocks it ([[Stalls.classify]]): waiting for a
    * person, draining, paused, a budget, quota, the scheduler's queue, a block, a failed item, no candidates, idle.
    */
  def stalls(
      days: Option[AgentAutopilotConfig.StallDays] = None,
      now: Instant = Instant.now(agents.clock)
  ): Stalls.StallReport =
    val n = math.max(0, days.fold(cfg.autopilot.stallDays.value)(_.value))
    val goals = agents.focusStore.goals().filterNot(g => GoalFocus.finished(DomainGoal.Status.Wire(g.status.value)))
    val progress = agents.managerStore.lastProgress()
    val articles = agents.workflows.linked()
    val bySubj = agents.approvalStore.openLight().groupBy(_.subject)
    val held = agents.managerStore.personHeldByGoal()
    val paused = agents.managerStore
      .light()
      .filter(_.state.value == State.Paused.wire.value)
      .flatMap(m => m.goal.map(_ -> m.id))
      .groupMap(_._1)(_._2)
    val snap = last.getOrElse(dry(now))
    val waits = snap.d.waiting.groupMap(_._1.c.goal)(x =>
      Stalls.SchedWait(
        Stalls.SchedWait.Code(x._2.code.value),
        x._2.why.transformInto[Stalls.SchedWait.Why],
        Stalls.SchedWait.Position(x._2.position.value)
      )
    )
    val stops = agents.focusStore.lastStops()
    val sources = agents.focusStore.sourceCounts()
    val sweeps = agents.managerStore.sweepOutcomes()
    val pace = agents.managerStore.pace(now.minusSeconds(7L * 86400L))
    val running = agents.managerStore.runningByGoal()
    val draining = agents.drain.draining
    val facts = goals.map { g =>
      val slug = DomainGoal.Slug.assume(g.slug.value)
      val last =
        (progress.get(Goal.Slug(g.slug.value)).map(_.value).toList :+ g.createdAt.value).flatMap(instantOf).maxOption
      val idle = last.map(t => java.time.Duration.between(t, now).toDays.toInt)
      val aps = (bySubj
        .getOrElse(Some(ApprovalDigest.Open.Subject(g.slug.value)), Nil) ++ articles
        .getOrElse(DomainGoal.Slug.assume(g.slug.value), Nil)
        .flatMap(a => bySubj.getOrElse(Some(ApprovalDigest.Open.Subject(a.value)), Nil)))
        .map(o => Stalls.Facts.ApprovalId(o.id.value) -> Stalls.Facts.ApprovalKind(o.kind.value))
        .distinct
      Stalls.Facts(
        Stalls.Facts.Slug(g.slug.value),
        Stalls.Facts.Title(g.title.value),
        Stalls.Facts.Status(g.status.value),
        last.map(t => Stalls.Facts.LastProgress(t.toString)),
        idle.map(Stalls.Facts.IdleDays(_)),
        aps,
        Stalls.Facts.PersonHeld(held.get(slug).fold(0)(_.value)),
        paused.getOrElse(ManagerLight.Goal(g.slug.value), Nil).map(id => Stalls.Facts.Paused(id.value)),
        Stalls.Facts.Draining(draining),
        waits.getOrElse(Some(Candidate.Goal(g.slug.value)), Nil),
        stops.get(slug).map(w => Stalls.Facts.LastStop(w.sentence)),
        Stalls.Facts.Sources(sources.get(slug).fold(0)(_.value)),
        Stalls.Facts.NoCandidates(sweeps.get(slug).exists(_.value.contains("no new candidates"))),
        Stalls.Facts.Open(
          List(seedId(Goal.Ref(g.slug.value)), watchId(Goal.Ref(g.slug.value))).flatMap(pace.get).map(_._1.value).sum
        ),
        Stalls.Facts.Running(running.get(slug).fold(0)(_.value))
      )
    }
    Stalls.report(facts, Stalls.ReportDays(n), Stalls.ReportAt(now.toString))

  // ── a person's switch ──────────────────────────────────────────────────────────────────────────

  /** Switch the strategy live (`default` = back to agents.scheduler.strategy); the next tick uses it (nudged now).
    * Answers what a pass would do with it.
    */
  def set(in: SchedulerSet, now: Instant = Instant.now(agents.clock)): Either[Api.Error, SchedulerStatus] =
    val by = in.by.map(_.value.trim).filter(_.nonEmpty).getOrElse("person")
    val name = in.strategy.value.trim.toLowerCase(java.util.Locale.ROOT)
    val done: Either[Api.Error, String] =
      if name == "default" then
        agents.autopilotRow.setStrategy(None, AutopilotRepo.Row.ChangedBy(by))
        Right(
          s"strategy back to agents.scheduler.strategy (${SchedulerPolicy.settings(ManagerDefaultsViews.scheduler(cfg.scheduler)).strategy.name}) (by $by)"
        )
      else
        Strategies.parse(SchedulerPolicy.StrategyName(name)) match
          case None =>
            Left(
              Api.Error(
                Problem.Unclassified(
                  Legacy.Text(
                    s"unknown strategy '${in.strategy}' (one of: ${Strategies.names.mkString(", ")}, default)"
                  )
                )
              )
            )
          case Some(st) =>
            agents.autopilotRow.setStrategy(
              Some(AutopilotRepo.StrategyName(st.name.value)),
              AutopilotRepo.Row.ChangedBy(by)
            )
            Right(s"strategy ${st.name} (by $by)")
    done.map { line =>
      last = None
      val _ = agents.workLists.add(
        WorkListControllers.ListName(Queue.Kind.Log.label.value),
        QueueAdd(Queue.QueueItem.Text(s"agent scheduler: $line".take(1000)))
      )
      scribe.info(s"agent scheduler: $line")
      agents.managers.nudge()
      status(None, now, List(SchedulerStatus.Changes(line)))
    }
