package com.histmersion
package domain

import com.histmersion.domain.schedulerpolicy.Candidate
import com.histmersion.domain.schedulerpolicy.Block
import com.histmersion.domain.schedulerpolicy.Settings
import com.histmersion.domain.schedulerpolicy.Resources
import com.histmersion.domain.schedulerpolicy.JobType
import com.histmersion.domain.schedulerpolicy.Decision
import com.histmersion.domain.schedulerpolicy.Scored

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.SchedulerPolicy.*

import java.time.Instant
import com.histmersion.lint.{tolerated, LintRule}

/** The central scheduler, pure: each strategy's order, aging, the max wait, the minimum share per job type, the
  * gathering places, the resources (room, plan pools, a manager's own cap, a block, a stop), a start that fails, and
  * the explanations. A simulated clock and fake producers: no database, no provider.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class SchedulerPolicySuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-24T12:00:00Z")
  private def ago(h: Int) = now.minusSeconds(h.toLong * 3600L)
  private def day(d: Int) = Some(Instant.parse(f"2026-10-$d%02dT00:00:00Z"))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def c(
      id: Candidate.Id,
      kind: Candidate.Kind = Candidate.Kind(ArticleReview),
      goal: Option[Candidate.Goal] = Some(Candidate.Goal("g")),
      prio: Candidate.GoalPriority = Candidate.GoalPriority(0),
      created: Option[Instant] = day(1),
      waited: Int = 0,
      pools: List[Candidate.Pools] = Nil,
      block: Option[Block] = None,
      gathering: Candidate.Gathering = Candidate.Gathering(false),
      manager: Candidate.ManagerId = Candidate.ManagerId("m")
  ) =
    Candidate(
      id,
      manager,
      kind,
      Candidate.Target(s"t$id"),
      goal,
      prio,
      created,
      gathering,
      Candidate.ItemPriority(0),
      ago(waited),
      pools,
      block
    )

  private val typePrio = parsePriorities(
    "review=9, compile=8, extend=7, filter=6, propose=5, goal-plan=4, enrich=3, sweep=2, currency=1"
  )
  private def settings(
      st: domain.schedulerpolicy.Strategy,
      aging: Settings.AgingHours = Settings.AgingHours(24),
      maxWait: Settings.MaxWaitHours = Settings.MaxWaitHours(72),
      minShare: Settings.MinShare = Settings.MinShare(0.0),
      weights: String = "",
      gathering: Settings.GatheringSlots = Settings.GatheringSlots(0)
  ) =
    Settings(st, aging, maxWait, minShare, parseWeights(weights), typePrio, gathering)

  private def res(maxRuns: Resources.MaxRuns, recent: List[JobType] = Nil) =
    Resources(maxRuns = maxRuns, occupied = Resources.Occupied(0), recent = recent)
  private val ok: Candidate => Either[Block.Refusal, Unit] = _ => Right(())
  private def ids(d: Decision) = d.started.map(_._1.c.id)

  // ── goal-first ─────────────────────────────────────────────────────────────────────────────────

  test("goal-first: the older goal of the same priority takes the place; the newer one waits behind it, saying why"):
    val cs = List(
      c(Candidate.Id(1), goal = Some(Candidate.Goal("new")), created = day(20)),
      c(Candidate.Id(2), goal = Some(Candidate.Goal("old")), created = day(2)),
      c(Candidate.Id(3), goal = Some(Candidate.Goal("new")), created = day(20))
    )
    val d = run(cs, res(Resources.MaxRuns(1)), settings(GoalFirst), now)(ok)
    assertEquals(ids(d), List(2L))
    val (_, w) = d.waiting.find(_._1.c.id.value == 1).get
    assertEquals(w.code, Code.Capacity)
    assertEquals(w.position, 1)
    assert(w.why.render.contains("goal old") && w.why.render.contains("goal-first"), w.why)
    assertEquals(w.behind, List("#2 article-review t2"))

  test("goal-first is work-conserving: what the first goal cannot use goes to the next one"):
    val cs = List(
      c(Candidate.Id(1), goal = Some(Candidate.Goal("old")), created = day(2)),
      c(Candidate.Id(2), goal = Some(Candidate.Goal("new")), created = day(20)),
      c(Candidate.Id(3), goal = Some(Candidate.Goal("newer")), created = day(22))
    )
    assertEquals(ids(run(cs, res(Resources.MaxRuns(2)), settings(GoalFirst), now)(ok)), List(1L, 2L))

  test("goal-first: a higher priority beats age; inside a goal the job type's priority orders"):
    val cs = List(
      c(Candidate.Id(1), Candidate.Kind(ProposeArticles), Some(Candidate.Goal("old")), created = day(1)),
      c(Candidate.Id(2), Candidate.Kind(ArticleReview), Some(Candidate.Goal("old")), created = day(1)),
      c(
        Candidate.Id(3),
        Candidate.Kind(AcquisitionSweep),
        Some(Candidate.Goal("urgent")),
        prio = Candidate.GoalPriority(5),
        created = day(23)
      )
    )
    assertEquals(ids(run(cs, res(Resources.MaxRuns(3)), settings(GoalFirst), now)(ok)), List(3L, 2L, 1L))

  test("library work (no goal) goes after every goal of the same priority"):
    val cs = List(
      c(Candidate.Id(1), Candidate.Kind(SourceEnrich), None, created = None),
      c(Candidate.Id(2), Candidate.Kind(SourceEnrich), Some(Candidate.Goal("g")), created = day(20))
    )
    assertEquals(ids(run(cs, res(Resources.MaxRuns(1)), settings(GoalFirst), now)(ok)), List(2L))

  // ── type-balanced ──────────────────────────────────────────────────────────────────────────────

  test("type-balanced: the starts follow the weights; inside a type the best goal first"):
    val cs = List(
      c(Candidate.Id(1), Candidate.Kind(ArticleReview), Some(Candidate.Goal("b")), created = day(5)),
      c(Candidate.Id(2), Candidate.Kind(ArticleReview), Some(Candidate.Goal("a")), created = day(1)),
      c(Candidate.Id(3), Candidate.Kind(AcquisitionSweep), Some(Candidate.Goal("z")), created = day(9)),
      c(Candidate.Id(4), Candidate.Kind(ArticleReview), Some(Candidate.Goal("c")), created = day(7)),
      c(
        Candidate.Id(5),
        Candidate.Kind(AcquisitionSweep),
        Some(Candidate.Goal("y")),
        prio = Candidate.GoalPriority(2),
        created = day(10)
      )
    )
    val d = run(cs, res(Resources.MaxRuns(3)), settings(TypeBalanced, weights = "review=2, sweep=1"), now)(ok)
    // review (1/2) before sweep (1/1); then review (2/2) ties sweep (1/1): the type's priority; then sweep
    assertEquals(ids(d), List(2L, 1L, 5L))
    assert(d.started.head._2.value.contains("type-balanced"), d.started.head._2)

  test("type-balanced counts the recent starts: a type that had many waits for the others"):
    val cs = List(c(Candidate.Id(1), Candidate.Kind(ArticleReview)), c(Candidate.Id(2), Candidate.Kind(SourceEnrich)))
    val recent = List.fill(6)(JobType.Review)
    assertEquals(
      ids(run(cs, res(Resources.MaxRuns(1), recent), settings(TypeBalanced, weights = "review=3, enrich=1"), now)(ok)),
      List(2L)
    )

  test("type-balanced: a type of weight 0 starts only through the minimum share or the max wait"):
    val cs = List(c(Candidate.Id(1), Candidate.Kind(CurrencyCheck)), c(Candidate.Id(2), Candidate.Kind(ArticleReview)))
    val s = settings(TypeBalanced, weights = "currency=0, review=1")
    assertEquals(ids(run(cs, res(Resources.MaxRuns(1)), s, now)(ok)), List(2L))
    assertEquals(
      ids(
        run(
          List(
            c(Candidate.Id(1), Candidate.Kind(CurrencyCheck), waited = 80),
            c(Candidate.Id(2), Candidate.Kind(ArticleReview))
          ),
          res(Resources.MaxRuns(1)),
          s,
          now
        )(ok)
      ),
      List(1L)
    )

  // ── anti-starvation ────────────────────────────────────────────────────────────────────────────

  test("aging: one level per aging-hours waited lifts a low goal's item over a fresh higher one"):
    val cs =
      List(
        c(Candidate.Id(1), goal = Some(Candidate.Goal("hi")), prio = Candidate.GoalPriority(1), created = day(1)),
        c(Candidate.Id(2), goal = Some(Candidate.Goal("lo")), created = day(20), waited = 49)
      )
    val d = run(cs, res(Resources.MaxRuns(1)), settings(GoalFirst, aging = Settings.AgingHours(24)), now)(ok)
    assertEquals(ids(d), List(2L), "2 levels aged beat priority 1")
    assertEquals(
      ids(run(cs, res(Resources.MaxRuns(1)), settings(GoalFirst, aging = Settings.AgingHours(0)), now)(ok)),
      List(1L),
      "no aging: priority wins"
    )

  test("max wait: an item that waited max-wait-hours is admitted next, the longest first"):
    val cs = List(
      c(Candidate.Id(1), goal = Some(Candidate.Goal("hi")), prio = Candidate.GoalPriority(50)),
      c(Candidate.Id(2), goal = Some(Candidate.Goal("lo")), waited = 73),
      c(Candidate.Id(3), goal = Some(Candidate.Goal("lo2")), waited = 100)
    )
    val d = run(
      cs,
      res(Resources.MaxRuns(2)),
      settings(GoalFirst, aging = Settings.AgingHours(0), maxWait = Settings.MaxWaitHours(72)),
      now
    )(ok)
    assertEquals(ids(d), List(3L, 2L))
    assert(d.started.head._2.value.contains("max-wait"), d.started.head._2)

  test("minimum share: a type with no start among the last ceil(1/share) starts goes next"):
    val cs = List(
      c(Candidate.Id(1), Candidate.Kind(ArticleReview), prio = Candidate.GoalPriority(9)),
      c(Candidate.Id(2), Candidate.Kind(SourceEnrich))
    )
    val recent = List.fill(20)(JobType.Review)
    val d = run(cs, res(Resources.MaxRuns(1), recent), settings(GoalFirst, minShare = Settings.MinShare(0.05)), now)(ok)
    assertEquals(ids(d), List(2L))
    assert(d.started.head._2.value.contains("minimum share"), d.started.head._2)
    // too few starts to measure a share: the strategy decides
    assertEquals(
      ids(
        run(
          cs,
          res(Resources.MaxRuns(1), recent.take(5)),
          settings(GoalFirst, minShare = Settings.MinShare(0.05)),
          now
        )(ok)
      ),
      List(1L)
    )

  test("shareDue: only with enough starts, and only for a type absent from the window"):
    assert(JobType.Enrich.shareDue(List.fill(10)(JobType.Review), Settings.MinShare(0.1)))
    assert(!JobType.Enrich.shareDue(List.fill(9)(JobType.Review), Settings.MinShare(0.1)))
    assert(!JobType.Enrich.shareDue(JobType.Enrich :: List.fill(9)(JobType.Review), Settings.MinShare(0.1)))
    assert(!JobType.Enrich.shareDue(List.fill(10)(JobType.Review), Settings.MinShare(0.0)))

  test("gathering places: a goal still gathering its sources gets its place before the strategy's order"):
    val cs = List(
      c(Candidate.Id(1), Candidate.Kind(ArticleReview), Some(Candidate.Goal("done")), prio = Candidate.GoalPriority(9)),
      c(
        Candidate.Id(2),
        Candidate.Kind(AcquisitionSweep),
        Some(Candidate.Goal("new")),
        gathering = Candidate.Gathering(true)
      ),
      c(Candidate.Id(3), Candidate.Kind(ArticleReview), Some(Candidate.Goal("done2")), prio = Candidate.GoalPriority(9))
    )
    val d = run(cs, res(Resources.MaxRuns(2)), settings(GoalFirst, gathering = Settings.GatheringSlots(1)), now)(ok)
    assertEquals(ids(d), List(2L, 1L))
    assert(d.started.head._2.value.contains("gathering"), d.started.head._2)
    // its place is taken already (a gathering run in flight): the strategy decides
    assertEquals(
      ids(
        run(
          cs,
          res(Resources.MaxRuns(1)).modify(_.gatheringUsed).setTo(Resources.GatheringUsed(1)),
          settings(GoalFirst, gathering = Settings.GatheringSlots(1)),
          now
        )(ok)
      ),
      List(1L)
    )

  // ── resources ──────────────────────────────────────────────────────────────────────────────────

  test("quota: a pool at its runs waits (saying which), another pool's item goes; a block waits whatever the order"):
    val cs = List(
      c(
        Candidate.Id(1),
        pools = List(Candidate.Pools("chatgpt-plan")),
        prio = Candidate.GoalPriority(9),
        goal = Some(Candidate.Goal("a"))
      ),
      c(Candidate.Id(2), pools = List(Candidate.Pools("claude-plan")), goal = Some(Candidate.Goal("b"))),
      c(
        Candidate.Id(3),
        block = Some(Block(Block.Why.Starved(Block.Chain("reviewer chain below its plan floor")))),
        prio = Candidate.GoalPriority(99),
        goal = Some(Candidate.Goal("c"))
      )
    )
    val r = res(Resources.MaxRuns(3))
      .modify(_.poolCap)
      .setTo(
        Map(
          Candidate.Pools("chatgpt-plan") -> Resources.PoolCapValue(2),
          Candidate.Pools("claude-plan") -> Resources.PoolCapValue(2)
        )
      )
      .modify(_.poolUsed)
      .setTo(Map(Candidate.Pools("chatgpt-plan") -> Resources.PoolUsedValue(2)))
    val d = run(cs, r, settings(GoalFirst), now)(ok)
    assertEquals(ids(d), List(2L))
    assertEquals(d.waitOf(Candidate.Id(1)).map(_.code), Some(Code.Pool))
    assert(d.waitOf(Candidate.Id(1)).exists(_.why.render.contains("chatgpt-plan")))
    assertEquals(
      d.waitOf(Candidate.Id(3)).map(w => (w.code, w.why)),
      Some((Code.Blocked, Block.Why.Starved(Block.Chain("reviewer chain below its plan floor"))))
    )

  test("a pool fills within one pass: the second item on it waits"):
    val cs = List(
      c(Candidate.Id(1), pools = List(Candidate.Pools("p"))),
      c(Candidate.Id(2), pools = List(Candidate.Pools("p"))),
      c(Candidate.Id(3))
    )
    val d = run(
      cs,
      res(Resources.MaxRuns(3)).modify(_.poolCap).setTo(Map(Candidate.Pools("p") -> Resources.PoolCapValue(1))),
      settings(GoalFirst),
      now
    )(ok)
    assertEquals(ids(d).toSet, Set(1L, 3L))
    assertEquals(d.waitOf(Candidate.Id(2)).map(_.code), Some(Code.Pool))
    assertEquals(d.waitOf(Candidate.Id(2)).map(_.behind), Some(List("#1 article-review t1")))

  test("only runs that run or are queued occupy places: occupied comes off maxRuns"):
    val cs = List(c(Candidate.Id(1)), c(Candidate.Id(2)), c(Candidate.Id(3)))
    assertEquals(
      ids(
        run(
          cs,
          Resources(maxRuns = Resources.MaxRuns(4), occupied = Resources.Occupied(3)),
          settings(GoalFirst),
          now
        )(ok)
      ).size,
      1
    )
    assertEquals(
      ids(
        run(
          cs,
          Resources(maxRuns = Resources.MaxRuns(4), occupied = Resources.Occupied(5)),
          settings(GoalFirst),
          now
        )(ok)
      ),
      Nil
    )

  test("a manager's own saved maxRuns still caps it"):
    val cs = List(
      c(Candidate.Id(1), manager = Candidate.ManagerId("review")),
      c(Candidate.Id(2), manager = Candidate.ManagerId("review")),
      c(Candidate.Id(3), manager = Candidate.ManagerId("enrich"), kind = Candidate.Kind(SourceEnrich))
    )
    val d =
      run(
        cs,
        res(Resources.MaxRuns(3))
          .modify(_.managerCap)
          .setTo(Map(Candidate.ManagerId("review") -> Resources.ManagerCapValue(2)))
          .modify(_.managerUsed)
          .setTo(Map(Candidate.ManagerId("review") -> Resources.ManagerUsedValue(1))),
        settings(GoalFirst),
        now
      )(
        ok
      )
    assertEquals(ids(d), List(1L, 3L))
    assertEquals(d.waitOf(Candidate.Id(2)).map(_.code), Some(Code.Manager))

  test("stop (drain, a budget): nothing starts, everything waits with the reason, in order"):
    val cs = List(
      c(Candidate.Id(1), goal = Some(Candidate.Goal("b")), created = day(9)),
      c(Candidate.Id(2), goal = Some(Candidate.Goal("a")), created = day(1))
    )
    val d =
      run(
        cs,
        res(Resources.MaxRuns(4))
          .modify(_.stop)
          .setTo(Some(Block(Block.Why.Draining(Block.Since("2026-11-01"), None)))),
        settings(GoalFirst),
        now
      )(ok)
    assertEquals(d.started, Nil)
    assertEquals(d.waiting.map(x => (x._1.c.id, x._2.code, x._2.position)), List((2L, "drain", 1), (1L, "drain", 2)))

  test("a start that fails (its target busy) uses no place: the next one goes"):
    val cs = List(
      c(Candidate.Id(1), prio = Candidate.GoalPriority(9), goal = Some(Candidate.Goal("a"))),
      c(Candidate.Id(2), goal = Some(Candidate.Goal("b")))
    )
    val d = run(cs, res(Resources.MaxRuns(1)), settings(GoalFirst), now)(x =>
      if x.id.value == 1 then Left(Block.Refusal.TargetWritten) else Right(())
    )
    assertEquals(ids(d), List(2L))
    assertEquals(d.waitOf(Candidate.Id(1)).map(_.code), Some(Code.Busy))

  test("order: the whole queue as it would start with unlimited room, deterministic"):
    val cs = List(
      c(Candidate.Id(1), goal = Some(Candidate.Goal("b")), created = day(9)),
      c(Candidate.Id(2), goal = Some(Candidate.Goal("a")), created = day(1)),
      c(Candidate.Id(3), goal = Some(Candidate.Goal("a")), created = day(1))
    )
      .map(_.score(settings(GoalFirst), now))
    val o1 = order(cs, res(Resources.MaxRuns(0)), settings(GoalFirst)).map(_._1.c.id)
    assertEquals(o1, List(2L, 3L, 1L))
    assertEquals(order(cs.reverse, res(Resources.MaxRuns(0)), settings(GoalFirst)).map(_._1.c.id), o1)

  test("strategies by name; job types of every item kind; type maps parse leniently"):
    assertEquals(Strategies.parse(SchedulerPolicy.StrategyName(" Goal-First ")).map(_.name), Some("goal-first"))
    assertEquals(Strategies.parse(SchedulerPolicy.StrategyName("nope")), None)
    assertEquals(Strategies.names, List("type-balanced", "goal-first"))
    assertEquals(
      List(
        PlanGoalItem,
        ProposeArticles,
        AcquisitionSweep,
        CurrencyCheck,
        SourceEnrich,
        ArticleCompile,
        ArticleExtend,
        InferFilter,
        ArticleReview
      ).map(k => JobType.of(Candidate.Kind(k)).wire),
      List("goal-plan", "propose", "sweep", "currency", "enrich", "compile", "extend", "filter", "review")
    )
    assertEquals(parseWeights("review=3, bogus=2, enrich = x, sweep=1"), Map(JobType.Review -> 3, JobType.Sweep -> 1))
    assertEquals(Scored.hours(Scored.WaitedMinutes(95)), "1 h 35 min")
    assertEquals(Scored.hours(Scored.WaitedMinutes(42)), "42 min")

  test("pools wanted: a waiting item's own pools, a quota-starved item's chain pools, none for another block"):
    val chains: Candidate => List[Candidate.Pools] =
      x =>
        if x.id.value == 3 then List(Candidate.Pools("claude-plan"), Candidate.Pools("chatgpt-plan"))
        else List(Candidate.Pools("never"))
    val waiting = List(
      c(Candidate.Id(1), pools = List(Candidate.Pools("chatgpt-plan"))),
      c(Candidate.Id(2)),
      c(
        Candidate.Id(3),
        block = Some(Block(Block.Why.Starved(Block.Chain("extractor chain below its plan floor"))))
      ),
      c(
        Candidate.Id(4),
        pools = List(Candidate.Pools("claude-plan")),
        block = Some(Block(Block.Why.Budget(Block.SpendCap.Daily(Block.Usd(BigDecimal(5))))))
      )
    )
    assertEquals(poolsWanted(waiting, chains), Set("chatgpt-plan", "claude-plan"))
    assertEquals(poolsWanted(waiting.take(2), chains), Set("chatgpt-plan"))
    assertEquals(poolsWanted(Nil, chains), Set.empty[String])

  test(
    "the settings: a person's strategy over the configured one, limits clamped, configured weights over the built-in"
  ):
    val d = SchedulerDefaults(
      strategy = SchedulerDefaults.Strategy("goal-first"),
      agingHours = SchedulerDefaults.AgingHours(-1),
      maxWaitHours = SchedulerDefaults.MaxWaitHours(48),
      minShare = SchedulerDefaults.MinShare(1.5),
      builtInWeights = SchedulerDefaults.BuiltInWeights("review=3, compile=3"),
      typeWeights = SchedulerDefaults.TypeWeights("review=5"),
      builtInPriorities = SchedulerDefaults.BuiltInPriorities("review=9, sweep=2"),
      typePriorities = SchedulerDefaults.TypePriorities("sweep=4"),
      gatheringSlots = SchedulerDefaults.GatheringSlots(-2)
    )
    val s = SchedulerPolicy.settings(d)
    assertEquals(s.strategy, GoalFirst)
    assertEquals((s.agingHours, s.maxWaitHours, s.minShare, s.gatheringSlots), (0, 48, 1.0, 0))
    assertEquals(s.weights, parseWeights("review=5, compile=3"))
    assertEquals(s.typePriority, parsePriorities("review=9, sweep=4"))
    assertEquals(SchedulerPolicy.settings(d, Some(SchedulerDefaults.Strategy("type-balanced"))).strategy, TypeBalanced)
    assertEquals(
      SchedulerPolicy
        .settings(
          d.modify(_.strategy).setTo(SchedulerDefaults.Strategy("no-such")),
          Some(SchedulerDefaults.Strategy("nor-this"))
        )
        .strategy,
      Strategies.all.head
    )
