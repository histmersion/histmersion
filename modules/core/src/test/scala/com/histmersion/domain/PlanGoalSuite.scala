package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.automation.Goal.Plans.{PlannedGoal, PlannedWork}
import com.histmersion.domain.PlanGoal.*

/** plan-goal's pure checks: what a retry can fix is sent back, what it cannot is dropped with a note, DOIs are looked
  * up before a person sees them, and the acquisition item of a work.
  */
class PlanGoalSuite extends munit.FunSuite:

  private val caps = Caps(subGoals = com.histmersion.domain.PlanGoal.Caps.SubGoals(2), queries = com.histmersion.domain.PlanGoal.Caps.Queries(2), works = com.histmersion.domain.PlanGoal.Caps.Works(2), queryChars = com.histmersion.domain.PlanGoal.Caps.QueryChars(40))
  private val taken = Set(com.histmersion.domain.automation.Goal.Slug("bronze-age"), com.histmersion.domain.automation.Goal.Slug("already-there"))

  private def answer(goals: List[SubGoalAnswer] = Nil, queries: List[com.histmersion.domain.PlanGoal.PlanAnswer.Queries] = Nil, works: List[WorkAnswer] = Nil) =
    PlanAnswer(com.histmersion.domain.PlanGoal.PlanAnswer.Rationale("covers it"), goals, queries, works)

  test("a clean plan is kept, DOIs normalised, duplicates folded"):
    val a = answer(
      List(SubGoalAnswer(com.histmersion.domain.PlanGoal.SubGoalAnswer.Slug("tin-trade"), com.histmersion.domain.PlanGoal.SubGoalAnswer.Title("Tin trade"), Some(com.histmersion.domain.PlanGoal.SubGoalAnswer.Notes("where tin came from")), List(com.histmersion.domain.PlanGoal.SubGoalAnswer.Tags(" tin "), com.histmersion.domain.PlanGoal.SubGoalAnswer.Tags("tin")))),
      List(com.histmersion.domain.PlanGoal.PlanAnswer.Queries("Bronze Age tin"), com.histmersion.domain.PlanGoal.PlanAnswer.Queries("bronze age tin")),
      List(WorkAnswer(com.histmersion.domain.PlanGoal.WorkAnswer.Doi("https://doi.org/10.1000/ABC"), com.histmersion.domain.PlanGoal.WorkAnswer.Why("the isotopes")), WorkAnswer(com.histmersion.domain.PlanGoal.WorkAnswer.Doi("doi:10.1000/abc"), com.histmersion.domain.PlanGoal.WorkAnswer.Why("again")))
    )
    val (p, warns) = check(a, com.histmersion.domain.automation.Goal.Slug("bronze-age"), taken, Set.empty, caps, last = com.histmersion.domain.PlanGoal.LastRound(false)).toOption.get
    assertEquals(p.subGoals, List(PlannedGoal(com.histmersion.api.automation.Goal.Slug("tin-trade"), com.histmersion.api.automation.Goal.Title("Tin trade"), Some(com.histmersion.api.automation.Goal.Notes("where tin came from")), List(com.histmersion.api.automation.Goal.Tags("tin")))))
    assertEquals(p.queries, List("Bronze Age tin"))
    assertEquals(p.works.map(_.doi), List("10.1000/abc"))
    assertEquals(warns, Nil)

  test("what a retry can fix is sent back: a bad slug or DOI, a missing title, a cap, an empty plan"):
    val bad = answer(
      List(SubGoalAnswer(com.histmersion.domain.PlanGoal.SubGoalAnswer.Slug("Not A Slug"), com.histmersion.domain.PlanGoal.SubGoalAnswer.Title("x")), SubGoalAnswer(com.histmersion.domain.PlanGoal.SubGoalAnswer.Slug("ok-slug"), com.histmersion.domain.PlanGoal.SubGoalAnswer.Title(" "))),
      List(com.histmersion.domain.PlanGoal.PlanAnswer.Queries("x" * 41)),
      List(WorkAnswer(com.histmersion.domain.PlanGoal.WorkAnswer.Doi("not-a-doi"), com.histmersion.domain.PlanGoal.WorkAnswer.Why("?")))
    )
    val ps = check(bad, com.histmersion.domain.automation.Goal.Slug("bronze-age"), taken, Set.empty, caps, last = com.histmersion.domain.PlanGoal.LastRound(false)).left.toOption.get
    assert(ps.exists(_.value.contains("invalid slug")), ps)
    assert(ps.exists(_.value.contains("has no title")), ps)
    assert(ps.exists(_.value.contains("longer than 40")), ps)
    assert(ps.exists(_.value.contains("is not a DOI")), ps)
    val tooMany = answer(queries = List(com.histmersion.domain.PlanGoal.PlanAnswer.Queries("a"), com.histmersion.domain.PlanGoal.PlanAnswer.Queries("b"), com.histmersion.domain.PlanGoal.PlanAnswer.Queries("c")))
    assert(
      check(tooMany, com.histmersion.domain.automation.Goal.Slug("g"), taken, Set.empty, caps, last = com.histmersion.domain.PlanGoal.LastRound(false)).left.exists(_.exists(_.value.contains("at most 2 queries")))
    )
    assert(
      check(answer(), com.histmersion.domain.automation.Goal.Slug("g"), taken, Set.empty, caps, last = com.histmersion.domain.PlanGoal.LastRound(false)).left.exists(_.exists(_.value.contains("proposes nothing")))
    )

  test("what a retry cannot change is dropped with a note: the goal itself, an existing goal, a known query"):
    val a = answer(
      List(
        SubGoalAnswer(com.histmersion.domain.PlanGoal.SubGoalAnswer.Slug("bronze-age"), com.histmersion.domain.PlanGoal.SubGoalAnswer.Title("itself")),
        SubGoalAnswer(com.histmersion.domain.PlanGoal.SubGoalAnswer.Slug("already-there"), com.histmersion.domain.PlanGoal.SubGoalAnswer.Title("taken")),
        SubGoalAnswer(com.histmersion.domain.PlanGoal.SubGoalAnswer.Slug("new-one"), com.histmersion.domain.PlanGoal.SubGoalAnswer.Title("New"))
      ),
      List(com.histmersion.domain.PlanGoal.PlanAnswer.Queries("Known query"), com.histmersion.domain.PlanGoal.PlanAnswer.Queries("fresh"))
    )
    val (p, _) = check(a, com.histmersion.domain.automation.Goal.Slug("bronze-age"), taken, Set(com.histmersion.domain.PlanGoal.Query("known query")), caps, last = com.histmersion.domain.PlanGoal.LastRound(false)).toOption.get
    assertEquals(p.subGoals.map(_.slug), List("new-one"))
    assertEquals(p.queries, List("fresh"))
    assertEquals(p.dropped.size, 3, p.dropped)

  test("on the last attempt what passed is kept, the problems become notes and warnings"):
    val a =
      answer(List(SubGoalAnswer(com.histmersion.domain.PlanGoal.SubGoalAnswer.Slug("BAD SLUG"), com.histmersion.domain.PlanGoal.SubGoalAnswer.Title("x")), SubGoalAnswer(com.histmersion.domain.PlanGoal.SubGoalAnswer.Slug("good"), com.histmersion.domain.PlanGoal.SubGoalAnswer.Title("Good"))), works = List(WorkAnswer(com.histmersion.domain.PlanGoal.WorkAnswer.Doi("nope"), com.histmersion.domain.PlanGoal.WorkAnswer.Why(""))))
    val (p, warns) = check(a, com.histmersion.domain.automation.Goal.Slug("g"), taken, Set.empty, caps, last = com.histmersion.domain.PlanGoal.LastRound(true)).toOption.get
    assertEquals(p.subGoals.map(_.slug), List("good"))
    assertEquals(p.works, Nil)
    assert(warns.exists(_.value.contains("not a DOI")) && p.dropped.exists(_.value.contains("invalid slug")), (warns, p.dropped))

  test("verify: unresolvable and retracted DOIs are dropped; a work in the corpus keeps its citekey"):
    val p = Proposed(
      com.histmersion.domain.PlanGoal.Proposed.Rationale("why"),
      Nil,
      Nil,
      List(ProposedWork(com.histmersion.domain.library.Doi("10.1000/a"), com.histmersion.domain.PlanGoal.ProposedWork.Why("a")), ProposedWork(com.histmersion.domain.library.Doi("10.1000/b"), com.histmersion.domain.PlanGoal.ProposedWork.Why("b")), ProposedWork(com.histmersion.domain.library.Doi("10.1000/c"), com.histmersion.domain.PlanGoal.ProposedWork.Why("c"))),
      Nil
    )
    val lookup: Lookup = doi => doi.value match {
      case "10.1000/a" =>
        Right(Work(Some(com.histmersion.domain.PlanGoal.Work.Title("A")), Some(com.histmersion.Year(2020)), Nil, Some(com.histmersion.domain.library.Citekey("a2020_x")), inDb = com.histmersion.domain.PlanGoal.Work.InDb(true), isOa = com.histmersion.domain.PlanGoal.Work.IsOa(false), isRetracted = com.histmersion.domain.PlanGoal.Work.IsRetracted(false)))
      case "10.1000/b" => Right(Work(Some(com.histmersion.domain.PlanGoal.Work.Title("B")), None, Nil, None, inDb = com.histmersion.domain.PlanGoal.Work.InDb(false), isOa = com.histmersion.domain.PlanGoal.Work.IsOa(false), isRetracted = com.histmersion.domain.PlanGoal.Work.IsRetracted(true)))
      case _           => Left(com.histmersion.domain.PlanGoal.Problem("404 from Crossref"))
    }
    val plan = verify(p, com.histmersion.domain.automation.Goal.Slug("g"), lookup)
    assertEquals(
      plan.works,
      List(PlannedWork(com.histmersion.api.library.Doi("10.1000/a"), Some(com.histmersion.api.library.Title("A")), Some(com.histmersion.api.catalogue.Year(2020)), Nil, Some(com.histmersion.api.library.Citekey("a2020_x")), inCorpus = com.histmersion.api.automation.Goal.Plans.PlannedWork.InCorpus(true), why = com.histmersion.api.automation.Goal.Plans.PlannedWork.Why("a")))
    )
    assertEquals(plan.dropped.size, 2)
    assert(plan.dropped.exists(_.value.contains("retracted")) && plan.dropped.exists(_.value.contains("404")))

  test("the acquisition item carries the DOI as a resolver URL (so the queue can be deduplicated)"):
    val w =
      PlannedWork(com.histmersion.api.library.Doi("10.1000/xyz"), Some(com.histmersion.api.library.Title("Tin isotopes")), Some(com.histmersion.api.catalogue.Year(2024)), List(com.histmersion.api.library.AuthorName("Berger"), com.histmersion.api.library.AuthorName("Lockhoff")), why = com.histmersion.api.automation.Goal.Plans.PlannedWork.Why("dates the tin"))
    val t = acquireText(w, com.histmersion.domain.automation.Goal.Slug("bronze-age"))
    assertEquals(
      t,
      "Berger et al. (2024). Tin isotopes. https://doi.org/10.1000/xyz — for goal bronze-age: dates the tin"
    )
    assertEquals(Scheduling.dois(t.value), Set("10.1000/xyz"))
