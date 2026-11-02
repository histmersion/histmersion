package com.histmersion.domain

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.SchedulerPolicy.*

import java.time.Instant

/** The central scheduler, pure: each strategy's order, aging, the max wait, the minimum share per job type, the
  * gathering places, the resources (room, plan pools, a manager's own cap, a block, a stop), a start that fails, and
  * the explanations. A simulated clock and fake producers: no database, no provider.
  */
class SchedulerPolicySuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-24T12:00:00Z")
  private def ago(h: Int) = now.minusSeconds(h.toLong * 3600L)
  private def day(d: Int) = Some(Instant.parse(f"2026-10-$d%02dT00:00:00Z"))

  private def c(
      id: Long,
      kind: String = ArticleReview,
      goal: Option[String] = Some("g"),
      prio: Int = 0,
      created: Option[Instant] = day(1),
      waited: Int = 0,
      pools: List[String] = Nil,
      block: Option[Block] = None,
      gathering: Boolean = false,
      manager: String = "m"
  ) =
    Candidate(id, manager, kind, s"t$id", goal, prio, created, gathering, 0, ago(waited), pools, block)

  private val typePrio = parseTypeMap(
    "review=9, compile=8, extend=7, filter=6, propose=5, goal-plan=4, enrich=3, sweep=2, currency=1"
  )
  private def settings(
      st: Strategy,
      aging: Int = 24,
      maxWait: Int = 72,
      minShare: Double = 0.0,
      weights: String = "",
      gathering: Int = 0
  ) =
    Settings(st, aging, maxWait, minShare, parseTypeMap(weights), typePrio, gathering)

  private def res(room: Int, recent: List[JobType] = Nil) = Resources(maxRuns = room, occupied = 0, recent = recent)
  private val ok: Candidate => Either[String, Unit] = _ => Right(())
  private def ids(d: Decision) = d.started.map(_._1.c.id)

  // ── goal-first ─────────────────────────────────────────────────────────────────────────────────

  test("goal-first: the older goal of the same priority takes the place; the newer one waits behind it, saying why"):
    val cs = List(
      c(1, goal = Some("new"), created = day(20)),
      c(2, goal = Some("old"), created = day(2)),
      c(3, goal = Some("new"), created = day(20))
    )
    val d = run(cs, res(1), settings(GoalFirst), now)(ok)
    assertEquals(ids(d), List(2L))
    val (_, w) = d.waiting.find(_._1.c.id == 1).get
    assertEquals(w.code, Code.Capacity)
    assertEquals(w.position, 1)
    assert(w.why.contains("goal old") && w.why.contains("goal-first"), w.why)
    assertEquals(w.behind, List("#2 article-review t2"))

  test("goal-first is work-conserving: what the first goal cannot use goes to the next one"):
    val cs = List(
      c(1, goal = Some("old"), created = day(2)),
      c(2, goal = Some("new"), created = day(20)),
      c(3, goal = Some("newer"), created = day(22))
    )
    assertEquals(ids(run(cs, res(2), settings(GoalFirst), now)(ok)), List(1L, 2L))

  test("goal-first: a higher priority beats age; inside a goal the job type's priority orders"):
    val cs = List(
      c(1, ProposeArticles, Some("old"), created = day(1)),
      c(2, ArticleReview, Some("old"), created = day(1)),
      c(3, AcquisitionSweep, Some("urgent"), prio = 5, created = day(23))
    )
    assertEquals(ids(run(cs, res(3), settings(GoalFirst), now)(ok)), List(3L, 2L, 1L))

  test("library work (no goal) goes after every goal of the same priority"):
    val cs = List(c(1, SourceEnrich, None, created = None), c(2, SourceEnrich, Some("g"), created = day(20)))
    assertEquals(ids(run(cs, res(1), settings(GoalFirst), now)(ok)), List(2L))

  // ── type-balanced ──────────────────────────────────────────────────────────────────────────────

  test("type-balanced: the starts follow the weights; inside a type the best goal first"):
    val cs = List(
      c(1, ArticleReview, Some("b"), created = day(5)),
      c(2, ArticleReview, Some("a"), created = day(1)),
      c(3, AcquisitionSweep, Some("z"), created = day(9)),
      c(4, ArticleReview, Some("c"), created = day(7)),
      c(5, AcquisitionSweep, Some("y"), prio = 2, created = day(10))
    )
    val d = run(cs, res(3), settings(TypeBalanced, weights = "review=2, sweep=1"), now)(ok)
    // review (1/2) before sweep (1/1); then review (2/2) ties sweep (1/1): the type's priority; then sweep
    assertEquals(ids(d), List(2L, 1L, 5L))
    assert(d.started.head._2.contains("type-balanced"), d.started.head._2)

  test("type-balanced counts the recent starts: a type that had many waits for the others"):
    val cs = List(c(1, ArticleReview), c(2, SourceEnrich))
    val recent = List.fill(6)(JobType.Review)
    assertEquals(
      ids(run(cs, res(1, recent), settings(TypeBalanced, weights = "review=3, enrich=1"), now)(ok)),
      List(2L)
    )

  test("type-balanced: a type of weight 0 starts only through the minimum share or the max wait"):
    val cs = List(c(1, CurrencyCheck), c(2, ArticleReview))
    val s = settings(TypeBalanced, weights = "currency=0, review=1")
    assertEquals(ids(run(cs, res(1), s, now)(ok)), List(2L))
    assertEquals(ids(run(List(c(1, CurrencyCheck, waited = 80), c(2, ArticleReview)), res(1), s, now)(ok)), List(1L))

  // ── anti-starvation ────────────────────────────────────────────────────────────────────────────

  test("aging: one level per aging-hours waited lifts a low goal's item over a fresh higher one"):
    val cs =
      List(c(1, goal = Some("hi"), prio = 1, created = day(1)), c(2, goal = Some("lo"), created = day(20), waited = 49))
    val d = run(cs, res(1), settings(GoalFirst, aging = 24), now)(ok)
    assertEquals(ids(d), List(2L), "2 levels aged beat priority 1")
    assertEquals(ids(run(cs, res(1), settings(GoalFirst, aging = 0), now)(ok)), List(1L), "no aging: priority wins")

  test("max wait: an item that waited max-wait-hours is admitted next, the longest first"):
    val cs = List(
      c(1, goal = Some("hi"), prio = 50),
      c(2, goal = Some("lo"), waited = 73),
      c(3, goal = Some("lo2"), waited = 100)
    )
    val d = run(cs, res(2), settings(GoalFirst, aging = 0, maxWait = 72), now)(ok)
    assertEquals(ids(d), List(3L, 2L))
    assert(d.started.head._2.contains("max-wait"), d.started.head._2)

  test("minimum share: a type with no start among the last ceil(1/share) starts goes next"):
    val cs = List(c(1, ArticleReview, prio = 9), c(2, SourceEnrich))
    val recent = List.fill(20)(JobType.Review)
    val d = run(cs, res(1, recent), settings(GoalFirst, minShare = 0.05), now)(ok)
    assertEquals(ids(d), List(2L))
    assert(d.started.head._2.contains("minimum share"), d.started.head._2)
    // too few starts to measure a share: the strategy decides
    assertEquals(ids(run(cs, res(1, recent.take(5)), settings(GoalFirst, minShare = 0.05), now)(ok)), List(1L))

  test("shareDue: only with enough starts, and only for a type absent from the window"):
    assert(shareDue(JobType.Enrich, List.fill(10)(JobType.Review), 0.1))
    assert(!shareDue(JobType.Enrich, List.fill(9)(JobType.Review), 0.1))
    assert(!shareDue(JobType.Enrich, JobType.Enrich :: List.fill(9)(JobType.Review), 0.1))
    assert(!shareDue(JobType.Enrich, List.fill(10)(JobType.Review), 0.0))

  test("gathering places: a goal still gathering its sources gets its place before the strategy's order"):
    val cs = List(
      c(1, ArticleReview, Some("done"), prio = 9),
      c(2, AcquisitionSweep, Some("new"), gathering = true),
      c(3, ArticleReview, Some("done2"), prio = 9)
    )
    val d = run(cs, res(2), settings(GoalFirst, gathering = 1), now)(ok)
    assertEquals(ids(d), List(2L, 1L))
    assert(d.started.head._2.contains("gathering"), d.started.head._2)
    // its place is taken already (a gathering run in flight): the strategy decides
    assertEquals(ids(run(cs, res(1).copy(gatheringUsed = 1), settings(GoalFirst, gathering = 1), now)(ok)), List(1L))

  // ── resources ──────────────────────────────────────────────────────────────────────────────────

  test("quota: a pool at its runs waits (saying which), another pool's item goes; a block waits whatever the order"):
    val cs = List(
      c(1, pools = List("chatgpt-plan"), prio = 9, goal = Some("a")),
      c(2, pools = List("claude-plan"), goal = Some("b")),
      c(3, block = Some(Block("quota", "reviewer chain below its plan floor")), prio = 99, goal = Some("c"))
    )
    val r = res(3).copy(poolCap = Map("chatgpt-plan" -> 2, "claude-plan" -> 2), poolUsed = Map("chatgpt-plan" -> 2))
    val d = run(cs, r, settings(GoalFirst), now)(ok)
    assertEquals(ids(d), List(2L))
    assertEquals(d.waitOf(1).map(_.code), Some(Code.Pool))
    assert(d.waitOf(1).exists(_.why.contains("chatgpt-plan")))
    assertEquals(d.waitOf(3).map(w => (w.code, w.why)), Some((Code.Blocked, "reviewer chain below its plan floor")))

  test("a pool fills within one pass: the second item on it waits"):
    val cs = List(c(1, pools = List("p")), c(2, pools = List("p")), c(3))
    val d = run(cs, res(3).copy(poolCap = Map("p" -> 1)), settings(GoalFirst), now)(ok)
    assertEquals(ids(d).toSet, Set(1L, 3L))
    assertEquals(d.waitOf(2).map(_.code), Some(Code.Pool))
    assertEquals(d.waitOf(2).map(_.behind), Some(List("#1 article-review t1")))

  test("only runs that run or are queued occupy places: occupied comes off maxRuns"):
    val cs = List(c(1), c(2), c(3))
    assertEquals(ids(run(cs, Resources(maxRuns = 4, occupied = 3), settings(GoalFirst), now)(ok)).size, 1)
    assertEquals(ids(run(cs, Resources(maxRuns = 4, occupied = 5), settings(GoalFirst), now)(ok)), Nil)

  test("a manager's own saved maxRuns still caps it"):
    val cs = List(c(1, manager = "review"), c(2, manager = "review"), c(3, manager = "enrich", kind = SourceEnrich))
    val d =
      run(cs, res(3).copy(managerCap = Map("review" -> 2), managerUsed = Map("review" -> 1)), settings(GoalFirst), now)(
        ok
      )
    assertEquals(ids(d), List(1L, 3L))
    assertEquals(d.waitOf(2).map(_.code), Some(Code.Manager))

  test("stop (drain, a budget): nothing starts, everything waits with the reason, in order"):
    val cs = List(c(1, goal = Some("b"), created = day(9)), c(2, goal = Some("a"), created = day(1)))
    val d =
      run(cs, res(4).copy(stop = Some(Block("drain", "draining: nothing new starts"))), settings(GoalFirst), now)(ok)
    assertEquals(d.started, Nil)
    assertEquals(d.waiting.map(x => (x._1.c.id, x._2.code, x._2.position)), List((2L, "drain", 1), (1L, "drain", 2)))

  test("a start that fails (its target busy) uses no place: the next one goes"):
    val cs = List(c(1, prio = 9, goal = Some("a")), c(2, goal = Some("b")))
    val d = run(cs, res(1), settings(GoalFirst), now)(x =>
      if x.id == 1 then Left("another run writes its target") else Right(())
    )
    assertEquals(ids(d), List(2L))
    assertEquals(d.waitOf(1).map(_.code), Some(Code.Busy))

  test("order: the whole queue as it would start with unlimited room, deterministic"):
    val cs = List(
      c(1, goal = Some("b"), created = day(9)),
      c(2, goal = Some("a"), created = day(1)),
      c(3, goal = Some("a"), created = day(1))
    )
      .map(score(_, settings(GoalFirst), now))
    val o1 = order(cs, res(0), settings(GoalFirst)).map(_._1.c.id)
    assertEquals(o1, List(2L, 3L, 1L))
    assertEquals(order(cs.reverse, res(0), settings(GoalFirst)).map(_._1.c.id), o1)

  test("strategies by name; job types of every item kind; type maps parse leniently"):
    assertEquals(Strategies.parse(" Goal-First ").map(_.name), Some("goal-first"))
    assertEquals(Strategies.parse("nope"), None)
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
      ).map(JobType.of(_).wire),
      List("goal-plan", "propose", "sweep", "currency", "enrich", "compile", "extend", "filter", "review")
    )
    assertEquals(parseTypeMap("review=3, bogus=2, enrich = x, sweep=1"), Map(JobType.Review -> 3, JobType.Sweep -> 1))
    assertEquals(hours(95), "1 h 35 min")
    assertEquals(hours(42), "42 min")

  test("pools wanted: a waiting item's own pools, a quota-starved item's chain pools, none for another block"):
    val chains: Candidate => List[String] =
      x => if x.id == 3 then List("claude-plan", "chatgpt-plan") else List("never")
    val waiting = List(
      c(1, pools = List("chatgpt-plan")),
      c(2),
      c(3, block = Some(Block(QuotaBlock, "starved: extractor chain below its plan floor"))),
      c(4, pools = List("claude-plan"), block = Some(Block("budget", "budget: today's agent spend reached")))
    )
    assertEquals(poolsWanted(waiting, chains), Set("chatgpt-plan", "claude-plan"))
    assertEquals(poolsWanted(waiting.take(2), chains), Set("chatgpt-plan"))
    assertEquals(poolsWanted(Nil, chains), Set.empty[String])

  test(
    "the settings: a person's strategy over the configured one, limits clamped, configured weights over the built-in"
  ):
    val d = SchedulerDefaults(
      strategy = "goal-first",
      agingHours = -1,
      maxWaitHours = 48,
      minShare = 1.5,
      builtInWeights = "review=3, compile=3",
      typeWeights = "review=5",
      builtInPriorities = "review=9, sweep=2",
      typePriorities = "sweep=4",
      gatheringSlots = -2
    )
    val s = SchedulerPolicy.settings(d)
    assertEquals(s.strategy, GoalFirst)
    assertEquals((s.agingHours, s.maxWaitHours, s.minShare, s.gatheringSlots), (0, 48, 1.0, 0))
    assertEquals(s.weights, parseTypeMap("review=5, compile=3"))
    assertEquals(s.typePriority, parseTypeMap("review=9, sweep=4"))
    assertEquals(SchedulerPolicy.settings(d, Some("type-balanced")).strategy, TypeBalanced)
    assertEquals(SchedulerPolicy.settings(d.copy(strategy = "no-such"), Some("nor-this")).strategy, Strategies.all.head)
