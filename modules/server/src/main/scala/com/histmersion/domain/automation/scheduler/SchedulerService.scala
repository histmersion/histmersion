package com.histmersion.domain.automation.scheduler

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.managers.{ManagerDefaultsViews, ManagerSupervisor}

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
import com.histmersion.api.automation.{Agents, AgentScheduler, PlanQuota, Stalls}
import com.histmersion.api.Api
import com.histmersion.api.automation.Goal

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
  def chosen: Option[(com.histmersion.domain.automation.scheduler.SchedulerService.ChosenResultPart1, com.histmersion.domain.automation.scheduler.SchedulerService.ChosenResultPart2, com.histmersion.domain.automation.scheduler.SchedulerService.ChosenResultPart3)] = agents.autopilotRow.strategy()

  /** The settings in effect now. */
  def settings: Settings = SchedulerPolicy.settings(ManagerDefaultsViews.scheduler(cfg.scheduler), chosen.map(_._1))

  /** One pass as shown: when, whether it was dry, its inputs and its decision. */
  final private case class Snapshot(at: Instant, dry: Snapshot.Dry, in: Inputs, s: Settings, d: Decision)

  object Snapshot:
    type Dry = Dry.T
    object Dry extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The last real pass (a `var`: the supervisor's tick writes it, the status reads it). */
  @volatile private var last: Option[Snapshot] = None

  /** One goal as the scheduler sees it: its priority (a pin added), when it was created, whether it is still gathering
    * its sources.
    */
  final private case class GoalInfo(
      priority: GoalInfo.Priority,
      created: Option[Instant],
      gathering: GoalInfo.Gathering,
      inProgress: Option[GoalInfo.InProgress]
  )

  object GoalInfo:
    type Priority = Priority.T
    object Priority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Gathering = Gathering.T
    object Gathering extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InProgress = InProgress.T
    object InProgress extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def goals(): Map[com.histmersion.domain.automation.scheduler.SchedulerService.GoalsResultPart1, GoalInfo] =
    agents.focus
      .facts()
      .map(f =>
        f.slug -> GoalInfo(
          f.priority + (if f.pinned then PinBoost else 0),
          ManagerPolicy.parseInstant(f.createdAt),
          !GoalFocus.finished(f.status) && GoalFocus.stage(f, agents.sourcesToPlan) == GoalFocus.Stage.Gathering,
          AutopilotPolicy.articlesInProgress(f.unreviewed, f.compileOpen)
        )
      )
      .toMap

  /** The inputs of one pass: the candidates (with their manager and item), the resources. */
  final case class Inputs(cands: List[Candidate], items: Map[Inputs.ItemsKey, (ManagerInfo, ManagerItem)], res: Resources)

  object Inputs:
    type ItemsKey = ItemsKey.T
    object ItemsKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Why nothing may start for any manager now (a spend cap reached), if so. */
  def budgetBlock(): Option[Block] =
    val (day, month) = agents.runs.spendDayMonth()
    if day >= BigDecimal(cfg.budgets.dailyUsd) then
      Some(Block("budget", s"budget: today's agent spend reached agents.budgets.daily-usd (${cfg.budgets.dailyUsd})"))
    else if month >= BigDecimal(cfg.budgets.monthlyUsd) then
      Some(
        Block(
          "budget",
          s"budget: this month's agent spend reached agents.budgets.monthly-usd (${cfg.budgets.monthlyUsd})"
        )
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
  ): Either[com.histmersion.domain.automation.scheduler.SchedulerService.PoolIdsResultPart1, List[PlanQuota.PoolId]] =
    val roles = pipelines.flatMap(_.roles).distinct
    val picks = roles.map { r =>
      val ref = over.find(o => Role.parse(o.role).contains(r)).map(_.model)
      r -> agents.picker.pick(r, ref, now.toEpochMilli, refresh = None)
    }
    picks.collectFirst { case (r, Left(f)) => (r, f) } match
      case Some((r, PickFailure.Exhausted(_, at, why))) =>
        Left(
          s"${r.wire} chain ${if why == PauseReason.PlanQuota then "below its plan floor" else "out of allowance"}" +
            at.fold("")(t => s"; frees ~$t")
        )
      case Some((r, PickFailure.Unavailable(msg))) => Left(s"${r.wire}: ${msg.take(200)}")
      case _                                       =>
        val plans = picks.collect { case (_, Right(p)) => p.billing }
        val pools = plans.collect { case Billing.Plan(p) => p }.distinct
        Right(if pools.nonEmpty && plans.forall(_.isInstanceOf[Billing.Plan]) then pools else Nil)

  private def poolCap(p: PlanQuota.PoolId): com.histmersion.domain.automation.scheduler.SchedulerService.PoolCapResult =
    val calls = p match
      case PlanQuota.PoolId.ClaudePlan  => cfg.quota.claudePlan.maxCallsInFlight
      case PlanQuota.PoolId.ChatgptPlan => cfg.quota.chatgptPlan.maxCallsInFlight
    math.max(1, calls) * math.max(1, cfg.scheduler.poolOvercommit)

  /** What one pass reads: every due pending item of `ms` (the active managers that may start runs) at `now`, the goals,
    * the blocks, the runs that occupy a place; `stop` = why nothing starts at all (draining).
    */
  def inputs(ms: List[ManagerInfo], now: Instant, stop: Option[Block]): Inputs =
    val gs = goals()
    val perManager = ms.map { m =>
      m -> agents.managerStore
        .pending(m.id)
        .filterNot(_.kind == OaIngest)
        .filter(i => i.notBefore.flatMap(ManagerPolicy.parseInstant).forall(!_.isAfter(now)))
    }
    def targetsOf(id: com.histmersion.domain.automation.scheduler.SchedulerService.InputsTargetsOfId) = perManager.filter(_._1.id == id).flatMap(_._2.map(_.target))
    val articleGoals = agents.focusStore.goalsOf(Goal.Plans.LinkKind.Article.wire, targetsOf(ReviewId))
    val sourceGoals = agents.focusStore.goalsOf(Goal.Plans.LinkKind.Source.wire, targetsOf(EnrichId))

    /** The goal an item advances: its manager's, else the best of the goals its target is linked to. */
    def goalOf(m: ManagerInfo, i: ManagerItem): Option[com.histmersion.domain.automation.scheduler.SchedulerService.InputsGoalOfResult] =
      m.goal.orElse {
        val linked = if m.id == ReviewId then articleGoals.getOrElse(i.target, Nil)
        else if m.id == EnrichId then sourceGoals.getOrElse(i.target, Nil)
        else Nil
        linked
          .filter(gs.contains)
          .minByOption(g => (-gs(g).priority, gs(g).created.fold(Long.MaxValue)(_.toEpochMilli), g))
          .orElse(linked.headOption)
      }
    val plansWait =
      if perManager.exists(_._2.exists(_.kind == PlanGoalItem)) then agents.focus.goalPlansWait() else None
    val cands = perManager.flatMap { (m, items) =>
      val daily = ManagerPolicy.dailyBlock(
        ManagerPolicy.dailyCap(m.config, ManagerDefaultsViews.defaults(cfg.managers)),
        BigDecimal(m.spentTodayUsd)
      )
      val open = if items.exists(i => i.kind == ArticleReview || i.kind == ProposeArticles) then
        agents.managerStore.openKeys(m.id)
      else Nil
      val over = m.config.models.getOrElse(Nil)
      val memo = collection.mutable.Map.empty[Pipeline, Either[String, List[PlanQuota.PoolId]]] // per manager and pass
      items.map { i =>
        val pipeline = agents.managers.pipelineOf(i.kind)
        val pools =
          if daily.isDefined then Right(Nil) else memo.getOrElseUpdate(pipeline, poolIds(over, now, List(pipeline)))
        val block = daily
          .map(Block("budget", _))
          .orElse(pools.left.toOption.map(why => Block(QuotaBlock, s"starved: $why")))
          .orElse(
            Option.when(ManagerPolicy.waitsFor(i.kind, i.target, open))(
              Block(
                "waits-for",
                if i.kind == ProposeArticles then "its goal's plan comes first (the plan-goal item is open)"
                else "its article's coverage filter is inferred first (the infer-filter item is open)"
              )
            )
          )
          // #3763: no new proposal while the goal's articles are in progress (a person's enqueue goes all the same)
          .orElse(
            Option
              .when(i.kind == ProposeArticles && i.origin != ItemOrigin.Person.wire)(
                goalOf(m, i).flatMap(gs.get).flatMap(_.inProgress)
              )
              .flatten
              .map(Block("articles-in-progress", _))
          )
          // no new goal plan while too many planned goals are unfinished (a person's enqueue goes all the same)
          .orElse(
            Option
              .when(i.kind == PlanGoalItem && i.origin != ItemOrigin.Person.wire)(plansWait)
              .flatten
              .map(Block("unfinished-goals", _))
          )
        val goal = goalOf(m, i)
        val info = goal.flatMap(gs.get)
        val since = List(
          ManagerPolicy.parseInstant(i.updatedAt),
          i.notBefore.flatMap(ManagerPolicy.parseInstant)
        ).flatten.maxOption
        Candidate(
          i.id,
          m.id,
          i.kind,
          i.target,
          goal,
          info.fold(0)(_.priority),
          info.flatMap(_.created),
          info.exists(_.gathering),
          i.priority,
          since.getOrElse(now),
          pools.toOption.getOrElse(Nil).map(_.wire),
          block,
          i.origin == ItemOrigin.Person.wire
        )
      }
    }
    val runs = agents.managerStore.occupyingRuns()
    val pipePools = collection.mutable.Map.empty[Pipeline, List[PlanQuota.PoolId]] // per pass
    def poolsOfRun(p: com.histmersion.domain.automation.scheduler.SchedulerService.InputsPoolsOfRunP): List[PlanQuota.PoolId] =
      Pipeline.parse(p).toList.flatMap(pl => pipePools.getOrElseUpdate(pl, poolIds(Nil, now, List(pl)).getOrElse(Nil)))
    val caps = ms.flatMap(m => m.config.maxRuns.map(m.id -> _)).toMap
    val res = Resources(
      maxRuns = cfg.maxConcurrentRuns,
      occupied = agents.jobRuns.count(Agents.JobKind, (Agents.RunState.Queued.wire, Agents.RunState.Running.wire)),
      poolCap = PlanQuota.PoolId.values.toList.map(p => p.wire -> poolCap(p)).toMap,
      poolUsed = runs.flatMap((p, _) => poolsOfRun(p)).groupMapReduce(_.wire)(_ => 1)(_ + _),
      managerCap = caps,
      managerUsed = caps.keys.map(id => id -> agents.managerStore.running(id)).toMap,
      gatheringUsed = runs.count(_._2.exists(g => gs.get(g).exists(_.gathering))),
      stop = stop.orElse(budgetBlock()),
      recent = agents.managerStore.recentKinds(math.max(1, cfg.scheduler.shareWindow)).map(JobType.of)
    )
    Inputs(cands, perManager.flatMap((m, is) => is.map(i => i.id -> (m, i))).toMap, res)

  /** One pass of the supervisor's tick: decide, start through `start` (the supervisor's dispatch of one item: Right
    * when its run was created), and keep the pass for the explanations.
    */
  def pass(ms: List[ManagerInfo], now: Instant, stop: Option[Block])(
      start: (ManagerInfo, ManagerItem) => Either[com.histmersion.domain.automation.scheduler.SchedulerService.PassStartResultPart1, Unit]
  ): Decision =
    val in = inputs(ms, now, stop)
    val s = settings
    val d =
      SchedulerPolicy.run(in.cands, in.res, s, now)(c => in.items.get(c.id).fold(Left("the item is gone"))(start(_, _)))
    last = Some(Snapshot(now, dry = false, in, s, d))
    d

  /** Read the plan pools on their schedules (`agents.quota.*.refresh-every-minutes`): while the autopilot is on and the
    * last pass left work waiting on a pool, so the next passes decide on current numbers. Nothing while draining. One
    * line per reading taken.
    */
  def refreshPlans(now: Instant): List[com.histmersion.domain.automation.scheduler.SchedulerService.RefreshPlansResult] =
    val wanted = if agents.drain.draining then Set.empty[String] else wantedPools()
    val autopilotOn = agents.autopilot.on
    PlanQuota.PoolId.values.toList.flatMap { p =>
      agents.pools
        .refreshOnSchedule(p, autopilotOn, wanted.contains(p.wire), now)
        .toOption
        .map(r => s"plan refresh ${p.wire} (${PlanQuota.RefreshTrigger.Schedule.wire}): ${r.text}")
    }

  private def wantedPools(): Set[com.histmersion.domain.automation.scheduler.SchedulerService.WantedPoolsResult] =
    last.fold(Set.empty[String])(snap =>
      SchedulerPolicy.poolsWanted(snap.d.waiting.map(_._1.c), chainPools(snap.in, _))
    )

  /** The pools every model of `c`'s role chains draws on, whether or not it is usable now. */
  private def chainPools(in: Inputs, c: Candidate): List[com.histmersion.domain.automation.scheduler.SchedulerService.ChainPoolsResult] =
    val over = in.items.get(c.id).flatMap(_._1.config.models).getOrElse(Nil)
    agents.managers
      .pipelineOf(c.kind)
      .roles
      .flatMap { r =>
        agents.picker.chainFor(r, over.find(o => Role.parse(o.role).contains(r)).map(_.model)).getOrElse(Nil)
      }
      .flatMap(agents.pools.poolOf)
      .map(_.wire)
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
      limit: Option[com.histmersion.domain.automation.scheduler.SchedulerService.StatusLimit] = None,
      now: Instant = Instant.now(agents.clock),
      changes: List[com.histmersion.domain.automation.scheduler.SchedulerService.StatusChanges] = Nil
  ): SchedulerStatus =
    val snap = last.getOrElse(dry(now))
    val s = snap.s
    val shown = math.max(0, limit.getOrElse(50))
    val waits = ordered(snap.d)
    val res = snap.in.res
    SchedulerStatus(
      s.strategy.name,
      if chosen.isDefined then "person" else "config",
      Strategies.all.map(x => StrategyLine(x.name, x.describe)),
      Some(snap.at.toString),
      snap.dry,
      res.maxRuns,
      res.occupied,
      res.poolCap.toList.sortBy(_._1).map((p, cap) => PoolUse(p, res.poolUsed.getOrElse(p, 0), cap)),
      JobType.values.toList.map(t =>
        TypeLine(
          t.wire,
          t.label,
          s.weight(t),
          s.priorityOf(t),
          res.recent.count(_ == t),
          waits.count(_._1.c.jobType == t)
        )
      ),
      snap.d.started.map((x, why) =>
        Started(x.c.id, x.c.managerId, x.c.kind, x.c.jobType.wire, x.c.target, x.c.goal, why)
      ),
      waits.take(shown).map(wire),
      waits.size,
      agents.managerStore.forPerson(),
      res.stop.map(_.why),
      List(
        s"aging: 1 level per ${s.agingHours} h",
        s"max wait ${s.maxWaitHours} h",
        s"min share ${s.minShare}",
        s"gathering places ${s.gatheringSlots}",
        s"pool overcommit ${math.max(1, cfg.scheduler.poolOvercommit)}"
      ),
      changes
    )

  /** The waiting items in the order shown: the ones in the order first (by place), then the rest. */
  private def ordered(d: Decision): List[(Scored, SchedulerPolicy.Wait)] =
    val (inOrder, rest) = d.waiting.partition(_._2.position > 0)
    inOrder.sortBy(_._2.position) ++ rest

  private def wire(x: (Scored, SchedulerPolicy.Wait)): Waiting =
    val (sc, w) = x
    Waiting(
      sc.c.id,
      sc.c.managerId,
      sc.c.kind,
      sc.c.jobType.wire,
      sc.c.target,
      sc.c.goal,
      sc.effective,
      sc.waitedMinutes,
      w.code,
      w.why,
      w.behind,
      w.position
    )

  /** Why one item (its id, or a target among the open items) is where it is. */
  def why(ref: com.histmersion.domain.automation.scheduler.SchedulerService.WhyRef, now: Instant = Instant.now(agents.clock)): Either[Api.Error, WhyInfo] =
    val r = ref.trim
    val item = r
      .stripPrefix("#")
      .toLongOption
      .flatMap(agents.managerStore.item(_))
      .orElse(agents.managerStore.openFor(r).headOption)
    item
      .toRight(Api.Error(s"no manager item '$r' (an item id, or the target of an open item)", Some(Write.CodeNotFound)))
      .map { i =>
        val base = WhyInfo(i.id, i.managerId, i.kind, i.target, i.status, run = i.runId)
        ItemStatus.parse(i.status) match
          case Some(ItemStatus.Pending) =>
            val snap = last.getOrElse(dry(now))
            snap.d.started.find(_._1.c.id == i.id) match
              case Some((_, w)) => base.modify(_.code).setTo(AgentScheduler.Code.Running).modify(_.why).setTo(s"started by the last pass: $w")
              case None         =>
                snap.d.waiting.find(_._1.c.id == i.id) match
                  case Some((_, w)) => base.modify(_.code).setTo(w.code).modify(_.why).setTo(w.why).modify(_.behind).setTo(w.behind).modify(_.position).setTo(w.position)
                  case None         =>
                    val notBefore = i.notBefore.flatMap(ManagerPolicy.parseInstant).filter(_.isAfter(now))
                    val paused = agents.managerStore.get(i.managerId).filterNot(_.state == State.Active.wire)
                    if i.kind == OaIngest then
                      base.modify(
                        _.code).setTo(AgentScheduler.Code.NotDue).modify(_.why).setTo("a legal open-access ingest: the ingest backlog drains it within the day's ingest allowance (no AI allowance)"
                      )
                    else
                      notBefore match
                        case Some(t) =>
                          base.modify(
                            _.code).setTo(AgentScheduler.Code.NotDue).modify(_.why).setTo(s"not due before $t (a cooldown or a failed run's backoff)"
                          )
                        case None =>
                          paused match
                            case Some(m) =>
                              base.modify(
                                _.code).setTo(AgentScheduler.Code.Paused).modify(_.why).setTo(s"its manager ${m.id} is ${m.state}: it starts nothing"
                              )
                            case None => base.modify(_.why).setTo("not looked at yet: the next pass decides it")
          case Some(ItemStatus.Held) =>
            i.hold match
              case Some(hd) if hd.untilApproval.isDefined =>
                base.modify(
                  _.code).setTo(AgentScheduler.Code.Person).modify(_.approval).setTo(hd.untilApproval).modify(_.why).setTo(s"waits for a person to decide approval #${hd.untilApproval.get}; it holds no place"
                )
              case Some(hd) =>
                base.modify(
                  _.code).setTo(AgentScheduler.Code.Held).modify(_.why).setTo(s"held: ${hd.reason}" +
                    hd.until.fold("")(u => s" (until $u)") + hd.untilSource
                      .fold("")(u => s" (until $u is in the corpus)")
                )
              case None => base.modify(_.code).setTo(AgentScheduler.Code.Held).modify(_.why).setTo("held")
          case Some(ItemStatus.Active) =>
            val run = i.runId.flatMap(agents.runs.get(_))
            run.map(_.status).flatMap(RunState.parse) match
              case Some(RunState.WaitingApproval) =>
                val open = run.toList
                  .flatMap(r => agents.approvalStore.approvalsOf(r.id, None))
                  .filter(_.state == ApprovalState.Open.wire)
                base.modify(
                  _.code).setTo(AgentScheduler.Code.Person).modify(_.approval).setTo(open.lastOption.map(_.id)).modify(_.why).setTo(s"its run waits for a person to decide ${open.lastOption
                      .fold("an approval")(a => s"approval #${a.id} (${a.kind})")}; " +
                    "it holds no place until then"
                )
              case Some(RunState.Paused) =>
                base.modify(
                  _.code).setTo(AgentScheduler.Code.Paused).modify(_.why).setTo(s"its run is paused: ${run.flatMap(_.error).getOrElse("")}".trim
                )
              case Some(st) => base.modify(_.code).setTo(AgentScheduler.Code.Running).modify(_.why).setTo(s"its run is ${st.wire}")
              case None     => base.modify(_.code).setTo(AgentScheduler.Code.Running).modify(_.why).setTo("its run is being evaluated")
          case _ => base.modify(_.code).setTo(AgentScheduler.Code.Settled).modify(_.why).setTo(s"settled: ${i.outcome.getOrElse(i.status)}")
      }

  // ── the stall report ───────────────────────────────────────────────────────────────────────────

  /** Every unfinished goal with no progress in `days` days (default `agents.autopilot.stall-days`; a goal's creation
    * counts as progress), the one reason it stands still and what unblocks it ([[Stalls.classify]]): waiting for a
    * person, draining, paused, a budget, quota, the scheduler's queue, a block, a failed item, no candidates, idle.
    */
  def stalls(days: Option[com.histmersion.domain.automation.scheduler.SchedulerService.StallsDays] = None, now: Instant = Instant.now(agents.clock)): Stalls.StallReport =
    val n = math.max(0, days.getOrElse(cfg.autopilot.stallDays))
    val goals = agents.focusStore.goals().filterNot(g => GoalFocus.finished(g.status))
    val progress = agents.managerStore.lastProgress()
    val articles = agents.workflows.linked()
    val bySubj = agents.approvalStore.openLight().groupBy(_.subject)
    val held = agents.managerStore.personHeldByGoal()
    val paused = agents.managerStore
      .light()
      .filter(_.state == State.Paused.wire)
      .flatMap(m => m.goal.map(_ -> m.id))
      .groupMap(_._1)(_._2)
    val snap = last.getOrElse(dry(now))
    val waits = snap.d.waiting.groupMap(_._1.c.goal)(x => Stalls.SchedWait(x._2.code, x._2.why, x._2.position))
    val stops = agents.focusStore.lastStops()
    val sources = agents.focusStore.sourceCounts()
    val sweeps = agents.managerStore.sweepOutcomes()
    val pace = agents.managerStore.pace(now.minusSeconds(7L * 86400L))
    val running = agents.managerStore.runningByGoal()
    val draining = agents.drain.draining
    val facts = goals.map { g =>
      val last = (progress.get(g.slug).toList :+ g.createdAt).flatMap(ManagerPolicy.parseInstant).maxOption
      val idle = last.map(t => java.time.Duration.between(t, now).toDays.toInt)
      val aps = (bySubj
        .getOrElse(Some(g.slug), Nil) ++ articles.getOrElse(g.slug, Nil).flatMap(a => bySubj.getOrElse(Some(a), Nil)))
        .map(o => o.id -> o.kind)
        .distinct
      Stalls.Facts(
        g.slug,
        g.title,
        g.status,
        last.map(_.toString),
        idle,
        aps,
        held.getOrElse(g.slug, 0),
        paused.getOrElse(g.slug, Nil),
        draining,
        waits.getOrElse(Some(g.slug), Nil),
        stops.get(g.slug),
        sources.getOrElse(g.slug, 0),
        sweeps.get(g.slug).exists(_.contains("no new candidates")),
        List(seedId(g.slug), watchId(g.slug)).flatMap(pace.get).map(_._1).sum,
        running.getOrElse(g.slug, 0)
      )
    }
    Stalls.report(facts, n, now.toString)

  // ── a person's switch ──────────────────────────────────────────────────────────────────────────

  /** Switch the strategy live (`default` = back to agents.scheduler.strategy); the next tick uses it (nudged now).
    * Answers what a pass would do with it.
    */
  def set(in: SchedulerSet, now: Instant = Instant.now(agents.clock)): Either[Api.Error, SchedulerStatus] =
    val by = in.by.map(_.trim).filter(_.nonEmpty).getOrElse("person")
    val name = in.strategy.trim.toLowerCase(java.util.Locale.ROOT)
    val done: Either[Api.Error, com.histmersion.domain.automation.scheduler.SchedulerService.SetDonePart2] =
      if name == "default" then
        agents.autopilotRow.setStrategy(None, by)
        Right(
          s"strategy back to agents.scheduler.strategy (${SchedulerPolicy.settings(ManagerDefaultsViews.scheduler(cfg.scheduler)).strategy.name}) (by $by)"
        )
      else
        Strategies.parse(name) match
          case None =>
            Left(Api.Error(s"unknown strategy '${in.strategy}' (one of: ${Strategies.names.mkString(", ")}, default)"))
          case Some(st) =>
            agents.autopilotRow.setStrategy(Some(st.name), by)
            Right(s"strategy ${st.name} (by $by)")
    done.map { line =>
      last = None
      val _ = agents.workLists.add("log", QueueAdd(s"agent scheduler: $line".take(1000)))
      scribe.info(s"agent scheduler: $line")
      agents.managers.nudge()
      status(None, now, List(line))
    }

object SchedulerService:
  type InputsTargetsOfId = InputsTargetsOfId.T
  object InputsTargetsOfId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InputsGoalOfResult = InputsGoalOfResult.T
  object InputsGoalOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InputsPoolsOfRunP = InputsPoolsOfRunP.T
  object InputsPoolsOfRunP extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetDonePart2 = SetDonePart2.T
  object SetDonePart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ChosenResultPart1 = ChosenResultPart1.T
  object ChosenResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChosenResultPart2 = ChosenResultPart2.T
  object ChosenResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChosenResultPart3 = ChosenResultPart3.T
  object ChosenResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalsResultPart1 = GoalsResultPart1.T
  object GoalsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PoolIdsResultPart1 = PoolIdsResultPart1.T
  object PoolIdsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PoolCapResult = PoolCapResult.T
  object PoolCapResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PassStartResultPart1 = PassStartResultPart1.T
  object PassStartResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshPlansResult = RefreshPlansResult.T
  object RefreshPlansResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WantedPoolsResult = WantedPoolsResult.T
  object WantedPoolsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChainPoolsResult = ChainPoolsResult.T
  object ChainPoolsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusLimit = StatusLimit.T
  object StatusLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusChanges = StatusChanges.T
  object StatusChanges extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WhyRef = WhyRef.T
  object WhyRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StallsDays = StallsDays.T
  object StallsDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
