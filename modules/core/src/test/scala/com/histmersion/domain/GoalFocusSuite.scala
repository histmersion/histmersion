package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.automation.Autopilot.{FocusGoal, FocusInfo, FocusWaiting}
import com.histmersion.domain.GoalFocus.*
import com.histmersion.api.automation.Managers.*
import com.histmersion.api.automation.{Autopilot, AutopilotMarkdown}
import com.histmersion.domain.automation.Goal

/** The autopilot's focus, pure (docs/research-process-managers.md "As built: the focus"): the selection order (closest
  * to done, depth-first, pins, a person's priority, ties), the sticky active set and what frees a slot (reviewed,
  * stuck, idle, waiting for a person), the parent rule, the holds, the fair order of the library-wide managers, the
  * goal-plan check and the Markdown.
  */
class GoalFocusSuite extends munit.FunSuite:

  private def w(id: Long, kind: String = ProposeArticles, status: String = "pending") = Work(id, kind, status, s"t$id")
  private def g(
      slug: String,
      parent: Option[String] = None,
      sources: Int = 0,
      articles: Int = 0,
      unreviewed: Int = 0,
      compile: Int = 0,
      created: String = "2026-10-20T00:00:00Z",
      work: List[Work] = List(w(1)),
      active: Boolean = false,
      pinned: Boolean = false,
      priority: Int = 0,
      status: String = Goal.Status.Seeding,
      stop: Option[String] = None
  ) =
    Facts(slug, parent, status, created, sources, articles, unreviewed, compile, work, stop, pinned, priority, active)

  private val s3 = Settings(max = com.histmersion.domain.GoalFocus.Settings.Max(3), minSources = com.histmersion.domain.GoalFocus.Settings.MinSources(3))

  private def active(p: Plan) = p.active.map(_.slug)

  // ── the order ──────────────────────────────────────────────────────────────────────────────────

  test("closest to done first: articles to review, then to compile, then enough sources, then gathering"):
    val goals = List(
      g("gather", sources = 1),
      g("ready", sources = 5),
      g("compile", compile = 2),
      g("review", articles = 2, unreviewed = 1)
    )
    val p = select(goals, s3)
    assertEquals(active(p), List("review", "compile", "ready"))
    assertEquals(p.active.map(_.stage), List(Stage.Reviewing, Stage.Compiling, Stage.Ready))
    assertEquals(p.queue.map(_.slug), List("gather"))
    assert(p.active.head.why.startsWith("reviewing: 1 of 2 articles still to pass review"), p.active.head.why)
    assert(p.active(1).why.contains("2 approved articles to compile"), p.active(1).why)
    assert(p.active(2).why.contains("5 sources (at least 3), no article yet"), p.active(2).why)
    assertEquals(p.joined, List("review", "compile", "ready"))

  test("depth-first: once a goal of a tree is chosen, its siblings come first, then its tree, then elsewhere"):
    // all at the same stage; age alone would pick b1, a1x, b2 (Oct 2, 3, 4) — after b1, its sibling b2 comes first
    val goals = List(
      g("root-a"),
      g("root-b"),
      g("a1", Some("root-a"), created = "2026-10-01T00:00:00Z"),
      g("b1", Some("root-b"), created = "2026-10-02T00:00:00Z"),
      g("a2", Some("root-a"), created = "2026-10-05T00:00:00Z"),
      g("a1x", Some("a1"), created = "2026-10-03T00:00:00Z"),
      g("b2", Some("root-b"), created = "2026-10-04T00:00:00Z")
    )
      .map(f => if f.slug.startsWith("root") then f.modify(_.work).setTo(Nil).modify(_.status).setTo(Goal.Status.Compiled) else f)
    // a1 has an unfinished sub-goal: it is not a candidate (its own articles wait); a1x is the oldest leaf
    val p = select(goals, s3)
    assertEquals(active(p), List("b1", "b2", "a1x"), p.active.map(_.why))
    assert(p.active(1).why.contains("a sibling of b1 (active)"), p.active(1).why)
    assert(p.waiting.exists((s, c, _) => s == "a1" && c == Code.Children), p.waiting)
    assert(p.waiting.exists((s, c, _) => s == "root-a" && c == Code.Children), p.waiting)
    assertEquals(p.queue.map(_.slug), List("a2"))
    assert(p.queue.head.why.contains("in the same tree as a1x"), p.queue.head.why)

  test("siblings of an active goal before an older goal of another tree"):
    val goals = List(
      g("x1", Some("x"), active = true),
      g("x2", Some("x"), created = "2026-10-21T00:00:00Z"),
      g("y1", Some("y"), created = "2026-01-01T00:00:00Z")
    )
    val p = select(goals, s3.modify(_.max).setTo(2))
    assertEquals(active(p), List("x1", "x2"))
    assert(p.active(1).why.contains("a sibling of x1 (active)"), p.active(1).why)

  test("a sub-goal of an active parent (planning) before a goal elsewhere"):
    val goals = List(
      g("p", work = List(w(1, PlanGoalItem)), active = true),
      g("k", Some("p"), created = "2026-10-22T00:00:00Z"),
      g("other", created = "2026-01-01T00:00:00Z")
    )
    val p = select(goals, s3.modify(_.max).setTo(2))
    assertEquals(active(p), List("p", "k"))
    assert(p.active(1).why.contains("a sub-goal of p (active)"), p.active(1).why)

  test("a person's priority, then more sources, then the oldest, then the slug"):
    val goals = List(
      g("old", created = "2026-01-01T00:00:00Z"),
      g("prio", priority = 5),
      g("more", sources = 2, created = "2026-10-22T00:00:00Z"),
      g("b"),
      g("a")
    )
    val p = select(goals, s3.modify(_.max).setTo(5))
    assertEquals(active(p), List("prio", "more", "old", "a", "b"))
    assert(p.active.head.why.contains("priority 5"), p.active.head.why)

  test("deterministic: the same facts in another order give the same plan"):
    val goals =
      List(g("c", sources = 4), g("a"), g("b", compile = 1), g("d", Some("a")), g("e", articles = 1, unreviewed = 1))
    assertEquals(select(goals.reverse, s3), select(goals, s3))

  test("pins are always active, even past the cap; then the cap is full"):
    val goals = List(g("review", articles = 1, unreviewed = 1), g("p1", pinned = true), g("p2", pinned = true))
    val p = select(goals, s3.modify(_.max).setTo(2))
    assertEquals(active(p), List("p1", "p2"))
    assert(p.active.forall(_.pinned) && p.active.head.why.startsWith("pinned by a person"), p.active)
    assertEquals(p.queue.map(_.slug), List("review"))
    assertEquals(active(select(goals, s3.modify(_.max).setTo(1))), List("p1", "p2"), "pins past the cap")

  test("no cap (max 0): every candidate is active"):
    assertEquals(select((1 to 12).toList.map(i => g(s"g$i")), s3.modify(_.max).setTo(0)).active.size, 12)

  // ── the active set holds; what frees a slot ────────────────────────────────────────────────────

  test("an active goal keeps its slot while it has work, even when a better candidate appears"):
    val goals =
      List(g("kept1", sources = 5, active = true), g("kept2", sources = 4, active = true), g("better", sources = 9))
    val p = select(goals, s3.modify(_.max).setTo(2))
    assertEquals(active(p), List("kept1", "kept2"))
    assert(p.active.head.why.startsWith("active, kept until it is reviewed"), p.active.head.why)
    assertEquals(p.joined, Nil)

  test("a goal with articles to review or compile takes the slot of one still gathering (never a pinned one)"):
    val goals = List(
      g("gathering", sources = 1, active = true),
      g("pin", sources = 0, pinned = true, active = true),
      g("toreview", articles = 2, unreviewed = 2)
    )
    val p = select(goals, s3.modify(_.max).setTo(2))
    assertEquals(active(p).toSet, Set("pin", "toreview"))
    assertEquals(p.left, List("gathering" -> "gave its slot to toreview, which has articles to review"))
    assert(p.active.find(_.slug == "toreview").get.why.contains("took the slot of gathering"))
    // a ready goal does not preempt
    assertEquals(
      active(select(List(g("gathering", active = true), g("ready", sources = 5)), s3.modify(_.max).setTo(1))),
      List("gathering")
    )

  test("reviewed frees the slot and the next takes it at once"):
    val goals = List(
      g("done", status = Goal.Status.Reviewed, active = true, work = Nil),
      g("a", active = true),
      g("next", sources = 4)
    )
    val p = select(goals, s3.modify(_.max).setTo(2))
    assertEquals(active(p), List("a", "next"))
    assertEquals(p.left, List("done" -> "it is reviewed"))
    assertEquals(p.joined, List("next"))

  test("stuck (its last item stopped, nothing open) and idle free the slot; neither is a candidate"):
    val goals = List(
      g("stuck", active = true, work = Nil, stop = Some("stopped: too many runs (task #12)")),
      g("idle", active = true, work = Nil),
      g("next")
    )
    val p = select(goals, s3.modify(_.max).setTo(1))
    assertEquals(active(p), List("next"))
    assertEquals(p.left.toMap.get("stuck"), Some("stuck: its last item stopped — stopped: too many runs (task #12)"))
    assert(p.left.toMap.apply("idle").startsWith("idle"), p.left)
    assertEquals(p.waiting.map((s, c, _) => s -> c).toMap, Map("stuck" -> Code.Stuck, "idle" -> Code.Idle))

  test("waiting only for a person frees the slot; waiting for the autopilot's checks keeps it"):
    val person = Work(com.histmersion.domain.GoalFocus.Work.ItemId(5), PlanGoalItem, com.histmersion.domain.automation.ItemStatus.Wire("active"), com.histmersion.domain.GoalFocus.Work.Target("x"), approval = Some(41), approvalByPerson = true)
    val auto = Work(com.histmersion.domain.GoalFocus.Work.ItemId(6), ArticleCompile, com.histmersion.domain.automation.ItemStatus.Wire("active"), com.histmersion.domain.GoalFocus.Work.Target("y"), approval = Some(42))
    val goals =
      List(g("byperson", active = true, work = List(person)), g("byauto", active = true, work = List(auto)), g("next"))
    val p = select(goals, s3.modify(_.max).setTo(2))
    assertEquals(active(p), List("byauto", "next"))
    assertEquals(p.left, List("byperson" -> "waits for a person: approval #41"))
    // a proposal behind a plan that waits for a person waits for that person too; behind a pending plan it is fine
    val behind = select(List(g("p", work = List(person.modify(_.target).setTo("p"), w(9, ProposeArticles)))), s3)
    assertEquals(behind.waiting, List(("p", Code.Person, "waits for a person: approval #41")))
    assertEquals(active(select(List(g("p", work = List(w(8, PlanGoalItem), w(9, ProposeArticles)))), s3)), List("p"))
    // a person's hold, or a paused manager
    val held = select(List(g("h", work = List(w(1).modify(_.personHeld).setTo(true)))), s3)
    assertEquals(held.waiting.map(_._2), List(Code.Held))

  test("a parent with unfinished sub-goals takes a slot only for its plan"):
    val kids = List(g("k1", Some("p")), g("k2", Some("p"), status = Goal.Status.Reviewed, work = Nil))
    val planning = select(g("p", work = List(w(1, PlanGoalItem), w(2, ProposeArticles))) :: kids, s3)
    assertEquals(active(planning).toSet, Set("p", "k1"))
    val waiting = select(g("p", work = List(w(2, ProposeArticles))) :: kids, s3)
    assertEquals(active(waiting), List("k1"))
    assert(
      waiting.waiting.exists((s, c, why) => s == "p" && c == Code.Children && why.contains("1 unfinished sub-goal")),
      waiting.waiting
    )
    // its sub-goals finished: it compiles its own synthesis
    val synth = select(
      g("p", sources = 4, work = List(w(2, ProposeArticles))) :: kids.map(
        _.modify(_.status).setTo(Goal.Status.Reviewed).modify(_.work).setTo(Nil)
      ),
      s3
    )
    assertEquals(active(synth), List("p"))

  test("enough goal plans already wait for a person: a goal whose next step is one more plan waits"):
    val plan = g("needsplan", work = List(w(1, PlanGoalItem)))
    val s = s3.modify(_.plansByPerson).setTo(true).modify(_.openPersonPlans).setTo(3)
    assertEquals(select(List(plan), s).waiting.map(_._2), List(Code.Plans))
    assertEquals(active(select(List(plan), s.modify(_.openPersonPlans).setTo(2))), List("needsplan"))
    assertEquals(active(select(List(plan.modify(_.active).setTo(true)), s)), List("needsplan"), "an active one keeps its slot")
    assertEquals(active(select(List(plan), s.modify(_.plansByPerson).setTo(false))), List("needsplan"), "plans decided by code")

  test("the cap lowered: the worst active goals go back to the queue first"):
    val goals = List(
      g("r", articles = 1, unreviewed = 1, active = true),
      g("c", compile = 1, active = true),
      g("x", sources = 1, active = true)
    )
    val p = select(goals, s3.modify(_.max).setTo(2))
    assertEquals(active(p), List("r", "c"))
    assertEquals(p.left.map(_._1), List("x"))
    assert(p.left.head._2.contains("cap of 2"), p.left)
    assertEquals(p.queue.map(_.slug), List("x"))

  // ── holds ──────────────────────────────────────────────────────────────────────────────────────

  test("holds: a queued goal's pending model items are held; an active goal's focus holds released; in flight never"):
    val goals = List(g("on", active = true), g("off"))
    val plan = Plan(active = List(Pick(com.histmersion.domain.automation.Goal.Slug("on"), Stage.Gathering, com.histmersion.domain.GoalFocus.Reason(""))))
    val items = List(
      Item(com.histmersion.domain.GoalFocus.Work.ItemId(1), ProposeArticles, com.histmersion.domain.automation.ItemStatus.Wire("pending"), List(com.histmersion.domain.automation.Goal.Slug("off"))),
      Item(com.histmersion.domain.GoalFocus.Work.ItemId(2), AcquisitionSweep, com.histmersion.domain.automation.ItemStatus.Wire("active"), List(com.histmersion.domain.automation.Goal.Slug("off"))),
      Item(com.histmersion.domain.GoalFocus.Work.ItemId(3), ArticleCompile, com.histmersion.domain.automation.ItemStatus.Wire("held"), List(com.histmersion.domain.automation.Goal.Slug("on")), focusHeld = true),
      Item(com.histmersion.domain.GoalFocus.Work.ItemId(4), CurrencyCheck, com.histmersion.domain.automation.ItemStatus.Wire("pending"), List(com.histmersion.domain.automation.Goal.Slug("off"))),
      Item(com.histmersion.domain.GoalFocus.Work.ItemId(5), PlanGoalItem, com.histmersion.domain.automation.ItemStatus.Wire("pending"), List(com.histmersion.domain.automation.Goal.Slug("off")), person = com.histmersion.domain.GoalFocus.Work.Person(true)),
      Item(com.histmersion.domain.GoalFocus.Work.ItemId(6), ArticleReview, com.histmersion.domain.automation.ItemStatus.Wire("pending"), List(com.histmersion.domain.automation.Goal.Slug("off"), com.histmersion.domain.automation.Goal.Slug("on"))),
      Item(com.histmersion.domain.GoalFocus.Work.ItemId(7), ArticleReview, com.histmersion.domain.automation.ItemStatus.Wire("pending"), Nil),
      Item(com.histmersion.domain.GoalFocus.Work.ItemId(8), ArticleReview, com.histmersion.domain.automation.ItemStatus.Wire("held"), List(com.histmersion.domain.automation.Goal.Slug("off")), focusHeld = true),
      Item(com.histmersion.domain.GoalFocus.Work.ItemId(9), ArticleCompile, com.histmersion.domain.automation.ItemStatus.Wire("held"), List(com.histmersion.domain.automation.Goal.Slug("on")))
    )
    val hs = holds(items, plan, goals)
    assertEquals(hs.hold.map(_._1), List(1L))
    assertEquals(hs.hold.head._2, "off")
    assert(hs.hold.head._3.startsWith(HoldPrefix) && hs.hold.head._3.contains("max-active-goals"), hs.hold)
    assertEquals(hs.release, List(3L), "the active goal's; a person's own hold (9) is not the focus's")

  test(
    "holds: a finished goal's items and library work are released; a person's item runs; an active parent holds all but its plan"
  ):
    val goals = List(g("fin", status = Goal.Status.Reviewed), g("p", active = true), g("k", Some("p")))
    val plan = Plan(active = List(Pick(com.histmersion.domain.automation.Goal.Slug("p"), Stage.Gathering, com.histmersion.domain.GoalFocus.Reason(""))))
    val items = List(
      Item(com.histmersion.domain.GoalFocus.Work.ItemId(1), AcquisitionSweep, com.histmersion.domain.automation.ItemStatus.Wire("held"), List(com.histmersion.domain.automation.Goal.Slug("fin")), focusHeld = true),
      Item(com.histmersion.domain.GoalFocus.Work.ItemId(2), ArticleReview, com.histmersion.domain.automation.ItemStatus.Wire("held"), List(com.histmersion.domain.automation.Goal.Slug("k")), person = com.histmersion.domain.GoalFocus.Work.Person(true), focusHeld = com.histmersion.domain.GoalFocus.Work.FocusHeld(true)),
      Item(com.histmersion.domain.GoalFocus.Work.ItemId(3), ProposeArticles, com.histmersion.domain.automation.ItemStatus.Wire("pending"), List(com.histmersion.domain.automation.Goal.Slug("p"))),
      Item(com.histmersion.domain.GoalFocus.Work.ItemId(4), PlanGoalItem, com.histmersion.domain.automation.ItemStatus.Wire("pending"), List(com.histmersion.domain.automation.Goal.Slug("p"))),
      Item(com.histmersion.domain.GoalFocus.Work.ItemId(5), OaIngest, com.histmersion.domain.automation.ItemStatus.Wire("held"), List(com.histmersion.domain.automation.Goal.Slug("k")), focusHeld = true)
    )
    val hs = holds(items, plan, goals)
    assertEquals(hs.release.toSet, Set(1L, 2L, 5L))
    assertEquals(hs.hold.map(h => (h._1, h._2)), List((3L, "p")))
    assert(hs.hold.head._3.contains("waits for its sub-goals"), hs.hold)

  // ── the library-wide managers ──────────────────────────────────────────────────────────────────

  test("fair order: ratio to one, counted on from the last starts; the order inside each class kept"):
    val cs = List("f1", "o1", "f2", "f3", "o2", "f4", "f5")
    val f = (s: String) => s.startsWith("f")
    assertEquals(fairOrder(cs, f, Nil, com.histmersion.domain.GoalFocus.Ratio(3)), List("f1", "f2", "f3", "o1", "f4", "f5", "o2"))
    assertEquals(fairOrder(cs, f, List(com.histmersion.domain.GoalFocus.Recent(true), com.histmersion.domain.GoalFocus.Recent(true), com.histmersion.domain.GoalFocus.Recent(true)), com.histmersion.domain.GoalFocus.Ratio(3)), List("o1", "f1", "f2", "f3", "o2", "f4", "f5"))
    assertEquals(fairOrder(cs, f, List(com.histmersion.domain.GoalFocus.Recent(true), com.histmersion.domain.GoalFocus.Recent(false), com.histmersion.domain.GoalFocus.Recent(true)), com.histmersion.domain.GoalFocus.Ratio(3)), List("f1", "f2", "o1", "f3", "f4", "f5", "o2"))
    assertEquals(fairOrder(cs, f, Nil, com.histmersion.domain.GoalFocus.Ratio(0)), List("f1", "f2", "f3", "f4", "f5", "o1", "o2"), "0: active goals first")
    assertEquals(fairOrder(List("o1", "o2"), f, List(com.histmersion.domain.GoalFocus.Recent(true)), com.histmersion.domain.GoalFocus.Ratio(3)), List("o1", "o2"), "no starvation without focus items")

  // ── the facts and what is shown ────────────────────────────────────────────────────────────────

  test("build: the per-goal counts and every item of the goals it belongs to"):
    val base = List(Facts(com.histmersion.domain.automation.Goal.Slug("a")), Facts(com.histmersion.domain.automation.Goal.Slug("b")))
    val work = List(List("a") -> w(2), List("a", "b") -> w(1, ArticleReview), List("x") -> w(3))
    val fs = build(
      base,
      work,
      Map("a" -> 4),
      Map("a" -> List("art1", "art2")),
      Set(com.histmersion.domain.research.Slug("art1")),
      Map("b" -> 2),
      Map("b" -> "stopped")
    )
    assertEquals(
      fs.map(f => (f.slug, f.sources, f.articles, f.unreviewed, f.compileOpen, f.work.map(_.itemId), f.lastStop)),
      List(("a", 4, 2, 1, 0, List(1L, 2L), None), ("b", 0, 0, 0, 2, List(1L), Some("stopped")))
    )

  test("doing: runs in flight, the approvals waited for and who decides, the next item"):
    val ws = List(
      Work(com.histmersion.domain.GoalFocus.Work.ItemId(1), ArticleCompile, com.histmersion.domain.automation.ItemStatus.Wire("active"), com.histmersion.domain.GoalFocus.Work.Target("x")),
      Work(com.histmersion.domain.GoalFocus.Work.ItemId(2), PlanGoalItem, com.histmersion.domain.automation.ItemStatus.Wire("active"), com.histmersion.domain.GoalFocus.Work.Target("g"), approval = Some(7), approvalByPerson = true),
      Work(com.histmersion.domain.GoalFocus.Work.ItemId(3), ArticleReview, com.histmersion.domain.automation.ItemStatus.Wire("pending"), com.histmersion.domain.GoalFocus.Work.Target("y")),
      Work(com.histmersion.domain.GoalFocus.Work.ItemId(4), ProposeArticles, com.histmersion.domain.automation.ItemStatus.Wire("held"), com.histmersion.domain.GoalFocus.Work.Target("g"), focusHeld = true)
    )
    assertEquals(
      doing(ws),
      Some("running article-compile x; approval #7 (a person decides); next article-review y (+1 more)")
    )
    assertEquals(doing(Nil), None)

  test("a goal plan under the focus: only for an active goal, within the open sub-goals"):
    assert(
      AutopilotPolicy.goalPlanFocus(com.histmersion.domain.automation.Goal.Slug("g"), active = false, com.histmersion.domain.AutopilotPolicy.OpenCount(0), com.histmersion.domain.AutopilotPolicy.Proposed(0), com.histmersion.domain.AutopilotPolicy.Limit(5)).exists(_.contains("not one of the active goals"))
    )
    assertEquals(AutopilotPolicy.goalPlanFocus(com.histmersion.domain.automation.Goal.Slug("g"), active = true, com.histmersion.domain.AutopilotPolicy.OpenCount(2), com.histmersion.domain.AutopilotPolicy.Proposed(3), com.histmersion.domain.AutopilotPolicy.Limit(5)), None)
    assert(
      AutopilotPolicy
        .goalPlanFocus(com.histmersion.domain.automation.Goal.Slug("g"), active = true, com.histmersion.domain.AutopilotPolicy.OpenCount(3), com.histmersion.domain.AutopilotPolicy.Proposed(3), com.histmersion.domain.AutopilotPolicy.Limit(5))
        .exists(_.contains("has 3 unfinished sub-goals and the plan adds 3"))
    )
    assertEquals(
      AutopilotPolicy.goalPlanFocus(com.histmersion.domain.automation.Goal.Slug("g"), active = true, com.histmersion.domain.AutopilotPolicy.OpenCount(9), com.histmersion.domain.AutopilotPolicy.Proposed(0), com.histmersion.domain.AutopilotPolicy.Limit(5)),
      None,
      "queries and works only: no new sub-goal"
    )

  test("goal plans by policy whatever the focus (#3762 proposal 2): only the sub-goals they open are bounded"):
    assertEquals(AutopilotPolicy.goalPlanSubGoals(com.histmersion.domain.automation.Goal.Slug("g"), com.histmersion.domain.AutopilotPolicy.OpenCount(0), com.histmersion.domain.AutopilotPolicy.Proposed(0), com.histmersion.domain.AutopilotPolicy.Limit(5)), None)
    assertEquals(AutopilotPolicy.goalPlanSubGoals(com.histmersion.domain.automation.Goal.Slug("g"), com.histmersion.domain.AutopilotPolicy.OpenCount(2), com.histmersion.domain.AutopilotPolicy.Proposed(3), com.histmersion.domain.AutopilotPolicy.Limit(5)), None)
    assert(
      AutopilotPolicy
        .goalPlanSubGoals(com.histmersion.domain.automation.Goal.Slug("g"), com.histmersion.domain.AutopilotPolicy.OpenCount(3), com.histmersion.domain.AutopilotPolicy.Proposed(3), com.histmersion.domain.AutopilotPolicy.Limit(5))
        .exists(_.contains("has 3 unfinished sub-goals and the plan adds 3"))
    )
    assertEquals(AutopilotPolicy.goalPlanSubGoals(com.histmersion.domain.automation.Goal.Slug("g"), com.histmersion.domain.AutopilotPolicy.OpenCount(9), com.histmersion.domain.AutopilotPolicy.Proposed(0), com.histmersion.domain.AutopilotPolicy.Limit(5)), None, "queries and works only")

  test("usesModel: only the currency check and the ingest backlog run without a model"):
    assertEquals(
      List(
        PlanGoalItem,
        ProposeArticles,
        ArticleCompile,
        AcquisitionSweep,
        CurrencyCheck,
        OaIngest,
        ArticleReview,
        InferFilter,
        ArticleExtend,
        SourceEnrich
      ).filterNot(usesModel),
      List(CurrencyCheck, OaIngest)
    )

  test("Markdown: the active goals with why, now and pace; the queue; who cannot use a slot"):
    val f = FocusInfo(
      on = com.histmersion.api.automation.Autopilot.FocusInfo.On(true),
      max = com.histmersion.api.automation.Autopilot.FocusInfo.Max(2),
      active = List(
        FocusGoal(
          com.histmersion.api.automation.Autopilot.FocusGoal.Slug("a"),
          status = com.histmersion.api.automation.Autopilot.FocusGoal.Status("seeding"),
          stage = com.histmersion.api.automation.Autopilot.FocusGoal.Stage("reviewing"),
          why = com.histmersion.api.automation.Autopilot.FocusGoal.Why("reviewing: 1 of 2"),
          doing = Some("running article-review x"),
          open = 3,
          doneLast7 = 7,
          etaDays = Some(3)
        ),
        FocusGoal(com.histmersion.api.automation.Autopilot.FocusGoal.Slug("b"), status = com.histmersion.api.automation.Autopilot.FocusGoal.Status("seeding"), stage = com.histmersion.api.automation.Autopilot.FocusGoal.Stage("gathering"), why = com.histmersion.api.automation.Autopilot.FocusGoal.Why("pinned by a person"), pinned = com.histmersion.api.automation.Autopilot.FocusGoal.Pinned(true))
      ),
      queue = List(FocusGoal(com.histmersion.api.automation.Autopilot.FocusGoal.Slug("c"), stage = com.histmersion.api.automation.Autopilot.FocusGoal.Stage("ready"), why = com.histmersion.api.automation.Autopilot.FocusGoal.Why("ready: 5 sources"))),
      queued = com.histmersion.api.automation.Autopilot.FocusInfo.Queued(4),
      waiting = List(FocusWaiting(com.histmersion.api.automation.Autopilot.FocusWaiting.Slug("d"), com.histmersion.api.automation.Autopilot.FocusWaiting.Code("person"), com.histmersion.api.automation.Autopilot.FocusWaiting.Why("waits for a person: approval #1")), FocusWaiting(com.histmersion.api.automation.Autopilot.FocusWaiting.Slug("e"), com.histmersion.api.automation.Autopilot.FocusWaiting.Code("person"), com.histmersion.api.automation.Autopilot.FocusWaiting.Why("x"))),
      held = com.histmersion.api.automation.Autopilot.FocusInfo.Held(12)
    )
    val md = AutopilotMarkdown.focus(f)
    assert(md.contains("## Focus: at most 2 active goals (agents.autopilot.max-active-goals)"), md)
    assert(md.contains("12 model-using items held for the focus"), md)
    assert(
      md.contains(
        "1. **a** _(seeding, reviewing)_ — reviewing: 1 of 2\n   - now: running article-review x\n   - 7 done in 7 days, 3 open, ~3 day(s)"
      ),
      md
    )
    assert(md.contains("2. **b** _(seeding, gathering)_ (pinned)"), md)
    assert(
      md.contains("### Queue (4)") && md.contains("1. c _(ready)_ — ready: 5 sources") && md.contains("…and 3 more"),
      md
    )
    assert(md.contains("- 2 goals person: d, e — e.g. waits for a person: approval #1"), md)
    assert(
      AutopilotMarkdown
        .focus(f.modify(_.on).setTo(false).modify(_.maxFrom).setTo("person").modify(_.note).setTo(Some("the autopilot is off")))
        .contains("not in force (set by a person)")
    )

  test("the autopilot status says plainly when goal plans wait for the unfinished goals"):
    val why =
      "goal plans wait until the unfinished goals drop to 10: 60 goals with an approved plan are not reviewed yet"
    val st =
      Autopilot.EngineStatus(on = com.histmersion.api.automation.Autopilot.EngineStatus.On(true), agentsEnabled = com.histmersion.api.automation.Autopilot.EngineStatus.AgentsEnabled(true), managersEnabled = com.histmersion.api.automation.Autopilot.EngineStatus.ManagersEnabled(true), goalPlansWait = Some(why))
    assert(
      AutopilotMarkdown.status(st).contains(s"- **$why; a person may still approve a plan**"),
      AutopilotMarkdown.status(st)
    )
    assert(!AutopilotMarkdown.status(st.modify(_.goalPlansWait).setTo(None)).contains("goal plans wait"))
