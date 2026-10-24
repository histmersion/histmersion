package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.core.Agents.{ApprovalState, Billing, Pipeline, PauseReason, Role, RunState}
import com.histmersion.core.Managers.*
import com.histmersion.core.Queue.QueueAdd
import com.histmersion.core.SchedulerPolicy.*
import com.histmersion.core.AgentScheduler.*
import com.histmersion.db.{AgentRepo, AutopilotRepo, FocusRepo, GoalLinkRepo, ManagerRepo}

import java.time.Instant
import javax.sql.DataSource

/** The central agent scheduler (docs/research-goal-advancement.md "As built"): the managers PRODUCE
  * pending items; once per managers' tick [[pass]] reads them all — with each goal's priority and age, the
  * plan pools each would draw on, what blocks it, the runs that occupy a place — and [[SchedulerPolicy.run]]
  * decides which start, calling the supervisor's dispatch for each. The pass is kept in memory for the
  * explanations ([[status]], [[why]]); with no pass yet (agents off, a fresh start) a DRY pass shows what
  * one would do, starting nothing.
  *
  * It replaces the per-manager slot caps (a manager's own saved `maxRuns` still caps it, counting only the
  * runs that run or are queued — never one waiting for a person) and the focus's slots (the focus no longer
  * holds anything; its pins and priorities are the goals' priorities here). */
final class SchedulerService(ds: DataSource, cfg: => AgentsConfig, h: com.histmersion.server.Handlers, agents: AgentsHandlers):

  /** A person's choice of strategy (name, by, at), if any. */
  def chosen: Option[(String, String, String)] = AutopilotRepo.strategy(ds)

  /** The settings in effect now. */
  def settings: Settings = SchedulerPolicy.settings(cfg.scheduler, chosen.map(_._1))

  /** One pass as shown: when, whether it was dry, its inputs and its decision. */
  private final case class Snapshot(at: Instant, dry: Boolean, in: Inputs, s: Settings, d: Decision)

  /** The last real pass (a `var`: the supervisor's tick writes it, the status reads it). */
  @volatile private var last: Option[Snapshot] = None

  /** One goal as the scheduler sees it: its priority (a pin added), when it was created, whether it is still
    * gathering its sources. */
  private final case class GoalInfo(priority: Int, created: Option[Instant], gathering: Boolean, inProgress: Option[String])

  private def goals(): Map[String, GoalInfo] =
    agents.focus.facts().map(f => f.slug -> GoalInfo(f.priority + (if f.pinned then PinBoost else 0),
      ManagerPolicy.parseInstant(f.createdAt),
      !GoalFocus.finished(f.status) && GoalFocus.stage(f, cfg.seed.minSources) == GoalFocus.Stage.Gathering,
      AutopilotPolicy.articlesInProgress(f.unreviewed, f.compileOpen))).toMap

  /** The inputs of one pass: the candidates (with their manager and item), the resources. */
  final case class Inputs(cands: List[Candidate], items: Map[Long, (ManagerInfo, ManagerItem)], res: Resources)

  /** Why nothing may start for any manager now (a spend cap reached), if so. */
  def budgetBlock(): Option[Block] =
    val (day, month) = AgentRepo.spendDayMonth(ds)
    if day >= BigDecimal(cfg.budgets.dailyUsd) then
      Some(Block("budget", s"budget: today's agent spend reached agents.budgets.daily-usd (${cfg.budgets.dailyUsd})"))
    else if month >= BigDecimal(cfg.budgets.monthlyUsd) then
      Some(Block("budget", s"budget: this month's agent spend reached agents.budgets.monthly-usd (${cfg.budgets.monthlyUsd})"))
    else None

  /** The plan pools the roles of `pipelines` draw on with `over` (a manager's model overrides) — picked
    * without spending (no plan reading is taken): Left = a role's chain cannot run now (why); Right(Nil) = a
    * metered model in the mix, or nothing known (no pool limit). */
  def poolIds(over: List[Agents.ModelOverride], now: Instant, pipelines: List[Pipeline]): Either[String, List[PlanQuota.PoolId]] =
    val roles = pipelines.flatMap(_.roles).distinct
    val picks = roles.map { r =>
      val ref = over.find(o => Role.parse(o.role).contains(r)).map(_.model)
      r -> agents.picker.pick(r, ref, now.toEpochMilli, refresh = None)
    }
    picks.collectFirst { case (r, Left(f)) => (r, f) } match
      case Some((r, PickFailure.Exhausted(_, at, why))) =>
        Left(s"${r.wire} chain ${if why == PauseReason.PlanQuota then "below its plan floor" else "out of allowance"}" +
          at.fold("")(t => s"; frees ~$t"))
      case Some((r, PickFailure.Unavailable(msg))) => Left(s"${r.wire}: ${msg.take(200)}")
      case _ =>
        val plans = picks.collect { case (_, Right(p)) => p.billing }
        val pools = plans.collect { case Billing.Plan(p) => p }.distinct
        Right(if pools.nonEmpty && plans.forall(_.isInstanceOf[Billing.Plan]) then pools else Nil)

  private def poolCap(p: PlanQuota.PoolId): Int =
    val calls = p match
      case PlanQuota.PoolId.ClaudePlan  => cfg.quota.claudePlan.maxCallsInFlight
      case PlanQuota.PoolId.ChatgptPlan => cfg.quota.chatgptPlan.maxCallsInFlight
    math.max(1, calls) * math.max(1, cfg.scheduler.poolOvercommit)

  /** What one pass reads: every due pending item of `ms` (the active managers that may start runs) at `now`,
    * the goals, the blocks, the runs that occupy a place; `stop` = why nothing starts at all (draining). */
  def inputs(ms: List[ManagerInfo], now: Instant, stop: Option[Block]): Inputs =
    val gs = goals()
    val perManager = ms.map { m =>
      m -> ManagerRepo.pending(ds, m.id).filterNot(_.kind == OaIngest)
        .filter(i => i.notBefore.flatMap(ManagerPolicy.parseInstant).forall(!_.isAfter(now)))
    }
    def targetsOf(id: String) = perManager.filter(_._1.id == id).flatMap(_._2.map(_.target))
    val articleGoals = FocusRepo.goalsOf(ds, GoalPlans.LinkKind.Article.wire, targetsOf(ReviewId))
    val sourceGoals  = FocusRepo.goalsOf(ds, GoalPlans.LinkKind.Source.wire, targetsOf(EnrichId))
    /** The goal an item advances: its manager's, else the best of the goals its target is linked to. */
    def goalOf(m: ManagerInfo, i: ManagerItem): Option[String] =
      m.goal.orElse {
        val linked = if m.id == ReviewId then articleGoals.getOrElse(i.target, Nil)
                     else if m.id == EnrichId then sourceGoals.getOrElse(i.target, Nil) else Nil
        linked.filter(gs.contains).minByOption(g => (-gs(g).priority, gs(g).created.fold(Long.MaxValue)(_.toEpochMilli), g))
          .orElse(linked.headOption)
      }
    val plansWait = if perManager.exists(_._2.exists(_.kind == PlanGoalItem)) then agents.focus.goalPlansWait() else None
    val cands = perManager.flatMap { (m, items) =>
      val daily = ManagerPolicy.dailyBlock(ManagerPolicy.dailyCap(m.config, cfg.managers), BigDecimal(m.spentTodayUsd))
      val open  = if items.exists(i => i.kind == ArticleReview || i.kind == ProposeArticles) then ManagerRepo.openKeys(ds, m.id) else Nil
      val over  = m.config.models.getOrElse(Nil)
      val memo  = collection.mutable.Map.empty[Pipeline, Either[String, List[PlanQuota.PoolId]]] // per manager and pass
      items.map { i =>
        val pipeline = ManagerSupervisor.pipelineOf(i.kind)
        val pools = if daily.isDefined then Right(Nil) else memo.getOrElseUpdate(pipeline, poolIds(over, now, List(pipeline)))
        val block = daily.map(Block("budget", _))
          .orElse(pools.left.toOption.map(why => Block(QuotaBlock, s"starved: $why")))
          .orElse(Option.when(ManagerPolicy.waitsFor(i.kind, i.target, open))(Block("waits-for",
            if i.kind == ProposeArticles then "its goal's plan comes first (the plan-goal item is open)"
            else "its article's coverage filter is inferred first (the infer-filter item is open)")))
          // #3763: no new proposal while the goal's articles are in progress (a person's enqueue goes all the same)
          .orElse(Option.when(i.kind == ProposeArticles && i.origin != ItemOrigin.Person.wire)(
            goalOf(m, i).flatMap(gs.get).flatMap(_.inProgress)).flatten.map(Block("articles-in-progress", _)))
          // no new goal plan while too many planned goals are unfinished (a person's enqueue goes all the same)
          .orElse(Option.when(i.kind == PlanGoalItem && i.origin != ItemOrigin.Person.wire)(plansWait).flatten
            .map(Block("unfinished-goals", _)))
        val goal = goalOf(m, i)
        val info = goal.flatMap(gs.get)
        val since = List(ManagerPolicy.parseInstant(i.updatedAt), i.notBefore.flatMap(ManagerPolicy.parseInstant)).flatten.maxOption
        Candidate(i.id, m.id, i.kind, i.target, goal, info.fold(0)(_.priority), info.flatMap(_.created),
          info.exists(_.gathering), i.priority, since.getOrElse(now), pools.toOption.getOrElse(Nil).map(_.wire), block,
          i.origin == ItemOrigin.Person.wire)
      }
    }
    val runs = ManagerRepo.occupyingRuns(ds)
    val pipePools = collection.mutable.Map.empty[Pipeline, List[PlanQuota.PoolId]] // per pass
    def poolsOfRun(p: String): List[PlanQuota.PoolId] =
      Pipeline.parse(p).toList.flatMap(pl => pipePools.getOrElseUpdate(pl, poolIds(Nil, now, List(pl)).getOrElse(Nil)))
    val caps = ms.flatMap(m => m.config.maxRuns.map(m.id -> _)).toMap
    val res = Resources(
      maxRuns = cfg.maxConcurrentRuns, occupied = AgentRepo.active(ds),
      poolCap = PlanQuota.PoolId.values.toList.map(p => p.wire -> poolCap(p)).toMap,
      poolUsed = runs.flatMap((p, _) => poolsOfRun(p)).groupMapReduce(_.wire)(_ => 1)(_ + _),
      managerCap = caps, managerUsed = caps.keys.map(id => id -> ManagerRepo.running(ds, id)).toMap,
      gatheringUsed = runs.count(_._2.exists(g => gs.get(g).exists(_.gathering))),
      stop = stop.orElse(budgetBlock()),
      recent = ManagerRepo.recentKinds(ds, math.max(1, cfg.scheduler.shareWindow)).map(JobType.of))
    Inputs(cands, perManager.flatMap((m, is) => is.map(i => i.id -> (m, i))).toMap, res)

  /** One pass of the supervisor's tick: decide, start through `start` (the supervisor's dispatch of one item:
    * Right when its run was created), and keep the pass for the explanations. */
  def pass(ms: List[ManagerInfo], now: Instant, stop: Option[Block])(start: (ManagerInfo, ManagerItem) => Either[String, Unit]): Decision =
    val in = inputs(ms, now, stop)
    val s  = settings
    val d  = SchedulerPolicy.run(in.cands, in.res, s, now)(c => in.items.get(c.id).fold(Left("the item is gone"))(start(_, _)))
    last = Some(Snapshot(now, dry = false, in, s, d))
    d

  /** Read the plan pools on their schedules (`agents.quota.*.refresh-every-minutes`): while the autopilot is
    * on and the last pass left work waiting on a pool, so the next passes decide on current numbers. Nothing
    * while draining. One line per reading taken. */
  def refreshPlans(now: Instant): List[String] =
    val wanted = if agents.drain.draining then Set.empty[String] else wantedPools()
    val autopilotOn = agents.autopilot.on
    PlanQuota.PoolId.values.toList.flatMap { p =>
      agents.pools.refreshOnSchedule(p, autopilotOn, wanted.contains(p.wire), now).toOption
        .map(r => s"plan refresh ${p.wire} (${PlanQuota.RefreshTrigger.Schedule.wire}): ${r.text}")
    }

  private def wantedPools(): Set[String] =
    last.fold(Set.empty[String])(snap => SchedulerPolicy.poolsWanted(snap.d.waiting.map(_._1.c), chainPools(snap.in, _)))

  /** The pools every model of `c`'s role chains draws on, whether or not it is usable now. */
  private def chainPools(in: Inputs, c: Candidate): List[String] =
    val over = in.items.get(c.id).flatMap(_._1.config.models).getOrElse(Nil)
    ManagerSupervisor.pipelineOf(c.kind).roles.flatMap { r =>
      agents.picker.chainFor(r, over.find(o => Role.parse(o.role).contains(r)).map(_.model)).getOrElse(Nil)
    }.flatMap(agents.pools.poolOf).map(_.wire).distinct

  /** The managers that may start runs (active, of a kind the supervisor runs). */
  def live(): List[ManagerInfo] =
    ManagerRepo.list(ds).filter(m => State.parse(m.state).contains(State.Active) &&
      Kind.parse(m.kind).exists(k => k == Kind.Review || k == Kind.Enrich || k == Kind.Seed || k == Kind.Watch))

  /** A pass that starts nothing: what one would do now. */
  private def dry(now: Instant): Snapshot =
    val in = inputs(live(), now, agents.drain.block)
    val s  = settings
    Snapshot(now, dry = true, in, s, SchedulerPolicy.run(in.cands, in.res, s, now)(_ => Right(())))

  // ── what it shows ──────────────────────────────────────────────────────────────────────────────

  /** The status: the last pass (or a dry one), the first `limit` waiting items (default 50). */
  def status(limit: Option[Int] = None, now: Instant = Instant.now(), changes: List[String] = Nil): SchedulerStatus =
    val snap = last.getOrElse(dry(now))
    val s = snap.s
    val shown = math.max(0, limit.getOrElse(50))
    val waits = ordered(snap.d)
    val res = snap.in.res
    SchedulerStatus(s.strategy.name, if chosen.isDefined then "person" else "config",
      Strategies.all.map(x => StrategyLine(x.name, x.describe)), Some(snap.at.toString), snap.dry, res.maxRuns, res.occupied,
      res.poolCap.toList.sortBy(_._1).map((p, cap) => PoolUse(p, res.poolUsed.getOrElse(p, 0), cap)),
      JobType.values.toList.map(t => TypeLine(t.wire, t.label, s.weight(t), s.priorityOf(t), res.recent.count(_ == t),
        waits.count(_._1.c.jobType == t))),
      snap.d.started.map((x, why) => Started(x.c.id, x.c.managerId, x.c.kind, x.c.jobType.wire, x.c.target, x.c.goal, why)),
      waits.take(shown).map(wire), waits.size, ManagerRepo.forPerson(ds), res.stop.map(_.why),
      List(s"aging: 1 level per ${s.agingHours} h", s"max wait ${s.maxWaitHours} h", s"min share ${s.minShare}",
        s"gathering places ${s.gatheringSlots}", s"pool overcommit ${math.max(1, cfg.scheduler.poolOvercommit)}"), changes)

  /** The waiting items in the order shown: the ones in the order first (by place), then the rest. */
  private def ordered(d: Decision): List[(Scored, SchedulerPolicy.Wait)] =
    val (inOrder, rest) = d.waiting.partition(_._2.position > 0)
    inOrder.sortBy(_._2.position) ++ rest

  private def wire(x: (Scored, SchedulerPolicy.Wait)): Waiting =
    val (sc, w) = x
    Waiting(sc.c.id, sc.c.managerId, sc.c.kind, sc.c.jobType.wire, sc.c.target, sc.c.goal, sc.effective, sc.waitedMinutes,
      w.code, w.why, w.behind, w.position)

  /** Why one item (its id, or a target among the open items) is where it is. */
  def why(ref: String, now: Instant = Instant.now()): Either[ApiError, WhyInfo] =
    val r = ref.trim
    val item = r.stripPrefix("#").toLongOption.flatMap(ManagerRepo.item(ds, _))
      .orElse(ManagerRepo.openFor(ds, r).headOption)
    item.toRight(ApiError(s"no manager item '$r' (an item id, or the target of an open item)", Some(Write.CodeNotFound))).map { i =>
      val base = WhyInfo(i.id, i.managerId, i.kind, i.target, i.status, run = i.runId)
      ItemStatus.parse(i.status) match
        case Some(ItemStatus.Pending) =>
          val snap = last.getOrElse(dry(now))
          snap.d.started.find(_._1.c.id == i.id) match
            case Some((_, w)) => base.copy(code = AgentScheduler.Code.Running, why = s"started by the last pass: $w")
            case None =>
              snap.d.waiting.find(_._1.c.id == i.id) match
                case Some((_, w)) => base.copy(code = w.code, why = w.why, behind = w.behind, position = w.position)
                case None =>
                  val notBefore = i.notBefore.flatMap(ManagerPolicy.parseInstant).filter(_.isAfter(now))
                  val paused = ManagerRepo.get(ds, i.managerId).filterNot(_.state == State.Active.wire)
                  if i.kind == OaIngest then base.copy(code = AgentScheduler.Code.NotDue,
                    why = "a legal open-access ingest: the ingest backlog drains it within the day's ingest allowance (no AI allowance)")
                  else notBefore match
                    case Some(t) => base.copy(code = AgentScheduler.Code.NotDue, why = s"not due before $t (a cooldown or a failed run's backoff)")
                    case None => paused match
                      case Some(m) => base.copy(code = AgentScheduler.Code.Paused, why = s"its manager ${m.id} is ${m.state}: it starts nothing")
                      case None    => base.copy(why = "not looked at yet: the next pass decides it")
        case Some(ItemStatus.Held) =>
          i.hold match
            case Some(hd) if hd.untilApproval.isDefined =>
              base.copy(code = AgentScheduler.Code.Person, approval = hd.untilApproval,
                why = s"waits for a person to decide approval #${hd.untilApproval.get}; it holds no place")
            case Some(hd) => base.copy(code = AgentScheduler.Code.Held, why = s"held: ${hd.reason}" +
                hd.until.fold("")(u => s" (until $u)") + hd.untilSource.fold("")(u => s" (until $u is in the corpus)"))
            case None => base.copy(code = AgentScheduler.Code.Held, why = "held")
        case Some(ItemStatus.Active) =>
          val run = i.runId.flatMap(AgentRepo.get(ds, _))
          run.map(_.status).flatMap(RunState.parse) match
            case Some(RunState.WaitingApproval) =>
              val open = run.toList.flatMap(r => AgentRepo.approvalsOf(ds, r.id, None)).filter(_.state == ApprovalState.Open.wire)
              base.copy(code = AgentScheduler.Code.Person, approval = open.lastOption.map(_.id),
                why = s"its run waits for a person to decide ${open.lastOption.fold("an approval")(a => s"approval #${a.id} (${a.kind})")}; " +
                  "it holds no place until then")
            case Some(RunState.Paused) => base.copy(code = AgentScheduler.Code.Paused,
                why = s"its run is paused: ${run.flatMap(_.error).getOrElse("")}".trim)
            case Some(st) => base.copy(code = AgentScheduler.Code.Running, why = s"its run is ${st.wire}")
            case None => base.copy(code = AgentScheduler.Code.Running, why = "its run is being evaluated")
        case _ => base.copy(code = AgentScheduler.Code.Settled, why = s"settled: ${i.outcome.getOrElse(i.status)}")
    }

  // ── the stall report ───────────────────────────────────────────────────────────────────────────

  /** Every unfinished goal with no progress in `days` days (default `agents.autopilot.stall-days`; a goal's
    * creation counts as progress), the one reason it stands still and what unblocks it ([[Stalls.classify]]):
    * waiting for a person, draining, paused, a budget, quota, the scheduler's queue, a block, a failed item,
    * no candidates, idle. */
  def stalls(days: Option[Int] = None, now: Instant = Instant.now()): Stalls.StallReport =
    val n        = math.max(0, days.getOrElse(cfg.autopilot.stallDays))
    val goals    = FocusRepo.goals(ds).filterNot(g => GoalFocus.finished(g.status))
    val progress = ManagerRepo.lastProgress(ds)
    val articles = GoalLinkRepo.articlesByGoal(ds)
    val bySubj   = AgentRepo.openLight(ds).groupBy(_.subject)
    val held     = ManagerRepo.personHeldByGoal(ds)
    val paused   = ManagerRepo.light(ds).filter(_.state == State.Paused.wire).flatMap(m => m.goal.map(_ -> m.id)).groupMap(_._1)(_._2)
    val snap     = last.getOrElse(dry(now))
    val waits    = snap.d.waiting.groupMap(_._1.c.goal)(x => Stalls.SchedWait(x._2.code, x._2.why, x._2.position))
    val stops    = FocusRepo.lastStops(ds)
    val sources  = FocusRepo.sourceCounts(ds)
    val sweeps   = ManagerRepo.sweepOutcomes(ds)
    val pace     = ManagerRepo.pace(ds, now.minusSeconds(7L * 86400L))
    val running  = ManagerRepo.runningByGoal(ds)
    val draining = agents.drain.draining
    val facts = goals.map { g =>
      val last = (progress.get(g.slug).toList :+ g.createdAt).flatMap(ManagerPolicy.parseInstant).maxOption
      val idle = last.map(t => java.time.Duration.between(t, now).toDays.toInt)
      val aps  = (bySubj.getOrElse(Some(g.slug), Nil) ++ articles.getOrElse(g.slug, Nil).flatMap(a => bySubj.getOrElse(Some(a), Nil)))
        .map(o => o.id -> o.kind).distinct
      Stalls.Facts(g.slug, g.title, g.status, last.map(_.toString), idle, aps, held.getOrElse(g.slug, 0), paused.getOrElse(g.slug, Nil),
        draining, waits.getOrElse(Some(g.slug), Nil), stops.get(g.slug), sources.getOrElse(g.slug, 0),
        sweeps.get(g.slug).exists(_.contains("no new candidates")),
        List(seedId(g.slug), watchId(g.slug)).flatMap(pace.get).map(_._1).sum, running.getOrElse(g.slug, 0))
    }
    Stalls.report(facts, n, now.toString)

  // ── a person's switch ──────────────────────────────────────────────────────────────────────────

  /** Switch the strategy live (`default` = back to agents.scheduler.strategy); the next tick uses it (nudged
    * now). Answers what a pass would do with it. */
  def set(in: SchedulerSet, now: Instant = Instant.now()): Either[ApiError, SchedulerStatus] =
    val by = in.by.map(_.trim).filter(_.nonEmpty).getOrElse("person")
    val name = in.strategy.trim.toLowerCase(java.util.Locale.ROOT)
    val done: Either[ApiError, String] =
      if name == "default" then
        AutopilotRepo.setStrategy(ds, None, by)
        Right(s"strategy back to agents.scheduler.strategy (${SchedulerPolicy.settings(cfg.scheduler).strategy.name}) (by $by)")
      else Strategies.parse(name) match
        case None => Left(ApiError(s"unknown strategy '${in.strategy}' (one of: ${Strategies.names.mkString(", ")}, default)"))
        case Some(st) =>
          AutopilotRepo.setStrategy(ds, Some(st.name), by)
          Right(s"strategy ${st.name} (by $by)")
    done.map { line =>
      last = None
      val _ = h.ops.queueAdd("log", QueueAdd(s"agent scheduler: $line".take(1000)))
      scribe.info(s"agent scheduler: $line")
      agents.managers.nudge()
      status(None, now, List(line))
    }
