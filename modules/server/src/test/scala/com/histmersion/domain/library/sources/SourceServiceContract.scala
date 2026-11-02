package com.histmersion.domain.library.sources

import com.histmersion.api.*
import com.histmersion.domain.Caller
import com.histmersion.domain.library.{LibraryStores, LibraryStoreSuite}
import com.histmersion.values.{Confidence, SourceStatus}

/** What the library's sources answer, the same on the database and in memory: a source written whole and in part, its
  * findings kept by id, its edges routed and promoted, its body read by part, renamed with its old citekey kept and its
  * links followed, deleted with what goes counted.
  */
abstract class SourceServiceContract extends LibraryStoreSuite:

  private val Me = Caller.Local

  private def finding(ck: String, n: Int, claim: String, at: Option[GeoPoint] = None): Finding =
    Finding(
      s"$ck#$n",
      ck,
      claim,
      Some("Zqland"),
      at,
      Some(Period(-1500, -1400)),
      None,
      List("zq"),
      Some(Confidence.High)
    )

  private def source(ck: String, title: String, findings: List[Finding] = Nil, edges: List[Edge] = Nil): Source =
    Source(
      ck,
      title,
      List("Zqauthor, Ann"),
      None,
      Some(1990),
      Some("Zq Review"),
      SourceStatus.Current,
      List("bronze age"),
      List("Zqland"),
      List("zq-oracle"),
      Some(Period(-1700, -1100)),
      Some(s"A study of $title."),
      None,
      None,
      None,
      None,
      Some(s"# $title\n\n## Walls\n\nThe walls of $title.\n\n## Harbour\n\nThe harbour of $title.\n"),
      findings,
      edges,
      Some(s"Notes on $title.")
    )

  private val A = "zqa1990_walls"
  private val B = "zqb2001_cove"
  private val C = "zqc1975_kings"

  stest("a source put and read back: its record, its findings in id order, its edges where their targets are") { s =>
    val token = s.sources.put(
      source(
        A,
        "Walls",
        List(finding(A, 1, "the walls"), finding(A, 0, "a gate", Some(GeoPoint(34.6, 40.0)))),
        List(Edge(A, B, "references"), Edge(A, "zq_article", "references"))
      ),
      Me
    )
    val (detail, rows) = s.sources.get(A).get
    assertEquals(detail.updatedAt, Some(token))
    assertEquals(
      (detail.title, detail.periodStart, detail.periodEnd, detail.tags),
      ("Walls", Some(-1700L), Some(-1100L), List("zq-oracle"))
    )
    assertEquals(rows.map(_.claim), List("a gate", "the walls"))
    assertEquals(rows.map(r => (r.lon, r.lat)), List((Some(34.6), Some(40.0)), (None, None)))
    assertEquals(s.sources.load(A).get.edges, List(Edge(A, "zq_article", "references"), Edge(A, B, "references")))
    assertEquals(
      s.citations.neighbors(A, Nil).map(r => (r.citekey, r.target)),
      List(("zq_article", NeighborTarget.Unresolved), (B, NeighborTarget.Unresolved))
    )
    // the target arrives: its parked edge becomes an edge between two sources
    val _ = s.sources.put(source(B, "Cove"), Me)
    assertEquals(
      s.citations.neighbors(A, Nil).map(r => (r.direction, r.citekey, r.target)),
      List(("out", "zq_article", NeighborTarget.Unresolved), ("out", B, NeighborTarget.Source))
    )
    assertEquals(s.citations.neighbors(B, Nil).map(r => (r.direction, r.citekey)), List(("in", A)))
    // a put again keeps the findings' uids by id
    val uid0 = rows.head.uid
    val _ = s.sources.put(source(A, "Walls", List(finding(A, 0, "a gate, again"))), Me)
    assertEquals(s.sources.get(A).get._2.map(r => (r.claim, r.uid)), List(("a gate, again", uid0)))
    assertEquals(s.sources.citekeys, List(A, B))
    assertEquals(s.sources.refs(List(B, A, "zqnone2000_x")).map(_.citekey), List(A, B))
    assertEquals(s.sources.get("zqnone2000_x"), None)
  }

  stest("a patch: what it names changes, the rest stays; a stale token and an unknown source are refused") { s =>
    val token = s.sources.put(source(A, "Walls", List(finding(A, 0, "a gate"), finding(A, 1, "the walls"))), Me)
    val patched = s.sources.patch(
      A,
      SourcePatch(
        metadata = Some(MetadataPatch(title = Some("Walls, revised"), clear = List(MetadataField.Venue))),
        tags = Some(ListPatch(add = List("zq-new"), remove = List("zq-oracle"))),
        findings = Some(FindingsPatch(add = List(FindingInput("a tower")), remove = List(s"$A#0"))),
        ifUpdatedAt = Some(token)
      ),
      Me
    )
    assert(patched.isRight, patched.toString)
    val (detail, rows) = s.sources.get(A).get
    assertEquals((detail.title, detail.venue, detail.tags), ("Walls, revised", None, List("zq-new")))
    assertEquals(
      s.sources.load(A).get.findings.map(f => (f.id, f.claim)),
      List((s"$A#0", "a tower"), (s"$A#1", "the walls"))
    )
    assertEquals(rows.size, 2)
    assertEquals(
      s.sources.patch(A, SourcePatch(summary = Some("x"), ifUpdatedAt = Some(token)), Me).left.map(_.code),
      Left(Some(Write.CodePrecondition))
    )
    assertEquals(
      s.sources.patch("zqnone2000_x", SourcePatch(summary = Some("x")), Me).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
  }

  stest("the body by part: its chunks, its table of contents, a heading, the text") { s =>
    val _ = s.sources.put(source(A, "Walls"), Me)
    val chunks = s.sources.bodyChunks(A)
    assert(chunks.size >= 2, chunks.toString)
    assertEquals(s.sources.toc(A).map(_.heading), chunks.flatMap(_.heading).distinct)
    assert(s.sources.bodyText(A).contains("The walls of Walls."))
    val last = s.sources.toc(A).last.heading
    assert(s.sources.bodyUnderHeading(A, last.take(4).toUpperCase).contains("The harbour of Walls."), last)
    assertEquals(s.sources.bodyChunks(A).map(_.ord), s.sources.bodyChunks(A).map(_.ord).sorted)
    assert(s.sources.markdown(A).startsWith("# Walls"))
    assertEquals(s.sources.bodyText("zqnone2000_x"), "")
    assertEquals(s.sources.bodyChunks("zqnone2000_x"), Nil)
  }

  stest("a rename: the findings renumbered, the old citekey kept, links in other sources' notes and edges followed") {
    s =>
      val _ = s.sources.put(source(B, "Cove"), Me)
      val _ = s.sources.put(source(A, "Walls", List(finding(A, 0, "a gate")), List(Edge(A, B, "refutes"))), Me)
      val _ =
        s.sources.put(source(C, "Kings", edges = List(Edge(C, A, "challenges"), Edge(C, "zqa1991_walls", "cites"))), Me)
      val _ = s.sources.patch(B, SourcePatch(notes = Some(s"See [[$A|the walls]] and [[zqa1990]].")), Me)
      assertEquals(s.sources.rename(A, "zqa1990_walls", Me).left.map(_.code), Left(Some(Write.CodeInvalid)))
      assertEquals(s.sources.rename(A, B, Me).left.map(_.code), Left(Some(Write.CodeInvalid)))
      assertEquals(s.sources.rename(A, "zqwalls", Me).left.map(_.code), Left(Some(Write.CodeInvalid)))
      assertEquals(
        s.sources.rename("zqnone2000_x", "zqnone2001_x", Me).left.map(_.code),
        Left(Some(Write.CodeNotFound))
      )
      val counts = s.sources.rename(A, "zqa1991_walls", Me).toOption.get
      assertEquals(
        (counts.findings, counts.sources, counts.links, counts.columns, counts.edgesPromoted),
        (1, List(B), 2, Nil, 1)
      )
      assertEquals(s.sources.current(A), Some("zqa1991_walls"))
      assertEquals(s.sources.aliasOf(A), Some("zqa1991_walls"))
      assertEquals(s.sources.aliases, List(SourceAlias(A, "zqa1991_walls")))
      assertEquals(s.sources.load("zqa1991_walls").get.findings.map(_.id), List("zqa1991_walls#0"))
      assertEquals(s.sources.load(B).get.notes, Some("See [[zqa1991_walls|the walls]] and [[zqa1991_walls]]."))
      assertEquals(
        s.citations.neighbors("zqa1991_walls", Nil).map(r => (r.direction, r.kind, r.citekey)),
        List(("in", "challenges", C), ("in", "cites", C), ("out", "refutes", B))
      )
      assertEquals(s.sources.rename(A, "zqx2000_x", Me).left.map(_.code), Left(Some(Write.CodeInvalid)))
      // renamed back: the alias goes
      assert(s.sources.rename("zqa1991_walls", A, Me).isRight)
      assertEquals(s.sources.aliases, List(SourceAlias("zqa1991_walls", A)))
  }

  stest("a delete: refused for an old citekey and an unknown source; what goes counted, the edges to it parked") { s =>
    val _ = s.sources.put(source(B, "Cove", edges = List(Edge(B, A, "references"))), Me)
    val _ =
      s.sources.put(source(A, "Walls", List(finding(A, 0, "a gate")), List(Edge(A, "zqnot2020_yet", "cites"))), Me)
    val _ = s.sources.rename(A, "zqa1991_walls", Me)
    assertEquals(s.sources.deletion(A).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(s.sources.deletion("zqnone2000_x").left.map(_.code), Left(Some(Write.CodeNotFound)))
    val plan = s.sources.deletion("zqa1991_walls").toOption.get
    assertEquals(
      plan.deletion.counts.map(c => (c.part, c.rows)),
      List(
        DeletePart.Findings -> 1,
        DeletePart.ParkedOut -> 1,
        DeletePart.EdgesIn -> 1,
        DeletePart.Chunks -> s.sources.bodyChunks("zqa1991_walls").size,
        DeletePart.Aliases -> 1
      )
    )
    assertEquals(
      (plan.deletion.aliases, plan.deletion.edgesIn, plan.keys),
      (List(A), List(Edge(B, "zqa1991_walls", "references")), List("zqa1991_walls", A))
    )
    val done =
      s.sources.delete("zqa1991_walls", "2026-11-01", p => List(s"file-of-${p.deletion.citekey}"), Me).toOption.get
    assertEquals(
      (done.deletion.applied, done.deletion.files, done.deletion.logEntry),
      (true, List("file-of-zqa1991_walls"), None)
    )
    assertEquals(s.sources.load("zqa1991_walls"), None)
    assertEquals(s.sources.current(A), None)
    assertEquals(s.sources.load(B).get.edges, List(Edge(B, "zqa1991_walls", "references")))
    assertEquals(s.citations.neighbors(B, Nil).map(_.target), List(NeighborTarget.Unresolved))
  }

  stest("links resolved: the citekey, an old one, the one source a short form fits; parked edges promoted by it") { s =>
    val _ = s.sources.put(source(A, "Walls"), Me)
    val _ = s.sources.put(source("zqa1990_gates", "Gates"), Me)
    val _ =
      s.sources.put(source(B, "Cove", edges = List(Edge(B, "zqb2001", "references"), Edge(B, "zqa1990", "cites"))), Me)
    val _ = s.sources.put(source(C, "Kings"), Me)
    val _ = s.sources.rename(C, "zqc1976_kings", Me)
    assertEquals(
      s.sources.resolveLinks(List(A, "ZQA1990_WALLS ", "zqa1990", "zqb2001", C, "zqnone", "")),
      Map(A -> A, "zqa1990_walls" -> A, "zqb2001" -> B, C -> "zqc1976_kings")
    )
    assertEquals(s.sources.resolveEdges(Me), 0) // a self-edge by a short form stays parked, an ambiguous one too
    val _ = s.sources.put(source("zqd2005_new", "New", edges = List(Edge("zqd2005_new", "zqc1976", "references"))), Me)
    assertEquals(s.sources.resolveEdges(Me), 1)
    assertEquals(
      s.citations.neighbors("zqc1976_kings", Nil).map(r => (r.direction, r.citekey)),
      List(("in", "zqd2005_new"))
    )
  }

  stest("what acquisition reads of the sources: existence, the DOI index, edges routed and counted") { s =>
    val _ = s.sources.put(source(A, "Walls").copy(doi = Some("10.5555/ZQ.Walls")), Me)
    val _ = s.sources.put(source(B, "Cove"), Me)
    assert(s.sources.exists(A))
    assert(!s.sources.exists("zqnone"))
    assertEquals(s.sources.doiIndex, Map("10.5555/zq.walls" -> A))
    val edges = List(Edge(A, B, "references"), Edge(A, "zq_not_yet", "references"), Edge(A, B, "references"))
    assertEquals(s.sources.addEdges(edges, Me), 2)
    assertEquals(s.sources.addEdges(edges, Me), 0, "an edge stored already is not new")
    assertEquals(s.sources.addEdges(Nil, Me), 0)
    assertEquals(s.sources.load(A).map(_.edges.map(_.dst).sorted), Some(List("zq_not_yet", B)))
  }

  stest("the citekey of a DOI: case and spaces ignored, the first by citekey of two holders, none for an unknown one") {
    s =>
      val _ = s.sources.put(source(B, "Cove").copy(doi = Some("10.5555/ZQ.Both")), Me)
      val _ = s.sources.put(source(A, "Walls").copy(doi = Some("10.5555/zq.both")), Me)
      assertEquals(s.sources.citekeyOfDoi(" 10.5555/zq.BOTH "), Some(List(A, B).min))
      assertEquals(s.sources.citekeyOfDoi("10.5555/zq.none"), None)
  }

  stest("a source's stored original: its digest, who holds one, set once a conversion is written") { s =>
    val _ = s.sources.put(source(A, "Walls"), Me)
    val _ = s.sources.put(source(B, "Cove"), Me)
    assertEquals(s.sources.original(A), None)
    s.sources.setOriginal(A, "a" * 64, Me)
    s.sources.setOriginal("zqnone", "b" * 64, Me)
    assertEquals(s.sources.original(A), Some("a" * 64))
    assertEquals(s.sources.holderOf("a" * 64), Some(A))
    assertEquals(s.sources.holderOf("b" * 64), None)
    assertEquals(s.sources.originals(List(A, B, "zqnone")), Map(A -> "a" * 64))
    assertEquals(s.sources.originals(Nil), Map.empty)
    val _ = s.sources.put(source(A, "Walls again"), Me)
    assertEquals(s.sources.original(A), Some("a" * 64), "a put keeps the original")
  }

  stest("a source's licence: recorded when it has none, classified as it is read") { s =>
    val _ = s.sources.put(source(A, "Walls"), Me)
    assertEquals(s.sources.licence(A), None)
    assert(!s.sources.recordLicence(A, com.histmersion.values.LicenceId("  "), Me), "a blank licence is never written")
    assert(s.sources.recordLicence(A, com.histmersion.values.LicenceId(" CC BY 4.0 "), Me))
    assert(!s.sources.recordLicence(A, com.histmersion.values.LicenceId("CC0-1.0"), Me), "the first one is kept")
    assertEquals(s.sources.licence(A), Some(com.histmersion.domain.Licence.classify("CC BY 4.0")))
    assert(!s.sources.recordLicence("zqnone", com.histmersion.values.LicenceId("CC0-1.0"), Me))
  }

  stest("the sources not in Calibre yet, a push's metadata, the Calibre id written back") { s =>
    val _ = s.sources.put(source(A, "Walls").copy(provenance = Some("unpaywall")), Me)
    val _ = s.sources.put(source(B, "Cove").copy(provenance = Some("calibre")), Me)
    val _ = s.sources.put(source(C, "Kings"), Me)
    assertEquals(s.sources.calibreGaps(None).map(_.citekey), List(A, C))
    assertEquals(s.sources.calibreGaps(Some(1)).map(_.citekey), List(A))
    assertEquals(s.sources.calibrePushMeta(B).map(g => (g.citekey, g.provenance)), Some((B, Some("calibre"))))
    assertEquals(s.sources.calibrePushMeta("zqnone"), None)
    assertEquals(s.sources.setCalibreId(A, "17", Me), Right(()))
    assert(s.sources.setCalibreId("zqnone", "18", Me).isLeft)
    assertEquals(s.sources.calibreGaps(None).map(_.citekey), List(C))
    assertEquals(s.sources.calibrePushMeta(A).flatMap(_.calibreId), Some("17"))
  }

class SourceServiceInMemorySuite extends SourceServiceContract with LibraryStoreSuite.InMemory
class SourceServiceDbSuite extends SourceServiceContract with LibraryStoreSuite.OnDatabase
