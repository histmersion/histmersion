package com.histmersion.core

/** Pure tests for the patch-merge (plan §1.9a). No database: `applyPatch` is a total function of
  * (existing, patch), which is exactly why the merge rules live in `core`. */
class WriteSuite extends munit.FunSuite:

  private val ck = "haak2015_massive"

  private def finding(n: Int, claim: String) =
    Finding(Write.findingId(ck, n), ck, claim, Some("Anatolia"), Some(GeoPoint(32.0, 39.0)),
      Some(Period(-2000, -1500)), Some("EBA"), List("steppe"), Some(Confidence.High))

  private val existing = Source(
    citekey = ck, title = "Massive migration", authors = List("Haak", "Reich"), doi = Some("10.1038/x"),
    year = Some(2015), venue = Some("Nature"), status = SourceStatus.Foundational,
    topics = List("ancient_dna"), regions = List("Eurasian Steppe"), tags = List("adna", "yamnaya"),
    period = Some(Period(-8000, -900)), summary = Some("original summary"), citedByCount = Some(2038),
    influentialCitations = None, provenance = Some("unpaywall"), calibreId = None,
    ocrMarkdown = Some("the whole paper"), findings = List(finding(0, "first"), finding(1, "second")),
    edges = List(Edge(ck, "anthony2007_horse", "references")), notes = Some("# article"),
    updatedAt = Some("2026-08-25T10:00:00.000000Z"))

  // ── absent parts are untouched ───────────────────────────────────────────────────────────────
  test("a tags-only patch leaves findings, body, notes and everything else untouched"):
    val out = Write.applyPatch(existing, SourcePatch(tags = Some(ListPatch(add = List("sea-peoples"))))).toOption.get
    assertEquals(out.tags, List("adna", "yamnaya", "sea-peoples"))
    assertEquals(out.findings, existing.findings)
    assertEquals(out.ocrMarkdown, existing.ocrMarkdown)
    assertEquals(out.notes, existing.notes)
    assertEquals(out.summary, existing.summary)
    assertEquals(out.edges, existing.edges)
    assertEquals(out.copy(tags = existing.tags), existing) // nothing else moved at all

  test("an empty patch is the identity"):
    assertEquals(Write.applyPatch(existing, SourcePatch()), Right(existing))

  // ── tags: add / remove / replace ─────────────────────────────────────────────────────────────
  test("tags: remove drops, add appends, both dedupe and keep order"):
    val p   = ListPatch(add = List("sea-peoples", "adna"), remove = List("yamnaya"))
    val out = Write.applyList(existing.tags, p)
    assertEquals(out, List("adna", "sea-peoples"))

  test("tags: replace is the new baseline, then remove and add apply to it"):
    val p = ListPatch(add = List("c"), remove = List("b"), replace = Some(List("a", "b")))
    assertEquals(Write.applyList(existing.tags, p), List("a", "c"))

  test("tags: removing something that was also added removes it (remove wins)"):
    assertEquals(Write.applyList(List("x"), ListPatch(add = List("y"), remove = List("y"))), List("x"))

  // ── findings: ids are stable and distinct ────────────────────────────────────────────────────
  test("findings: two successive adds get two distinct ids"):
    val add   = FindingsPatch(add = List(FindingInput(claim = "third")))
    val once  = Write.applyPatch(existing, SourcePatch(findings = Some(add))).toOption.get
    val twice = Write.applyPatch(once, SourcePatch(findings = Some(FindingsPatch(add = List(FindingInput(claim = "fourth")))))).toOption.get
    assertEquals(once.findings.map(_.id), List(s"$ck#0", s"$ck#1", s"$ck#2"))
    assertEquals(twice.findings.map(_.id), List(s"$ck#0", s"$ck#1", s"$ck#2", s"$ck#3"))
    assertEquals(twice.findings.map(_.claim), List("first", "second", "third", "fourth"))

  test("findings: an add reuses the lowest FREE id after a removal, never a duplicate"):
    val gapped = Write.applyPatch(existing, SourcePatch(findings = Some(FindingsPatch(remove = List(s"$ck#0"))))).toOption.get
    assertEquals(gapped.findings.map(_.id), List(s"$ck#1"))
    val refilled = Write.applyPatch(gapped, SourcePatch(findings = Some(FindingsPatch(add = List(FindingInput("new")))))).toOption.get
    assertEquals(refilled.findings.map(_.id), List(s"$ck#1", s"$ck#0"))
    assertEquals(refilled.findings.map(_.id).distinct.size, 2)

  test("findings: several adds in ONE patch still get distinct ids"):
    val p   = FindingsPatch(add = List(FindingInput("a"), FindingInput("b"), FindingInput("c")))
    val out = Write.applyPatch(existing, SourcePatch(findings = Some(p))).toOption.get
    assertEquals(out.findings.map(_.id), List(s"$ck#0", s"$ck#1", s"$ck#2", s"$ck#3", s"$ck#4"))

  test("findings: replace renumbers from 0 and drops the old ones"):
    val p   = FindingsPatch(replace = Some(List(FindingInput("only one"))))
    val out = Write.applyPatch(existing, SourcePatch(findings = Some(p))).toOption.get
    assertEquals(out.findings.map(_.id), List(s"$ck#0"))
    assertEquals(out.findings.map(_.claim), List("only one"))

  test("findings: remove of an unknown id is a no-op, not an error"):
    val out = Write.applyPatch(existing, SourcePatch(findings = Some(FindingsPatch(remove = List("nope#9")))))
    assertEquals(out.map(_.findings), Right(existing.findings))

  test("findings: geo needs BOTH lon and lat; period needs both bounds"):
    val p   = FindingsPatch(add = List(FindingInput("half", lat = Some(39.0), periodStart = None, periodEnd = None)))
    val out = Write.applyPatch(existing, SourcePatch(findings = Some(p))).toOption.get
    assertEquals(out.findings.last.geo, None)
    assertEquals(out.findings.last.period, None)

  test("findings: a half-specified period is rejected with a readable message"):
    val p = FindingsPatch(add = List(FindingInput("bad", periodStart = Some(-1000))))
    val e = Write.applyPatch(existing, SourcePatch(findings = Some(p))).swap.toOption.get
    assert(e.contains("period_start"), e)

  // ── metadata / scalars ───────────────────────────────────────────────────────────────────────
  test("metadata: present fields replace, absent fields are left alone"):
    val p   = SourcePatch(
      metadata = Some(MetadataPatch(status = Some(SourceStatus.Superseded), year = Some(2016))),
      edges = Some(EdgesPatch(add = List(Edge("src", "newer2020_source", "superseded_by")))))
    val out = Write.applyPatch(existing, p).toOption.get
    assertEquals(out.status, SourceStatus.Superseded)
    assertEquals(out.year, Some(2016))
    assertEquals(out.title, existing.title)
    assertEquals(out.authors, existing.authors)
    assertEquals(out.doi, existing.doi)

  test("summary / body / notes / topics / regions each replace only themselves"):
    val out = Write.applyPatch(existing, SourcePatch(notes = Some("# rewritten"))).toOption.get
    assertEquals(out.notes, Some("# rewritten"))
    assertEquals(out.summary, existing.summary)
    assertEquals(out.ocrMarkdown, existing.ocrMarkdown)
    assertEquals(out.topics, existing.topics)

  test("period: replaced when present, and an inverted range is rejected"):
    val ok = Write.applyPatch(existing, SourcePatch(period = Some(PeriodInput(-3300, -2600)))).toOption.get
    assertEquals(ok.period, Some(Period(-3300, -2600)))
    assert(Write.applyPatch(existing, SourcePatch(period = Some(PeriodInput(-100, -900)))).isLeft)

  // ── edges ────────────────────────────────────────────────────────────────────────────────────
  test("edges: add and remove, always rooted at this citekey, deduped"):
    val p = EdgesPatch(
      add = List(Edge("whatever", "cline2014_1177", "supersedes"), Edge(ck, "anthony2007_horse", "references")),
      remove = List(Edge(ck, "anthony2007_horse", "references")))
    val out = Write.applyEdges(ck, existing.edges, p)
    assertEquals(out, List(Edge(ck, "cline2014_1177", "supersedes")))
    assert(out.forall(_.src == ck))

  // ── ifUpdatedAt precondition ─────────────────────────────────────────────────────────────────
  test("ifUpdatedAt: matching token passes, stale token fails with an actionable message"):
    assertEquals(Write.precondition(existing, SourcePatch(ifUpdatedAt = existing.updatedAt)), Right(()))
    val e = Write.precondition(existing, SourcePatch(ifUpdatedAt = Some("2026-01-01T00:00:00.000000Z"))).swap.toOption.get
    assert(e.contains("has moved on"), e)
    assert(e.contains(ck), e)

  test("ifUpdatedAt: absent means last-write-wins, and a stale one blocks the whole patch"):
    assert(Write.applyPatch(existing, SourcePatch(tags = Some(ListPatch(add = List("x"))))).isRight)
    val stale = SourcePatch(tags = Some(ListPatch(add = List("x"))), ifUpdatedAt = Some("nope"))
    assert(Write.applyPatch(existing, stale).isLeft)

  // ── put ──────────────────────────────────────────────────────────────────────────────────────
  test("put: numbers findings from 0, roots edges at the citekey, defaults status to unassessed"):
    val in  = SourcePut(title = "T", findings = List(FindingInput("a"), FindingInput("b")),
      edges = List(Edge("bogus", "x", "references")))
    val out = Write.applyPut(ck, in).toOption.get
    assertEquals(out.findings.map(_.id), List(s"$ck#0", s"$ck#1"))
    assertEquals(out.edges, List(Edge(ck, "x", "references")))
    assertEquals(out.status, SourceStatus.Unassessed)

  test("put: SourcePut.of round-trips a source through applyPut"):
    val out = Write.applyPut(ck, SourcePut.of(existing)).toOption.get
    assertEquals(out, existing.copy(updatedAt = None))

  // ── period bounds must stay inside the representable window ──────────────────────────────────

  test("a period bound outside [Earliest, Latest] is a 400, not a stored sentinel"):
    val put = SourcePut(title = "T", periodStart = Some(Long.MinValue + 1), periodEnd = Some(-1200))
    val e   = Write.applyPut("zz_range", put).swap.toOption.get
    assert(e.contains("outside the representable range"), e)
    assert(Write.applyPut("zz_range",
      SourcePut(title = "T", periodStart = Some(-1200), periodEnd = Some(Long.MaxValue))).isLeft)

  test("the sentinels themselves are the boundary, and are allowed"):
    assert(Write.applyPut("zz_range",
      SourcePut(title = "T", periodStart = Some(Filter.Earliest), periodEnd = Some(Filter.Latest)))
      .isRight)

  test("ordinary deep time still passes"):
    assertEquals(
      Write.applyPut("zz_range", SourcePut(title = "T", periodStart = Some(-3300),
        periodEnd = Some(-1200))).map(_.period),
      Right(Some(Period(-3300, -1200))))

  test("a finding's period is range-checked too"):
    val put = SourcePut(title = "T", findings = List(
      FindingInput(claim = "c", periodStart = Some(-1200), periodEnd = Some(Long.MaxValue))))
    assert(Write.applyPut("zz_range", put).isLeft)

  // #395: status was a free String — an unknown value silently became "unassessed"
  test("a status in a write must be one the store knows; the wire spelling round-trips"):
    import io.circe.parser.decode
    import io.circe.syntax.*
    assertEquals(decode[SourcePatch]("""{"metadata":{"status":"superseded"}}""").map(_.metadata.flatMap(_.status)),
      Right(Some(SourceStatus.Superseded)))
    assert(decode[SourcePatch]("""{"metadata":{"status":"supersceded"}}""").isLeft, "a misspelling is refused")
    assert(decode[SourcePut]("""{"title":"T","status":"bogus"}""").isLeft)
    assertEquals(SourcePut("T", status = Some(SourceStatus.Contested)).asJson.noSpaces.contains("\"status\":\"contested\""), true)

  // a split left a volume record with doi = "" — neither a DOI nor "none"
  test("a blank DOI or venue is stored as none; in a patch, \"\" clears the field"):
    import io.circe.parser.decode
    val put = Write.applyPut("zz_blank", SourcePut("T", doi = Some("  "), venue = Some("")))
    assertEquals(put.map(s => (s.doi, s.venue)), Right((None, None)))
    val existing = put.toOption.get.copy(doi = Some("10.1/x"), venue = Some("V"))
    val cleared = decode[SourcePatch]("""{"metadata":{"doi":"","venue":" "}}""").toOption.get
    assertEquals(Write.applyPatch(existing, cleared).map(s => (s.doi, s.venue)), Right((None, None)))
    val kept = decode[SourcePatch]("""{"metadata":{"title":"T2"}}""").toOption.get
    assertEquals(Write.applyPatch(existing, kept).map(s => (s.doi, s.venue)), Right((Some("10.1/x"), Some("V"))))

