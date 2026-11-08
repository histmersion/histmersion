package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.ListFilters.*

import java.time.Instant
import com.histmersion.api.automation.Approval
import com.histmersion.api.library.Filter
import com.histmersion.api.library.Facet
import com.histmersion.api.jobs.JobKinds

/** The list filters' pure parts: the multi-valued parameters, the time bounds, who decided an approval, the facet
  * header and its round trip, and what a heading or a next-page line says.
  */
class ListFiltersSuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-23T12:00:00Z")

  test("a list parameter is repeated or comma-separated, trimmed, each value once"):
    assertEquals(
      values(List(Term("goal-plan, edge"), Term(" edge"), Term(""), Term("article-publish"))),
      List("goal-plan", "edge", "article-publish")
    )
    assertEquals(values(Nil), Nil)
    assertEquals(one(Some(Term("  "))), None)
    assertEquals(one(Some(Term(" tin "))), Some("tin"))

  test("normalized filters lower-case the closed vocabularies, keep a manager or a model as typed"):
    val f = RunFilter(
      status = List(RunFilter.Status("Failed,DONE")),
      pipeline = List(RunFilter.Pipeline("Review-Article")),
      manager = List(RunFilter.Manager("seed:Tin")),
      model = List(RunFilter.Model("codex:GPT-6")),
      subject = Some(RunFilter.Subject("  "))
    )
    val n = f.normalized
    assertEquals(
      (n.status, n.pipeline, n.manager, n.model, n.subject),
      (List("failed", "done"), List("review-article"), List("seed:Tin"), List("codex:GPT-6"), None)
    )
    assertEquals(
      ApprovalFilter(state = Some(Approval.State.Wire(" ALL ")), kind = List(Approval.Kind.Wire("Edge"))).normalized,
      ApprovalFilter(Some(Approval.State.Wire("all")), List(Approval.Kind.Wire("edge")))
    )

  test("a time bound: a day (until = the day included), an instant, or a time ago; anything else is refused"):
    assertEquals(timeBound(TimeBound("2026-10-01"), end = false, now), Right(Instant.parse("2026-10-01T00:00:00Z")))
    assertEquals(timeBound(TimeBound("2026-10-01"), end = true, now), Right(Instant.parse("2026-10-02T00:00:00Z")))
    assertEquals(timeBound(TimeBound("2026-10-01T08:30:00Z"), end = true, now), Right(Instant.parse("2026-10-01T08:30:00Z")))
    assertEquals(timeBound(TimeBound("7d"), end = false, now), Right(Instant.parse("2026-10-16T12:00:00Z")))
    assertEquals(timeBound(TimeBound(" 12H "), end = false, now), Right(Instant.parse("2026-10-23T00:00:00Z")))
    assertEquals(timeBound(TimeBound("30m"), end = false, now), Right(Instant.parse("2026-10-23T11:30:00Z")))
    assert(timeBound(TimeBound("yesterday"), end = false, now).isLeft)
    assert(timeBound(TimeBound("2026-13-45"), end = false, now).isLeft)
    assertEquals(
      range(Some(TimeBound("2026-10-01")), Some(TimeBound("2026-10-01")), now).map(_._2),
      Right(Some(Instant.parse("2026-10-02T00:00:00Z")))
    )
    assert(range(Some(TimeBound("2026-10-05")), Some(TimeBound("2026-10-01")), now).left.exists(_.value.contains("before")))
    assert(range(Some(TimeBound("soon")), None, now).left.exists(_.value.startsWith("since:")))
    assertEquals(range(None, None, now), Right((None, None)))

  test("a closed vocabulary names the first unknown value and the words allowed"):
    assertEquals(known(FacetName("kind"), List(Term("edge")), List(Term("edge"), Term("goal-plan"))), Right(List("edge")))
    assertEquals(known(FacetName("kind"), List(Term("edge"), Term("bogus")), List(Term("edge"))), Left("unknown kind 'bogus' (one of: edge)"))

  test("who decided an approval, from its note and its result"):
    assertEquals(DecidedBy.of(Approval.State.Wire("open"), Some(Approval.Note("autopilot: x")), None), None)
    assertEquals(DecidedBy.of(Approval.State.Wire("approved"), Some(Approval.Note("autopilot: approved — 2 articles")), None), Some(DecidedBy.Autopilot))
    assertEquals(DecidedBy.of(Approval.State.Wire("approved"), None, Some(Approval.Outcome("applied automatically: edge added"))), Some(DecidedBy.Auto))
    assertEquals(DecidedBy.of(Approval.State.Wire("approved"), None, Some(Approval.Outcome("applied: edge added"))), Some(DecidedBy.Person))
    assertEquals(DecidedBy.of(Approval.State.Wire("rejected"), Some(Approval.Note("not the critic")), None), Some(DecidedBy.Person))
    // the SQL writes the prefixes in (AgentRepo): nothing in them may need quoting or escaping
    List(DecidedBy.AutopilotNote, DecidedBy.AutoResult).foreach(p => assert(!p.exists("'%_\\".contains(_)), p))

  test("the facet header round-trips any value, in order; a malformed triple is skipped"):
    val fs = List(
      FacetName("kind") -> List(Term("goal-plan") -> RowCount(64), Term("edge") -> RowCount(3)),
      FacetName("model") -> List(
        Term("codex:gpt-6.1-sol") -> RowCount(752),
        Term("a,b=c%d") -> RowCount(1),
        Term("Žeus") -> RowCount(2)
      )
    )
    val enc = encodeFacets(fs)
    assert(!enc.value.contains("Ž") && enc.value.split(',').length == 5, enc)
    assertEquals(decodeFacets(enc), fs)
    assertEquals(
      decodeFacets(EncodedFacets("kind=edge=3,junk,kind=x=notanumber,=v=1,kind=goal-plan=2")),
      List("kind" -> List("edge" -> 3, "goal-plan" -> 2))
    )
    assertEquals(unescape("100%"), "100%")
    assertEquals(decodeFacets(EncodedFacets("")), Nil)

  test("a page carries the facets in a header of its own, and its total is the states picked"):
    val p = Paging.Page.filtered(
      List(Paging.Page.CountState("open") -> Paging.Count(87), Paging.Page.CountState("approved") -> Paging.Count(290)),
      List(Paging.Page.CountState("open")),
      Some(Filter.Last("12")),
      List(
        Facet.Name("kind") -> List(Facet.Value("goal-plan") -> Facet.Count(64)),
        Facet.Name("decidedBy") -> Nil
      )
    )
    assertEquals(p.total, 87)
    assertEquals(p.facets, List("kind" -> List("goal-plan" -> 64)))
    assertEquals(p.facet(Facet.Name("kind")), List("goal-plan" -> 64))
    assertEquals(p.facet(Facet.Name("nothing")), Nil)
    assertEquals(
      Paging.Page.filtered(List(Paging.Page.CountState("open") -> Paging.Count(2), Paging.Page.CountState("failed") -> Paging.Count(1), Paging.Page.CountState("done") -> Paging.Count(4)), List(Paging.Page.CountState("open"), Paging.Page.CountState("failed")), None).total,
      3
    )
    assertEquals(Paging.Page.filtered(List(Paging.Page.CountState("open") -> Paging.Count(2), Paging.Page.CountState("done") -> Paging.Count(4)), Nil, None).total, 6)
    assertEquals(facetLine(FacetName("By kind"), List(Term("goal-plan") -> RowCount(64), Term("edge") -> RowCount(3))), "By kind: goal-plan 64 · edge 3")
    assertEquals(facetLine(FacetName("By kind"), Nil), "")

  test("what a heading and a next-page line say about a filter"):
    assertEquals(ApprovalFilter().qualifier, Some("open"))
    assertEquals(
      ApprovalFilter(state = Some(Approval.State.Wire("all")), kind = List(Approval.Kind.Wire("goal-plan"), Approval.Kind.Wire("edge"))).qualifier,
      Some("goal-plan/edge")
    )
    assertEquals(
      RunFilter(status = List(RunFilter.Status("failed")), pipeline = List(RunFilter.Pipeline("review-article"))).qualifier,
      Some("failed review-article")
    )
    assertEquals(JobFilter().qualifier, None)
    assertEquals(
      ApprovalFilter(kind = List(Approval.Kind.Wire("edge,goal-plan")), decidedBy = List(Approval.DecidedBy.Wire("person"))).options,
      List[(String, Option[String])](
        "state" -> None,
        "kind" -> Some("edge"),
        "kind" -> Some("goal-plan"),
        "subject" -> None,
        "goal" -> None,
        "decided-by" -> Some("person")
      )
    )
    assertEquals(
      JobFilter(kind = List(JobKinds.Wire("ingest")), since = Some(JobFilter.Since("7d"))).options,
      List[(String, Option[String])]("kind" -> Some("ingest"), "since" -> Some("7d"), "until" -> None, "subject" -> None)
    )
