package com.histmersion.core

import com.histmersion.core.ListFilters.*

import java.time.Instant

/** The list filters' pure parts: the multi-valued parameters, the time bounds, who decided an
  * approval, the facet header and its round trip, and what a heading or a next-page line says. */
class ListFiltersSuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-23T12:00:00Z")

  test("a list parameter is repeated or comma-separated, trimmed, each value once"):
    assertEquals(values(List("goal-plan, edge", " edge", "", "article-publish")), List("goal-plan", "edge", "article-publish"))
    assertEquals(values(Nil), Nil)
    assertEquals(one(Some("  ")), None)
    assertEquals(one(Some(" tin ")), Some("tin"))

  test("normalized filters lower-case the closed vocabularies, keep a manager or a model as typed"):
    val f = RunFilter(status = List("Failed,DONE"), pipeline = List("Review-Article"), manager = List("seed:Tin"),
      model = List("codex:GPT-6"), subject = Some("  "))
    val n = f.normalized
    assertEquals((n.status, n.pipeline, n.manager, n.model, n.subject), (List("failed", "done"), List("review-article"),
      List("seed:Tin"), List("codex:GPT-6"), None))
    assertEquals(ApprovalFilter(state = Some(" ALL "), kind = List("Edge")).normalized, ApprovalFilter(Some("all"), List("edge")))

  test("a time bound: a day (until = the day included), an instant, or a time ago; anything else is refused"):
    assertEquals(timeBound("2026-10-01", end = false, now), Right(Instant.parse("2026-10-01T00:00:00Z")))
    assertEquals(timeBound("2026-10-01", end = true, now), Right(Instant.parse("2026-10-02T00:00:00Z")))
    assertEquals(timeBound("2026-10-01T08:30:00Z", end = true, now), Right(Instant.parse("2026-10-01T08:30:00Z")))
    assertEquals(timeBound("7d", end = false, now), Right(Instant.parse("2026-10-16T12:00:00Z")))
    assertEquals(timeBound(" 12H ", end = false, now), Right(Instant.parse("2026-10-23T00:00:00Z")))
    assertEquals(timeBound("30m", end = false, now), Right(Instant.parse("2026-10-23T11:30:00Z")))
    assert(timeBound("yesterday", end = false, now).isLeft)
    assert(timeBound("2026-13-45", end = false, now).isLeft)
    assertEquals(range(Some("2026-10-01"), Some("2026-10-01"), now).map(_._2), Right(Some(Instant.parse("2026-10-02T00:00:00Z"))))
    assert(range(Some("2026-10-05"), Some("2026-10-01"), now).left.exists(_.contains("before")))
    assert(range(Some("soon"), None, now).left.exists(_.startsWith("since:")))
    assertEquals(range(None, None, now), Right((None, None)))

  test("a closed vocabulary names the first unknown value and the words allowed"):
    assertEquals(known("kind", List("edge"), List("edge", "goal-plan")), Right(List("edge")))
    assertEquals(known("kind", List("edge", "bogus"), List("edge")), Left("unknown kind 'bogus' (one of: edge)"))

  test("who decided an approval, from its note and its result"):
    assertEquals(DecidedBy.of("open", Some("autopilot: x"), None), None)
    assertEquals(DecidedBy.of("approved", Some("autopilot: approved — 2 articles"), None), Some(DecidedBy.Autopilot))
    assertEquals(DecidedBy.of("approved", None, Some("applied automatically: edge added")), Some(DecidedBy.Auto))
    assertEquals(DecidedBy.of("approved", None, Some("applied: edge added")), Some(DecidedBy.Person))
    assertEquals(DecidedBy.of("rejected", Some("not the critic"), None), Some(DecidedBy.Person))
    // the SQL writes the prefixes in (AgentRepo): nothing in them may need quoting or escaping
    List(DecidedBy.AutopilotNote, DecidedBy.AutoResult).foreach(p => assert(!p.exists("'%_\\".contains(_)), p))

  test("the facet header round-trips any value, in order; a malformed triple is skipped"):
    val fs = List("kind" -> List("goal-plan" -> 64, "edge" -> 3),
      "model" -> List("codex:gpt-6.1-sol" -> 752, "a,b=c%d" -> 1, "Žeus" -> 2))
    val enc = encodeFacets(fs)
    assert(!enc.contains("Ž") && enc.split(',').length == 5, enc)
    assertEquals(decodeFacets(enc), fs)
    assertEquals(decodeFacets("kind=edge=3,junk,kind=x=notanumber,=v=1,kind=goal-plan=2"), List("kind" -> List("edge" -> 3, "goal-plan" -> 2)))
    assertEquals(unescape("100%"), "100%")
    assertEquals(decodeFacets(""), Nil)

  test("a page carries the facets in a header of its own, and its total is the states picked"):
    val p = Paging.Page.filtered(List("open" -> 87, "approved" -> 290), List("open"), Some("12"),
      List("kind" -> List("goal-plan" -> 64), "decidedBy" -> Nil))
    assertEquals(p.total, 87)
    assertEquals(p.facets, List("kind" -> List("goal-plan" -> 64)))
    assertEquals(p.facet("kind"), List("goal-plan" -> 64))
    assertEquals(p.facet("nothing"), Nil)
    assertEquals(Paging.Page.filtered(List("open" -> 2, "failed" -> 1, "done" -> 4), List("open", "failed"), None).total, 3)
    assertEquals(Paging.Page.filtered(List("open" -> 2, "done" -> 4), Nil, None).total, 6)
    assertEquals(facetLine("By kind", List("goal-plan" -> 64, "edge" -> 3)), "By kind: goal-plan 64 · edge 3")
    assertEquals(facetLine("By kind", Nil), "")

  test("what a heading and a next-page line say about a filter"):
    assertEquals(ApprovalFilter().qualifier, Some("open"))
    assertEquals(ApprovalFilter(state = Some("all"), kind = List("goal-plan", "edge")).qualifier, Some("goal-plan/edge"))
    assertEquals(RunFilter(status = List("failed"), pipeline = List("review-article")).qualifier, Some("failed review-article"))
    assertEquals(JobFilter().qualifier, None)
    assertEquals(ApprovalFilter(kind = List("edge,goal-plan"), decidedBy = List("person")).options,
      List("state" -> None, "kind" -> Some("edge"), "kind" -> Some("goal-plan"), "subject" -> None, "goal" -> None,
        "decided-by" -> Some("person")))
    assertEquals(JobFilter(kind = List("ingest"), since = Some("7d")).options,
      List("kind" -> Some("ingest"), "since" -> Some("7d"), "until" -> None, "subject" -> None))
