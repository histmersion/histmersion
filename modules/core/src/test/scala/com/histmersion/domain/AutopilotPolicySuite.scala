package com.histmersion
package domain

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence
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
import com.histmersion.domain.research.Slug
import com.histmersion.domain.automation.Manager
import com.histmersion.domain.automation.Pipeline
import com.histmersion.domain.automation.Run
import com.histmersion.api.automation.Managers
import com.histmersion.domain.ManagerPolicy.Item
import com.histmersion.lint.{LintRule, tolerated}

/** The autopilot's pure rules (docs/research-process-managers.md "As built: the autopilot"): the checked auto approvals
  * and their caps, the goal-status advance, the managers every goal has, the spread crons, the extend threshold, the
  * shared ingest cap, housekeeping and the pace.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class AutopilotPolicySuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-19T12:00:00Z")

  // ── approvals ──────────────────────────────────────────────────────────────────────────────────

  test("article-plan: every slug free and every filter at min-sources → approved, counted against the area's cap"):
    val ok = List(
      PlannedFacts(
        AutopilotPolicy.PlannedFacts.Slug("a"),
        slugFree = AutopilotPolicy.PlannedFacts.SlugFree(true),
        AutopilotPolicy.PlannedFacts.Hits(9)
      ),
      PlannedFacts(
        AutopilotPolicy.PlannedFacts.Slug("b"),
        slugFree = AutopilotPolicy.PlannedFacts.SlugFree(true),
        AutopilotPolicy.PlannedFacts.Hits(5)
      )
    )
    articlePlan(ok, AutopilotPolicy.MinSources(5), AutopilotPolicy.ApprovedToday(0), AutopilotPolicy.Cap(6)) match
      case Verdict.Approve(r) =>
        assert(s"$r".contains("2 articles") && s"$r".contains("a 9, b 5") && s"$r".contains("2 of 6"), r)
      case v => fail(v.toString)
    assert(
      articlePlan(ok, AutopilotPolicy.MinSources(5), AutopilotPolicy.ApprovedToday(4), AutopilotPolicy.Cap(6))
        .isInstanceOf[Verdict.Approve]
    )
    assertEquals(
      articlePlan(ok, AutopilotPolicy.MinSources(5), AutopilotPolicy.ApprovedToday(5), AutopilotPolicy.Cap(6)),
      Verdict.Leave(
        AutopilotPolicy.Rationale("the area's daily cap is reached (5 of 6 articles approved today); decided tomorrow")
      )
    )
    assert(
      articlePlan(ok, AutopilotPolicy.MinSources(5), AutopilotPolicy.ApprovedToday(0), AutopilotPolicy.Cap(1))
        .isInstanceOf[Verdict.Leave],
      "a plan larger than the whole cap is a person's"
    )

  test("article-plan: a taken slug or a thin filter rejects it; an empty plan too"):
    articlePlan(
      List(
        PlannedFacts(
          AutopilotPolicy.PlannedFacts.Slug("a"),
          slugFree = AutopilotPolicy.PlannedFacts.SlugFree(false),
          AutopilotPolicy.PlannedFacts.Hits(9)
        ),
        PlannedFacts(
          AutopilotPolicy.PlannedFacts.Slug("b"),
          slugFree = AutopilotPolicy.PlannedFacts.SlugFree(true),
          AutopilotPolicy.PlannedFacts.Hits(2)
        )
      ),
      AutopilotPolicy.MinSources(5),
      AutopilotPolicy.ApprovedToday(0),
      AutopilotPolicy.Cap(6)
    ) match
      case Verdict.Reject(r) =>
        assert(s"$r".contains("a: the slug is taken") && s"$r".contains("b: its filter returns 2"), r)
      case v => fail(v.toString)
    assert(
      articlePlan(Nil, AutopilotPolicy.MinSources(5), AutopilotPolicy.ApprovedToday(0), AutopilotPolicy.Cap(6))
        .isInstanceOf[Verdict.Reject]
    )

  private val draft =
    DraftFacts(
      AutopilotPolicy.DraftFacts.Slug("x"),
      create = AutopilotPolicy.DraftFacts.Create(true),
      filterRecorded = AutopilotPolicy.DraftFacts.FilterRecorded(true),
      hits = Some(AutopilotPolicy.DraftFacts.Hits(8)),
      problems = Nil,
      cited = AutopilotPolicy.DraftFacts.Cited(4),
      slugFree = AutopilotPolicy.DraftFacts.SlugFree(true)
    )

  test(
    "article-publish: filter recorded and wide enough, no problems, cites, slug free → approved within the daily cap"
  ):
    assert(
      articlePublish(draft, AutopilotPolicy.MinSources(3), AutopilotPolicy.ApprovedToday(0), AutopilotPolicy.Cap(10))
        .isInstanceOf[Verdict.Approve]
    )
    assert(
      articlePublish(draft, AutopilotPolicy.MinSources(3), AutopilotPolicy.ApprovedToday(10), AutopilotPolicy.Cap(10))
        .isInstanceOf[Verdict.Leave]
    )
    assert(
      articlePublish(
        draft
          .modify(_.create)
          .setTo(AutopilotPolicy.DraftFacts.Create(false))
          .modify(_.slugFree)
          .setTo(AutopilotPolicy.DraftFacts.SlugFree(false)),
        AutopilotPolicy.MinSources(3),
        AutopilotPolicy.ApprovedToday(0),
        AutopilotPolicy.Cap(10)
      ).isInstanceOf[Verdict.Approve],
      "an extension"
    )

  test("article-publish: each failed check rejects with why"):
    def why(d: DraftFacts) =
      articlePublish(d, AutopilotPolicy.MinSources(3), AutopilotPolicy.ApprovedToday(0), AutopilotPolicy.Cap(10)) match
        case Verdict.Reject(r) => s"$r"
        case v                 => fail(v.toString)
    assert(
      why(draft.modify(_.filterRecorded).setTo(AutopilotPolicy.DraftFacts.FilterRecorded(false)))
        .contains("no coverage filter")
    )
    assert(
      why(draft.modify(_.hits).setTo(Some(AutopilotPolicy.DraftFacts.Hits(1))))
        .contains("returns 1 source(s), fewer than 3")
    )
    assert(why(draft.modify(_.hits).setTo(None)).contains("unknown number"))
    assert(
      why(
        draft
          .modify(_.problems)
          .setTo(List(AutopilotPolicy.DraftFacts.Problems("p1"), AutopilotPolicy.DraftFacts.Problems("p2")))
      ).contains("2 problems: p1; p2")
    )
    assert(why(draft.modify(_.cited).setTo(AutopilotPolicy.DraftFacts.Cited(0))).contains("cites no source"))
    assert(why(draft.modify(_.slugFree).setTo(AutopilotPolicy.DraftFacts.SlugFree(false))).contains("slug x is taken"))

  test(
    "goal-plan: something proposed → approved; sub-goals at the depth limit rejected; nothing rejected; the cap leaves it"
  ):
    val p = GoalPlanFacts(
      AutopilotPolicy.GoalPlanFacts.Goal("g"),
      subGoals = AutopilotPolicy.GoalPlanFacts.SubGoals(3),
      queries = AutopilotPolicy.GoalPlanFacts.Queries(4),
      works = AutopilotPolicy.GoalPlanFacts.Works(2),
      depth = AutopilotPolicy.GoalPlanFacts.Depth(1)
    )
    assert(
      goalPlan(p, AutopilotPolicy.DepthLimit(2), AutopilotPolicy.ApprovedToday(0), AutopilotPolicy.Cap(5))
        .isInstanceOf[Verdict.Approve]
    )
    goalPlan(
      p.modify(_.depth).setTo(AutopilotPolicy.GoalPlanFacts.Depth(2)),
      AutopilotPolicy.DepthLimit(2),
      AutopilotPolicy.ApprovedToday(0),
      AutopilotPolicy.Cap(5)
    ) match
      case Verdict.Reject(r) =>
        assert(s"$r".contains("3 sub-goals") && s"$r".contains("2 level(s) below a root"), r)
      case v => fail(v.toString)
    assert(
      goalPlan(
        p.modify(_.subGoals)
          .setTo(AutopilotPolicy.GoalPlanFacts.SubGoals(0))
          .modify(_.depth)
          .setTo(AutopilotPolicy.GoalPlanFacts.Depth(2)),
        AutopilotPolicy.DepthLimit(2),
        AutopilotPolicy.ApprovedToday(0),
        AutopilotPolicy.Cap(5)
      ).isInstanceOf[Verdict.Approve],
      "no sub-goals: fine at any depth"
    )
    assert(
      goalPlan(
        p.modify(_.subGoals)
          .setTo(AutopilotPolicy.GoalPlanFacts.SubGoals(0))
          .modify(_.queries)
          .setTo(AutopilotPolicy.GoalPlanFacts.Queries(0))
          .modify(_.works)
          .setTo(AutopilotPolicy.GoalPlanFacts.Works(0)),
        AutopilotPolicy.DepthLimit(2),
        AutopilotPolicy.ApprovedToday(0),
        AutopilotPolicy.Cap(5)
      ).isInstanceOf[Verdict.Reject]
    )
    assert(
      goalPlan(p, AutopilotPolicy.DepthLimit(2), AutopilotPolicy.ApprovedToday(5), AutopilotPolicy.Cap(5))
        .isInstanceOf[Verdict.Leave]
    )

  test("the note marks the decision as the autopilot's; a left one carries none"):
    assertEquals(note(Verdict.Approve(AutopilotPolicy.Rationale("r"))), Some("autopilot: approved — r"))
    assertEquals(note(Verdict.Reject(AutopilotPolicy.Rationale("r"))), Some("autopilot: rejected — r"))
    assertEquals(note(Verdict.Leave(AutopilotPolicy.Rationale("r"))), None)

  test("depth: the parents above a goal; a cycle stops"):
    val parent = Map(Slug("c") -> Slug("b"), Slug("b") -> Slug("a"))
    assertEquals(depth(Slug("c"), parent.get), 2)
    assertEquals(depth(Slug("a"), parent.get), 0)
    assertEquals(depth(Slug("x"), Map(Slug("x") -> Slug("y"), Slug("y") -> Slug("x")).get), 1)

  // ── goal status ────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val g =
    GoalFacts(
      AutopilotPolicy.GoalFacts.Slug("g"),
      Goal.Status.Wire(Goal.Status.Planned),
      managed = AutopilotPolicy.GoalFacts.Managed(false),
      articles = AutopilotPolicy.GoalFacts.Articles(0),
      compileOpen = AutopilotPolicy.GoalFacts.CompileOpen(false),
      reviewed = AutopilotPolicy.GoalFacts.Reviewed(false),
      Nil
    )

  test(
    "advance: planned → seeding once managed; → compiled with an article and nothing left; → reviewed when it passed"
  ):
    assertEquals(advance(g), None)
    assertEquals(
      advance(g.modify(_.managed).setTo(AutopilotPolicy.GoalFacts.Managed(true))).map(_._1),
      Some(Goal.Status.Seeding)
    )
    assertEquals(
      advance(
        g.modify(_.managed)
          .setTo(AutopilotPolicy.GoalFacts.Managed(true))
          .modify(_.articles)
          .setTo(AutopilotPolicy.GoalFacts.Articles(2))
          .modify(_.compileOpen)
          .setTo(AutopilotPolicy.GoalFacts.CompileOpen(true))
      ).map(_._1),
      Some(Goal.Status.Seeding)
    )
    assertEquals(
      advance(
        g.modify(_.managed)
          .setTo(AutopilotPolicy.GoalFacts.Managed(true))
          .modify(_.articles)
          .setTo(AutopilotPolicy.GoalFacts.Articles(2))
      ).map(_._1),
      Some(Goal.Status.Compiled)
    )
    assertEquals(
      advance(
        g.modify(_.status)
          .setTo(Goal.Status.Wire(Goal.Status.Compiled))
          .modify(_.managed)
          .setTo(AutopilotPolicy.GoalFacts.Managed(true))
          .modify(_.articles)
          .setTo(AutopilotPolicy.GoalFacts.Articles(2))
          .modify(_.reviewed)
          .setTo(AutopilotPolicy.GoalFacts.Reviewed(true))
      ).map((a, p) => (a, s"$p")),
      Some((Goal.Status.Reviewed, "its 2 articles passed review"))
    )

  test("advance: never back, never past done, an unknown status is left"):
    assertEquals(
      advance(
        g.modify(_.status)
          .setTo(Goal.Status.Wire(Goal.Status.Reviewed))
          .modify(_.managed)
          .setTo(AutopilotPolicy.GoalFacts.Managed(true))
      ),
      None
    )
    assertEquals(
      advance(
        g.modify(_.status)
          .setTo(Goal.Status.Wire(Goal.Status.Done))
          .modify(_.managed)
          .setTo(AutopilotPolicy.GoalFacts.Managed(true))
          .modify(_.articles)
          .setTo(AutopilotPolicy.GoalFacts.Articles(3))
          .modify(_.reviewed)
          .setTo(AutopilotPolicy.GoalFacts.Reviewed(true))
      ),
      None
    )
    assertEquals(
      advance(
        g.modify(_.status)
          .setTo(Goal.Status.Wire("frozen"))
          .modify(_.managed)
          .setTo(AutopilotPolicy.GoalFacts.Managed(true))
      ),
      None
    )
    assertEquals(
      advance(
        g.modify(_.status)
          .setTo(Goal.Status.Wire(Goal.Status.Compiled))
          .modify(_.managed)
          .setTo(AutopilotPolicy.GoalFacts.Managed(true))
          .modify(_.articles)
          .setTo(AutopilotPolicy.GoalFacts.Articles(1))
          .modify(_.compileOpen)
          .setTo(AutopilotPolicy.GoalFacts.CompileOpen(true))
      ),
      None
    )

  test("advance: a parent follows its sub-goals"):
    val p =
      g.modify(_.status)
        .setTo(Goal.Status.Wire(Goal.Status.Seeding))
        .modify(_.managed)
        .setTo(AutopilotPolicy.GoalFacts.Managed(true))
        .modify(_.children)
        .setTo(List(Goal.Status.Wire(Goal.Status.Compiled), Goal.Status.Wire(Goal.Status.Seeding)))
    assertEquals(advance(p), None)
    assertEquals(
      advance(
        p.modify(_.children).setTo(List(Goal.Status.Wire(Goal.Status.Compiled), Goal.Status.Wire(Goal.Status.Reviewed)))
      ).map(_._1),
      Some(Goal.Status.Compiled)
    )
    assertEquals(
      advance(
        p.modify(_.children).setTo(List(Goal.Status.Wire(Goal.Status.Reviewed), Goal.Status.Wire(Goal.Status.Done)))
      ).map(_._1),
      Some(Goal.Status.Reviewed),
      "no own article: reviewed when its sub-goals are"
    )
    assertEquals(
      advance(
        p.modify(_.articles)
          .setTo(AutopilotPolicy.GoalFacts.Articles(1))
          .modify(_.children)
          .setTo(List(Goal.Status.Wire(Goal.Status.Reviewed)))
      ).map(_._1),
      Some(Goal.Status.Compiled),
      "its own article not reviewed yet"
    )

  // ── ensure ─────────────────────────────────────────────────────────────────────────────────────

  private val st = Settings(
    AutopilotPolicy.Cron("@weekly"),
    AutopilotPolicy.Cron("@weekly"),
    AutopilotPolicy.Cron("@monthly"),
    AutopilotPolicy.Settings.WatchIngestPerDay(5),
    AutopilotPolicy.Settings.ExtendMinNew(3)
  )

  test(
    "ensure: an unfinished goal gets an active seeding manager (plan first) and a watcher; a finished one a watcher"
  ):
    val steps = ensure(
      List(
        GoalRow(
          Goal.Slug("a"),
          Goal.Status.Wire(Goal.Status.Planned),
          needsPlan = AutopilotPolicy.GoalRow.NeedsPlan(true)
        ),
        GoalRow(
          Goal.Slug("r"),
          Goal.Status.Wire(Goal.Status.Reviewed),
          needsPlan = AutopilotPolicy.GoalRow.NeedsPlan(false)
        )
      ),
      Nil,
      Set.empty,
      st
    )
    assertEquals(steps.map(_.getClass.getSimpleName), List("CreateSeed", "CreateWatch", "CreateWatch"))
    assertEquals(
      steps.head,
      Step.CreateSeed(
        AutopilotPolicy.Step.CreateSeed.Goal("a"),
        stagger(AutopilotPolicy.Cron("@weekly"), ManagerRow.Id.seed(Goal.Slug("a"))),
        planFirst = AutopilotPolicy.Step.CreateSeed.PlanFirst(true)
      )
    )
    assertEquals(
      steps(2),
      Step.CreateWatch(
        AutopilotPolicy.Step.CreateWatch.Goal("r"),
        stagger(AutopilotPolicy.Cron("@monthly"), ManagerRow.Id.watch(Goal.Slug("r"))),
        AutopilotPolicy.Step.CreateWatch.PerDay(5)
      )
    )

  test("ensure: idempotent; fills only what is unset; resumes only what it paused; review and enrich active"):
    val ms = List(
      ManagerRow(ManagerRow.Id(ReviewId), Manager.Kind.Wire("review"), None, Manager.State.Wire("paused"), None, None),
      ManagerRow(ManagerRow.Id(EnrichId), Manager.Kind.Wire("enrich"), None, Manager.State.Wire("active"), None, None),
      ManagerRow(
        ManagerRow.Id.seed(Goal.Slug("a")),
        Manager.Kind.Wire("seed"),
        Some(Goal.Slug("a")),
        Manager.State.Wire("paused"),
        Some(AutopilotPolicy.Cron("0 3 * * 1")),
        None,
        planItem = AutopilotPolicy.ManagerRow.PlanItem(true)
      ),
      ManagerRow(
        ManagerRow.Id.watch(Goal.Slug("a")),
        Manager.Kind.Wire("watch"),
        Some(Goal.Slug("a")),
        Manager.State.Wire("paused"),
        None,
        Some(AutopilotPolicy.ManagerRow.AutoIngest(false))
      ),
      ManagerRow(
        ManagerRow.Id.seed(Goal.Slug("b")),
        Manager.Kind.Wire("seed"),
        Some(Goal.Slug("b")),
        Manager.State.Wire("paused"),
        None,
        None
      ),
      ManagerRow(
        ManagerRow.Id.watch(Goal.Slug("b")),
        Manager.Kind.Wire("watch"),
        Some(Goal.Slug("b")),
        Manager.State.Wire("active"),
        Some(AutopilotPolicy.Cron("@weekly")),
        None
      )
    )
    val steps = ensure(
      List(
        GoalRow(
          Goal.Slug("a"),
          Goal.Status.Wire(Goal.Status.Seeding),
          needsPlan = AutopilotPolicy.GoalRow.NeedsPlan(true)
        ),
        GoalRow(
          Goal.Slug("b"),
          Goal.Status.Wire(Goal.Status.Planned),
          needsPlan = AutopilotPolicy.GoalRow.NeedsPlan(true)
        )
      ),
      ms,
      Set(ManagerRow.Id.seed(Goal.Slug("a"))),
      st
    )
    assertEquals(
      steps,
      List(
        Step.Resume(ManagerRow.Id(ReviewId)),
        Step.ExtendMinNew(ManagerRow.Id(ReviewId), AutopilotPolicy.Step.ExtendMinNew.N(3)),
        Step.Resume(ManagerRow.Id.seed(Goal.Slug("a"))),
        Step.SetCron(
          ManagerRow.Id.watch(Goal.Slug("a")),
          stagger(AutopilotPolicy.Cron("@weekly"), ManagerRow.Id.watch(Goal.Slug("a")))
        ),
        Step.SetCron(
          ManagerRow.Id.seed(Goal.Slug("b")),
          stagger(AutopilotPolicy.Cron("@weekly"), ManagerRow.Id.seed(Goal.Slug("b")))
        ),
        Step.AddPlanItem(ManagerRow.Id.seed(Goal.Slug("b")), AutopilotPolicy.Step.AddPlanItem.Goal("b")),
        Step.AutoIngestOn(ManagerRow.Id.watch(Goal.Slug("b")), AutopilotPolicy.Step.AutoIngestOn.PerDay(5))
      )
    )
    val done = ms.map(m =>
      m.modify(_.state)
        .setTo(if m.id == ManagerRow.Id.watch(Goal.Slug("a")) then Manager.State.Wire("paused")
        else Manager.State.Wire("active"))
        .modify(_.cron)
        .setTo(m.cron.orElse(Some(AutopilotPolicy.Cron("x"))))
        .modify(_.autoIngest)
        .setTo(m.autoIngest.orElse(Some(AutopilotPolicy.ManagerRow.AutoIngest(true))))
        .modify(_.planItem)
        .setTo(AutopilotPolicy.ManagerRow.PlanItem(true))
        .modify(_.extendMinNew)
        .setTo(Some(AutopilotPolicy.Settings.ExtendMinNew(3)))
    )
    assertEquals(
      ensure(
        List(
          GoalRow(Goal.Slug("a"), Goal.Status.Wire(Goal.Status.Seeding), AutopilotPolicy.GoalRow.NeedsPlan(true)),
          GoalRow(Goal.Slug("b"), Goal.Status.Wire(Goal.Status.Planned), AutopilotPolicy.GoalRow.NeedsPlan(true))
        ),
        done,
        Set.empty,
        st
      ),
      Nil
    )

  test("toPause: the active seeding managers and watchers only"):
    val ms = List(
      ManagerRow(ManagerRow.Id(ReviewId), Manager.Kind.Wire("review"), None, Manager.State.Wire("active"), None, None),
      ManagerRow(
        ManagerRow.Id.seed(Goal.Slug("a")),
        Manager.Kind.Wire("seed"),
        Some(Goal.Slug("a")),
        Manager.State.Wire("active"),
        None,
        None
      ),
      ManagerRow(
        ManagerRow.Id.watch(Goal.Slug("a")),
        Manager.Kind.Wire("watch"),
        Some(Goal.Slug("a")),
        Manager.State.Wire("paused"),
        None,
        None
      ),
      ManagerRow(
        ManagerRow.Id.watch(Goal.Slug("b")),
        Manager.Kind.Wire("watch"),
        Some(Goal.Slug("b")),
        Manager.State.Wire("active"),
        None,
        None
      )
    )
    assertEquals(toPause(ms), List(ManagerRow.Id.seed(Goal.Slug("a")), ManagerRow.Id.watch(Goal.Slug("b"))))

  test("stagger: spread per key, stable, valid, at most weekly; another cron kept"):
    val crons =
      (1 to 60).map(i => stagger(AutopilotPolicy.Cron("@weekly"), AutopilotPolicy.ManagerRow.Id(s"watch:goal-$i")))
    assert(crons.distinct.size > 50, crons.distinct.size)
    assertEquals(
      stagger(AutopilotPolicy.Cron("@weekly"), AutopilotPolicy.ManagerRow.Id("watch:x")),
      stagger(AutopilotPolicy.Cron("@weekly"), AutopilotPolicy.ManagerRow.Id("watch:x"))
    )
    crons.foreach(c =>
      assert(
        ManagerPolicy
          .checkCron(domain.Cron.Text(c.value), now, ManagerPolicy.MinIntervalMinutes(60))
          .isRight,
        c
      )
    )
    assert(
      domain.Cron
        .parse(
          domain.Cron
            .Text(stagger(AutopilotPolicy.Cron("@daily"), AutopilotPolicy.ManagerRow.Id("k")).value)
        )
        .isRight && domain.Cron
        .parse(
          domain.Cron
            .Text(stagger(AutopilotPolicy.Cron("@monthly"), AutopilotPolicy.ManagerRow.Id("k")).value)
        )
        .isRight
    )
    assertEquals(stagger(AutopilotPolicy.Cron("0 4 * * 1"), AutopilotPolicy.ManagerRow.Id("k")), "0 4 * * 1")
    val minutes = (1 to 60)
      .map(i =>
        stagger(AutopilotPolicy.Cron("@daily"), AutopilotPolicy.ManagerRow.Id(s"g$i")).value.split(" ")(0).toInt
      )
      .distinct
    assert(minutes.size > 25, minutes.size)

  // ── extend, caps, housekeeping, pace ───────────────────────────────────────────────────────────

  test("extendDue: at the threshold and past the cooldown; 0 = never"):
    assert(
      extendDue(
        AutopilotPolicy.NewSources(3),
        AutopilotPolicy.Settings.ExtendMinNew(3),
        None,
        AutopilotPolicy.CooldownDays(14),
        now
      ).exists(_.value.contains("3 sources"))
    )
    assertEquals(
      extendDue(
        AutopilotPolicy.NewSources(2),
        AutopilotPolicy.Settings.ExtendMinNew(3),
        None,
        AutopilotPolicy.CooldownDays(14),
        now
      ),
      None
    )
    assertEquals(
      extendDue(
        AutopilotPolicy.NewSources(9),
        AutopilotPolicy.Settings.ExtendMinNew(0),
        None,
        AutopilotPolicy.CooldownDays(14),
        now
      ),
      None
    )
    assertEquals(
      extendDue(
        AutopilotPolicy.NewSources(9),
        AutopilotPolicy.Settings.ExtendMinNew(3),
        Some(now.minusSeconds(3600)),
        AutopilotPolicy.CooldownDays(14),
        now
      ),
      None
    )
    assert(
      extendDue(
        AutopilotPolicy.NewSources(9),
        AutopilotPolicy.Settings.ExtendMinNew(3),
        Some(now.minusSeconds(15L * 86400)),
        AutopilotPolicy.CooldownDays(14),
        now
      ).isDefined
    )

  test("sharedIngestLeft, due, etaDays"):
    assertEquals(sharedIngestLeft(AutopilotPolicy.Cap(20), AutopilotPolicy.IngestedToday(7)), Some(13))
    assertEquals(sharedIngestLeft(AutopilotPolicy.Cap(20), AutopilotPolicy.IngestedToday(25)), Some(0))
    assertEquals(sharedIngestLeft(AutopilotPolicy.Cap(0), AutopilotPolicy.IngestedToday(7)), None)
    assert(
      due(None, AutopilotPolicy.EveryHours(24), now) && !due(
        Some(now.minusSeconds(3600)),
        AutopilotPolicy.EveryHours(24),
        now
      ) && due(Some(now.minusSeconds(86400)), AutopilotPolicy.EveryHours(24), now)
    )
    assert(!due(None, AutopilotPolicy.EveryHours(0), now))
    assertEquals(etaDays(AutopilotPolicy.OpenCount(10), AutopilotPolicy.DoneLast7(7)), Some(10))
    assertEquals(etaDays(AutopilotPolicy.OpenCount(1), AutopilotPolicy.DoneLast7(70)), Some(1))
    assertEquals(etaDays(AutopilotPolicy.OpenCount(0), AutopilotPolicy.DoneLast7(7)), None)
    assertEquals(etaDays(AutopilotPolicy.OpenCount(5), AutopilotPolicy.DoneLast7(0)), None)

  // ── the manager policy additions ───────────────────────────────────────────────────────────────

  test("waitsFor: a goal's article proposal waits for its plan-goal item"):
    assert(
      ManagerPolicy.waitsFor(
        Item.Kind.ProposeArticles,
        ManagerPolicy.Candidate.Target("g"),
        List((Item.Kind.PlanGoalItem, ManagerPolicy.Candidate.Target("g")))
      )
    )
    assert(
      !ManagerPolicy.waitsFor(
        Item.Kind.ProposeArticles,
        ManagerPolicy.Candidate.Target("h"),
        List((Item.Kind.PlanGoalItem, ManagerPolicy.Candidate.Target("g")))
      )
    )
    assertEquals(itemKindOf(Kind.Seed, api.ItemKind.Word("plan-goal")), Some(PlanGoalItem))
    assertEquals(
      itemKindOf(Kind.Seed, api.ItemKind.Word("plan")),
      Some(ProposeArticles),
      "`plan` keeps naming the article plan"
    )

  test("blocks: an article waits for any run on it; a goal's item only for a run of its own pipeline"):
    assert(
      ManagerPolicy.blocks(
        Item.Kind.ArticleReview,
        Pipeline.Wire("review-article"),
        List(Pipeline.Wire("compile-article"))
      )
    )
    assert(!ManagerPolicy.blocks(Item.Kind.ArticleReview, Pipeline.Wire("review-article"), Nil))
    assert(
      !ManagerPolicy.blocks(
        Item.Kind.AcquisitionSweep,
        Pipeline.Wire("acquisition-sweep"),
        List(Pipeline.Wire("plan-goal"), Pipeline.Wire("propose-articles"))
      )
    )
    assert(
      ManagerPolicy.blocks(
        Item.Kind.AcquisitionSweep,
        Pipeline.Wire("acquisition-sweep"),
        List(Pipeline.Wire("acquisition-sweep"))
      )
    )
    assert(
      !ManagerPolicy.blocks(
        Item.Kind.ProposeArticles,
        Pipeline.Wire("propose-articles"),
        List(Pipeline.Wire("currency-check"))
      )
    )

  test("evaluateStep: a draft the checks rejected is compiled again after the backoff, then stopped with a task"):
    val s = ReviewSettings(
      ReviewSettings.MaxRuns(1),
      ReviewSettings.MaxOpen(2),
      ReviewSettings.Rounds(1),
      ReviewSettings.MaxRunsPerArticle(1),
      ReviewSettings.NoProgressRuns(1),
      ReviewSettings.ReopenLimit(1),
      ReviewSettings.CooldownMinutes(0),
      ReviewSettings.LowWater(0),
      ReviewSettings.RefillBatch(0),
      maxFailures = ReviewSettings.MaxFailures(2),
      backoffMinutes = ReviewSettings.BackoffMinutes(15),
      maxBackoffMinutes = ReviewSettings.MaxBackoffMinutes(360)
    )
    ManagerPolicy.evaluateStep(
      s,
      ManagerPolicy.StepLabel(ArticleCompile),
      ManagerPolicy.Candidate.Target("x"),
      Next.Retry.Failures(0),
      ItemState(),
      Run.Id("1feffdd6-4fb2-79cf-4978-543444f78922"),
      Run.State.Done,
      None,
      StepFacts.Redo(Item.Outcome("the draft was rejected")),
      now
    ) match
      case Next.Retry(at, f, note) if f.value == 1 => assert(at.isAfter(now) && note.value.contains("again"), note)
      case n                                       => fail(n.toString)
    ManagerPolicy.evaluateStep(
      s,
      ManagerPolicy.StepLabel(ArticleCompile),
      ManagerPolicy.Candidate.Target("x"),
      Next.Retry.Failures(1),
      ItemState(),
      Run.Id("1feffdd6-4fb2-79cf-4978-543444f78922"),
      Run.State.Done,
      None,
      StepFacts.Redo(Item.Outcome("the draft was rejected")),
      now
    ) match
      case Next.Stop(o, Some(task), _) => assert(o.value.contains("2 attempts") && task.value.contains("[[x]]"), task)
      case n                           => fail(n.toString)

  test("ManagerConfig: autoApprove and extendMinNew parse, merge and print"):
    val c = ManagerConfig
      .parse(List(Managers.ManagerConfig.Spec("autoApprove=on"), Managers.ManagerConfig.Spec("extend-min-new=4")))
      .toOption
      .get
    assertEquals((c.autoApprove, c.extendMinNew), (Some(true), Some(4)))
    assertEquals(ManagerConfig().merge(c).autoApprove, Some(true))
    assert(ManagersMarkdown.configLine(c).contains("autoApprove=true, extendMinNew=4"))

/** The daily caps scaled to the backlog (#3762 proposal 3). */
class ScaledCapSuite extends munit.FunSuite:
  import AutopilotPolicy.{capText, scaledCap}

  test("share 0 keeps the fixed cap; a share scales it up to the ceiling, never below the base"):
    assertEquals(
      scaledCap(
        AutopilotPolicy.Base(5),
        AutopilotPolicy.Backlog(64),
        AutopilotPolicy.Share(0),
        AutopilotPolicy.Cap(50)
      ),
      5
    )
    assertEquals(
      scaledCap(
        AutopilotPolicy.Base(5),
        AutopilotPolicy.Backlog(64),
        AutopilotPolicy.Share(0.34),
        AutopilotPolicy.Cap(50)
      ),
      22
    )
    assertEquals(
      scaledCap(
        AutopilotPolicy.Base(5),
        AutopilotPolicy.Backlog(6),
        AutopilotPolicy.Share(0.34),
        AutopilotPolicy.Cap(50)
      ),
      5,
      "a small backlog: the base"
    )
    assertEquals(
      scaledCap(
        AutopilotPolicy.Base(5),
        AutopilotPolicy.Backlog(640),
        AutopilotPolicy.Share(0.34),
        AutopilotPolicy.Cap(50)
      ),
      50,
      "the ceiling"
    )
    assertEquals(
      scaledCap(
        AutopilotPolicy.Base(5),
        AutopilotPolicy.Backlog(640),
        AutopilotPolicy.Share(0.34),
        AutopilotPolicy.Cap(0)
      ),
      218,
      "no ceiling"
    )
    assertEquals(
      scaledCap(
        AutopilotPolicy.Base(10),
        AutopilotPolicy.Backlog(640),
        AutopilotPolicy.Share(0.34),
        AutopilotPolicy.Cap(4)
      ),
      10,
      "a ceiling below the base keeps the base"
    )
    assertEquals(
      scaledCap(
        AutopilotPolicy.Base(-1),
        AutopilotPolicy.Backlog(-5),
        AutopilotPolicy.Share(0.5),
        AutopilotPolicy.Cap(10)
      ),
      0
    )

  test("the status text"):
    assertEquals(
      capText(
        AutopilotPolicy.CapTextBase(5),
        AutopilotPolicy.CapTextBacklog(64),
        AutopilotPolicy.CapTextShare(0),
        AutopilotPolicy.CapTextCeiling(50)
      ),
      "5 a day"
    )
    assertEquals(
      capText(
        AutopilotPolicy.CapTextBase(5),
        AutopilotPolicy.CapTextBacklog(64),
        AutopilotPolicy.CapTextShare(0.34),
        AutopilotPolicy.CapTextCeiling(50)
      ),
      "5 a day, 22 today for a backlog of 64 (at most 50)"
    )

/** #3763: new articles only once the current ones are done. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class NextArticleSuite extends munit.FunSuite:
  import AutopilotPolicy.{articlesInProgress, nextArticle, NextFacts}
  import com.histmersion.domain.automation.{ItemStatus, Manager}

  private val now = java.time.Instant.parse("2026-10-24T12:00:00Z")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val done = NextFacts(
    NextFacts.Goal("g"),
    Goal.Status.Wire(Goal.Status.Reviewed),
    articles = NextFacts.Articles(2),
    unreviewed = NextFacts.Unreviewed(0),
    compileOpen = NextFacts.CompileOpen(0),
    seedState = Some(Manager.State.Active.wire),
    proposeStatus = Some(ItemStatus.Done.wire),
    proposeSettled = Some(now.minusSeconds(2 * 86400))
  )

  test("in progress: an article to compile or to pass review keeps a new proposal back"):
    assertEquals(articlesInProgress(NextFacts.Unreviewed(0), NextFacts.CompileOpen(0)), None)
    assert(
      articlesInProgress(NextFacts.Unreviewed(1), NextFacts.CompileOpen(0)).exists(_.value.contains("1 to pass review"))
    )
    assert(
      articlesInProgress(NextFacts.Unreviewed(0), NextFacts.CompileOpen(2)).exists(_.value.contains("2 to compile"))
    )

  test("the next article: every article done, the seeding manager active, no proposal open, the cooldown over"):
    assert(
      nextArticle(done, now, AutopilotPolicy.CooldownHours(24))
        .exists(_.value.contains("2 articles are compiled and passed review"))
    )
    assertEquals(
      nextArticle(done.modify(_.unreviewed).setTo(NextFacts.Unreviewed(1)), now, AutopilotPolicy.CooldownHours(24)),
      None,
      "an article to review"
    )
    assertEquals(
      nextArticle(done.modify(_.compileOpen).setTo(NextFacts.CompileOpen(1)), now, AutopilotPolicy.CooldownHours(24)),
      None,
      "an article to compile"
    )
    assertEquals(
      nextArticle(done.modify(_.articles).setTo(NextFacts.Articles(0)), now, AutopilotPolicy.CooldownHours(24)),
      None,
      "no article yet: the first proposal is the plan's"
    )
    assertEquals(
      nextArticle(
        done.modify(_.proposeStatus).setTo(Some(ItemStatus.Pending.wire)),
        now,
        AutopilotPolicy.CooldownHours(24)
      ),
      None,
      "a proposal open"
    )
    assertEquals(
      nextArticle(
        done.modify(_.proposeSettled).setTo(Some(now.minusSeconds(3600))),
        now,
        AutopilotPolicy.CooldownHours(24)
      ),
      None,
      "the cooldown"
    )
    assertEquals(
      nextArticle(
        done.modify(_.seedState).setTo(Some(Manager.State.Paused.wire)),
        now,
        AutopilotPolicy.CooldownHours(24)
      ),
      None,
      "a paused manager"
    )
    assertEquals(
      nextArticle(
        done.modify(_.status).setTo(Goal.Status.Wire(Goal.Status.Done)),
        now,
        AutopilotPolicy.CooldownHours(24)
      ),
      None,
      "a person's done"
    )
    assertEquals(nextArticle(done, now, AutopilotPolicy.CooldownHours(0)), None, "off")
    assert(
      nextArticle(
        done.modify(_.proposeStatus).setTo(None).modify(_.proposeSettled).setTo(None),
        now,
        AutopilotPolicy.CooldownHours(24)
      ).isDefined,
      "never proposed"
    )

  test("unfinished goals: the planned goals neither reviewed nor done"):
    val goals = List(
      Goal.Slug("a") -> Goal.Status.Wire(Goal.Status.Planned),
      Goal.Slug("b") -> Goal.Status.Wire(Goal.Status.Seeding),
      Goal.Slug("c") -> Goal.Status.Wire(Goal.Status.Compiled),
      Goal.Slug("d") -> Goal.Status.Wire(Goal.Status.Reviewed),
      Goal.Slug("e") -> Goal.Status.Wire(Goal.Status.Done),
      Goal.Slug("f") -> Goal.Status.Wire(Goal.Status.Seeding)
    )
    assertEquals(
      unfinishedPlanned(goals, Set(Goal.Slug("a"), Goal.Slug("b"), Goal.Slug("c"), Goal.Slug("d"), Goal.Slug("e"))),
      3
    )
    assertEquals(unfinishedPlanned(goals, Set.empty), 0, "a goal without an approved plan is not counted")

  test("goal plans wait while the unfinished goals exceed the limit; 0 = no limit"):
    assertEquals(
      goalPlansWait(AutopilotPolicy.Unfinished(10), AutopilotPolicy.Limit(10)),
      None,
      "at the limit: offered"
    )
    val w = goalPlansWait(AutopilotPolicy.Unfinished(60), AutopilotPolicy.Limit(10)).getOrElse(fail("waits")).value
    assert(
      w.contains("drop to 10") && w.contains("60 goals with an approved plan are not reviewed yet") &&
        w.contains("the autopilot's limit on unfinished goals") && !w.contains("agents."),
      w
    )
    assert(goalPlansWait(AutopilotPolicy.Unfinished(2), AutopilotPolicy.Limit(1)).exists(_.value.contains("2 goals")))
    assertEquals(goalPlansWait(AutopilotPolicy.Unfinished(60), AutopilotPolicy.Limit(0)), None)
