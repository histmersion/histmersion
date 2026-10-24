package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.GoalPlans.{PlannedGoal, PlannedWork}
import com.histmersion.domain.PlanGoal.*

/** plan-goal's pure checks: what a retry can fix is sent back, what it cannot is dropped with a note,
  * DOIs are looked up before a person sees them, and the acquisition item of a work. */
class PlanGoalSuite extends munit.FunSuite:

  private val caps = Caps(subGoals = 2, queries = 2, works = 2, queryChars = 40)
  private val taken = Set("bronze-age", "already-there")

  private def answer(goals: List[SubGoalAnswer] = Nil, queries: List[String] = Nil, works: List[WorkAnswer] = Nil) =
    PlanAnswer("covers it", goals, queries, works)

  test("a clean plan is kept, DOIs normalised, duplicates folded"):
    val a = answer(
      List(SubGoalAnswer("tin-trade", "Tin trade", Some("where tin came from"), List(" tin ", "tin"))),
      List("Bronze Age tin", "bronze age tin"),
      List(WorkAnswer("https://doi.org/10.1000/ABC", "the isotopes"), WorkAnswer("doi:10.1000/abc", "again")))
    val (p, warns) = check(a, "bronze-age", taken, Set.empty, caps, last = false).toOption.get
    assertEquals(p.subGoals, List(PlannedGoal("tin-trade", "Tin trade", Some("where tin came from"), List("tin"))))
    assertEquals(p.queries, List("Bronze Age tin"))
    assertEquals(p.works.map(_.doi), List("10.1000/abc"))
    assertEquals(warns, Nil)

  test("what a retry can fix is sent back: a bad slug or DOI, a missing title, a cap, an empty plan"):
    val bad = answer(
      List(SubGoalAnswer("Not A Slug", "x"), SubGoalAnswer("ok-slug", " ")),
      List("x" * 41),
      List(WorkAnswer("not-a-doi", "?")))
    val ps = check(bad, "bronze-age", taken, Set.empty, caps, last = false).left.toOption.get
    assert(ps.exists(_.contains("invalid slug")), ps)
    assert(ps.exists(_.contains("has no title")), ps)
    assert(ps.exists(_.contains("longer than 40")), ps)
    assert(ps.exists(_.contains("is not a DOI")), ps)
    val tooMany = answer(queries = List("a", "b", "c"))
    assert(check(tooMany, "g", taken, Set.empty, caps, last = false).left.exists(_.exists(_.contains("at most 2 queries"))))
    assert(check(answer(), "g", taken, Set.empty, caps, last = false).left.exists(_.exists(_.contains("proposes nothing"))))

  test("what a retry cannot change is dropped with a note: the goal itself, an existing goal, a known query"):
    val a = answer(
      List(SubGoalAnswer("bronze-age", "itself"), SubGoalAnswer("already-there", "taken"), SubGoalAnswer("new-one", "New")),
      List("Known query", "fresh"))
    val (p, _) = check(a, "bronze-age", taken, Set("known query"), caps, last = false).toOption.get
    assertEquals(p.subGoals.map(_.slug), List("new-one"))
    assertEquals(p.queries, List("fresh"))
    assertEquals(p.dropped.size, 3, p.dropped)

  test("on the last attempt what passed is kept, the problems become notes and warnings"):
    val a = answer(List(SubGoalAnswer("BAD SLUG", "x"), SubGoalAnswer("good", "Good")), works = List(WorkAnswer("nope", "")))
    val (p, warns) = check(a, "g", taken, Set.empty, caps, last = true).toOption.get
    assertEquals(p.subGoals.map(_.slug), List("good"))
    assertEquals(p.works, Nil)
    assert(warns.exists(_.contains("not a DOI")) && p.dropped.exists(_.contains("invalid slug")), (warns, p.dropped))

  test("verify: unresolvable and retracted DOIs are dropped; a work in the corpus keeps its citekey"):
    val p = Proposed("why", Nil, Nil, List(ProposedWork("10.1000/a", "a"), ProposedWork("10.1000/b", "b"), ProposedWork("10.1000/c", "c")), Nil)
    val lookup: String => Either[String, WorkRef] = {
      case "10.1000/a" => Right(WorkRef(doi = Some("10.1000/a"), title = Some("A"), year = Some(2020), citekey = Some("a2020_x"), inDb = true))
      case "10.1000/b" => Right(WorkRef(doi = Some("10.1000/b"), title = Some("B"), isRetracted = true))
      case _        => Left("404 from Crossref")
    }
    val plan = verify(p, "g", lookup)
    assertEquals(plan.works, List(PlannedWork("10.1000/a", Some("A"), Some(2020), Nil, Some("a2020_x"), inCorpus = true, why = "a")))
    assertEquals(plan.dropped.size, 2)
    assert(plan.dropped.exists(_.contains("retracted")) && plan.dropped.exists(_.contains("404")))

  test("the acquisition item carries the DOI as a resolver URL (so the queue can be deduplicated)"):
    val w = PlannedWork("10.1000/xyz", Some("Tin isotopes"), Some(2024), List("Berger", "Lockhoff"), why = "dates the tin")
    val t = acquireText(w, "bronze-age")
    assertEquals(t, "Berger et al. (2024). Tin isotopes. https://doi.org/10.1000/xyz — for goal bronze-age: dates the tin")
    assertEquals(Scheduling.dois(t), Set("10.1000/xyz"))
