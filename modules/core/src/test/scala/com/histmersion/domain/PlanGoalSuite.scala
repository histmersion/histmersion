package com.histmersion
package domain

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.automation.Goal.Plans.{PlannedGoal, PlannedWork}
import com.histmersion.domain.PlanGoal.*
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.library.Doi
import com.histmersion.domain.library.Citekey
import com.histmersion.api.automation.Goal.Rationale
import com.histmersion.lint.{LintRule, tolerated}

/** plan-goal's pure checks: what a retry can fix is sent back, what it cannot is dropped with a note, DOIs are looked
  * up before a person sees them, and the acquisition item of a work.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
@tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
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
        WorkAnswer(WorkAnswer.Doi("https://doi.org/10.1000/ABC"), Rationale("the isotopes")),
        WorkAnswer(WorkAnswer.Doi("doi:10.1000/abc"), Rationale("again"))
      )
    )
    val (p, warns) = check(a, Goal.Slug("bronze-age"), taken, Set.empty, caps, last = LastRound(false)).toOption.get
    assertEquals(
      p.subGoals,
      List(
        PlannedGoal(
          api.automation.Goal.Slug("tin-trade"),
          api.automation.Goal.Title("Tin trade"),
          Some(api.automation.Goal.Notes("where tin came from")),
          List(api.automation.Goal.Tags("tin"))
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
      List(WorkAnswer(WorkAnswer.Doi("not-a-doi"), Rationale("?")))
    )
    val ps = check(bad, Goal.Slug("bronze-age"), taken, Set.empty, caps, last = LastRound(false)).left.toOption.get
    assert(ps.exists(_.sentence.contains("is not a slug")), ps)
    assert(ps.exists(_.sentence.contains("has no title")), ps)
    assert(ps.exists(_.sentence.contains("longer than 40")), ps)
    assert(ps.exists(_.sentence.contains("is not a DOI")), ps)
    val tooMany = answer(queries = List(PlanAnswer.Queries("a"), PlanAnswer.Queries("b"), PlanAnswer.Queries("c")))
    assert(
      check(tooMany, Goal.Slug("g"), taken, Set.empty, caps, last = LastRound(false)).left
        .exists(_.exists(_.sentence.contains("at most 2 queries")))
    )
    assert(
      check(answer(), Goal.Slug("g"), taken, Set.empty, caps, last = LastRound(false)).left
        .exists(_.exists(_.sentence.contains("proposes nothing")))
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
        works = List(WorkAnswer(WorkAnswer.Doi("nope"), Rationale("")))
      )
    val (p, warns) = check(a, Goal.Slug("g"), taken, Set.empty, caps, last = LastRound(true)).toOption.get
    assertEquals(p.subGoals.map(_.slug), List("good"))
    assertEquals(p.works, Nil)
    assert(
      warns.exists(_.sentence.contains("not a DOI")) && p.dropped.exists(_.value.contains("is not a slug")),
      (warns, p.dropped)
    )

  test("verify: unresolvable and retracted DOIs are dropped; a work in the corpus keeps its citekey"):
    val p = Proposed(
      Proposed.Rationale("why"),
      Nil,
      Nil,
      List(
        ProposedWork(Doi("10.1000/a"), Rationale("a")),
        ProposedWork(Doi("10.1000/b"), Rationale("b")),
        ProposedWork(Doi("10.1000/c"), Rationale("c"))
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
        case _ => Left(Problem.Unclassified(Legacy.Text("404 from Crossref")))
      }
    val plan = verify(p, Goal.Slug("g"), lookup)
    assertEquals(
      plan.works,
      List(
        PlannedWork(
          api.library.Doi("10.1000/a"),
          Some(api.library.Title("A")),
          Some(api.catalogue.Year(2020)),
          Nil,
          Some(api.library.Citekey("a2020_x")),
          inCorpus = PlannedWork.InCorpus(true),
          why = Rationale("a")
        )
      )
    )
    assertEquals(plan.dropped.size, 2)
    assert(plan.dropped.exists(_.value.contains("retracted")) && plan.dropped.exists(_.value.contains("404")))

  test("the acquisition item carries the DOI as a resolver URL (so the queue can be deduplicated)"):
    val w =
      PlannedWork(
        api.library.Doi("10.1000/xyz"),
        Some(api.library.Title("Tin isotopes")),
        Some(api.catalogue.Year(2024)),
        List(api.library.AuthorName("Berger"), api.library.AuthorName("Lockhoff")),
        why = Rationale("dates the tin")
      )
    val t = acquireText(w, Goal.Slug("bronze-age"))
    assertEquals(
      t,
      "Berger et al. (2024). Tin isotopes. https://doi.org/10.1000/xyz — for goal bronze-age: dates the tin"
    )
    assertEquals(Scheduling.dois(t.value), Set("10.1000/xyz"))
