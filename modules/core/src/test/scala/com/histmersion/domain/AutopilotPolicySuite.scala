package com.histmersion.domain

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.automation.Agents.{ApprovalKind, RunState}
import com.histmersion.domain.AutopilotPolicy.*
import com.histmersion.domain.ManagerPolicy.{Next, ReviewSettings, StepFacts}
import com.histmersion.api.automation.Managers.*

import java.time.Instant
import com.histmersion.values.GoalStatus
import com.histmersion.api.automation.ManagersMarkdown

/** The autopilot's pure rules (docs/research-process-managers.md "As built: the autopilot"): the checked auto approvals
  * and their caps, the goal-status advance, the managers every goal has, the spread crons, the extend threshold, the
  * shared ingest cap, housekeeping and the pace.
  */
class AutopilotPolicySuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-19T12:00:00Z")

  // ── approvals ──────────────────────────────────────────────────────────────────────────────────

  test("article-plan: every slug free and every filter at min-sources → approved, counted against the area's cap"):
    val ok = List(PlannedFacts("a", slugFree = true, 9), PlannedFacts("b", slugFree = true, 5))
    articlePlan(ok, 5, 0, 6) match
      case Verdict.Approve(r) => assert(r.contains("2 articles") && r.contains("a 9, b 5") && r.contains("2 of 6"), r)
      case v                  => fail(v.toString)
    assert(articlePlan(ok, 5, 4, 6).isInstanceOf[Verdict.Approve])
    assertEquals(
      articlePlan(ok, 5, 5, 6),
      Verdict.Leave("the area's daily cap is reached (5 of 6 articles approved today); decided tomorrow")
    )
    assert(articlePlan(ok, 5, 0, 1).isInstanceOf[Verdict.Leave], "a plan larger than the whole cap is a person's")

  test("article-plan: a taken slug or a thin filter rejects it; an empty plan too"):
    articlePlan(List(PlannedFacts("a", slugFree = false, 9), PlannedFacts("b", slugFree = true, 2)), 5, 0, 6) match
      case Verdict.Reject(r) => assert(r.contains("a: the slug is taken") && r.contains("b: its filter returns 2"), r)
      case v                 => fail(v.toString)
    assert(articlePlan(Nil, 5, 0, 6).isInstanceOf[Verdict.Reject])

  private val draft =
    DraftFacts("x", create = true, filterRecorded = true, hits = Some(8), problems = Nil, cited = 4, slugFree = true)

  test(
    "article-publish: filter recorded and wide enough, no problems, cites, slug free → approved within the daily cap"
  ):
    assert(articlePublish(draft, 3, 0, 10).isInstanceOf[Verdict.Approve])
    assert(articlePublish(draft, 3, 10, 10).isInstanceOf[Verdict.Leave])
    assert(
      articlePublish(draft.copy(create = false, slugFree = false), 3, 0, 10).isInstanceOf[Verdict.Approve],
      "an extension"
    )

  test("article-publish: each failed check rejects with why"):
    def why(d: DraftFacts) = articlePublish(d, 3, 0, 10) match
      case Verdict.Reject(r) => r
      case v                 => fail(v.toString)
    assert(why(draft.copy(filterRecorded = false)).contains("no coverage filter"))
    assert(why(draft.copy(hits = Some(1))).contains("returns 1 source(s), fewer than 3"))
    assert(why(draft.copy(hits = None)).contains("unknown number"))
    assert(why(draft.copy(problems = List("p1", "p2"))).contains("2 problems: p1; p2"))
    assert(why(draft.copy(cited = 0)).contains("cites no source"))
    assert(why(draft.copy(slugFree = false)).contains("slug x is taken"))

  test(
    "goal-plan: something proposed → approved; sub-goals at the depth limit rejected; nothing rejected; the cap leaves it"
  ):
    val p = GoalPlanFacts("g", subGoals = 3, queries = 4, works = 2, depth = 1)
    assert(goalPlan(p, 2, 0, 5).isInstanceOf[Verdict.Approve])
    goalPlan(p.copy(depth = 2), 2, 0, 5) match
      case Verdict.Reject(r) => assert(r.contains("3 sub-goals") && r.contains("2 level(s) below a root"), r)
      case v                 => fail(v.toString)
    assert(
      goalPlan(p.copy(subGoals = 0, depth = 2), 2, 0, 5).isInstanceOf[Verdict.Approve],
      "no sub-goals: fine at any depth"
    )
    assert(goalPlan(p.copy(subGoals = 0, queries = 0, works = 0), 2, 0, 5).isInstanceOf[Verdict.Reject])
    assert(goalPlan(p, 2, 5, 5).isInstanceOf[Verdict.Leave])

  test("the note marks the decision as the autopilot's; a left one carries none"):
    assertEquals(note(Verdict.Approve("r")), Some("autopilot: approved — r"))
    assertEquals(note(Verdict.Reject("r")), Some("autopilot: rejected — r"))
    assertEquals(note(Verdict.Leave("r")), None)

  test("depth: the parents above a goal; a cycle stops"):
    val parent = Map("c" -> "b", "b" -> "a")
    assertEquals(depth("c", parent.get), 2)
    assertEquals(depth("a", parent.get), 0)
    assertEquals(depth("x", Map("x" -> "y", "y" -> "x").get), 1)

  // ── goal status ────────────────────────────────────────────────────────────────────────────────

  private val g =
    GoalFacts("g", GoalStatus.Planned, managed = false, articles = 0, compileOpen = false, reviewed = false, Nil)

  test(
    "advance: planned → seeding once managed; → compiled with an article and nothing left; → reviewed when it passed"
  ):
    assertEquals(advance(g), None)
    assertEquals(advance(g.copy(managed = true)).map(_._1), Some(GoalStatus.Seeding))
    assertEquals(advance(g.copy(managed = true, articles = 2, compileOpen = true)).map(_._1), Some(GoalStatus.Seeding))
    assertEquals(advance(g.copy(managed = true, articles = 2)).map(_._1), Some(GoalStatus.Compiled))
    assertEquals(
      advance(g.copy(status = GoalStatus.Compiled, managed = true, articles = 2, reviewed = true)),
      Some((GoalStatus.Reviewed, "its 2 articles passed review"))
    )

  test("advance: never back, never past done, an unknown status is left"):
    assertEquals(advance(g.copy(status = GoalStatus.Reviewed, managed = true)), None)
    assertEquals(advance(g.copy(status = GoalStatus.Done, managed = true, articles = 3, reviewed = true)), None)
    assertEquals(advance(g.copy(status = "frozen", managed = true)), None)
    assertEquals(advance(g.copy(status = GoalStatus.Compiled, managed = true, articles = 1, compileOpen = true)), None)

  test("advance: a parent follows its sub-goals"):
    val p =
      g.copy(status = GoalStatus.Seeding, managed = true, children = List(GoalStatus.Compiled, GoalStatus.Seeding))
    assertEquals(advance(p), None)
    assertEquals(
      advance(p.copy(children = List(GoalStatus.Compiled, GoalStatus.Reviewed))).map(_._1),
      Some(GoalStatus.Compiled)
    )
    assertEquals(
      advance(p.copy(children = List(GoalStatus.Reviewed, GoalStatus.Done))).map(_._1),
      Some(GoalStatus.Reviewed),
      "no own article: reviewed when its sub-goals are"
    )
    assertEquals(
      advance(p.copy(articles = 1, children = List(GoalStatus.Reviewed))).map(_._1),
      Some(GoalStatus.Compiled),
      "its own article not reviewed yet"
    )

  // ── ensure ─────────────────────────────────────────────────────────────────────────────────────

  private val st = Settings("@weekly", "@weekly", "@monthly", 5, 3)

  test(
    "ensure: an unfinished goal gets an active seeding manager (plan first) and a watcher; a finished one a watcher"
  ):
    val steps = ensure(
      List(GoalRow("a", GoalStatus.Planned, needsPlan = true), GoalRow("r", GoalStatus.Reviewed, needsPlan = false)),
      Nil,
      Set.empty,
      st
    )
    assertEquals(steps.map(_.getClass.getSimpleName), List("CreateSeed", "CreateWatch", "CreateWatch"))
    assertEquals(steps.head, Step.CreateSeed("a", stagger("@weekly", seedId("a")), planFirst = true))
    assertEquals(steps(2), Step.CreateWatch("r", stagger("@monthly", watchId("r")), 5))

  test("ensure: idempotent; fills only what is unset; resumes only what it paused; review and enrich active"):
    val ms = List(
      ManagerRow(ReviewId, "review", None, "paused", None, None),
      ManagerRow(EnrichId, "enrich", None, "active", None, None),
      ManagerRow(seedId("a"), "seed", Some("a"), "paused", Some("0 3 * * 1"), None, planItem = true),
      ManagerRow(watchId("a"), "watch", Some("a"), "paused", None, Some(false)),
      ManagerRow(seedId("b"), "seed", Some("b"), "paused", None, None),
      ManagerRow(watchId("b"), "watch", Some("b"), "active", Some("@weekly"), None)
    )
    val steps = ensure(
      List(GoalRow("a", GoalStatus.Seeding, needsPlan = true), GoalRow("b", GoalStatus.Planned, needsPlan = true)),
      ms,
      Set(seedId("a")),
      st
    )
    assertEquals(
      steps,
      List(
        Step.Resume(ReviewId),
        Step.ExtendMinNew(ReviewId, 3),
        Step.Resume(seedId("a")),
        Step.SetCron(watchId("a"), stagger("@weekly", watchId("a"))),
        Step.SetCron(seedId("b"), stagger("@weekly", seedId("b"))),
        Step.AddPlanItem(seedId("b"), "b"),
        Step.AutoIngestOn(watchId("b"), 5)
      )
    )
    val done = ms.map(m =>
      m.copy(
        state = if m.id == watchId("a") then "paused" else "active",
        cron = m.cron.orElse(Some("x")),
        autoIngest = m.autoIngest.orElse(Some(true)),
        planItem = true,
        extendMinNew = Some(3)
      )
    )
    assertEquals(
      ensure(List(GoalRow("a", GoalStatus.Seeding, true), GoalRow("b", GoalStatus.Planned, true)), done, Set.empty, st),
      Nil
    )

  test("toPause: the active seeding managers and watchers only"):
    val ms = List(
      ManagerRow(ReviewId, "review", None, "active", None, None),
      ManagerRow(seedId("a"), "seed", Some("a"), "active", None, None),
      ManagerRow(watchId("a"), "watch", Some("a"), "paused", None, None),
      ManagerRow(watchId("b"), "watch", Some("b"), "active", None, None)
    )
    assertEquals(toPause(ms), List(seedId("a"), watchId("b")))

  test("stagger: spread per key, stable, valid, at most weekly; another cron kept"):
    val crons = (1 to 60).map(i => stagger("@weekly", s"watch:goal-$i"))
    assert(crons.distinct.size > 50, crons.distinct.size)
    assertEquals(stagger("@weekly", "watch:x"), stagger("@weekly", "watch:x"))
    crons.foreach(c => assert(ManagerPolicy.checkCron(c, now, 60).isRight, c))
    assert(Cron.parse(stagger("@daily", "k")).isRight && Cron.parse(stagger("@monthly", "k")).isRight)
    assertEquals(stagger("0 4 * * 1", "k"), "0 4 * * 1")
    val minutes = (1 to 60).map(i => stagger("@daily", s"g$i").split(" ")(0).toInt).distinct
    assert(minutes.size > 25, minutes.size)

  // ── extend, caps, housekeeping, pace ───────────────────────────────────────────────────────────

  test("extendDue: at the threshold and past the cooldown; 0 = never"):
    assert(extendDue(3, 3, None, 14, now).exists(_.contains("3 sources")))
    assertEquals(extendDue(2, 3, None, 14, now), None)
    assertEquals(extendDue(9, 0, None, 14, now), None)
    assertEquals(extendDue(9, 3, Some(now.minusSeconds(3600)), 14, now), None)
    assert(extendDue(9, 3, Some(now.minusSeconds(15L * 86400)), 14, now).isDefined)

  test("sharedIngestLeft, due, etaDays"):
    assertEquals(sharedIngestLeft(20, 7), Some(13))
    assertEquals(sharedIngestLeft(20, 25), Some(0))
    assertEquals(sharedIngestLeft(0, 7), None)
    assert(
      due(None, 24, now) && !due(Some(now.minusSeconds(3600)), 24, now) && due(Some(now.minusSeconds(86400)), 24, now)
    )
    assert(!due(None, 0, now))
    assertEquals(etaDays(10, 7), Some(10))
    assertEquals(etaDays(1, 70), Some(1))
    assertEquals(etaDays(0, 7), None)
    assertEquals(etaDays(5, 0), None)

  // ── the manager policy additions ───────────────────────────────────────────────────────────────

  test("waitsFor: a goal's article proposal waits for its plan-goal item"):
    assert(ManagerPolicy.waitsFor(ProposeArticles, "g", List((PlanGoalItem, "g"))))
    assert(!ManagerPolicy.waitsFor(ProposeArticles, "h", List((PlanGoalItem, "g"))))
    assertEquals(itemKindOf(Kind.Seed, "plan-goal"), Some(PlanGoalItem))
    assertEquals(itemKindOf(Kind.Seed, "plan"), Some(ProposeArticles), "`plan` keeps naming the article plan")

  test("blocks: an article waits for any run on it; a goal's item only for a run of its own pipeline"):
    assert(ManagerPolicy.blocks(ArticleReview, "review-article", List("compile-article")))
    assert(!ManagerPolicy.blocks(ArticleReview, "review-article", Nil))
    assert(!ManagerPolicy.blocks(AcquisitionSweep, "acquisition-sweep", List("plan-goal", "propose-articles")))
    assert(ManagerPolicy.blocks(AcquisitionSweep, "acquisition-sweep", List("acquisition-sweep")))
    assert(!ManagerPolicy.blocks(ProposeArticles, "propose-articles", List("currency-check")))

  test("evaluateStep: a draft the checks rejected is compiled again after the backoff, then stopped with a task"):
    val s = ReviewSettings(1, 2, 1, 1, 1, 1, 0, 0, 0, maxFailures = 2, backoffMinutes = 15, maxBackoffMinutes = 360)
    ManagerPolicy.evaluateStep(
      s,
      ArticleCompile,
      "x",
      0,
      ItemState(),
      "run-12345678",
      RunState.Done,
      None,
      StepFacts.Redo("the draft was rejected"),
      now
    ) match
      case Next.Retry(at, 1, note) => assert(at.isAfter(now) && note.contains("again"), note)
      case n                       => fail(n.toString)
    ManagerPolicy.evaluateStep(
      s,
      ArticleCompile,
      "x",
      1,
      ItemState(),
      "run-12345678",
      RunState.Done,
      None,
      StepFacts.Redo("the draft was rejected"),
      now
    ) match
      case Next.Stop(o, Some(task), _) => assert(o.contains("2 attempts") && task.contains("[[x]]"), task)
      case n                           => fail(n.toString)

  test("ManagerConfig: autoApprove and extendMinNew parse, merge and print"):
    val c = ManagerConfig.parse(List("autoApprove=on", "extend-min-new=4")).toOption.get
    assertEquals((c.autoApprove, c.extendMinNew), (Some(true), Some(4)))
    assertEquals(ManagerConfig().merge(c).autoApprove, Some(true))
    assert(ManagersMarkdown.configLine(c).contains("autoApprove=true, extendMinNew=4"))

/** The daily caps scaled to the backlog (#3762 proposal 3). */
class ScaledCapSuite extends munit.FunSuite:
  import AutopilotPolicy.{capText, scaledCap}

  test("share 0 keeps the fixed cap; a share scales it up to the ceiling, never below the base"):
    assertEquals(scaledCap(5, 64, 0, 50), 5)
    assertEquals(scaledCap(5, 64, 0.34, 50), 22)
    assertEquals(scaledCap(5, 6, 0.34, 50), 5, "a small backlog: the base")
    assertEquals(scaledCap(5, 640, 0.34, 50), 50, "the ceiling")
    assertEquals(scaledCap(5, 640, 0.34, 0), 218, "no ceiling")
    assertEquals(scaledCap(10, 640, 0.34, 4), 10, "a ceiling below the base keeps the base")
    assertEquals(scaledCap(-1, -5, 0.5, 10), 0)

  test("the status text"):
    assertEquals(capText(5, 64, 0, 50), "5 a day")
    assertEquals(capText(5, 64, 0.34, 50), "5 a day, 22 today for a backlog of 64 (at most 50)")

/** #3763: new articles only once the current ones are done. */
class NextArticleSuite extends munit.FunSuite:
  import AutopilotPolicy.{articlesInProgress, nextArticle, NextFacts}
  import com.histmersion.api.automation.Managers.{ItemStatus, State}

  private val now = java.time.Instant.parse("2026-10-24T12:00:00Z")
  private val done = NextFacts(
    "g",
    GoalStatus.Reviewed,
    articles = 2,
    unreviewed = 0,
    compileOpen = 0,
    seedState = Some(State.Active.wire),
    proposeStatus = Some(ItemStatus.Done.wire),
    proposeSettled = Some(now.minusSeconds(2 * 86400))
  )

  test("in progress: an article to compile or to pass review keeps a new proposal back"):
    assertEquals(articlesInProgress(0, 0), None)
    assert(articlesInProgress(1, 0).exists(_.contains("1 to pass review")))
    assert(articlesInProgress(0, 2).exists(_.contains("2 to compile")))

  test("the next article: every article done, the seeding manager active, no proposal open, the cooldown over"):
    assert(nextArticle(done, now, 24).exists(_.contains("2 articles are compiled and passed review")))
    assertEquals(nextArticle(done.copy(unreviewed = 1), now, 24), None, "an article to review")
    assertEquals(nextArticle(done.copy(compileOpen = 1), now, 24), None, "an article to compile")
    assertEquals(
      nextArticle(done.copy(articles = 0), now, 24),
      None,
      "no article yet: the first proposal is the plan's"
    )
    assertEquals(
      nextArticle(done.copy(proposeStatus = Some(ItemStatus.Pending.wire)), now, 24),
      None,
      "a proposal open"
    )
    assertEquals(nextArticle(done.copy(proposeSettled = Some(now.minusSeconds(3600))), now, 24), None, "the cooldown")
    assertEquals(nextArticle(done.copy(seedState = Some(State.Paused.wire)), now, 24), None, "a paused manager")
    assertEquals(nextArticle(done.copy(status = GoalStatus.Done), now, 24), None, "a person's done")
    assertEquals(nextArticle(done, now, 0), None, "off")
    assert(nextArticle(done.copy(proposeStatus = None, proposeSettled = None), now, 24).isDefined, "never proposed")

  test("unfinished goals: the planned goals neither reviewed nor done"):
    val goals = List(
      "a" -> GoalStatus.Planned,
      "b" -> GoalStatus.Seeding,
      "c" -> GoalStatus.Compiled,
      "d" -> GoalStatus.Reviewed,
      "e" -> GoalStatus.Done,
      "f" -> GoalStatus.Seeding
    )
    assertEquals(unfinishedPlanned(goals, Set("a", "b", "c", "d", "e")), 3)
    assertEquals(unfinishedPlanned(goals, Set.empty), 0, "a goal without an approved plan is not counted")

  test("goal plans wait while the unfinished goals exceed the limit; 0 = no limit"):
    assertEquals(goalPlansWait(10, 10), None, "at the limit: offered")
    val w = goalPlansWait(60, 10).getOrElse(fail("waits"))
    assert(
      w.contains("drop to 10") && w.contains("60 goals with an approved plan are not reviewed yet") &&
        w.contains("the autopilot's limit on unfinished goals") && !w.contains("agents."),
      w
    )
    assert(goalPlansWait(2, 1).exists(_.contains("2 goals")))
    assertEquals(goalPlansWait(60, 0), None)
