package com.histmersion.web.library

import com.histmersion.api.library.{NeighborRow, NeighborTarget}

class CitationCountsSuite extends munit.FunSuite:

  private def row(direction: String, kind: String, other: String, target: NeighborTarget = NeighborTarget.Source) =
    NeighborRow(direction, kind, other, None, target = target)

  test("what it cites is every edge out of it, as its page's Cites list counts them"):
    val c = CitationCounts.of(
      List(
        row("out", "references", "a"),
        row("out", "references", "x", NeighborTarget.Unresolved),
        row("out", "challenges", "b"),
        row("in", "references", "c")
      ),
      0
    )
    assertEquals(c.cites, 3)

  test("who answers it: the critics pointing at it, and the newer source superseding it — once each"):
    val c = CitationCounts.of(
      List(
        row("in", "challenges", "critic1"),
        row("in", "refutes", "critic2"),
        row("in", "updates", "critic3"),
        row("in", "refutes", "critic1"),
        row("out", "superseded_by", "newer"),
        row("in", "supersedes", "newer"),
        row("in", "references", "plain"),
        row("out", "challenges", "its-own-target")
      ),
      2
    )
    assertEquals(c, CitationCounts(cites = 2, answers = 4, articles = 2))
