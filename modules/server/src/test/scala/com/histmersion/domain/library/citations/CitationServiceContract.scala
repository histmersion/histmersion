package com.histmersion
package domain.library.citations

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.Caller
import com.histmersion.domain.library.LibraryStoreSuite
import com.histmersion.api.library.{Edge, GraphLink, NeighborTarget, Source}
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Title
import com.histmersion.domain.library.LibraryStores
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given

/** What the citation graph answers, the same on the database and in memory: a source's edges both ways by kind, the
  * graph around it to a depth, the disagreements by edge and by status.
  */
abstract class CitationServiceContract extends LibraryStoreSuite:

  private def source(
      ck: Citekey,
      year: Year,
      status: Source.Status,
      edges: List[Edge] = Nil
  ): Source =
    Source(
      ck,
      Title(s"Title of $ck"),
      Nil,
      None,
      Some(year),
      None,
      status,
      Nil,
      Nil,
      Nil,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      Nil,
      edges
    )

  private def dom(c: Citekey) = domain.library.Citekey.applyUnsafe(c.value)
  private val A = Citekey("zqa1990_a")
  private val B = Citekey("zqb2001_b")
  private val C = Citekey("zqc1975_c")
  private val D = Citekey("zqd2010_d")
  private val E = Citekey("zqe1950_e")

  private def corpus(s: LibraryStores): Unit =
    List(
      source(E, Year(1950), Source.Status.Contested),
      source(D, Year(2010), Source.Status.Current),
      source(C, Year(1975), Source.Status.Superseded, List(Edge(C, A, Edge.Kind.SupersededBy))),
      source(
        B,
        Year(2001),
        Source.Status.Current,
        List(Edge(B, A, Edge.Kind.Refutes), Edge(B, D, Edge.Kind.References))
      ),
      source(
        A,
        Year(1990),
        Source.Status.Current,
        List(Edge(A, B, Edge.Kind.References), Edge(A, Citekey("zq_topic"), Edge.Kind.References))
      )
    ).foreach(x => s.sources.put(x, Caller.Local): Unit)

  stest("neighbors: both ways, narrowed by kind, a parked target unresolved, an unknown source none") { s =>
    corpus(s)
    assertEquals(
      s.citations
        .neighbors(dom(A), Nil)
        .map(r => (r.direction, Edge.Kind.wireOf(r.kind), r.citekey, r.status, r.target)),
      List(
        ("in", "refutes", B, Some(Source.Status.Current), NeighborTarget.Source),
        (
          "in",
          "superseded_by",
          C,
          Some(Source.Status.Superseded),
          NeighborTarget.Source
        ),
        ("out", "references", "zq_topic", None, NeighborTarget.Unresolved),
        ("out", "references", B, Some(Source.Status.Current), NeighborTarget.Source)
      )
    )
    assertEquals(
      s.citations.neighbors(dom(A), List(domain.library.Edge.Kind.Wire("refutes"))).map(_.citekey),
      List(B)
    )
    assertEquals(s.citations.neighbors(domain.library.Citekey("zqnone2000_x"), Nil), Nil)
  }

  stest("graph: breadth-first to a depth, every link between two nodes") { s =>
    corpus(s)
    assertEquals(s.citations.graph(dom(A), CitationService.Depth(0)).nodes.map(n => (n.id, n.depth)), List((A, 0)))
    val one = s.citations.graph(dom(A), CitationService.Depth(1))
    assertEquals(one.nodes.map(n => (n.id, n.depth)), List((A, 0), (B, 1), (C, 1)))
    assertEquals(
      one.links,
      List(
        GraphLink(A, B, Edge.Kind.References),
        GraphLink(B, A, Edge.Kind.Refutes),
        GraphLink(C, A, Edge.Kind.SupersededBy)
      )
    )
    val two = s.citations.graph(dom(A), CitationService.Depth(9))
    assertEquals((two.depth, two.nodes.map(_.id)), (4, List(A, B, C, D)))
    assertEquals(
      s.citations.graph(domain.library.Citekey("zqnone2000_x"), CitationService.Depth(2)).nodes,
      Nil
    )
  }

  stest("contradictions: a source's disagreeing edges both ways; the corpus's, by year; statuses without an edge") {
    s =>
      corpus(s)
      assertEquals(
        s.citations
          .contradictions(Some(dom(A)), None)
          .rows
          .map(r => (r.direction, r.kind.map(Edge.Kind.wireOf), r.otherCitekey)),
        List((Some("in"), Some("refutes"), Some(B)), (Some("in"), Some("superseded_by"), Some(C)))
      )
      val all = s.citations.contradictions(None, None)
      assertEquals(
        all.rows.map(r => (r.citekey, r.direction, r.kind.map(Edge.Kind.wireOf), r.otherCitekey)),
        List(
          (A, Some("in"), Some("refutes"), Some(B)),
          (A, Some("in"), Some("superseded_by"), Some(C)),
          (B, Some("out"), Some("refutes"), Some(A)),
          (C, Some("out"), Some("superseded_by"), Some(A)),
          (E, None, None, None)
        )
      )
      assertEquals(all.total, 5)
      assertEquals(
        s.citations.contradictions(None, Some(CitationService.Since(1980))).rows.map(r => (r.citekey, r.otherCitekey)),
        List((A, Some(B)), (B, Some(A)), (C, Some(A)), (E, None))
      )
      assertEquals(
        s.citations.contradictions(Some(domain.library.Citekey("zqnone2000_x")), None).total,
        0
      )
  }

class CitationServiceInMemorySuite extends CitationServiceContract with LibraryStoreSuite.InMemory
class CitationServiceDbSuite extends CitationServiceContract with LibraryStoreSuite.OnDatabase
