package com.histmersion
package domain.library.sources

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.util.problem.{Problems, Raised}
import com.histmersion.api.library.Citekey as ApiCitekey

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
import com.histmersion.domain.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Title
import com.histmersion.api.library.AuthorName
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.domain.library.sources.referrers.SourceReferrers
import com.histmersion.api.text.WikiLinks
import com.histmersion.api.library.Doi
import com.histmersion.domain.Licence
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** What the library's sources answer, the same on the database and in memory: a source written whole and in part, its
  * findings kept by id, its edges routed and promoted, its body read by part, renamed with its old citekey kept and its
  * links followed, deleted with what goes counted.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
abstract class SourceServiceContract extends LibraryStoreSuite:

  private val Me = Caller.Local

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private def api(c: Citekey) = com.histmersion.api.library.Citekey(c.value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def finding(ck: Citekey, n: Int, claim: Finding.Claim, at: Option[GeoPoint] = None): Finding =
    Finding(
      Finding.Id(s"$ck#$n"),
      api(ck),
      claim,
      Some(Finding.Region("Zqland")),
      at,
      Some(Period(Year(-1500), Year(-1400))),
      None,
      List(Finding.Tags("zq")),
      Some(Confidence.High)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  protected def source(ck: Citekey, title: Title, findings: List[Finding] = Nil, edges: List[Edge] = Nil): Source =
    Source(
      api(ck),
      title,
      List(AuthorName("Zqauthor, Ann")),
      None,
      Some(Year(1990)),
      Some(Source.Venue("Zq Review")),
      Source.Status.Current,
      List(Source.Topics("bronze age")),
      List(Source.Regions("Zqland")),
      List(Source.Tags("zq-oracle")),
      Some(Period(Year(-1700), Year(-1100))),
      Some(Source.Summary(s"A study of $title.")),
      None,
      None,
      None,
      None,
      Some(
        Source.OcrMarkdown(s"# $title\n\n## Walls\n\nThe walls of $title.\n\n## Harbour\n\nThe harbour of $title.\n")
      ),
      findings,
      edges,
      Some(Source.Notes(s"Notes on $title."))
    )

  private val A = Citekey("zqa1990_walls")
  private val B = Citekey("zqb2001_cove")
  private val C = Citekey("zqc1975_kings")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val ShaA = Sha256.applyUnsafe("a" * 64)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val ShaB = Sha256.applyUnsafe("b" * 64)

  stest("a source put and read back: its record, its findings in id order, its edges where their targets are") { s =>
    val token = s.sources.put(
      source(
        A,
        Title("Walls"),
        List(
          finding(A, 1, Finding.Claim("the walls")),
          finding(A, 0, Finding.Claim("a gate"), Some(GeoPoint(Longitude(34.6), Latitude(40.0))))
        ),
        List(
          Edge(api(A), api(B), Edge.Kind.References),
          Edge(api(A), com.histmersion.api.library.Citekey("zq_article"), Edge.Kind.References)
        )
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
    assertEquals(
      s.sources.load(A).get.edges,
      List(
        Edge(api(A), com.histmersion.api.library.Citekey("zq_article"), Edge.Kind.References),
        Edge(api(A), api(B), Edge.Kind.References)
      )
    )
    assertEquals(
      s.citations.neighbors(A, Nil).map(r => (r.citekey.value: String, r.target)),
      List(("zq_article", NeighborTarget.Unresolved), (B.value, NeighborTarget.Unresolved))
    )
    // the target arrives: its parked edge becomes an edge between two sources
    val _ = s.sources.put(source(B, Title("Cove")), Me)
    assertEquals(
      s.citations.neighbors(A, Nil).map(r => (r.direction, r.citekey.value: String, r.target)),
      List(("out", "zq_article", NeighborTarget.Unresolved), ("out", B.value, NeighborTarget.Source))
    )
    assertEquals(s.citations.neighbors(B, Nil).map(r => (r.direction, r.citekey.value: String)), List(("in", A)))
    // a put again keeps the findings' uids by id
    val uid0 = rows.head.uid
    val _ = s.sources.put(source(A, Title("Walls"), List(finding(A, 0, Finding.Claim("a gate, again")))), Me)
    assertEquals(s.sources.get(A).get._2.map(r => (r.claim, r.uid)), List(("a gate, again", uid0)))
    assertEquals(s.sources.citekeys, List(A, B))
    assertEquals(s.sources.refs(List(B, A, Citekey("zqnone2000_x"))).map(_.citekey.value: String), List(A, B))
    assertEquals(s.sources.get(Citekey("zqnone2000_x")), None)
  }

  stest("a patch: what it names changes, the rest stays; a stale token and an unknown source are refused") { s =>
    val token = s.sources.put(
      source(
        A,
        Title("Walls"),
        List(finding(A, 0, Finding.Claim("a gate")), finding(A, 1, Finding.Claim("the walls")))
      ),
      Me
    )
    val patched = s.sources.patch(
      A,
      Source.Patch(
        metadata = Some(MetadataPatch(title = Some(Title("Walls, revised")), clear = List(MetadataField.Venue))),
        tags = Some(ListPatch(add = List(ListPatch.Add("zq-new")), remove = List(ListPatch.Remove("zq-oracle")))),
        findings = Some(
          FindingsPatch(
            add = List(Finding.Input(Finding.Claim("a tower"))),
            remove = List(FindingsPatch.Remove(s"$A#0"))
          )
        ),
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
      s.sources
        .patch(A, Source.Patch(summary = Some(Source.Summary("x")), ifUpdatedAt = Some(token)), Me)
        .left
        .map(_.code),
      Left(Some(Write.CodePrecondition))
    )
    assertEquals(
      s.sources.patch(Citekey("zqnone2000_x"), Source.Patch(summary = Some(Source.Summary("x"))), Me).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
  }

  stest("the body by part: its chunks, its table of contents, a heading, the text") { s =>
    val _ = s.sources.put(source(A, Title("Walls")), Me)
    val chunks = s.sources.bodyChunks(A)
    assert(chunks.size >= 2, chunks.toString)
    assertEquals(s.sources.toc(A).map(_.heading.value: String), chunks.flatMap(_.heading).distinct)
    assert(s.sources.bodyText(A).value.contains("The walls of Walls."))
    val last = s.sources.toc(A).last.heading
    assert(
      s.sources
        .bodyUnderHeading(A, SourceService.Heading(last.value.take(4).toUpperCase))
        .value
        .contains("The harbour of Walls."),
      last
    )
    assertEquals(s.sources.bodyChunks(A).map(_.ord), s.sources.bodyChunks(A).map(_.ord).sortBy[Int](_.value))
    assert(s.sources.markdown(A).value.startsWith("# Walls"))
    assertEquals(s.sources.bodyText(Citekey("zqnone2000_x")), "")
    assertEquals(s.sources.bodyChunks(Citekey("zqnone2000_x")), Nil)
  }

  stest("a rename: the findings renumbered, the old citekey kept, links in other sources' notes and edges followed") {
    s =>
      val _ = s.sources.put(source(B, Title("Cove")), Me)
      val _ = s.sources.put(
        source(
          A,
          Title("Walls"),
          List(finding(A, 0, Finding.Claim("a gate"))),
          List(Edge(api(A), api(B), Edge.Kind.Refutes))
        ),
        Me
      )
      val _ =
        s.sources.put(
          source(
            C,
            Title("Kings"),
            edges = List(
              Edge(api(C), api(A), Edge.Kind.Challenges),
              Edge(api(C), com.histmersion.api.library.Citekey("zqa1991_walls"), Edge.Kind.Cites)
            )
          ),
          Me
        )
      val _ = s.sources.patch(B, Source.Patch(notes = Some(Source.Notes(s"See [[$A|the walls]] and [[zqa1990]]."))), Me)
      assertEquals(s.sources.rename(A, Citekey("zqa1990_walls"), Me).left.map(_.code), Left(Some(Write.CodeInvalid)))
      assertEquals(s.sources.rename(A, B, Me).left.map(_.code), Left(Some(Write.CodeInvalid)))
      assertEquals(s.sources.rename(A, Citekey("zqwalls"), Me).left.map(_.code), Left(Some(Write.CodeInvalid)))
      assertEquals(
        s.sources.rename(Citekey("zqnone2000_x"), Citekey("zqnone2001_x"), Me).left.map(_.code),
        Left(Some(Write.CodeNotFound))
      )
      val counts = s.sources.rename(A, Citekey("zqa1991_walls"), Me).toOption.get
      assertEquals(
        (counts.findings, counts.sources.map(_.value: String), counts.links, counts.columns, counts.edgesPromoted),
        (1, List(B), 2, Nil, 1)
      )
      assertEquals(s.sources.current(A), Some("zqa1991_walls"))
      assertEquals(s.sources.aliasOf(A), Some("zqa1991_walls"))
      assertEquals(
        s.sources.aliases,
        List(Source.Alias(Source.Alias.Value(A.value), com.histmersion.api.library.Citekey("zqa1991_walls")))
      )
      assertEquals(s.sources.load(Citekey("zqa1991_walls")).get.findings.map(_.id), List("zqa1991_walls#0"))
      assertEquals(s.sources.load(B).get.notes, Some("See [[zqa1991_walls|the walls]] and [[zqa1991_walls]]."))
      assertEquals(
        s.citations
          .neighbors(Citekey("zqa1991_walls"), Nil)
          .map(r => (r.direction, Edge.Kind.wireOf(r.kind), r.citekey.value: String)),
        List(("in", "challenges", C), ("in", "cites", C), ("out", "refutes", B))
      )
      assertEquals(s.sources.rename(A, Citekey("zqx2000_x"), Me).left.map(_.code), Left(Some(Write.CodeInvalid)))
      // renamed back: the alias goes
      assert(s.sources.rename(Citekey("zqa1991_walls"), A, Me).isRight)
      assertEquals(s.sources.aliases, List(Source.Alias(Source.Alias.Value("zqa1991_walls"), api(A))))
  }

  stest("a delete: refused for an old citekey and an unknown source; what goes counted, the edges to it parked") { s =>
    val _ = s.sources.put(source(B, Title("Cove"), edges = List(Edge(api(B), api(A), Edge.Kind.References))), Me)
    val _ =
      s.sources.put(
        source(
          A,
          Title("Walls"),
          List(finding(A, 0, Finding.Claim("a gate"))),
          List(Edge(api(A), com.histmersion.api.library.Citekey("zqnot2020_yet"), Edge.Kind.Cites))
        ),
        Me
      )
    val _ = s.sources.rename(A, Citekey("zqa1991_walls"), Me)
    assertEquals(s.sources.deletion(A).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(s.sources.deletion(Citekey("zqnone2000_x")).left.map(_.code), Left(Some(Write.CodeNotFound)))
    val plan = s.sources.deletion(Citekey("zqa1991_walls")).toOption.get
    assertEquals(
      plan.deletion.counts.map(c => (c.part, c.rows)),
      List(
        DeletePart.Findings -> 1,
        DeletePart.ParkedOut -> 1,
        DeletePart.EdgesIn -> 1,
        DeletePart.Chunks -> s.sources.bodyChunks(Citekey("zqa1991_walls")).size,
        DeletePart.Aliases -> 1
      )
    )
    assertEquals(
      (plan.deletion.aliases.map(_.value: String), plan.deletion.edgesIn, plan.keys.map(_.value: String)),
      (
        List(A),
        List(Edge(api(B), com.histmersion.api.library.Citekey("zqa1991_walls"), Edge.Kind.References)),
        List("zqa1991_walls", A.value)
      )
    )
    val done =
      s.sources
        .delete(
          Citekey("zqa1991_walls"),
          SourceReferrers.Day("2026-11-01"),
          p => List(Source.Deletion.Files(s"file-of-${p.deletion.citekey}")),
          Me
        )
        .toOption
        .get
    assertEquals(
      (done.deletion.applied, done.deletion.files, done.deletion.logEntry),
      (true, List("file-of-zqa1991_walls"), None)
    )
    assertEquals(s.sources.load(Citekey("zqa1991_walls")), None)
    assertEquals(s.sources.current(A), None)
    assertEquals(
      s.sources.load(B).get.edges,
      List(Edge(api(B), com.histmersion.api.library.Citekey("zqa1991_walls"), Edge.Kind.References))
    )
    assertEquals(s.citations.neighbors(B, Nil).map(_.target), List(NeighborTarget.Unresolved))
  }

  stest("links resolved: the citekey, an old one, the one source a short form fits; parked edges promoted by it") { s =>
    val _ = s.sources.put(source(A, Title("Walls")), Me)
    val _ = s.sources.put(source(Citekey("zqa1990_gates"), Title("Gates")), Me)
    val _ =
      s.sources.put(
        source(
          B,
          Title("Cove"),
          edges = List(
            Edge(api(B), com.histmersion.api.library.Citekey("zqb2001"), Edge.Kind.References),
            Edge(api(B), com.histmersion.api.library.Citekey("zqa1990"), Edge.Kind.Cites)
          )
        ),
        Me
      )
    val _ = s.sources.put(source(C, Title("Kings")), Me)
    val _ = s.sources.rename(C, Citekey("zqc1976_kings"), Me)
    assertEquals(
      s.sources
        .resolveLinks(
          List(
            WikiLinks.Occurrence.Target(A.value),
            WikiLinks.Occurrence.Target("ZQA1990_WALLS "),
            WikiLinks.Occurrence.Target("zqa1990"),
            WikiLinks.Occurrence.Target("zqb2001"),
            WikiLinks.Occurrence.Target(C.value),
            WikiLinks.Occurrence.Target("zqnone"),
            WikiLinks.Occurrence.Target("")
          )
        )
        .map((target, citekey) => (target.value: String) -> (citekey.value: String)),
      Map[String, String](
        A.value -> A.value,
        "zqa1990_walls" -> A.value,
        "zqb2001" -> B.value,
        C.value -> "zqc1976_kings"
      )
    )
    assertEquals(s.sources.resolveEdges(Me), 0) // a self-edge by a short form stays parked, an ambiguous one too
    val _ = s.sources.put(
      source(
        Citekey("zqd2005_new"),
        Title("New"),
        edges = List(
          Edge(
            com.histmersion.api.library.Citekey("zqd2005_new"),
            com.histmersion.api.library.Citekey("zqc1976"),
            Edge.Kind.References
          )
        )
      ),
      Me
    )
    assertEquals(s.sources.resolveEdges(Me), 1)
    assertEquals(
      s.citations.neighbors(Citekey("zqc1976_kings"), Nil).map(r => (r.direction, r.citekey)),
      List(("in", "zqd2005_new"))
    )
  }

  stest("what acquisition reads of the sources: existence, the DOI index, edges routed and counted") { s =>
    val _ = s.sources.put(source(A, Title("Walls")).modify(_.doi).setTo(Some(Doi("10.5555/ZQ.Walls"))), Me)
    val _ = s.sources.put(source(B, Title("Cove")), Me)
    assert(s.sources.exists(A))
    assert(!s.sources.exists(Citekey("zqnone")))
    assertEquals(s.sources.doiIndex, Map("10.5555/zq.walls" -> A))
    val edges = List(
      Edge(api(A), api(B), Edge.Kind.References),
      Edge(api(A), com.histmersion.api.library.Citekey("zq_not_yet"), Edge.Kind.References),
      Edge(api(A), api(B), Edge.Kind.References)
    )
    assertEquals(s.sources.addEdges(edges, Me), 2)
    assertEquals(s.sources.addEdges(edges, Me), 0, "an edge stored already is not new")
    assertEquals(s.sources.addEdges(Nil, Me), 0)
    assertEquals(s.sources.load(A).map(_.edges.map(_.dst.value: String).sorted), Some(List("zq_not_yet", B.value)))
  }

  stest("the citekey of a DOI: case and spaces ignored, the first by citekey of two holders, none for an unknown one") {
    s =>
      val _ = s.sources.put(source(B, Title("Cove")).modify(_.doi).setTo(Some(Doi("10.5555/ZQ.Both"))), Me)
      val _ = s.sources.put(source(A, Title("Walls")).modify(_.doi).setTo(Some(Doi("10.5555/zq.both"))), Me)
      assertEquals(
        s.sources.citekeyOfDoi(domain.library.Doi.assume(" 10.5555/zq.BOTH ")),
        Some(List(A, B).min)
      )
      assertEquals(s.sources.citekeyOfDoi(domain.library.Doi("10.5555/zq.none")), None)
  }

  stest("a source's stored original: its digest, who holds one, set once a conversion is written") { s =>
    val _ = s.sources.put(source(A, Title("Walls")), Me)
    val _ = s.sources.put(source(B, Title("Cove")), Me)
    assertEquals(s.sources.original(A), None)
    s.sources.setOriginal(A, ShaA, Me)
    s.sources.setOriginal(Citekey("zqnone"), ShaB, Me)
    assertEquals(s.sources.original(A), Some("a" * 64))
    assertEquals(s.sources.holderOf(ShaA), Some(A))
    assertEquals(s.sources.holderOf(ShaB), None)
    assertEquals(s.sources.originals(List(A, B, Citekey("zqnone"))), Map(A -> "a" * 64))
    assertEquals(s.sources.originals(Nil), Map.empty)
    val _ = s.sources.put(source(A, Title("Walls again")), Me)
    assertEquals(s.sources.original(A), Some("a" * 64), "a put keeps the original")
  }

  stest("a source's licence: recorded when it has none, classified as it is read") { s =>
    val _ = s.sources.put(source(A, Title("Walls")), Me)
    assertEquals(s.sources.licence(A), None)
    assert(!s.sources.recordLicence(A, Licence.Id("  "), Me), "a blank licence is never written")
    assert(s.sources.recordLicence(A, Licence.Id(" CC BY 4.0 "), Me))
    assert(!s.sources.recordLicence(A, Licence.Id("CC0-1.0"), Me), "the first one is kept")
    assertEquals(s.sources.licence(A), Some(Licence.classify(Licence.Id("CC BY 4.0"))))
    assert(!s.sources.recordLicence(Citekey("zqnone"), Licence.Id("CC0-1.0"), Me))
  }

  stest("the sources not in Calibre yet, a push's metadata, the Calibre id written back") { s =>
    val _ =
      s.sources.put(source(A, Title("Walls")).modify(_.provenance).setTo(Some(Source.Provenance("unpaywall"))), Me)
    val _ = s.sources.put(source(B, Title("Cove")).modify(_.provenance).setTo(Some(Source.Provenance("calibre"))), Me)
    val _ = s.sources.put(source(C, Title("Kings")), Me)
    assertEquals(s.sources.calibreGaps(None).map(_.citekey.value: String), List(A, C))
    assertEquals(s.sources.calibreGaps(Some(PageSize(1))).map(_.citekey.value: String), List(A))
    assertEquals(
      s.sources.calibrePushMeta(B).map(g => (g.citekey.value: String, g.provenance)),
      Some((B, Some("calibre")))
    )
    assertEquals(s.sources.calibrePushMeta(Citekey("zqnone")), None)
    assertEquals(s.sources.setCalibreId(A, FullSourceRow.CalibreId("17"), Me), Right(()))
    assert(s.sources.setCalibreId(Citekey("zqnone"), FullSourceRow.CalibreId("18"), Me).isLeft)
    assertEquals(s.sources.calibreGaps(None).map(_.citekey.value: String), List(C))
    assertEquals(s.sources.calibrePushMeta(A).flatMap(_.calibreId), Some("17"))
  }

class SourceServiceInMemorySuite extends SourceServiceContract with LibraryStoreSuite.InMemory:

  stest("a source handed with a citekey that is none is refused with the citekey's problem, and nothing is written") {
    s =>
      val bad = source(Citekey("zqbad1990_x"), Title("Bad")).modify(_.citekey).setTo(ApiCitekey("Not a citekey"))
      val refused = intercept[Raised](s.sources.put(bad, Caller.Local))
      assertEquals[Problems, Problems](refused.problems, Citekey.parse("Not a citekey").swap.toOption.get)
      assertEquals(s.sources.citekeys, Nil)
  }

class SourceServiceDbSuite extends SourceServiceContract with LibraryStoreSuite.OnDatabase
