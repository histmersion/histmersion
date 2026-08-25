package histdb.core

/** [[Markdown.source]] — what MCP `get_source` returns and what the HTML article page renders. Pure. */
class MarkdownSuite extends munit.FunSuite:

  private val detail = SourceDetail(
    citekey = "cline2014_collapse", title = "1177 BC", authors = "Eric H. Cline", year = Some(2014),
    venue = Some("PUP"), status = "current", topics = "collapse", regions = "Levant",
    tags = "sea-peoples", periodStart = Some(-1300), periodEnd = Some(-1100),
    summary = Some("Systems collapse."), doi = Some("10.1/x"), ocrMarkdown = Some("BODY TEXT"))

  private val finding = FindingRow("Ugarit destroyed", Some("Ugarit"), Some(35.78), Some(35.6),
    Some(-1200), Some(-1180), None, "destruction", Some("high"))

  test("notes are rendered under a Notes heading, after the summary and before the findings"):
    val md = Markdown.source(detail.copy(notes = Some("  Our article about it.  ")),
      Vector(finding), fullText = false)
    assert(md.contains("## Notes\n\nOur article about it."), md)
    assert(md.indexOf("Systems collapse.") < md.indexOf("## Notes"), md)
    assert(md.indexOf("## Notes") < md.indexOf("## Findings (1)"), md)

  test("no notes column, no heading"):
    val md = Markdown.source(detail, Vector(finding), fullText = false)
    assert(!md.contains("## Notes"), md)
    assertEquals(Markdown.source(detail.copy(notes = Some("   ")), Vector.empty, false).contains("Notes"), false)

  test("notes come before the full text, which stays last"):
    val md = Markdown.source(detail.copy(notes = Some("N")), Vector.empty, fullText = true)
    assert(md.indexOf("## Notes") < md.indexOf("BODY TEXT"), md)

  test("the header still carries the metadata, topics, regions and DOI"):
    val md = Markdown.source(detail.copy(notes = Some("N")), Vector.empty, fullText = false)
    assert(md.startsWith("# 1177 BC\n\n_Eric H. Cline · 2014 · PUP · current · 1300 BCE–1100 BCE_"), md)
    assert(md.contains("**Topics:** collapse"), md)
    assert(md.contains("**DOI:** 10.1/x"), md)

  test("searchResults(SearchResult) renders the FTS snippet quote line with its heading"):
    val hit = SearchHit("k1_a", "T", Some(2020), "current", "", "", None, None, Some("sum"),
      snippet = Some("…the **Uluburun** wreck…"), matchedHeading = Some("CHAPTER 3"))
    val md = Markdown.searchResults(SearchResult(1, List(Hit(hit, Nil, List("full text matches 'x'")))))
    assert(md.contains("> _CHAPTER 3_ — …the **Uluburun** wreck…"), md)

  // ── paging footer (A6) ────────────────────────────────────────────────────────────────────────

  test("pagingFooter shows next and full set when the page does not cover the whole result"):
    val f = Markdown.pagingFooter(1, 20, 54, 20)
    assert(f.contains("showing 1–20 of 54"), f)
    assert(f.contains("next: offset=20"), f)
    assert(f.contains("full set: limit=54"), f)

  test("pagingFooter is empty when everything fits on one page"):
    assertEquals(Markdown.pagingFooter(1, 10, 10, 10), "")

  test("pagingFooter omits next when showing the last page"):
    val f = Markdown.pagingFooter(41, 54, 54, 14)
    assert(f.contains("showing 41–54 of 54"), f)
    assert(!f.contains("next:"), f)

  test("searchResults(SearchResult) includes paging footer when total > hits"):
    val hits = (1 to 3).map { i =>
      val sh = SearchHit(s"k$i", s"T$i", Some(2020), "current", "", "", None, None, None)
      Hit(sh, Nil, Nil)
    }.toList
    val md = Markdown.searchResults(SearchResult(50, hits), offset = 0)
    assert(md.contains("showing 1–3 of 50"), md)
    assert(md.contains("next: offset=3"), md)

  test("searchResults(SearchResult) omits paging footer when everything fits"):
    val hits = (1 to 3).map { i =>
      val sh = SearchHit(s"k$i", s"T$i", Some(2020), "current", "", "", None, None, None)
      Hit(sh, Nil, Nil)
    }.toList
    val md = Markdown.searchResults(SearchResult(3, hits))
    assert(!md.contains("showing"), md)

  test("missing year is rendered as n.d."):
    val md = Markdown.source(detail.copy(year = None), Vector.empty, false)
    assert(md.contains("n.d."), md)
    assert(!md.contains("?"), md)

  // ── histogram collapse (A16) ──────────────────────────────────────────────────────────────────

  test("collapseHistogram leaves short histograms unchanged"):
    val buckets = (1 to 5).map(i => TimeBucket(i * 100L, i * 100 + 99, i)).toList
    val result = Markdown.collapseHistogram(buckets)
    assertEquals(result.size, 5)
    assert(result.forall(_.merged == 1))

  test("collapseHistogram collapses runs of identical counts when > 12 rows"):
    val buckets = (1 to 20).map(i => TimeBucket(i * 100L, i * 100 + 99, if i <= 10 then 2 else 5)).toList
    val result = Markdown.collapseHistogram(buckets)
    assert(result.sizeIs < 20, s"expected fewer rows, got ${result.size}")
    assertEquals(result.head.count, 2)
    assertEquals(result.head.merged, 10)
    assertEquals(result(1).count, 5)
    assertEquals(result(1).merged, 10)
