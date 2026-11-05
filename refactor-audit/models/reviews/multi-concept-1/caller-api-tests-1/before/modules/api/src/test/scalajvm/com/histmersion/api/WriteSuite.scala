package com.histmersion.api

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.Confidence
import com.histmersion.api.automation.Queue
import com.histmersion.api.catalogue.{EventPatch, Period}
import com.histmersion.api.library.{
  Edge,
  EdgesPatch,
  Filter,
  Finding,
  FindingsPatch,
  MetadataField,
  MetadataPatch,
  Source
}
import com.histmersion.api.automation.Goal
import com.histmersion.api.catalogue.Attestation
import com.histmersion.api.research.Article

/** Pure tests for the patch-merge (plan §1.9a). No database: `applyPatch` is a total function of (existing, patch),
  * which is exactly why the merge rules live in `core`.
  */
class WriteSuite extends munit.FunSuite:

  private val ck = "haak2015_massive"

  private def finding(n: Int, claim: String) =
    Finding(
      Source.Write.findingId(com.histmersion.api.library.Citekey(ck), n),
      com.histmersion.api.library.Citekey(ck),
      claim,
      Some("Anatolia"),
      Some(GeoPoint(32.0, 39.0)),
      Some(Period(-2000, -1500)),
      Some("EBA"),
      List("steppe"),
      Some(Confidence.High)
    )

  private val existing = Source(
    citekey = com.histmersion.api.library.Citekey(ck),
    title = com.histmersion.api.library.Title("Massive migration"),
    authors = (List("Haak", "Reich")).map(com.histmersion.api.library.AuthorName.apply),
    doi = (Some("10.1038/x")).map(com.histmersion.api.library.Doi.apply),
    year = (Some(2015)).map(com.histmersion.api.catalogue.Year.apply),
    venue = (Some("Nature")).map(com.histmersion.api.library.Venue.apply),
    status = com.histmersion.api.library.Source.Status.Foundational,
    topics = List("ancient_dna"),
    regions = List("Eurasian Steppe"),
    tags = List("adna", "yamnaya"),
    period = Some(Period(-8000, -900)),
    summary = Some("original summary"),
    citedByCount = Some(2038),
    influentialCitations = None,
    provenance = Some("unpaywall"),
    calibreId = None,
    ocrMarkdown = Some("the whole paper"),
    findings = List(finding(0, "first"), finding(1, "second")),
    edges = List(Edge(com.histmersion.api.library.Citekey(ck), com.histmersion.api.library.Citekey("anthony2007_horse"), "references")),
    notes = Some("# article"),
    updatedAt = Some("2026-08-25T10:00:00.000000Z")
  )

  // ── absent parts are untouched ───────────────────────────────────────────────────────────────
  test("a tags-only patch leaves findings, body, notes and everything else untouched"):
    val out =
      Source.Write.applyPatch(existing, Source.Patch(tags = Some(ListPatch(add = List("sea-peoples"))))).toOption.get
    assertEquals(out.tags, List("adna", "yamnaya", "sea-peoples"))
    assertEquals(out.findings, existing.findings)
    assertEquals(out.ocrMarkdown, existing.ocrMarkdown)
    assertEquals(out.notes, existing.notes)
    assertEquals(out.summary, existing.summary)
    assertEquals(out.edges, existing.edges)
    assertEquals(out.modify(_.tags).setTo(existing.tags), existing) // nothing else moved at all

  test("an empty patch is the identity"):
    assertEquals(Source.Write.applyPatch(existing, Source.Patch()), Right(existing))

  // ── tags: add / remove / replace ─────────────────────────────────────────────────────────────
  test("tags: remove drops, add appends, both dedupe and keep order"):
    val p = ListPatch(add = List("sea-peoples", "adna"), remove = List("yamnaya"))
    val out = Write.applyList(existing.tags, p)
    assertEquals(out, List("adna", "sea-peoples"))

  test("tags: replace is the new baseline, then remove and add apply to it"):
    val p = ListPatch(add = List("c"), remove = List("b"), replace = Some(List("a", "b")))
    assertEquals(Write.applyList(existing.tags, p), List("a", "c"))

  test("tags: removing something that was also added removes it (remove wins)"):
    assertEquals(Write.applyList(List("x"), ListPatch(add = List("y"), remove = List("y"))), List("x"))

  // ── findings: ids are stable and distinct ────────────────────────────────────────────────────
  test("findings: two successive adds get two distinct ids"):
    val add = FindingsPatch(add = List(Finding.Input(claim = "third")))
    val once = Source.Write.applyPatch(existing, Source.Patch(findings = Some(add))).toOption.get
    val twice = Source.Write
      .applyPatch(once, Source.Patch(findings = Some(FindingsPatch(add = List(Finding.Input(claim = "fourth"))))))
      .toOption
      .get
    assertEquals(once.findings.map(_.id), List(s"$ck#0", s"$ck#1", s"$ck#2"))
    assertEquals(twice.findings.map(_.id), List(s"$ck#0", s"$ck#1", s"$ck#2", s"$ck#3"))
    assertEquals(twice.findings.map(_.claim), List("first", "second", "third", "fourth"))

  test("findings: an add reuses the lowest FREE id after a removal, never a duplicate"):
    val gapped =
      Source.Write
        .applyPatch(existing, Source.Patch(findings = Some(FindingsPatch(remove = List(s"$ck#0")))))
        .toOption
        .get
    assertEquals(gapped.findings.map(_.id), List(s"$ck#1"))
    val refilled = Source.Write
      .applyPatch(gapped, Source.Patch(findings = Some(FindingsPatch(add = List(Finding.Input("new"))))))
      .toOption
      .get
    assertEquals(refilled.findings.map(_.id), List(s"$ck#1", s"$ck#0"))
    assertEquals(refilled.findings.map(_.id).distinct.size, 2)

  test("findings: several adds in ONE patch still get distinct ids"):
    val p = FindingsPatch(add = List(Finding.Input("a"), Finding.Input("b"), Finding.Input("c")))
    val out = Source.Write.applyPatch(existing, Source.Patch(findings = Some(p))).toOption.get
    assertEquals(out.findings.map(_.id), List(s"$ck#0", s"$ck#1", s"$ck#2", s"$ck#3", s"$ck#4"))

  test("findings: a confidence outside the vocabulary is refused, never dropped or read as high (#1798)"):
    val bad = FindingsPatch(add = List(Finding.Input("x", confidence = Some("certain"))))
    val r = Source.Write.applyPatch(existing, Source.Patch(findings = Some(bad)))
    assert(
      r.left.exists(m => m.contains("confidence 'certain'") && m.contains("high|medium|low|contested")),
      r.toString
    )
    val ok = Source.Write.applyPatch(
      existing,
      Source.Patch(findings =
        Some(
          FindingsPatch(add =
            List(Finding.Input("y", confidence = Some(" Medium ")), Finding.Input("z", confidence = Some("")))
          )
        )
      )
    )
    assertEquals(ok.map(_.findings.takeRight(2).map(_.confidence)), Right(List(Some(Confidence.Medium), None)))

  test("findings: replace renumbers from 0 and drops the old ones"):
    val p = FindingsPatch(replace = Some(List(Finding.Input("only one"))))
    val out = Source.Write.applyPatch(existing, Source.Patch(findings = Some(p))).toOption.get
    assertEquals(out.findings.map(_.id), List(s"$ck#0"))
    assertEquals(out.findings.map(_.claim), List("only one"))

  test("findings: remove of an unknown id is a no-op, not an error"):
    val out = Source.Write.applyPatch(existing, Source.Patch(findings = Some(FindingsPatch(remove = List("nope#9")))))
    assertEquals(out.map(_.findings), Right(existing.findings))

  test("findings: geo needs BOTH lon and lat; period needs both bounds"):
    val p = FindingsPatch(add = List(Finding.Input("half", lat = Some(39.0), periodStart = None, periodEnd = None)))
    val out = Source.Write.applyPatch(existing, Source.Patch(findings = Some(p))).toOption.get
    assertEquals(out.findings.last.geo, None)
    assertEquals(out.findings.last.period, None)

  test("findings: a half-specified period is rejected with a readable message"):
    val p = FindingsPatch(add = List(Finding.Input("bad", periodStart = Some(-1000))))
    val e = Source.Write.applyPatch(existing, Source.Patch(findings = Some(p))).swap.toOption.get
    assert(e.contains("period_start"), e)

  // ── metadata / scalars ───────────────────────────────────────────────────────────────────────
  test("metadata: present fields replace, absent fields are left alone"):
    val p = Source.Patch(
      metadata =
        Some(MetadataPatch(status = Some(com.histmersion.api.library.Source.Status.Superseded), year = (Some(2016)).map(com.histmersion.api.catalogue.Year.apply))),
      edges = Some(
        EdgesPatch(add = List(Edge.Input("newer2020_source", com.histmersion.api.library.Edge.Kind.SupersededBy)))
      )
    )
    val out = Source.Write.applyPatch(existing, p).toOption.get
    assertEquals(out.status, com.histmersion.api.library.Source.Status.Superseded)
    assertEquals(out.year, Some(2016))
    assertEquals(out.title, existing.title)
    assertEquals(out.authors, existing.authors)
    assertEquals(out.doi, existing.doi)

  test("summary / body / notes / topics / regions each replace only themselves"):
    val out = Source.Write.applyPatch(existing, Source.Patch(notes = Some("# rewritten"))).toOption.get
    assertEquals(out.notes, Some("# rewritten"))
    assertEquals(out.summary, existing.summary)
    assertEquals(out.ocrMarkdown, existing.ocrMarkdown)
    assertEquals(out.topics, existing.topics)

  test("period: replaced when present, and an inverted range is rejected"):
    val ok = Source.Write.applyPatch(existing, Source.Patch(period = Some(Period.Input(-3300, -2600)))).toOption.get
    assertEquals(ok.period, Some(Period(-3300, -2600)))
    assert(Source.Write.applyPatch(existing, Source.Patch(period = Some(Period.Input(-100, -900)))).isLeft)

  // ── edges ────────────────────────────────────────────────────────────────────────────────────
  test("edges: add and remove, always rooted at this citekey, deduped"):
    val p = EdgesPatch(
      add = List(
        Edge.Input("cline2014_1177", com.histmersion.api.library.Edge.Kind.Supersedes),
        Edge.Input("anthony2007_horse", com.histmersion.api.library.Edge.Kind.References, Some(ck))
      ),
      remove = List(Edge.Input("anthony2007_horse", com.histmersion.api.library.Edge.Kind.References))
    )
    val out = Source.Write.applyEdges(com.histmersion.api.library.Citekey(ck), existing.edges, p).toOption.get
    assertEquals(out, List(Edge(com.histmersion.api.library.Citekey(ck), com.histmersion.api.library.Citekey("cline2014_1177"), "supersedes")))
    assert(out.forall(_.src == ck))

  // #698: a src naming ANOTHER source was rewritten to the patched one — a critic → target edge
  // sent to the target's patch became target → target
  test("edges: a src other than the patched citekey is refused, never rewritten into a self-edge"):
    val json = """{"edges":{"add":[{"src":"kassian2024_rebuttal","dst":"haak2015_massive","kind":"refutes"}]}}"""
    val p = io.circe.parser.decode[Source.Patch](json).toOption.get
    val e = Source.Write.applyPatch(existing, p).swap.toOption.get
    assert(e.contains("src 'kassian2024_rebuttal' is not the patched source"), e)
    assert(e.contains("patch 'kassian2024_rebuttal'"), e)
    val rm = EdgesPatch(remove =
      List(Edge.Input("anthony2007_horse", com.histmersion.api.library.Edge.Kind.References, Some("other")))
    )
    assert(Source.Write.applyEdges(com.histmersion.api.library.Citekey(ck), existing.edges, rm).swap.toOption.get.contains("edges.remove"))

  test("edges: src is optional and defaults to the patched citekey; a self-edge is refused"):
    val p =
      io.circe.parser.decode[Source.Patch]("""{"edges":{"add":[{"dst":"x2020_y","kind":"challenges"}]}}""").toOption.get
    assertEquals(Source.Write.applyPatch(existing, p).map(_.edges.last), Right(Edge(com.histmersion.api.library.Citekey(ck), com.histmersion.api.library.Citekey("x2020_y"), "challenges")))
    val self = EdgesPatch(add = List(Edge.Input(ck, com.histmersion.api.library.Edge.Kind.Challenges)))
    assert(Source.Write.applyEdges(com.histmersion.api.library.Citekey(ck), existing.edges, self).isLeft)

  test("edges: an unknown kind is refused when the patch is decoded"):
    assert(io.circe.parser.decode[Source.Patch]("""{"edges":{"add":[{"dst":"a","kind":"disagrees"}]}}""").isLeft)

  test("edges: a src naming the OLD citekey of a renamed source counts as the source itself"):
    val p = Source.Patch(edges =
      Some(
        EdgesPatch(add = List(Edge.Input("b2020_x", com.histmersion.api.library.Edge.Kind.Refutes, Some("old_key"))))
      )
    )
    val fixed = Source.Write.srcAliased(p, "old_key", ck)
    assertEquals(Source.Write.applyPatch(existing, fixed).map(_.edges.last), Right(Edge(com.histmersion.api.library.Citekey(ck), com.histmersion.api.library.Citekey("b2020_x"), "refutes")))
    assertEquals(Source.Write.srcAliased(p, ck, ck), p)

  // ── ifUpdatedAt precondition ─────────────────────────────────────────────────────────────────
  test("ifUpdatedAt: matching token passes, stale token fails with an actionable message"):
    assertEquals(Source.Write.precondition(existing, Source.Patch(ifUpdatedAt = existing.updatedAt)), Right(()))
    val e =
      Source.Write
        .precondition(existing, Source.Patch(ifUpdatedAt = Some("2026-01-01T00:00:00.000000Z")))
        .swap
        .toOption
        .get
    assert(e.contains("has moved on"), e)
    assert(e.contains(ck), e)

  test("ifUpdatedAt: absent means last-write-wins, and a stale one blocks the whole patch"):
    assert(Source.Write.applyPatch(existing, Source.Patch(tags = Some(ListPatch(add = List("x"))))).isRight)
    val stale = Source.Patch(tags = Some(ListPatch(add = List("x"))), ifUpdatedAt = Some("nope"))
    assert(Source.Write.applyPatch(existing, stale).isLeft)

  // ── put ──────────────────────────────────────────────────────────────────────────────────────
  test("put: numbers findings from 0, roots edges at the citekey, defaults status to unassessed"):
    val in = Source.Put(
      title = com.histmersion.api.library.Title("T"),
      findings = List(Finding.Input("a"), Finding.Input("b")),
      edges = List(
        Edge.Input("x", com.histmersion.api.library.Edge.Kind.References),
        Edge.Input("y", com.histmersion.api.library.Edge.Kind.Refutes, Some(ck))
      )
    )
    val out = Source.Write.applyPut(com.histmersion.api.library.Citekey(ck), in).toOption.get
    assertEquals(out.findings.map(_.id), List(s"$ck#0", s"$ck#1"))
    assertEquals(out.edges, List(Edge(com.histmersion.api.library.Citekey(ck), com.histmersion.api.library.Citekey("x"), "references"), Edge(com.histmersion.api.library.Citekey(ck), com.histmersion.api.library.Citekey("y"), "refutes")))
    assertEquals(out.status, com.histmersion.api.library.Source.Status.Unassessed)

  // #766: put rewrote any edge src to the citekey, the pattern #698 fixed for patch only
  test("put: a src naming another source is refused, never rewritten"):
    val in = io.circe.parser
      .decode[Source.Put]("""{"title":"T","edges":[{"src":"kassian2024_rebuttal","dst":"x2020_y","kind":"refutes"}]}""")
      .toOption
      .get
    val e = Source.Write.applyPut(com.histmersion.api.library.Citekey(ck), in).swap.toOption.get
    assert(e.contains("src 'kassian2024_rebuttal' is not the source being put"), e)

  test("put: a self-edge is refused, including one to an old citekey of this source"):
    val self =
      Source.Put(title = com.histmersion.api.library.Title("T"), edges = List(Edge.Input(ck, com.histmersion.api.library.Edge.Kind.Challenges)))
    assert(Source.Write.applyPut(com.histmersion.api.library.Citekey(ck), self).swap.toOption.get.contains("self-edge"))
    val viaAlias =
      Source.Put(title = com.histmersion.api.library.Title("T"), edges = List(Edge.Input("old_key", com.histmersion.api.library.Edge.Kind.Challenges)))
    assert(Source.Write.applyPut(com.histmersion.api.library.Citekey(ck), viaAlias, (Set("old_key")).map(com.histmersion.api.library.Citekey.apply)).isLeft)

  test("put: a src naming an old citekey of this source is this source"):
    val in = Source.Put(
      title = com.histmersion.api.library.Title("T"),
      edges = List(Edge.Input("b2020_x", com.histmersion.api.library.Edge.Kind.Refutes, Some("old_key")))
    )
    assertEquals(
      Source.Write.applyPut(com.histmersion.api.library.Citekey(ck), in, (Set("old_key")).map(com.histmersion.api.library.Citekey.apply)).map(_.edges),
      Right(List(Edge(com.histmersion.api.library.Citekey(ck), com.histmersion.api.library.Citekey("b2020_x"), "refutes")))
    )
    assert(Source.Write.applyPut(com.histmersion.api.library.Citekey(ck), in).isLeft, "without the alias it is another source")

  test("put: SourcePut.of round-trips a source through applyPut"):
    val out = Source.Write.applyPut(com.histmersion.api.library.Citekey(ck), Source.Put.of(existing)).toOption.get
    assertEquals(out, existing.modify(_.updatedAt).setTo(None))

  // ── period bounds must stay inside the representable window ──────────────────────────────────

  test("a period bound outside [Earliest, Latest] is a 400, not a stored sentinel"):
    val put = Source.Put(title = com.histmersion.api.library.Title("T"), periodStart = Some(Long.MinValue + 1), periodEnd = Some(-1200))
    val e = Source.Write.applyPut(com.histmersion.api.library.Citekey("zz_range"), put).swap.toOption.get
    assert(e.contains("outside the representable range"), e)
    assert(
      Source.Write
        .applyPut(com.histmersion.api.library.Citekey("zz_range"), Source.Put(title = com.histmersion.api.library.Title("T"), periodStart = Some(-1200), periodEnd = Some(Long.MaxValue)))
        .isLeft
    )

  test("the sentinels themselves are the boundary, and are allowed"):
    assert(
      Source.Write
        .applyPut(
          com.histmersion.api.library.Citekey("zz_range"),
          Source.Put(title = com.histmersion.api.library.Title("T"), periodStart = Some(Filter.Earliest), periodEnd = Some(Filter.Latest))
        )
        .isRight
    )

  test("ordinary deep time still passes"):
    assertEquals(
      Source.Write
        .applyPut(com.histmersion.api.library.Citekey("zz_range"), Source.Put(title = com.histmersion.api.library.Title("T"), periodStart = Some(-3300), periodEnd = Some(-1200)))
        .map(_.period),
      Right(Some(Period(-3300, -1200)))
    )

  test("a finding's period is range-checked too"):
    val put = Source.Put(
      title = com.histmersion.api.library.Title("T"),
      findings = List(Finding.Input(claim = "c", periodStart = Some(-1200), periodEnd = Some(Long.MaxValue)))
    )
    assert(Source.Write.applyPut(com.histmersion.api.library.Citekey("zz_range"), put).isLeft)

  // #395: status was a free String — an unknown value silently became "unassessed"
  test("a status in a write must be one the store knows; the wire spelling round-trips"):
    import io.circe.parser.decode
    import io.circe.syntax.*
    assertEquals(
      decode[Source.Patch]("""{"metadata":{"status":"superseded"}}""").map(_.metadata.flatMap(_.status)),
      Right(Some(com.histmersion.api.library.Source.Status.Superseded))
    )
    assert(decode[Source.Patch]("""{"metadata":{"status":"supersceded"}}""").isLeft, "a misspelling is refused")
    assert(decode[Source.Put]("""{"title":"T","status":"bogus"}""").isLeft)
    assertEquals(
      Source
        .Put(com.histmersion.api.library.Title("T"), status = Some(com.histmersion.api.library.Source.Status.Contested))
        .asJson
        .noSpaces
        .contains("\"status\":\"contested\""),
      true
    )

  // a split left a volume record with doi = "" — neither a DOI nor "none"
  test("a blank DOI or venue is stored as none; in a patch, \"\" clears the field"):
    import io.circe.parser.decode
    val put = Source.Write.applyPut(com.histmersion.api.library.Citekey("zz_blank"), Source.Put(com.histmersion.api.library.Title("T"), doi = (Some("  ")).map(com.histmersion.api.library.Doi.apply), venue = (Some("")).map(com.histmersion.api.library.Venue.apply)))
    assertEquals(put.map(s => (s.doi, s.venue)), Right((None, None)))
    val existing = put.toOption.get.copy(doi = Some("10.1/x"), venue = Some("V"))
    val cleared = decode[Source.Patch]("""{"metadata":{"doi":"","venue":" "}}""").toOption.get
    assertEquals(Source.Write.applyPatch(existing, cleared).map(s => (s.doi, s.venue)), Right((None, None)))
    val kept = decode[Source.Patch]("""{"metadata":{"title":"T2"}}""").toOption.get
    assertEquals(Source.Write.applyPatch(existing, kept).map(s => (s.doi, s.venue)), Right((Some("10.1/x"), Some("V"))))

  // #745: {"doi":null} decoded to "absent" and was silently ignored
  test("metadata: an explicit null clears a nullable field; absent leaves it alone"):
    import io.circe.parser.decode
    val ex = existing.modify(_.calibreId).setTo(Some("1805"))
    val p = decode[Source.Patch](
      """{"metadata":{"doi":null,"venue":null,"year":null,"provenance":null,"calibreId":null}}"""
    ).toOption.get
    assertEquals(p.metadata.map(_.clear.toSet), Some(MetadataField.values.toSet))
    val out = Source.Write.applyPatch(ex, p).toOption.get
    assertEquals((out.doi, out.venue, out.year, out.provenance, out.calibreId), (None, None, None, None, None))
    val kept =
      Source.Write.applyPatch(ex, decode[Source.Patch]("""{"metadata":{"title":"T2"}}""").toOption.get).toOption.get
    assertEquals((kept.doi, kept.year, kept.calibreId), (ex.doi, ex.year, ex.calibreId))

  test("metadata: null on a field that cannot be cleared is refused, not ignored"):
    import io.circe.parser.decode
    val e = decode[Source.Patch]("""{"metadata":{"title":null}}""").swap.toOption.get
    assert(e.getMessage.contains("metadata.title cannot be cleared"), e.getMessage)

  test("metadata: a field both set and cleared is refused"):
    val p = Source.Patch(metadata = Some(MetadataPatch(doi = (Some("10.1/x")).map(com.histmersion.api.library.Doi.apply), clear = List(MetadataField.Doi))))
    assert(Source.Write.applyPatch(existing, p).swap.toOption.get.contains("both set and cleared"))

  test("metadata: the encoder omits absent fields, so a round trip never turns absent into a clear"):
    import io.circe.parser.decode
    import io.circe.syntax.*
    val m = MetadataPatch(title = (Some("T")).map(com.histmersion.api.library.Title.apply), clear = List(MetadataField.Doi))
    val json = Source.Patch(metadata = Some(m)).asJson.noSpaces
    assert(!json.contains("\"venue\""), json)
    assertEquals(decode[Source.Patch](json).map(_.metadata), Right(Some(m)))

  // #721: metadata.calibreId was not part of the patch at all
  test("metadata: calibreId re-links the source; it must be a Calibre entry number"):
    val p = Source.Patch(metadata = Some(MetadataPatch(calibreId = Some("1806"))))
    assertEquals(Source.Write.applyPatch(existing, p).map(_.calibreId), Right(Some("1806")))
    val bad = Source.Patch(metadata = Some(MetadataPatch(calibreId = Some("abc"))))
    assert(Source.Write.applyPatch(existing, bad).swap.toOption.get.contains("not a Calibre id"))

  // ── #813: an unknown or misplaced key is refused, naming the key and the expected shape ─────────
  test("strict: an unknown top-level key in a patch is refused, with the keys that are accepted"):
    import io.circe.parser.decode
    val e = decode[Source.Patch]("""{"tagz":{"add":["x"]},"summary":"s"}""").swap.toOption.get.getMessage
    assert(e.contains("unknown key 'tagz'"), e)
    assert(e.contains("expected only: metadata, summary, body, notes"), e)

  test("strict: a key that belongs in a nested part says where it goes"):
    import io.circe.parser.decode
    val e = decode[Source.Patch]("""{"status":"current","title":"T"}""").swap.toOption.get.getMessage
    assert(e.contains("unknown keys 'status', 'title'"), e)
    assert(e.contains("'status' belongs under 'metadata' (metadata.status)"), e)
    assert(e.contains("'title' belongs under 'metadata' (metadata.title)"), e)

  test("strict: nested parts are strict too, and the message carries the path"):
    import io.circe.parser.decode
    val tags = decode[Source.Patch]("""{"tags":{"items":["x"]}}""").swap.toOption.get.getMessage
    assert(tags.contains("tags: unknown key 'items'; expected only: add, remove, replace"), tags)
    val f = decode[Source.Patch]("""{"findings":{"add":[{"claim":"c","date":-1200}]}}""").swap.toOption.get.getMessage
    assert(f.contains("unknown key 'date'"), f)
    assert(f.contains("findings.add"), f)
    val meta = decode[Source.Patch]("""{"metadata":{"stauts":"current"}}""").swap.toOption.get.getMessage
    assert(meta.contains("metadata: unknown key 'stauts'"), meta)

  test("strict: an array where an object is expected names the expected shape"):
    import io.circe.parser.decode
    val e = decode[Source.Patch]("""{"tags":["a","b"]}""").swap.toOption.get.getMessage
    assert(e.contains("tags: expected an object with keys add, remove, replace; got an array"), e)

  test("strict: put, articles and the other write inputs refuse unknown keys"):
    import io.circe.parser.decode
    assert(decode[Source.Put]("""{"title":"T","citekey":"x"}""").isLeft)
    assert(decode[Source.Put]("""{"title":"T","edges":[{"dst":"a","kind":"refutes","weight":1}]}""").isLeft)
    assert(decode[Article.Patch]("""{"appendix":"x"}""").isLeft)
    assert(decode[Article.Put]("""{"title":"T","kind":"concept","body":"b","period":{"start":1,"end":2}}""").isLeft)
    assert(decode[Goal.Patch]("""{"state":"done"}""").isLeft)
    assert(decode[EventPatch]("""{"name":"x"}""").isLeft)
    assert(decode[Attestation.Patch]("""{"quoted":"x"}""").isLeft)
    assert(decode[Queue.QueuePatch]("""{"done":true}""").isLeft)

  test("strict: what the encoders write decodes again (the CLI and the web UI never send a refused key)"):
    import io.circe.parser.decode
    import io.circe.syntax.*
    val p = Source.Patch(
      metadata = Some(MetadataPatch(title = (Some("T")).map(com.histmersion.api.library.Title.apply), clear = List(MetadataField.Doi))),
      summary = Some("s"),
      tags = Some(ListPatch(add = List("a"), replace = Some(List("b")))),
      findings = Some(FindingsPatch(add = List(Finding.Input("c", lat = Some(1.0), lon = Some(2.0))))),
      edges = Some(EdgesPatch(add = List(Edge.Input("d", com.histmersion.api.library.Edge.Kind.Refutes)))),
      period = Some(Period.Input(-10, 10)),
      ifUpdatedAt = Some("t")
    )
    assertEquals(decode[Source.Patch](p.asJson.noSpaces), Right(p))
    val put = Source.Put.of(existing)
    assertEquals(decode[Source.Put](put.asJson.noSpaces), Right(put))
    val ap =
      Article.Patch(append = Some("x"), tags = Some(ListPatch(add = List("t"))), period = Some(Period.Input(1, 2)))
    assertEquals(decode[Article.Patch](ap.asJson.noSpaces), Right(ap))

  test("strict: metadata null-clears still work alongside the key check"):
    import io.circe.parser.decode
    assertEquals(
      decode[Source.Patch]("""{"metadata":{"doi":null}}""").map(_.metadata.map(_.clear)),
      Right(Some(List(MetadataField.Doi)))
    )
    assert(
      decode[Source.Patch]("""{"metadata":{"isbn":null}}""").swap.toOption.get.getMessage.contains("unknown key 'isbn'")
    )

  test("ApiError.hasCode selects an error output by its code, and nothing else (#829)"):
    val notFound = Api.Error.hasCode(Write.CodeNotFound)
    assert(notFound.isDefinedAt(Api.Error("no such source", Some(Write.CodeNotFound))))
    assert(!notFound.isDefinedAt(Api.Error("stale", Some(Write.CodePrecondition))))
    assert(!notFound.isDefinedAt(Api.Error("plain 400")))
    assert(!notFound.isDefinedAt("not an ApiError"))
