package com.histmersion.api

import com.histmersion.api.automation.ListFilters.*

import java.time.Instant

/** The list filters' pure parts: the multi-valued parameters, the time bounds, who decided an approval, the facet
  * header and its round trip, and what a heading or a next-page line says.
  */
class ListFiltersSuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-23T12:00:00Z")

  test("a list parameter is repeated or comma-separated, trimmed, each value once"):
    assertEquals(
      values(List(com.histmersion.api.automation.ListFilters.Term("goal-plan, edge"), com.histmersion.api.automation.ListFilters.Term(" edge"), com.histmersion.api.automation.ListFilters.Term(""), com.histmersion.api.automation.ListFilters.Term("article-publish"))),
      List("goal-plan", "edge", "article-publish")
    )
    assertEquals(values(Nil), Nil)
    assertEquals(one(Some(com.histmersion.api.automation.ListFilters.Term("  "))), None)
    assertEquals(one(Some(com.histmersion.api.automation.ListFilters.Term(" tin "))), Some("tin"))

  test("normalized filters lower-case the closed vocabularies, keep a manager or a model as typed"):
    val f = RunFilter(
      status = List(com.histmersion.api.automation.ListFilters.RunFilter.Status("Failed,DONE")),
      pipeline = List(com.histmersion.api.automation.ListFilters.RunFilter.Pipeline("Review-Article")),
      manager = List(com.histmersion.api.automation.ListFilters.RunFilter.Manager("seed:Tin")),
      model = List(com.histmersion.api.automation.ListFilters.RunFilter.Model("codex:GPT-6")),
      subject = Some("  ")
    )
    val n = f.normalized
    assertEquals(
      (n.status, n.pipeline, n.manager, n.model, n.subject),
      (List("failed", "done"), List("review-article"), List("seed:Tin"), List("codex:GPT-6"), None)
    )
    assertEquals(
      ApprovalFilter(state = Some(com.histmersion.api.automation.Approval.State.Wire(" ALL ")), kind = List(com.histmersion.api.automation.Approval.Kind.Wire("Edge"))).normalized,
      ApprovalFilter(Some(com.histmersion.api.automation.Approval.State.Wire("all")), List(com.histmersion.api.automation.Approval.Kind.Wire("edge")))
    )

  test("a time bound: a day (until = the day included), an instant, or a time ago; anything else is refused"):
    assertEquals(timeBound(com.histmersion.api.automation.ListFilters.TimeBound("2026-10-01"), end = false, now), Right(Instant.parse("2026-10-01T00:00:00Z")))
    assertEquals(timeBound(com.histmersion.api.automation.ListFilters.TimeBound("2026-10-01"), end = true, now), Right(Instant.parse("2026-10-02T00:00:00Z")))
    assertEquals(timeBound(com.histmersion.api.automation.ListFilters.TimeBound("2026-10-01T08:30:00Z"), end = true, now), Right(Instant.parse("2026-10-01T08:30:00Z")))
    assertEquals(timeBound(com.histmersion.api.automation.ListFilters.TimeBound("7d"), end = false, now), Right(Instant.parse("2026-10-16T12:00:00Z")))
    assertEquals(timeBound(com.histmersion.api.automation.ListFilters.TimeBound(" 12H "), end = false, now), Right(Instant.parse("2026-10-23T00:00:00Z")))
    assertEquals(timeBound(com.histmersion.api.automation.ListFilters.TimeBound("30m"), end = false, now), Right(Instant.parse("2026-10-23T11:30:00Z")))
    assert(timeBound(com.histmersion.api.automation.ListFilters.TimeBound("yesterday"), end = false, now).isLeft)
    assert(timeBound(com.histmersion.api.automation.ListFilters.TimeBound("2026-13-45"), end = false, now).isLeft)
    assertEquals(
      range(Some(com.histmersion.api.automation.ListFilters.TimeBound("2026-10-01")), Some(com.histmersion.api.automation.ListFilters.TimeBound("2026-10-01")), now).map(_._2),
      Right(Some(Instant.parse("2026-10-02T00:00:00Z")))
    )
    assert(range(Some(com.histmersion.api.automation.ListFilters.TimeBound("2026-10-05")), Some(com.histmersion.api.automation.ListFilters.TimeBound("2026-10-01")), now).left.exists(_.contains("before")))
    assert(range(Some(com.histmersion.api.automation.ListFilters.TimeBound("soon")), None, now).left.exists(_.startsWith("since:")))
    assertEquals(range(None, None, now), Right((None, None)))

  test("a closed vocabulary names the first unknown value and the words allowed"):
    assertEquals(known(com.histmersion.api.automation.ListFilters.FacetName("kind"), List(com.histmersion.api.automation.ListFilters.Term("edge")), List(com.histmersion.api.automation.ListFilters.Term("edge"), com.histmersion.api.automation.ListFilters.Term("goal-plan"))), Right(List("edge")))
    assertEquals(known(com.histmersion.api.automation.ListFilters.FacetName("kind"), List(com.histmersion.api.automation.ListFilters.Term("edge"), com.histmersion.api.automation.ListFilters.Term("bogus")), List(com.histmersion.api.automation.ListFilters.Term("edge"))), Left("unknown kind 'bogus' (one of: edge)"))

  test("who decided an approval, from its note and its result"):
    assertEquals(DecidedBy.of(com.histmersion.api.automation.Approval.State.Wire("open"), Some(com.histmersion.api.automation.Approval.Note("autopilot: x")), None), None)
    assertEquals(DecidedBy.of(com.histmersion.api.automation.Approval.State.Wire("approved"), Some(com.histmersion.api.automation.Approval.Note("autopilot: approved — 2 articles")), None), Some(DecidedBy.Autopilot))
    assertEquals(DecidedBy.of(com.histmersion.api.automation.Approval.State.Wire("approved"), None, Some(com.histmersion.api.automation.Approval.Outcome("applied automatically: edge added"))), Some(DecidedBy.Auto))
    assertEquals(DecidedBy.of(com.histmersion.api.automation.Approval.State.Wire("approved"), None, Some(com.histmersion.api.automation.Approval.Outcome("applied: edge added"))), Some(DecidedBy.Person))
    assertEquals(DecidedBy.of(com.histmersion.api.automation.Approval.State.Wire("rejected"), Some(com.histmersion.api.automation.Approval.Note("not the critic")), None), Some(DecidedBy.Person))
    // the SQL writes the prefixes in (AgentRepo): nothing in them may need quoting or escaping
    List(DecidedBy.AutopilotNote, DecidedBy.AutoResult).foreach(p => assert(!p.exists("'%_\\".contains(_)), p))

  test("the facet header round-trips any value, in order; a malformed triple is skipped"):
    val fs = List(
      "kind" -> List("goal-plan" -> 64, "edge" -> 3),
      "model" -> List("codex:gpt-6.1-sol" -> 752, "a,b=c%d" -> 1, "Žeus" -> 2)
    )
    val enc = encodeFacets(fs)
    assert(!enc.contains("Ž") && enc.split(',').length == 5, enc)
    assertEquals(decodeFacets(enc), fs)
    assertEquals(
      decodeFacets(com.histmersion.api.automation.ListFilters.EncodedFacets("kind=edge=3,junk,kind=x=notanumber,=v=1,kind=goal-plan=2")),
      List("kind" -> List("edge" -> 3, "goal-plan" -> 2))
    )
    assertEquals(unescape("100%"), "100%")
    assertEquals(decodeFacets(com.histmersion.api.automation.ListFilters.EncodedFacets("")), Nil)

  test("a page carries the facets in a header of its own, and its total is the states picked"):
    val p = Paging.Page.filtered(
      List("open" -> 87, "approved" -> 290),
      List(com.histmersion.api.Paging.Page.CountState("open")),
      Some(com.histmersion.api.library.Filter.Last("12")),
      List("kind" -> List("goal-plan" -> 64), "decidedBy" -> Nil)
    )
    assertEquals(p.total, 87)
    assertEquals(p.facets, List("kind" -> List("goal-plan" -> 64)))
    assertEquals(p.facet("kind"), List("goal-plan" -> 64))
    assertEquals(p.facet("nothing"), Nil)
    assertEquals(
      Paging.Page.filtered(List("open" -> 2, "failed" -> 1, "done" -> 4), List(com.histmersion.api.Paging.Page.CountState("open"), com.histmersion.api.Paging.Page.CountState("failed")), None).total,
      3
    )
    assertEquals(Paging.Page.filtered(List("open" -> 2, "done" -> 4), Nil, None).total, 6)
    assertEquals(facetLine(com.histmersion.api.automation.ListFilters.FacetName("By kind"), List("goal-plan" -> 64, "edge" -> 3)), "By kind: goal-plan 64 · edge 3")
    assertEquals(facetLine(com.histmersion.api.automation.ListFilters.FacetName("By kind"), Nil), "")

  test("what a heading and a next-page line say about a filter"):
    assertEquals(ApprovalFilter().qualifier, Some("open"))
    assertEquals(
      ApprovalFilter(state = Some(com.histmersion.api.automation.Approval.State.Wire("all")), kind = List(com.histmersion.api.automation.Approval.Kind.Wire("goal-plan"), com.histmersion.api.automation.Approval.Kind.Wire("edge"))).qualifier,
      Some("goal-plan/edge")
    )
    assertEquals(
      RunFilter(status = List(com.histmersion.api.automation.ListFilters.RunFilter.Status("failed")), pipeline = List(com.histmersion.api.automation.ListFilters.RunFilter.Pipeline("review-article"))).qualifier,
      Some("failed review-article")
    )
    assertEquals(JobFilter().qualifier, None)
    assertEquals(
      ApprovalFilter(kind = List(com.histmersion.api.automation.Approval.Kind.Wire("edge,goal-plan")), decidedBy = List(com.histmersion.api.automation.Approval.DecidedBy.Wire("person"))).options,
      List(
        "state" -> None,
        "kind" -> Some("edge"),
        "kind" -> Some("goal-plan"),
        "subject" -> None,
        "goal" -> None,
        "decided-by" -> Some("person")
      )
    )
    assertEquals(
      JobFilter(kind = List(com.histmersion.api.jobs.JobKinds.Wire("ingest")), since = Some(com.histmersion.api.automation.ListFilters.JobFilter.Since("7d"))).options,
      List("kind" -> Some("ingest"), "since" -> Some("7d"), "until" -> None, "subject" -> None)
    )
