package com.histmersion.domain.library.citations

import com.histmersion.domain.Caller
import com.histmersion.domain.library.LibraryStoreSuite
import com.histmersion.values.SourceStatus
import com.histmersion.api.library.{Edge, GraphLink, NeighborTarget, Source}

/** What the citation graph answers, the same on the database and in memory: a source's edges both ways by kind, the
  * graph around it to a depth, the disagreements by edge and by status.
  */
abstract class CitationServiceContract extends LibraryStoreSuite:

  private def source(ck: String, year: Int, status: SourceStatus, edges: List[Edge] = Nil): Source =
    Source(
      ck,
      s"Title of $ck",
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

  private val A = "zqa1990_a"
  private val B = "zqb2001_b"
  private val C = "zqc1975_c"
  private val D = "zqd2010_d"
  private val E = "zqe1950_e"

  private def corpus(s: com.histmersion.domain.library.LibraryStores): Unit =
    List(
      source(E, 1950, SourceStatus.Contested),
      source(D, 2010, SourceStatus.Current),
      source(C, 1975, SourceStatus.Superseded, List(Edge(C, A, "superseded_by"))),
      source(B, 2001, SourceStatus.Current, List(Edge(B, A, "refutes"), Edge(B, D, "references"))),
      source(A, 1990, SourceStatus.Current, List(Edge(A, B, "references"), Edge(A, "zq_topic", "references")))
    ).foreach(x => s.sources.put(x, Caller.Local): Unit)

  stest("neighbors: both ways, narrowed by kind, a parked target unresolved, an unknown source none") { s =>
    corpus(s)
    assertEquals(
      s.citations.neighbors(A, Nil).map(r => (r.direction, r.kind, r.citekey, r.status, r.target)),
      List(
        ("in", "refutes", B, Some(SourceStatus.Current), NeighborTarget.Source),
        ("in", "superseded_by", C, Some(SourceStatus.Superseded), NeighborTarget.Source),
        ("out", "references", "zq_topic", None, NeighborTarget.Unresolved),
        ("out", "references", B, Some(SourceStatus.Current), NeighborTarget.Source)
      )
    )
    assertEquals(s.citations.neighbors(A, List("refutes")).map(_.citekey), List(B))
    assertEquals(s.citations.neighbors("zqnone2000_x", Nil), Nil)
  }

  stest("graph: breadth-first to a depth, every link between two nodes") { s =>
    corpus(s)
    assertEquals(s.citations.graph(A, 0).nodes.map(n => (n.id, n.depth)), List((A, 0)))
    val one = s.citations.graph(A, 1)
    assertEquals(one.nodes.map(n => (n.id, n.depth)), List((A, 0), (B, 1), (C, 1)))
    assertEquals(
      one.links,
      List(GraphLink(A, B, "references"), GraphLink(B, A, "refutes"), GraphLink(C, A, "superseded_by"))
    )
    val two = s.citations.graph(A, 9)
    assertEquals((two.depth, two.nodes.map(_.id)), (4, List(A, B, C, D)))
    assertEquals(s.citations.graph("zqnone2000_x", 2).nodes, Nil)
  }

  stest("contradictions: a source's disagreeing edges both ways; the corpus's, by year; statuses without an edge") {
    s =>
      corpus(s)
      assertEquals(
        s.citations.contradictions(Some(A), None).rows.map(r => (r.direction, r.kind, r.otherCitekey)),
        List((Some("in"), Some("refutes"), Some(B)), (Some("in"), Some("superseded_by"), Some(C)))
      )
      val all = s.citations.contradictions(None, None)
      assertEquals(
        all.rows.map(r => (r.citekey, r.direction, r.kind, r.otherCitekey)),
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
        s.citations.contradictions(None, Some(1980)).rows.map(r => (r.citekey, r.otherCitekey)),
        List((A, Some(B)), (B, Some(A)), (C, Some(A)), (E, None))
      )
      assertEquals(s.citations.contradictions(Some("zqnone2000_x"), None).total, 0)
  }

class CitationServiceInMemorySuite extends CitationServiceContract with LibraryStoreSuite.InMemory
class CitationServiceDbSuite extends CitationServiceContract with LibraryStoreSuite.OnDatabase
