package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.automation.Agents.{ApprovalKind, RunState}
import com.histmersion.domain.AutopilotPolicy.*
import com.histmersion.domain.ManagerPolicy.{Next, ReviewSettings, StepFacts}
import com.histmersion.api.automation.Managers.*

import java.time.Instant
import com.histmersion.api.automation.ManagersMarkdown
import com.histmersion.domain.automation.Goal

/** The autopilot's pure rules (docs/research-process-managers.md "As built: the autopilot"): the checked auto approvals
  * and their caps, the goal-status advance, the managers every goal has, the spread crons, the extend threshold, the
  * shared ingest cap, housekeeping and the pace.
  */
class AutopilotPolicySuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-19T12:00:00Z")

  // ── approvals ──────────────────────────────────────────────────────────────────────────────────

  test("article-plan: every slug free and every filter at min-sources → approved, counted against the area's cap"):
    val ok = List(PlannedFacts(com.histmersion.domain.AutopilotPolicy.PlannedFacts.Slug("a"), slugFree = com.histmersion.domain.AutopilotPolicy.PlannedFacts.SlugFree(true), com.histmersion.domain.AutopilotPolicy.PlannedFacts.Hits(9)), PlannedFacts(com.histmersion.domain.AutopilotPolicy.PlannedFacts.Slug("b"), slugFree = com.histmersion.domain.AutopilotPolicy.PlannedFacts.SlugFree(true), com.histmersion.domain.AutopilotPolicy.PlannedFacts.Hits(5)))
    articlePlan(ok, com.histmersion.domain.AutopilotPolicy.MinSources(5), com.histmersion.domain.AutopilotPolicy.ApprovedToday(0), com.histmersion.domain.AutopilotPolicy.Cap(6)) match
      case Verdict.Approve(r) => assert(r.contains("2 articles") && r.contains("a 9, b 5") && r.contains("2 of 6"), r)
      case v                  => fail(v.toString)
    assert(articlePlan(ok, com.histmersion.domain.AutopilotPolicy.MinSources(5), com.histmersion.domain.AutopilotPolicy.ApprovedToday(4), com.histmersion.domain.AutopilotPolicy.Cap(6)).isInstanceOf[Verdict.Approve])
    assertEquals(
      articlePlan(ok, com.histmersion.domain.AutopilotPolicy.MinSources(5), com.histmersion.domain.AutopilotPolicy.ApprovedToday(5), com.histmersion.domain.AutopilotPolicy.Cap(6)),
      Verdict.Leave("the area's daily cap is reached (5 of 6 articles approved today); decided tomorrow")
    )
    assert(articlePlan(ok, com.histmersion.domain.AutopilotPolicy.MinSources(5), com.histmersion.domain.AutopilotPolicy.ApprovedToday(0), com.histmersion.domain.AutopilotPolicy.Cap(1)).isInstanceOf[Verdict.Leave], "a plan larger than the whole cap is a person's")

  test("article-plan: a taken slug or a thin filter rejects it; an empty plan too"):
    articlePlan(List(PlannedFacts(com.histmersion.domain.AutopilotPolicy.PlannedFacts.Slug("a"), slugFree = com.histmersion.domain.AutopilotPolicy.PlannedFacts.SlugFree(false), com.histmersion.domain.AutopilotPolicy.PlannedFacts.Hits(9)), PlannedFacts(com.histmersion.domain.AutopilotPolicy.PlannedFacts.Slug("b"), slugFree = com.histmersion.domain.AutopilotPolicy.PlannedFacts.SlugFree(true), com.histmersion.domain.AutopilotPolicy.PlannedFacts.Hits(2))), com.histmersion.domain.AutopilotPolicy.MinSources(5), com.histmersion.domain.AutopilotPolicy.ApprovedToday(0), com.histmersion.domain.AutopilotPolicy.Cap(6)) match
      case Verdict.Reject(r) => assert(r.contains("a: the slug is taken") && r.contains("b: its filter returns 2"), r)
      case v                 => fail(v.toString)
    assert(articlePlan(Nil, com.histmersion.domain.AutopilotPolicy.MinSources(5), com.histmersion.domain.AutopilotPolicy.ApprovedToday(0), com.histmersion.domain.AutopilotPolicy.Cap(6)).isInstanceOf[Verdict.Reject])

  private val draft =
    DraftFacts(com.histmersion.domain.AutopilotPolicy.DraftFacts.Slug("x"), create = com.histmersion.domain.AutopilotPolicy.DraftFacts.Create(true), filterRecorded = com.histmersion.domain.AutopilotPolicy.DraftFacts.FilterRecorded(true), hits = Some(com.histmersion.domain.AutopilotPolicy.DraftFacts.Hits(8)), problems = Nil, cited = com.histmersion.domain.AutopilotPolicy.DraftFacts.Cited(4), slugFree = com.histmersion.domain.AutopilotPolicy.DraftFacts.SlugFree(true))

  test(
    "article-publish: filter recorded and wide enough, no problems, cites, slug free → approved within the daily cap"
  ):
    assert(articlePublish(draft, com.histmersion.domain.AutopilotPolicy.MinSources(3), com.histmersion.domain.AutopilotPolicy.ApprovedToday(0), com.histmersion.domain.AutopilotPolicy.Cap(10)).isInstanceOf[Verdict.Approve])
    assert(articlePublish(draft, com.histmersion.domain.AutopilotPolicy.MinSources(3), com.histmersion.domain.AutopilotPolicy.ApprovedToday(10), com.histmersion.domain.AutopilotPolicy.Cap(10)).isInstanceOf[Verdict.Leave])
    assert(
      articlePublish(draft.modify(_.create).setTo(false).modify(_.slugFree).setTo(false), com.histmersion.domain.AutopilotPolicy.MinSources(3), com.histmersion.domain.AutopilotPolicy.ApprovedToday(0), com.histmersion.domain.AutopilotPolicy.Cap(10)).isInstanceOf[Verdict.Approve],
      "an extension"
    )

  test("article-publish: each failed check rejects with why"):
    def why(d: DraftFacts) = articlePublish(d, com.histmersion.domain.AutopilotPolicy.MinSources(3), com.histmersion.domain.AutopilotPolicy.ApprovedToday(0), com.histmersion.domain.AutopilotPolicy.Cap(10)) match
      case Verdict.Reject(r) => r
      case v                 => fail(v.toString)
    assert(why(draft.modify(_.filterRecorded).setTo(false)).contains("no coverage filter"))
    assert(why(draft.modify(_.hits).setTo(Some(1))).contains("returns 1 source(s), fewer than 3"))
    assert(why(draft.modify(_.hits).setTo(None)).contains("unknown number"))
    assert(why(draft.modify(_.problems).setTo(List("p1", "p2"))).contains("2 problems: p1; p2"))
    assert(why(draft.modify(_.cited).setTo(0)).contains("cites no source"))
    assert(why(draft.modify(_.slugFree).setTo(false)).contains("slug x is taken"))

  test(
    "goal-plan: something proposed → approved; sub-goals at the depth limit rejected; nothing rejected; the cap leaves it"
  ):
    val p = GoalPlanFacts(com.histmersion.domain.AutopilotPolicy.GoalPlanFacts.Goal("g"), subGoals = com.histmersion.domain.AutopilotPolicy.GoalPlanFacts.SubGoals(3), queries = com.histmersion.domain.AutopilotPolicy.GoalPlanFacts.Queries(4), works = com.histmersion.domain.AutopilotPolicy.GoalPlanFacts.Works(2), depth = com.histmersion.domain.AutopilotPolicy.GoalPlanFacts.Depth(1))
    assert(goalPlan(p, com.histmersion.domain.AutopilotPolicy.DepthLimit(2), com.histmersion.domain.AutopilotPolicy.ApprovedToday(0), com.histmersion.domain.AutopilotPolicy.Cap(5)).isInstanceOf[Verdict.Approve])
    goalPlan(p.modify(_.depth).setTo(2), com.histmersion.domain.AutopilotPolicy.DepthLimit(2), com.histmersion.domain.AutopilotPolicy.ApprovedToday(0), com.histmersion.domain.AutopilotPolicy.Cap(5)) match
      case Verdict.Reject(r) => assert(r.contains("3 sub-goals") && r.contains("2 level(s) below a root"), r)
      case v                 => fail(v.toString)
    assert(
      goalPlan(p.modify(_.subGoals).setTo(0).modify(_.depth).setTo(2), com.histmersion.domain.AutopilotPolicy.DepthLimit(2), com.histmersion.domain.AutopilotPolicy.ApprovedToday(0), com.histmersion.domain.AutopilotPolicy.Cap(5)).isInstanceOf[Verdict.Approve],
      "no sub-goals: fine at any depth"
    )
    assert(goalPlan(p.modify(_.subGoals).setTo(0).modify(_.queries).setTo(0).modify(_.works).setTo(0), com.histmersion.domain.AutopilotPolicy.DepthLimit(2), com.histmersion.domain.AutopilotPolicy.ApprovedToday(0), com.histmersion.domain.AutopilotPolicy.Cap(5)).isInstanceOf[Verdict.Reject])
    assert(goalPlan(p, com.histmersion.domain.AutopilotPolicy.DepthLimit(2), com.histmersion.domain.AutopilotPolicy.ApprovedToday(5), com.histmersion.domain.AutopilotPolicy.Cap(5)).isInstanceOf[Verdict.Leave])

  test("the note marks the decision as the autopilot's; a left one carries none"):
    assertEquals(note(Verdict.Approve(com.histmersion.domain.AutopilotPolicy.Verdict.Approve.Reason("r"))), Some("autopilot: approved — r"))
    assertEquals(note(Verdict.Reject(com.histmersion.domain.AutopilotPolicy.Verdict.Reject.Reason("r"))), Some("autopilot: rejected — r"))
    assertEquals(note(Verdict.Leave(com.histmersion.domain.AutopilotPolicy.Verdict.Leave.Why("r"))), None)

  test("depth: the parents above a goal; a cycle stops"):
    val parent = Map("c" -> "b", "b" -> "a")
    assertEquals(depth(com.histmersion.domain.research.Slug("c"), parent.get), 2)
    assertEquals(depth(com.histmersion.domain.research.Slug("a"), parent.get), 0)
    assertEquals(depth(com.histmersion.domain.research.Slug("x"), Map("x" -> "y", "y" -> "x").get), 1)

  // ── goal status ────────────────────────────────────────────────────────────────────────────────

  private val g =
    GoalFacts(com.histmersion.domain.AutopilotPolicy.GoalFacts.Slug("g"), Goal.Status.Planned, managed = com.histmersion.domain.AutopilotPolicy.GoalFacts.Managed(false), articles = com.histmersion.domain.AutopilotPolicy.GoalFacts.Articles(0), compileOpen = com.histmersion.domain.AutopilotPolicy.GoalFacts.CompileOpen(false), reviewed = com.histmersion.domain.AutopilotPolicy.GoalFacts.Reviewed(false), Nil)

  test(
    "advance: planned → seeding once managed; → compiled with an article and nothing left; → reviewed when it passed"
  ):
    assertEquals(advance(g), None)
    assertEquals(advance(g.modify(_.managed).setTo(true)).map(_._1), Some(Goal.Status.Seeding))
    assertEquals(advance(g.modify(_.managed).setTo(true).modify(_.articles).setTo(2).modify(_.compileOpen).setTo(true)).map(_._1), Some(Goal.Status.Seeding))
    assertEquals(advance(g.modify(_.managed).setTo(true).modify(_.articles).setTo(2)).map(_._1), Some(Goal.Status.Compiled))
    assertEquals(
      advance(g.modify(_.status).setTo(Goal.Status.Compiled).modify(_.managed).setTo(true).modify(_.articles).setTo(2).modify(_.reviewed).setTo(true)),
      Some((Goal.Status.Reviewed, "its 2 articles passed review"))
    )

  test("advance: never back, never past done, an unknown status is left"):
    assertEquals(advance(g.modify(_.status).setTo(Goal.Status.Reviewed).modify(_.managed).setTo(true)), None)
    assertEquals(advance(g.modify(_.status).setTo(Goal.Status.Done).modify(_.managed).setTo(true).modify(_.articles).setTo(3).modify(_.reviewed).setTo(true)), None)
    assertEquals(advance(g.modify(_.status).setTo("frozen").modify(_.managed).setTo(true)), None)
    assertEquals(advance(g.modify(_.status).setTo(Goal.Status.Compiled).modify(_.managed).setTo(true).modify(_.articles).setTo(1).modify(_.compileOpen).setTo(true)), None)

  test("advance: a parent follows its sub-goals"):
    val p =
      g.modify(_.status).setTo(Goal.Status.Seeding).modify(_.managed).setTo(true).modify(_.children).setTo(List(Goal.Status.Compiled, Goal.Status.Seeding))
    assertEquals(advance(p), None)
    assertEquals(
      advance(p.modify(_.children).setTo(List(Goal.Status.Compiled, Goal.Status.Reviewed))).map(_._1),
      Some(Goal.Status.Compiled)
    )
    assertEquals(
      advance(p.modify(_.children).setTo(List(Goal.Status.Reviewed, Goal.Status.Done))).map(_._1),
      Some(Goal.Status.Reviewed),
      "no own article: reviewed when its sub-goals are"
    )
    assertEquals(
      advance(p.modify(_.articles).setTo(1).modify(_.children).setTo(List(Goal.Status.Reviewed))).map(_._1),
      Some(Goal.Status.Compiled),
      "its own article not reviewed yet"
    )

  // ── ensure ─────────────────────────────────────────────────────────────────────────────────────

  private val st = Settings(com.histmersion.domain.AutopilotPolicy.Cron("@weekly"), com.histmersion.domain.AutopilotPolicy.Cron("@weekly"), com.histmersion.domain.AutopilotPolicy.Cron("@monthly"), com.histmersion.domain.AutopilotPolicy.Settings.WatchIngestPerDay(5), com.histmersion.domain.AutopilotPolicy.Settings.ExtendMinNew(3))

  test(
    "ensure: an unfinished goal gets an active seeding manager (plan first) and a watcher; a finished one a watcher"
  ):
    val steps = ensure(
      List(GoalRow(com.histmersion.domain.automation.Goal.Slug("a"), Goal.Status.Planned, needsPlan = com.histmersion.domain.AutopilotPolicy.GoalRow.NeedsPlan(true)), GoalRow(com.histmersion.domain.automation.Goal.Slug("r"), Goal.Status.Reviewed, needsPlan = com.histmersion.domain.AutopilotPolicy.GoalRow.NeedsPlan(false))),
      Nil,
      Set.empty,
      st
    )
    assertEquals(steps.map(_.getClass.getSimpleName), List("CreateSeed", "CreateWatch", "CreateWatch"))
    assertEquals(steps.head, Step.CreateSeed(com.histmersion.domain.AutopilotPolicy.Step.CreateSeed.Goal("a"), stagger(com.histmersion.domain.AutopilotPolicy.Cron("@weekly"), seedId(com.histmersion.api.automation.Goal.Ref("a"))), planFirst = com.histmersion.domain.AutopilotPolicy.Step.CreateSeed.PlanFirst(true)))
    assertEquals(steps(2), Step.CreateWatch(com.histmersion.domain.AutopilotPolicy.Step.CreateWatch.Goal("r"), stagger(com.histmersion.domain.AutopilotPolicy.Cron("@monthly"), watchId(com.histmersion.api.automation.Goal.Ref("r"))), com.histmersion.domain.AutopilotPolicy.Step.CreateWatch.PerDay(5)))

  test("ensure: idempotent; fills only what is unset; resumes only what it paused; review and enrich active"):
    val ms = List(
      ManagerRow(ReviewId, com.histmersion.domain.automation.Manager.Kind.Wire("review"), None, com.histmersion.domain.automation.Manager.State.Wire("paused"), None, None),
      ManagerRow(EnrichId, com.histmersion.domain.automation.Manager.Kind.Wire("enrich"), None, com.histmersion.domain.automation.Manager.State.Wire("active"), None, None),
      ManagerRow(seedId(com.histmersion.api.automation.Goal.Ref("a")), com.histmersion.domain.automation.Manager.Kind.Wire("seed"), Some(com.histmersion.domain.automation.Goal.Slug("a")), com.histmersion.domain.automation.Manager.State.Wire("paused"), Some(com.histmersion.domain.AutopilotPolicy.Cron("0 3 * * 1")), None, planItem = com.histmersion.domain.AutopilotPolicy.ManagerRow.PlanItem(true)),
      ManagerRow(watchId(com.histmersion.api.automation.Goal.Ref("a")), com.histmersion.domain.automation.Manager.Kind.Wire("watch"), Some(com.histmersion.domain.automation.Goal.Slug("a")), com.histmersion.domain.automation.Manager.State.Wire("paused"), None, Some(com.histmersion.domain.AutopilotPolicy.ManagerRow.AutoIngest(false))),
      ManagerRow(seedId(com.histmersion.api.automation.Goal.Ref("b")), com.histmersion.domain.automation.Manager.Kind.Wire("seed"), Some(com.histmersion.domain.automation.Goal.Slug("b")), com.histmersion.domain.automation.Manager.State.Wire("paused"), None, None),
      ManagerRow(watchId(com.histmersion.api.automation.Goal.Ref("b")), com.histmersion.domain.automation.Manager.Kind.Wire("watch"), Some(com.histmersion.domain.automation.Goal.Slug("b")), com.histmersion.domain.automation.Manager.State.Wire("active"), Some(com.histmersion.domain.AutopilotPolicy.Cron("@weekly")), None)
    )
    val steps = ensure(
      List(GoalRow(com.histmersion.domain.automation.Goal.Slug("a"), Goal.Status.Seeding, needsPlan = com.histmersion.domain.AutopilotPolicy.GoalRow.NeedsPlan(true)), GoalRow(com.histmersion.domain.automation.Goal.Slug("b"), Goal.Status.Planned, needsPlan = com.histmersion.domain.AutopilotPolicy.GoalRow.NeedsPlan(true))),
      ms,
      Set(seedId(com.histmersion.api.automation.Goal.Ref("a"))),
      st
    )
    assertEquals(
      steps,
      List(
        Step.Resume(ReviewId),
        Step.ExtendMinNew(ReviewId, com.histmersion.domain.AutopilotPolicy.Step.ExtendMinNew.N(3)),
        Step.Resume(seedId(com.histmersion.api.automation.Goal.Ref("a"))),
        Step.SetCron(watchId(com.histmersion.api.automation.Goal.Ref("a")), stagger(com.histmersion.domain.AutopilotPolicy.Cron("@weekly"), watchId(com.histmersion.api.automation.Goal.Ref("a")))),
        Step.SetCron(seedId(com.histmersion.api.automation.Goal.Ref("b")), stagger(com.histmersion.domain.AutopilotPolicy.Cron("@weekly"), seedId(com.histmersion.api.automation.Goal.Ref("b")))),
        Step.AddPlanItem(seedId(com.histmersion.api.automation.Goal.Ref("b")), com.histmersion.domain.AutopilotPolicy.Step.AddPlanItem.Goal("b")),
        Step.AutoIngestOn(watchId(com.histmersion.api.automation.Goal.Ref("b")), com.histmersion.domain.AutopilotPolicy.Step.AutoIngestOn.PerDay(5))
      )
    )
    val done = ms.map(m =>
      m.modify(
        _.state).setTo(if m.id == watchId("a") then "paused" else "active").modify(_.cron).setTo(m.cron.orElse(Some("x"))).modify(_.autoIngest).setTo(m.autoIngest.orElse(Some(true))).modify(_.planItem).setTo(true).modify(_.extendMinNew).setTo(Some(3)
      )
    )
    assertEquals(
      ensure(
        List(GoalRow(com.histmersion.domain.automation.Goal.Slug("a"), Goal.Status.Seeding, com.histmersion.domain.AutopilotPolicy.GoalRow.NeedsPlan(true)), GoalRow(com.histmersion.domain.automation.Goal.Slug("b"), Goal.Status.Planned, com.histmersion.domain.AutopilotPolicy.GoalRow.NeedsPlan(true))),
        done,
        Set.empty,
        st
      ),
      Nil
    )

  test("toPause: the active seeding managers and watchers only"):
    val ms = List(
      ManagerRow(ReviewId, com.histmersion.domain.automation.Manager.Kind.Wire("review"), None, com.histmersion.domain.automation.Manager.State.Wire("active"), None, None),
      ManagerRow(seedId(com.histmersion.api.automation.Goal.Ref("a")), com.histmersion.domain.automation.Manager.Kind.Wire("seed"), Some(com.histmersion.domain.automation.Goal.Slug("a")), com.histmersion.domain.automation.Manager.State.Wire("active"), None, None),
      ManagerRow(watchId(com.histmersion.api.automation.Goal.Ref("a")), com.histmersion.domain.automation.Manager.Kind.Wire("watch"), Some(com.histmersion.domain.automation.Goal.Slug("a")), com.histmersion.domain.automation.Manager.State.Wire("paused"), None, None),
      ManagerRow(watchId(com.histmersion.api.automation.Goal.Ref("b")), com.histmersion.domain.automation.Manager.Kind.Wire("watch"), Some(com.histmersion.domain.automation.Goal.Slug("b")), com.histmersion.domain.automation.Manager.State.Wire("active"), None, None)
    )
    assertEquals(toPause(ms), List(seedId(com.histmersion.api.automation.Goal.Ref("a")), watchId(com.histmersion.api.automation.Goal.Ref("b"))))

  test("stagger: spread per key, stable, valid, at most weekly; another cron kept"):
    val crons = (1 to 60).map(i => stagger(com.histmersion.domain.AutopilotPolicy.Cron("@weekly"), s"watch:goal-$i"))
    assert(crons.distinct.size > 50, crons.distinct.size)
    assertEquals(stagger(com.histmersion.domain.AutopilotPolicy.Cron("@weekly"), com.histmersion.domain.AutopilotPolicy.ManagerRow.Id("watch:x")), stagger(com.histmersion.domain.AutopilotPolicy.Cron("@weekly"), com.histmersion.domain.AutopilotPolicy.ManagerRow.Id("watch:x")))
    crons.foreach(c => assert(ManagerPolicy.checkCron(c, now, com.histmersion.domain.ManagerPolicy.MinIntervalMinutes(60)).isRight, c))
    assert(Cron.parse(stagger(com.histmersion.domain.AutopilotPolicy.Cron("@daily"), com.histmersion.domain.AutopilotPolicy.ManagerRow.Id("k"))).isRight && Cron.parse(stagger("@monthly", "k")).isRight)
    assertEquals(stagger(com.histmersion.domain.AutopilotPolicy.Cron("0 4 * * 1"), com.histmersion.domain.AutopilotPolicy.ManagerRow.Id("k")), "0 4 * * 1")
    val minutes = (1 to 60).map(i => stagger(com.histmersion.domain.AutopilotPolicy.Cron("@daily"), s"g$i").split(" ")(0).toInt).distinct
    assert(minutes.size > 25, minutes.size)

  // ── extend, caps, housekeeping, pace ───────────────────────────────────────────────────────────

  test("extendDue: at the threshold and past the cooldown; 0 = never"):
    assert(extendDue(com.histmersion.domain.AutopilotPolicy.NewSources(3), com.histmersion.domain.AutopilotPolicy.Settings.ExtendMinNew(3), None, com.histmersion.domain.AutopilotPolicy.CooldownDays(14), now).exists(_.contains("3 sources")))
    assertEquals(extendDue(com.histmersion.domain.AutopilotPolicy.NewSources(2), com.histmersion.domain.AutopilotPolicy.Settings.ExtendMinNew(3), None, com.histmersion.domain.AutopilotPolicy.CooldownDays(14), now), None)
    assertEquals(extendDue(com.histmersion.domain.AutopilotPolicy.NewSources(9), com.histmersion.domain.AutopilotPolicy.Settings.ExtendMinNew(0), None, com.histmersion.domain.AutopilotPolicy.CooldownDays(14), now), None)
    assertEquals(extendDue(com.histmersion.domain.AutopilotPolicy.NewSources(9), com.histmersion.domain.AutopilotPolicy.Settings.ExtendMinNew(3), Some(now.minusSeconds(3600)), com.histmersion.domain.AutopilotPolicy.CooldownDays(14), now), None)
    assert(extendDue(com.histmersion.domain.AutopilotPolicy.NewSources(9), com.histmersion.domain.AutopilotPolicy.Settings.ExtendMinNew(3), Some(now.minusSeconds(15L * 86400)), com.histmersion.domain.AutopilotPolicy.CooldownDays(14), now).isDefined)

  test("sharedIngestLeft, due, etaDays"):
    assertEquals(sharedIngestLeft(com.histmersion.domain.AutopilotPolicy.Cap(20), com.histmersion.domain.AutopilotPolicy.IngestedToday(7)), Some(13))
    assertEquals(sharedIngestLeft(com.histmersion.domain.AutopilotPolicy.Cap(20), com.histmersion.domain.AutopilotPolicy.IngestedToday(25)), Some(0))
    assertEquals(sharedIngestLeft(com.histmersion.domain.AutopilotPolicy.Cap(0), com.histmersion.domain.AutopilotPolicy.IngestedToday(7)), None)
    assert(
      due(None, com.histmersion.domain.AutopilotPolicy.EveryHours(24), now) && !due(Some(now.minusSeconds(3600)), 24, now) && due(Some(now.minusSeconds(86400)), 24, now)
    )
    assert(!due(None, com.histmersion.domain.AutopilotPolicy.EveryHours(0), now))
    assertEquals(etaDays(com.histmersion.domain.AutopilotPolicy.OpenCount(10), com.histmersion.domain.AutopilotPolicy.DoneLast7(7)), Some(10))
    assertEquals(etaDays(com.histmersion.domain.AutopilotPolicy.OpenCount(1), com.histmersion.domain.AutopilotPolicy.DoneLast7(70)), Some(1))
    assertEquals(etaDays(com.histmersion.domain.AutopilotPolicy.OpenCount(0), com.histmersion.domain.AutopilotPolicy.DoneLast7(7)), None)
    assertEquals(etaDays(com.histmersion.domain.AutopilotPolicy.OpenCount(5), com.histmersion.domain.AutopilotPolicy.DoneLast7(0)), None)

  // ── the manager policy additions ───────────────────────────────────────────────────────────────

  test("waitsFor: a goal's article proposal waits for its plan-goal item"):
    assert(ManagerPolicy.waitsFor(ProposeArticles, com.histmersion.domain.ManagerPolicy.Candidate.Target("g"), List((PlanGoalItem, com.histmersion.domain.ManagerPolicy.Candidate.Target("g")))))
    assert(!ManagerPolicy.waitsFor(ProposeArticles, com.histmersion.domain.ManagerPolicy.Candidate.Target("h"), List((PlanGoalItem, com.histmersion.domain.ManagerPolicy.Candidate.Target("g")))))
    assertEquals(itemKindOf(Kind.Seed, com.histmersion.api.ItemKind.Word("plan-goal")), Some(PlanGoalItem))
    assertEquals(itemKindOf(Kind.Seed, com.histmersion.api.ItemKind.Word("plan")), Some(ProposeArticles), "`plan` keeps naming the article plan")

  test("blocks: an article waits for any run on it; a goal's item only for a run of its own pipeline"):
    assert(ManagerPolicy.blocks(ArticleReview, com.histmersion.domain.automation.Pipeline.Wire("review-article"), List(com.histmersion.domain.automation.Pipeline.Wire("compile-article"))))
    assert(!ManagerPolicy.blocks(ArticleReview, com.histmersion.domain.automation.Pipeline.Wire("review-article"), Nil))
    assert(!ManagerPolicy.blocks(AcquisitionSweep, com.histmersion.domain.automation.Pipeline.Wire("acquisition-sweep"), List(com.histmersion.domain.automation.Pipeline.Wire("plan-goal"), com.histmersion.domain.automation.Pipeline.Wire("propose-articles"))))
    assert(ManagerPolicy.blocks(AcquisitionSweep, com.histmersion.domain.automation.Pipeline.Wire("acquisition-sweep"), List(com.histmersion.domain.automation.Pipeline.Wire("acquisition-sweep"))))
    assert(!ManagerPolicy.blocks(ProposeArticles, com.histmersion.domain.automation.Pipeline.Wire("propose-articles"), List(com.histmersion.domain.automation.Pipeline.Wire("currency-check"))))

  test("evaluateStep: a draft the checks rejected is compiled again after the backoff, then stopped with a task"):
    val s = ReviewSettings(com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxRuns(1), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxOpen(2), com.histmersion.domain.ManagerPolicy.ReviewSettings.Rounds(1), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxRunsPerArticle(1), com.histmersion.domain.ManagerPolicy.ReviewSettings.NoProgressRuns(1), com.histmersion.domain.ManagerPolicy.ReviewSettings.ReopenLimit(1), com.histmersion.domain.ManagerPolicy.ReviewSettings.CooldownMinutes(0), com.histmersion.domain.ManagerPolicy.ReviewSettings.LowWater(0), com.histmersion.domain.ManagerPolicy.ReviewSettings.RefillBatch(0), maxFailures = com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxFailures(2), backoffMinutes = com.histmersion.domain.ManagerPolicy.ReviewSettings.BackoffMinutes(15), maxBackoffMinutes = com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxBackoffMinutes(360))
    ManagerPolicy.evaluateStep(
      s,
      ArticleCompile,
      com.histmersion.domain.ManagerPolicy.Candidate.Target("x"),
      com.histmersion.domain.ManagerPolicy.Next.Retry.Failures(0),
      ItemState(),
      com.histmersion.domain.automation.Run.Id("run-12345678"),
      com.histmersion.domain.automation.Run.State.Done,
      None,
      StepFacts.Redo(com.histmersion.domain.ManagerPolicy.ItemOutcome("the draft was rejected")),
      now
    ) match
      case Next.Retry(at, 1, note) => assert(at.isAfter(now) && note.contains("again"), note)
      case n                       => fail(n.toString)
    ManagerPolicy.evaluateStep(
      s,
      ArticleCompile,
      com.histmersion.domain.ManagerPolicy.Candidate.Target("x"),
      com.histmersion.domain.ManagerPolicy.Next.Retry.Failures(1),
      ItemState(),
      com.histmersion.domain.automation.Run.Id("run-12345678"),
      com.histmersion.domain.automation.Run.State.Done,
      None,
      StepFacts.Redo(com.histmersion.domain.ManagerPolicy.ItemOutcome("the draft was rejected")),
      now
    ) match
      case Next.Stop(o, Some(task), _) => assert(o.contains("2 attempts") && task.contains("[[x]]"), task)
      case n                           => fail(n.toString)

  test("ManagerConfig: autoApprove and extendMinNew parse, merge and print"):
    val c = ManagerConfig.parse(List(com.histmersion.api.automation.Managers.ManagerConfig.Spec("autoApprove=on"), com.histmersion.api.automation.Managers.ManagerConfig.Spec("extend-min-new=4"))).toOption.get
    assertEquals((c.autoApprove, c.extendMinNew), (Some(true), Some(4)))
    assertEquals(ManagerConfig().merge(c).autoApprove, Some(true))
    assert(ManagersMarkdown.configLine(c).contains("autoApprove=true, extendMinNew=4"))

/** The daily caps scaled to the backlog (#3762 proposal 3). */
class ScaledCapSuite extends munit.FunSuite:
  import AutopilotPolicy.{capText, scaledCap}

  test("share 0 keeps the fixed cap; a share scales it up to the ceiling, never below the base"):
    assertEquals(scaledCap(com.histmersion.domain.AutopilotPolicy.Base(5), com.histmersion.domain.AutopilotPolicy.Backlog(64), com.histmersion.domain.AutopilotPolicy.Share(0), com.histmersion.domain.AutopilotPolicy.Cap(50)), 5)
    assertEquals(scaledCap(com.histmersion.domain.AutopilotPolicy.Base(5), com.histmersion.domain.AutopilotPolicy.Backlog(64), 0.34, com.histmersion.domain.AutopilotPolicy.Cap(50)), 22)
    assertEquals(scaledCap(com.histmersion.domain.AutopilotPolicy.Base(5), com.histmersion.domain.AutopilotPolicy.Backlog(6), 0.34, com.histmersion.domain.AutopilotPolicy.Cap(50)), 5, "a small backlog: the base")
    assertEquals(scaledCap(com.histmersion.domain.AutopilotPolicy.Base(5), com.histmersion.domain.AutopilotPolicy.Backlog(640), 0.34, com.histmersion.domain.AutopilotPolicy.Cap(50)), 50, "the ceiling")
    assertEquals(scaledCap(com.histmersion.domain.AutopilotPolicy.Base(5), com.histmersion.domain.AutopilotPolicy.Backlog(640), 0.34, com.histmersion.domain.AutopilotPolicy.Cap(0)), 218, "no ceiling")
    assertEquals(scaledCap(com.histmersion.domain.AutopilotPolicy.Base(10), com.histmersion.domain.AutopilotPolicy.Backlog(640), 0.34, com.histmersion.domain.AutopilotPolicy.Cap(4)), 10, "a ceiling below the base keeps the base")
    assertEquals(scaledCap(com.histmersion.domain.AutopilotPolicy.Base(-1), com.histmersion.domain.AutopilotPolicy.Backlog(-5), 0.5, com.histmersion.domain.AutopilotPolicy.Cap(10)), 0)

  test("the status text"):
    assertEquals(capText(com.histmersion.domain.AutopilotPolicy.CapTextBase(5), com.histmersion.domain.AutopilotPolicy.CapTextBacklog(64), com.histmersion.domain.AutopilotPolicy.CapTextShare(0), com.histmersion.domain.AutopilotPolicy.CapTextCeiling(50)), "5 a day")
    assertEquals(capText(com.histmersion.domain.AutopilotPolicy.CapTextBase(5), com.histmersion.domain.AutopilotPolicy.CapTextBacklog(64), 0.34, com.histmersion.domain.AutopilotPolicy.CapTextCeiling(50)), "5 a day, 22 today for a backlog of 64 (at most 50)")

/** #3763: new articles only once the current ones are done. */
class NextArticleSuite extends munit.FunSuite:
  import AutopilotPolicy.{articlesInProgress, nextArticle, NextFacts}
  import com.histmersion.api.automation.Managers.{ItemStatus, State}

  private val now = java.time.Instant.parse("2026-10-24T12:00:00Z")
  private val done = NextFacts(
    com.histmersion.domain.AutopilotPolicy.NextFacts.Goal("g"),
    Goal.Status.Reviewed,
    articles = com.histmersion.domain.AutopilotPolicy.NextFacts.Articles(2),
    unreviewed = com.histmersion.domain.AutopilotPolicy.NextFacts.Unreviewed(0),
    compileOpen = com.histmersion.domain.AutopilotPolicy.NextFacts.CompileOpen(0),
    seedState = Some(State.Active.wire),
    proposeStatus = Some(ItemStatus.Done.wire),
    proposeSettled = Some(now.minusSeconds(2 * 86400))
  )

  test("in progress: an article to compile or to pass review keeps a new proposal back"):
    assertEquals(articlesInProgress(com.histmersion.domain.AutopilotPolicy.NextFacts.Unreviewed(0), com.histmersion.domain.AutopilotPolicy.NextFacts.CompileOpen(0)), None)
    assert(articlesInProgress(com.histmersion.domain.AutopilotPolicy.NextFacts.Unreviewed(1), com.histmersion.domain.AutopilotPolicy.NextFacts.CompileOpen(0)).exists(_.contains("1 to pass review")))
    assert(articlesInProgress(com.histmersion.domain.AutopilotPolicy.NextFacts.Unreviewed(0), com.histmersion.domain.AutopilotPolicy.NextFacts.CompileOpen(2)).exists(_.contains("2 to compile")))

  test("the next article: every article done, the seeding manager active, no proposal open, the cooldown over"):
    assert(nextArticle(done, now, com.histmersion.domain.AutopilotPolicy.CooldownHours(24)).exists(_.contains("2 articles are compiled and passed review")))
    assertEquals(nextArticle(done.modify(_.unreviewed).setTo(1), now, com.histmersion.domain.AutopilotPolicy.CooldownHours(24)), None, "an article to review")
    assertEquals(nextArticle(done.modify(_.compileOpen).setTo(1), now, com.histmersion.domain.AutopilotPolicy.CooldownHours(24)), None, "an article to compile")
    assertEquals(
      nextArticle(done.modify(_.articles).setTo(0), now, com.histmersion.domain.AutopilotPolicy.CooldownHours(24)),
      None,
      "no article yet: the first proposal is the plan's"
    )
    assertEquals(
      nextArticle(done.modify(_.proposeStatus).setTo(Some(ItemStatus.Pending.wire)), now, com.histmersion.domain.AutopilotPolicy.CooldownHours(24)),
      None,
      "a proposal open"
    )
    assertEquals(nextArticle(done.modify(_.proposeSettled).setTo(Some(now.minusSeconds(3600))), now, com.histmersion.domain.AutopilotPolicy.CooldownHours(24)), None, "the cooldown")
    assertEquals(nextArticle(done.modify(_.seedState).setTo(Some(State.Paused.wire)), now, com.histmersion.domain.AutopilotPolicy.CooldownHours(24)), None, "a paused manager")
    assertEquals(nextArticle(done.modify(_.status).setTo(Goal.Status.Done), now, com.histmersion.domain.AutopilotPolicy.CooldownHours(24)), None, "a person's done")
    assertEquals(nextArticle(done, now, com.histmersion.domain.AutopilotPolicy.CooldownHours(0)), None, "off")
    assert(nextArticle(done.modify(_.proposeStatus).setTo(None).modify(_.proposeSettled).setTo(None), now, com.histmersion.domain.AutopilotPolicy.CooldownHours(24)).isDefined, "never proposed")

  test("unfinished goals: the planned goals neither reviewed nor done"):
    val goals = List(
      "a" -> Goal.Status.Planned,
      "b" -> Goal.Status.Seeding,
      "c" -> Goal.Status.Compiled,
      "d" -> Goal.Status.Reviewed,
      "e" -> Goal.Status.Done,
      "f" -> Goal.Status.Seeding
    )
    assertEquals(unfinishedPlanned(goals, Set(com.histmersion.domain.automation.Goal.Slug("a"), com.histmersion.domain.automation.Goal.Slug("b"), com.histmersion.domain.automation.Goal.Slug("c"), com.histmersion.domain.automation.Goal.Slug("d"), com.histmersion.domain.automation.Goal.Slug("e"))), 3)
    assertEquals(unfinishedPlanned(goals, Set.empty), 0, "a goal without an approved plan is not counted")

  test("goal plans wait while the unfinished goals exceed the limit; 0 = no limit"):
    assertEquals(goalPlansWait(com.histmersion.domain.AutopilotPolicy.Unfinished(10), com.histmersion.domain.AutopilotPolicy.Limit(10)), None, "at the limit: offered")
    val w = goalPlansWait(com.histmersion.domain.AutopilotPolicy.Unfinished(60), com.histmersion.domain.AutopilotPolicy.Limit(10)).getOrElse(fail("waits"))
    assert(
      w.contains("drop to 10") && w.contains("60 goals with an approved plan are not reviewed yet") &&
        w.contains("the autopilot's limit on unfinished goals") && !w.contains("agents."),
      w
    )
    assert(goalPlansWait(com.histmersion.domain.AutopilotPolicy.Unfinished(2), com.histmersion.domain.AutopilotPolicy.Limit(1)).exists(_.contains("2 goals")))
    assertEquals(goalPlansWait(com.histmersion.domain.AutopilotPolicy.Unfinished(60), com.histmersion.domain.AutopilotPolicy.Limit(0)), None)
