package com.histmersion
package web.library

import com.histmersion.api.library.{Citekey, Edge, NeighborRow, NeighborTarget}

class CitationCountsSuite extends munit.FunSuite:

  private val Out = NeighborRow.Direction("out")
  private val In = NeighborRow.Direction("in")

  private def row(direction: NeighborRow.Direction, kind: Edge.Kind, other: Citekey, target: NeighborTarget = NeighborTarget.Source) =
    NeighborRow(direction, kind, other, None, target = target)

  test("what it cites is every edge out of it, as its page's Cites list counts them"):
    val c = CitationCounts.of(
      List(
        row(Out, Edge.Kind.References, Citekey("a")),
        row(Out, Edge.Kind.References, Citekey("x"), NeighborTarget.Unresolved),
        row(Out, Edge.Kind.Challenges, Citekey("b")),
        row(In, Edge.Kind.References, Citekey("c"))
      ),
      0
    )
    assertEquals(c.cites, 3)

  test("who answers it: the critics pointing at it, and the newer source superseding it — once each"):
    val c = CitationCounts.of(
      List(
        row(In, Edge.Kind.Challenges, Citekey("critic1")),
        row(In, Edge.Kind.Refutes, Citekey("critic2")),
        row(In, Edge.Kind.Updates, Citekey("critic3")),
        row(In, Edge.Kind.Refutes, Citekey("critic1")),
        row(Out, Edge.Kind.SupersededBy, Citekey("newer")),
        row(In, Edge.Kind.Supersedes, Citekey("newer")),
        row(In, Edge.Kind.References, Citekey("plain")),
        row(Out, Edge.Kind.Challenges, Citekey("its-own-target"))
      ),
      2
    )
    assertEquals(c, CitationCounts(cites = 2, answers = 4, articles = 2))
