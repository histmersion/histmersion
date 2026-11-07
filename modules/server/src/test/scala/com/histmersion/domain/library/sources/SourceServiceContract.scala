package com.histmersion.domain.library.sources

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{GeoPoint, ListPatch, Write}
import com.histmersion.domain.Caller
import com.histmersion.domain.library.{LibraryStores, LibraryStoreSuite}
import com.histmersion.api.library.Confidence
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.{
  DeletePart,
  Edge,
  Finding,
  FindingsPatch,
  MetadataField,
  MetadataPatch,
  NeighborTarget,
  Source
}

/** What the library's sources answer, the same on the database and in memory: a source written whole and in part, its
  * findings kept by id, its edges routed and promoted, its body read by part, renamed with its old citekey kept and its
  * links followed, deleted with what goes counted.
  */
abstract class SourceServiceContract extends LibraryStoreSuite:

  private val Me = Caller.Local

  private def api(c: com.histmersion.domain.library.Citekey) = com.histmersion.api.library.Citekey(c.value)

  private def finding(ck: com.histmersion.domain.library.Citekey, n: Int, claim: Finding.Claim, at: Option[GeoPoint] = None): Finding =
    Finding(
      Finding.Id(s"$ck#$n"),
      api(ck),
      claim,
      Some(com.histmersion.api.library.Finding.Region("Zqland")),
      at,
      Some(Period(com.histmersion.api.catalogue.Year(-1500), com.histmersion.api.catalogue.Year(-1400))),
      None,
      List(com.histmersion.api.library.Finding.Tags("zq")),
      Some(Confidence.High)
    )

  private def source(ck: com.histmersion.domain.library.Citekey, title: com.histmersion.api.library.Title, findings: List[Finding] = Nil, edges: List[Edge] = Nil): Source =
    Source(
      api(ck),
      title,
      List(com.histmersion.api.library.AuthorName("Zqauthor, Ann")),
      None,
      Some(com.histmersion.api.catalogue.Year(1990)),
      Some(com.histmersion.api.library.Source.Venue("Zq Review")),
      com.histmersion.api.library.Source.Status.Current,
      List(com.histmersion.api.library.Source.Topics("bronze age")),
      List(com.histmersion.api.library.Source.Regions("Zqland")),
      List(com.histmersion.api.library.Source.Tags("zq-oracle")),
      Some(Period(com.histmersion.api.catalogue.Year(-1700), com.histmersion.api.catalogue.Year(-1100))),
      Some(Source.Summary(s"A study of $title.")),
      None,
      None,
      None,
      None,
      Some(Source.OcrMarkdown(s"# $title\n\n## Walls\n\nThe walls of $title.\n\n## Harbour\n\nThe harbour of $title.\n")),
      findings,
      edges,
      Some(Source.Notes(s"Notes on $title."))
    )

  private val A = com.histmersion.domain.library.Citekey("zqa1990_walls")
  private val B = com.histmersion.domain.library.Citekey("zqb2001_cove")
  private val C = com.histmersion.domain.library.Citekey("zqc1975_kings")
  private val ShaA = com.histmersion.Sha256.applyUnsafe("a" * 64)
  private val ShaB = com.histmersion.Sha256.applyUnsafe("b" * 64)

  stest("a source put and read back: its record, its findings in id order, its edges where their targets are") { s =>
    val token = s.sources.put(
      source(
        A,
        "Walls",
        List(finding(A, 1, Finding.Claim("the walls")), finding(A, 0, Finding.Claim("a gate"), Some(GeoPoint(com.histmersion.api.catalogue.Longitude(34.6), com.histmersion.api.catalogue.Latitude(40.0))))),
        List(Edge(api(A), api(B), Edge.Kind.References), Edge(api(A), com.histmersion.api.library.Citekey("zq_article"), Edge.Kind.References))
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
    assertEquals(s.sources.load(A).get.edges, List(Edge(api(A), com.histmersion.api.library.Citekey("zq_article"), Edge.Kind.References), Edge(api(A), api(B), Edge.Kind.References)))
    assertEquals(
      s.citations.neighbors(A, Nil).map(r => (r.citekey, r.target)),
      List(("zq_article", NeighborTarget.Unresolved), (B, NeighborTarget.Unresolved))
    )
    // the target arrives: its parked edge becomes an edge between two sources
    val _ = s.sources.put(source(B, com.histmersion.api.library.Title("Cove")), Me)
    assertEquals(
      s.citations.neighbors(A, Nil).map(r => (r.direction, r.citekey, r.target)),
      List(("out", "zq_article", NeighborTarget.Unresolved), ("out", B, NeighborTarget.Source))
    )
    assertEquals(s.citations.neighbors(B, Nil).map(r => (r.direction, r.citekey)), List(("in", A)))
    // a put again keeps the findings' uids by id
    val uid0 = rows.head.uid
    val _ = s.sources.put(source(A, com.histmersion.api.library.Title("Walls"), List(finding(A, 0, Finding.Claim("a gate, again")))), Me)
    assertEquals(s.sources.get(A).get._2.map(r => (r.claim, r.uid)), List(("a gate, again", uid0)))
    assertEquals(s.sources.citekeys, List(A, B))
    assertEquals(s.sources.refs(List(B, A, com.histmersion.domain.library.Citekey("zqnone2000_x"))).map(_.citekey), List(A, B))
    assertEquals(s.sources.get(com.histmersion.domain.library.Citekey("zqnone2000_x")), None)
  }

  stest("a patch: what it names changes, the rest stays; a stale token and an unknown source are refused") { s =>
    val token = s.sources.put(source(A, com.histmersion.api.library.Title("Walls"), List(finding(A, 0, Finding.Claim("a gate")), finding(A, 1, Finding.Claim("the walls")))), Me)
    val patched = s.sources.patch(
      A,
      Source.Patch(
        metadata = Some(MetadataPatch(title = Some(com.histmersion.api.library.Title("Walls, revised")), clear = List(MetadataField.Venue))),
        tags = Some(ListPatch(add = List(ListPatch.Add("zq-new")), remove = List(ListPatch.Remove("zq-oracle")))),
        findings = Some(FindingsPatch(add = List(Finding.Input(com.histmersion.api.library.Finding.Claim("a tower"))), remove = List(FindingsPatch.Remove(s"$A#0")))),
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
      s.sources.patch(A, Source.Patch(summary = Some(com.histmersion.api.library.Source.Summary("x")), ifUpdatedAt = Some(token)), Me).left.map(_.code),
      Left(Some(Write.CodePrecondition))
    )
    assertEquals(
      s.sources.patch(com.histmersion.domain.library.Citekey("zqnone2000_x"), Source.Patch(summary = Some(com.histmersion.api.library.Source.Summary("x"))), Me).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
  }

  stest("the body by part: its chunks, its table of contents, a heading, the text") { s =>
    val _ = s.sources.put(source(A, com.histmersion.api.library.Title("Walls")), Me)
    val chunks = s.sources.bodyChunks(A)
    assert(chunks.size >= 2, chunks.toString)
    assertEquals(s.sources.toc(A).map(_.heading), chunks.flatMap(_.heading).distinct)
    assert(s.sources.bodyText(A).value.contains("The walls of Walls."))
    val last = s.sources.toc(A).last.heading
    assert(s.sources.bodyUnderHeading(A, SourceService.Heading(last.value.take(4).toUpperCase)).value.contains("The harbour of Walls."), last)
    assertEquals(s.sources.bodyChunks(A).map(_.ord), s.sources.bodyChunks(A).map(_.ord).sortBy(_.value))
    assert(s.sources.markdown(A).value.startsWith("# Walls"))
    assertEquals(s.sources.bodyText(com.histmersion.domain.library.Citekey("zqnone2000_x")), "")
    assertEquals(s.sources.bodyChunks(com.histmersion.domain.library.Citekey("zqnone2000_x")), Nil)
  }

  stest("a rename: the findings renumbered, the old citekey kept, links in other sources' notes and edges followed") {
    s =>
      val _ = s.sources.put(source(B, com.histmersion.api.library.Title("Cove")), Me)
      val _ = s.sources.put(source(A, com.histmersion.api.library.Title("Walls"), List(finding(A, 0, Finding.Claim("a gate"))), List(Edge(api(A), api(B), Edge.Kind.Refutes))), Me)
      val _ =
        s.sources.put(source(C, com.histmersion.api.library.Title("Kings"), edges = List(Edge(api(C), api(A), Edge.Kind.Challenges), Edge(api(C), com.histmersion.api.library.Citekey("zqa1991_walls"), Edge.Kind.Cites))), Me)
      val _ = s.sources.patch(B, Source.Patch(notes = Some(Source.Notes(s"See [[$A|the walls]] and [[zqa1990]]."))), Me)
      assertEquals(s.sources.rename(A, com.histmersion.domain.library.Citekey("zqa1990_walls"), Me).left.map(_.code), Left(Some(Write.CodeInvalid)))
      assertEquals(s.sources.rename(A, B, Me).left.map(_.code), Left(Some(Write.CodeInvalid)))
      assertEquals(s.sources.rename(A, com.histmersion.domain.library.Citekey("zqwalls"), Me).left.map(_.code), Left(Some(Write.CodeInvalid)))
      assertEquals(
        s.sources.rename(com.histmersion.domain.library.Citekey("zqnone2000_x"), com.histmersion.domain.library.Citekey("zqnone2001_x"), Me).left.map(_.code),
        Left(Some(Write.CodeNotFound))
      )
      val counts = s.sources.rename(A, com.histmersion.domain.library.Citekey("zqa1991_walls"), Me).toOption.get
      assertEquals(
        (counts.findings, counts.sources, counts.links, counts.columns, counts.edgesPromoted),
        (1, List(B), 2, Nil, 1)
      )
      assertEquals(s.sources.current(A), Some("zqa1991_walls"))
      assertEquals(s.sources.aliasOf(A), Some("zqa1991_walls"))
      assertEquals(s.sources.aliases, List(Source.Alias(Source.Alias.Value(A.value), com.histmersion.api.library.Citekey("zqa1991_walls"))))
      assertEquals(s.sources.load(com.histmersion.domain.library.Citekey("zqa1991_walls")).get.findings.map(_.id), List("zqa1991_walls#0"))
      assertEquals(s.sources.load(B).get.notes, Some("See [[zqa1991_walls|the walls]] and [[zqa1991_walls]]."))
      assertEquals(
        s.citations.neighbors(com.histmersion.domain.library.Citekey("zqa1991_walls"), Nil).map(r => (r.direction, Edge.Kind.wireOf(r.kind), r.citekey)),
        List(("in", "challenges", C), ("in", "cites", C), ("out", "refutes", B))
      )
      assertEquals(s.sources.rename(A, com.histmersion.domain.library.Citekey("zqx2000_x"), Me).left.map(_.code), Left(Some(Write.CodeInvalid)))
      // renamed back: the alias goes
      assert(s.sources.rename(com.histmersion.domain.library.Citekey("zqa1991_walls"), A, Me).isRight)
      assertEquals(s.sources.aliases, List(Source.Alias(Source.Alias.Value("zqa1991_walls"), api(A))))
  }

  stest("a delete: refused for an old citekey and an unknown source; what goes counted, the edges to it parked") { s =>
    val _ = s.sources.put(source(B, com.histmersion.api.library.Title("Cove"), edges = List(Edge(api(B), api(A), Edge.Kind.References))), Me)
    val _ =
      s.sources.put(source(A, com.histmersion.api.library.Title("Walls"), List(finding(A, 0, Finding.Claim("a gate"))), List(Edge(api(A), com.histmersion.api.library.Citekey("zqnot2020_yet"), Edge.Kind.Cites))), Me)
    val _ = s.sources.rename(A, com.histmersion.domain.library.Citekey("zqa1991_walls"), Me)
    assertEquals(s.sources.deletion(A).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(s.sources.deletion(com.histmersion.domain.library.Citekey("zqnone2000_x")).left.map(_.code), Left(Some(Write.CodeNotFound)))
    val plan = s.sources.deletion(com.histmersion.domain.library.Citekey("zqa1991_walls")).toOption.get
    assertEquals(
      plan.deletion.counts.map(c => (c.part, c.rows)),
      List(
        DeletePart.Findings -> 1,
        DeletePart.ParkedOut -> 1,
        DeletePart.EdgesIn -> 1,
        DeletePart.Chunks -> s.sources.bodyChunks(com.histmersion.domain.library.Citekey("zqa1991_walls")).size,
        DeletePart.Aliases -> 1
      )
    )
    assertEquals(
      (plan.deletion.aliases, plan.deletion.edgesIn, plan.keys),
      (List(A), List(Edge(api(B), com.histmersion.api.library.Citekey("zqa1991_walls"), Edge.Kind.References)), List("zqa1991_walls", A))
    )
    val done =
      s.sources.delete(com.histmersion.domain.library.Citekey("zqa1991_walls"), com.histmersion.domain.library.sources.referrers.SourceReferrers.Day("2026-11-01"), p => List(Source.Deletion.Files(s"file-of-${p.deletion.citekey}")), Me).toOption.get
    assertEquals(
      (done.deletion.applied, done.deletion.files, done.deletion.logEntry),
      (true, List("file-of-zqa1991_walls"), None)
    )
    assertEquals(s.sources.load(com.histmersion.domain.library.Citekey("zqa1991_walls")), None)
    assertEquals(s.sources.current(A), None)
    assertEquals(s.sources.load(B).get.edges, List(Edge(api(B), com.histmersion.api.library.Citekey("zqa1991_walls"), Edge.Kind.References)))
    assertEquals(s.citations.neighbors(B, Nil).map(_.target), List(NeighborTarget.Unresolved))
  }

  stest("links resolved: the citekey, an old one, the one source a short form fits; parked edges promoted by it") { s =>
    val _ = s.sources.put(source(A, com.histmersion.api.library.Title("Walls")), Me)
    val _ = s.sources.put(source(com.histmersion.domain.library.Citekey("zqa1990_gates"), com.histmersion.api.library.Title("Gates")), Me)
    val _ =
      s.sources.put(source(B, com.histmersion.api.library.Title("Cove"), edges = List(Edge(api(B), com.histmersion.api.library.Citekey("zqb2001"), Edge.Kind.References), Edge(api(B), com.histmersion.api.library.Citekey("zqa1990"), Edge.Kind.Cites))), Me)
    val _ = s.sources.put(source(C, com.histmersion.api.library.Title("Kings")), Me)
    val _ = s.sources.rename(C, com.histmersion.domain.library.Citekey("zqc1976_kings"), Me)
    assertEquals(
      s.sources.resolveLinks(List(com.histmersion.api.text.WikiLinks.Occurrence.Target(A.value), com.histmersion.api.text.WikiLinks.Occurrence.Target("ZQA1990_WALLS "), com.histmersion.api.text.WikiLinks.Occurrence.Target("zqa1990"), com.histmersion.api.text.WikiLinks.Occurrence.Target("zqb2001"), com.histmersion.api.text.WikiLinks.Occurrence.Target(C.value), com.histmersion.api.text.WikiLinks.Occurrence.Target("zqnone"), com.histmersion.api.text.WikiLinks.Occurrence.Target(""))),
      Map(A -> A, "zqa1990_walls" -> A, "zqb2001" -> B, C -> "zqc1976_kings")
    )
    assertEquals(s.sources.resolveEdges(Me), 0) // a self-edge by a short form stays parked, an ambiguous one too
    val _ = s.sources.put(source(com.histmersion.domain.library.Citekey("zqd2005_new"), com.histmersion.api.library.Title("New"), edges = List(Edge(com.histmersion.api.library.Citekey("zqd2005_new"), com.histmersion.api.library.Citekey("zqc1976"), Edge.Kind.References))), Me)
    assertEquals(s.sources.resolveEdges(Me), 1)
    assertEquals(
      s.citations.neighbors(com.histmersion.domain.library.Citekey("zqc1976_kings"), Nil).map(r => (r.direction, r.citekey)),
      List(("in", "zqd2005_new"))
    )
  }

  stest("what acquisition reads of the sources: existence, the DOI index, edges routed and counted") { s =>
    val _ = s.sources.put(source(A, com.histmersion.api.library.Title("Walls")).modify(_.doi).setTo(Some(com.histmersion.api.library.Doi("10.5555/ZQ.Walls"))), Me)
    val _ = s.sources.put(source(B, com.histmersion.api.library.Title("Cove")), Me)
    assert(s.sources.exists(A))
    assert(!s.sources.exists(com.histmersion.domain.library.Citekey("zqnone")))
    assertEquals(s.sources.doiIndex, Map("10.5555/zq.walls" -> A))
    val edges = List(Edge(api(A), api(B), Edge.Kind.References), Edge(api(A), com.histmersion.api.library.Citekey("zq_not_yet"), Edge.Kind.References), Edge(api(A), api(B), Edge.Kind.References))
    assertEquals(s.sources.addEdges(edges, Me), 2)
    assertEquals(s.sources.addEdges(edges, Me), 0, "an edge stored already is not new")
    assertEquals(s.sources.addEdges(Nil, Me), 0)
    assertEquals(s.sources.load(A).map(_.edges.map(_.dst).sortBy(_.value)), Some(List("zq_not_yet", B)))
  }

  stest("the citekey of a DOI: case and spaces ignored, the first by citekey of two holders, none for an unknown one") {
    s =>
      val _ = s.sources.put(source(B, com.histmersion.api.library.Title("Cove")).modify(_.doi).setTo(Some(com.histmersion.api.library.Doi("10.5555/ZQ.Both"))), Me)
      val _ = s.sources.put(source(A, com.histmersion.api.library.Title("Walls")).modify(_.doi).setTo(Some(com.histmersion.api.library.Doi("10.5555/zq.both"))), Me)
      assertEquals(s.sources.citekeyOfDoi(com.histmersion.domain.library.Doi(" 10.5555/zq.BOTH ")), Some(List(A, B).minBy(_.value)))
      assertEquals(s.sources.citekeyOfDoi(com.histmersion.domain.library.Doi("10.5555/zq.none")), None)
  }

  stest("a source's stored original: its digest, who holds one, set once a conversion is written") { s =>
    val _ = s.sources.put(source(A, com.histmersion.api.library.Title("Walls")), Me)
    val _ = s.sources.put(source(B, com.histmersion.api.library.Title("Cove")), Me)
    assertEquals(s.sources.original(A), None)
    s.sources.setOriginal(A, ShaA, Me)
    s.sources.setOriginal(com.histmersion.domain.library.Citekey("zqnone"), ShaB, Me)
    assertEquals(s.sources.original(A), Some("a" * 64))
    assertEquals(s.sources.holderOf(ShaA), Some(A))
    assertEquals(s.sources.holderOf(ShaB), None)
    assertEquals(s.sources.originals(List(A, B, com.histmersion.domain.library.Citekey("zqnone"))), Map(A -> "a" * 64))
    assertEquals(s.sources.originals(Nil), Map.empty)
    val _ = s.sources.put(source(A, com.histmersion.api.library.Title("Walls again")), Me)
    assertEquals(s.sources.original(A), Some("a" * 64), "a put keeps the original")
  }

  stest("a source's licence: recorded when it has none, classified as it is read") { s =>
    val _ = s.sources.put(source(A, com.histmersion.api.library.Title("Walls")), Me)
    assertEquals(s.sources.licence(A), None)
    assert(!s.sources.recordLicence(A, com.histmersion.domain.Licence.Id("  "), Me), "a blank licence is never written")
    assert(s.sources.recordLicence(A, com.histmersion.domain.Licence.Id(" CC BY 4.0 "), Me))
    assert(!s.sources.recordLicence(A, com.histmersion.domain.Licence.Id("CC0-1.0"), Me), "the first one is kept")
    assertEquals(s.sources.licence(A), Some(com.histmersion.domain.Licence.classify(com.histmersion.domain.Licence.Id("CC BY 4.0"))))
    assert(!s.sources.recordLicence(com.histmersion.domain.library.Citekey("zqnone"), com.histmersion.domain.Licence.Id("CC0-1.0"), Me))
  }

  stest("the sources not in Calibre yet, a push's metadata, the Calibre id written back") { s =>
    val _ = s.sources.put(source(A, com.histmersion.api.library.Title("Walls")).modify(_.provenance).setTo(Some(Source.Provenance("unpaywall"))), Me)
    val _ = s.sources.put(source(B, com.histmersion.api.library.Title("Cove")).modify(_.provenance).setTo(Some(Source.Provenance("calibre"))), Me)
    val _ = s.sources.put(source(C, com.histmersion.api.library.Title("Kings")), Me)
    assertEquals(s.sources.calibreGaps(None).map(_.citekey), List(A, C))
    assertEquals(s.sources.calibreGaps(Some(com.histmersion.PageSize(1))).map(_.citekey), List(A))
    assertEquals(s.sources.calibrePushMeta(B).map(g => (g.citekey, g.provenance)), Some((B, Some("calibre"))))
    assertEquals(s.sources.calibrePushMeta(com.histmersion.domain.library.Citekey("zqnone")), None)
    assertEquals(s.sources.setCalibreId(A, com.histmersion.domain.library.sources.FullSourceRow.CalibreId("17"), Me), Right(()))
    assert(s.sources.setCalibreId(com.histmersion.domain.library.Citekey("zqnone"), com.histmersion.domain.library.sources.FullSourceRow.CalibreId("18"), Me).isLeft)
    assertEquals(s.sources.calibreGaps(None).map(_.citekey), List(C))
    assertEquals(s.sources.calibrePushMeta(A).flatMap(_.calibreId), Some("17"))
  }

class SourceServiceInMemorySuite extends SourceServiceContract with LibraryStoreSuite.InMemory
class SourceServiceDbSuite extends SourceServiceContract with LibraryStoreSuite.OnDatabase
