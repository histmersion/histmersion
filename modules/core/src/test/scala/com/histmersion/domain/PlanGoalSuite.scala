package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.automation.Goal.Plans.{PlannedGoal, PlannedWork}
import com.histmersion.domain.PlanGoal.*
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.library.Doi
import com.histmersion.domain.library.Citekey

/** plan-goal's pure checks: what a retry can fix is sent back, what it cannot is dropped with a note, DOIs are looked
  * up before a person sees them, and the acquisition item of a work.
  */
class PlanGoalSuite extends munit.FunSuite:

  private val caps = Caps(
    subGoals = Caps.SubGoals(2),
    queries = Caps.Queries(2),
    works = Caps.Works(2),
    queryChars = Caps.QueryChars(40)
  )
  private val taken = Set(Goal.Slug("bronze-age"), Goal.Slug("already-there"))

  private def answer(
      goals: List[SubGoalAnswer] = Nil,
      queries: List[PlanAnswer.Queries] = Nil,
      works: List[WorkAnswer] = Nil
  ) =
    PlanAnswer(PlanAnswer.Rationale("covers it"), goals, queries, works)

  test("a clean plan is kept, DOIs normalised, duplicates folded"):
    val a = answer(
      List(
        SubGoalAnswer(
          SubGoalAnswer.Slug("tin-trade"),
          SubGoalAnswer.Title("Tin trade"),
          Some(SubGoalAnswer.Notes("where tin came from")),
          List(SubGoalAnswer.Tags(" tin "), SubGoalAnswer.Tags("tin"))
        )
      ),
      List(PlanAnswer.Queries("Bronze Age tin"), PlanAnswer.Queries("bronze age tin")),
      List(
        WorkAnswer(WorkAnswer.Doi("https://doi.org/10.1000/ABC"), WorkAnswer.Why("the isotopes")),
        WorkAnswer(WorkAnswer.Doi("doi:10.1000/abc"), WorkAnswer.Why("again"))
      )
    )
    val (p, warns) = check(a, Goal.Slug("bronze-age"), taken, Set.empty, caps, last = LastRound(false)).toOption.get
    assertEquals(
      p.subGoals,
      List(
        PlannedGoal(
          com.histmersion.api.automation.Goal.Slug("tin-trade"),
          com.histmersion.api.automation.Goal.Title("Tin trade"),
          Some(com.histmersion.api.automation.Goal.Notes("where tin came from")),
          List(com.histmersion.api.automation.Goal.Tags("tin"))
        )
      )
    )
    assertEquals(p.queries, List("Bronze Age tin"))
    assertEquals(p.works.map(_.doi), List("10.1000/abc"))
    assertEquals(warns, Nil)

  test("what a retry can fix is sent back: a bad slug or DOI, a missing title, a cap, an empty plan"):
    val bad = answer(
      List(
        SubGoalAnswer(SubGoalAnswer.Slug("Not A Slug"), SubGoalAnswer.Title("x")),
        SubGoalAnswer(SubGoalAnswer.Slug("ok-slug"), SubGoalAnswer.Title(" "))
      ),
      List(PlanAnswer.Queries("x" * 41)),
      List(WorkAnswer(WorkAnswer.Doi("not-a-doi"), WorkAnswer.Why("?")))
    )
    val ps = check(bad, Goal.Slug("bronze-age"), taken, Set.empty, caps, last = LastRound(false)).left.toOption.get
    assert(ps.exists(_.value.contains("invalid slug")), ps)
    assert(ps.exists(_.value.contains("has no title")), ps)
    assert(ps.exists(_.value.contains("longer than 40")), ps)
    assert(ps.exists(_.value.contains("is not a DOI")), ps)
    val tooMany = answer(queries = List(PlanAnswer.Queries("a"), PlanAnswer.Queries("b"), PlanAnswer.Queries("c")))
    assert(
      check(tooMany, Goal.Slug("g"), taken, Set.empty, caps, last = LastRound(false)).left
        .exists(_.exists(_.value.contains("at most 2 queries")))
    )
    assert(
      check(answer(), Goal.Slug("g"), taken, Set.empty, caps, last = LastRound(false)).left
        .exists(_.exists(_.value.contains("proposes nothing")))
    )

  test("what a retry cannot change is dropped with a note: the goal itself, an existing goal, a known query"):
    val a = answer(
      List(
        SubGoalAnswer(SubGoalAnswer.Slug("bronze-age"), SubGoalAnswer.Title("itself")),
        SubGoalAnswer(SubGoalAnswer.Slug("already-there"), SubGoalAnswer.Title("taken")),
        SubGoalAnswer(SubGoalAnswer.Slug("new-one"), SubGoalAnswer.Title("New"))
      ),
      List(PlanAnswer.Queries("Known query"), PlanAnswer.Queries("fresh"))
    )
    val (p, _) =
      check(a, Goal.Slug("bronze-age"), taken, Set(Query("known query")), caps, last = LastRound(false)).toOption.get
    assertEquals(p.subGoals.map(_.slug), List("new-one"))
    assertEquals(p.queries, List("fresh"))
    assertEquals(p.dropped.size, 3, p.dropped)

  test("on the last attempt what passed is kept, the problems become notes and warnings"):
    val a =
      answer(
        List(
          SubGoalAnswer(SubGoalAnswer.Slug("BAD SLUG"), SubGoalAnswer.Title("x")),
          SubGoalAnswer(SubGoalAnswer.Slug("good"), SubGoalAnswer.Title("Good"))
        ),
        works = List(WorkAnswer(WorkAnswer.Doi("nope"), WorkAnswer.Why("")))
      )
    val (p, warns) = check(a, Goal.Slug("g"), taken, Set.empty, caps, last = LastRound(true)).toOption.get
    assertEquals(p.subGoals.map(_.slug), List("good"))
    assertEquals(p.works, Nil)
    assert(
      warns.exists(_.value.contains("not a DOI")) && p.dropped.exists(_.value.contains("invalid slug")),
      (warns, p.dropped)
    )

  test("verify: unresolvable and retracted DOIs are dropped; a work in the corpus keeps its citekey"):
    val p = Proposed(
      Proposed.Rationale("why"),
      Nil,
      Nil,
      List(
        ProposedWork(Doi("10.1000/a"), ProposedWork.Why("a")),
        ProposedWork(Doi("10.1000/b"), ProposedWork.Why("b")),
        ProposedWork(Doi("10.1000/c"), ProposedWork.Why("c"))
      ),
      Nil
    )
    val lookup: Lookup = doi =>
      doi.value match {
        case "10.1000/a" =>
          Right(
            Work(
              Some(Work.Title("A")),
              Some(Year(2020)),
              Nil,
              Some(Citekey("a2020_x")),
              inDb = Work.InDb(true),
              isOa = Work.IsOa(false),
              isRetracted = Work.IsRetracted(false)
            )
          )
        case "10.1000/b" =>
          Right(
            Work(
              Some(Work.Title("B")),
              None,
              Nil,
              None,
              inDb = Work.InDb(false),
              isOa = Work.IsOa(false),
              isRetracted = Work.IsRetracted(true)
            )
          )
        case _ => Left(Problem("404 from Crossref"))
      }
    val plan = verify(p, Goal.Slug("g"), lookup)
    assertEquals(
      plan.works,
      List(
        PlannedWork(
          com.histmersion.api.library.Doi("10.1000/a"),
          Some(com.histmersion.api.library.Title("A")),
          Some(com.histmersion.api.catalogue.Year(2020)),
          Nil,
          Some(com.histmersion.api.library.Citekey("a2020_x")),
          inCorpus = PlannedWork.InCorpus(true),
          why = Problem("a")
        )
      )
    )
    assertEquals(plan.dropped.size, 2)
    assert(plan.dropped.exists(_.value.contains("retracted")) && plan.dropped.exists(_.value.contains("404")))

  test("the acquisition item carries the DOI as a resolver URL (so the queue can be deduplicated)"):
    val w =
      PlannedWork(
        com.histmersion.api.library.Doi("10.1000/xyz"),
        Some(com.histmersion.api.library.Title("Tin isotopes")),
        Some(com.histmersion.api.catalogue.Year(2024)),
        List(com.histmersion.api.library.AuthorName("Berger"), com.histmersion.api.library.AuthorName("Lockhoff")),
        why = Problem("dates the tin")
      )
    val t = acquireText(w, Goal.Slug("bronze-age"))
    assertEquals(
      t,
      "Berger et al. (2024). Tin isotopes. https://doi.org/10.1000/xyz — for goal bronze-age: dates the tin"
    )
    assertEquals(Scheduling.dois(t.value), Set("10.1000/xyz"))
