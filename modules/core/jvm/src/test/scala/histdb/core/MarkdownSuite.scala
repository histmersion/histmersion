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
    // item 4: metadata is now a set of links back into the atlas, not prose
    assert(md.contains("**Topics:** [collapse](/#topic=collapse)"), md)
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

/** The source page's two-way hops (UX item 4) and the article's de-duplicated footer (item 23). */
class MarkdownHopsSuite extends munit.FunSuite:

  private val src = SourceDetail(
    citekey = "x2020_y", title = "T", authors = "A", year = Some(2020), venue = None,
    status = "current", topics = "steppe, migration", regions = "anatolia", tags = "sea_peoples",
    periodStart = Some(-1500), periodEnd = Some(-1200), summary = None, doi = None,
    ocrMarkdown = None)

  test("chipLinks points at the atlas hash keys the UI actually reads"):
    assertEquals(Markdown.chipLinks("steppe, migration", "topic"),
      "[steppe](/#topic=steppe), [migration](/#topic=migration)")

  test("chipLinks encodes a value that needs it"):
    assert(Markdown.chipLinks("near east", "tag").contains("(/#tag=near%20east)"))

  test("topics, regions AND tags are all rendered, each as its own chip key"):
    val md = Markdown.source(src, Vector.empty, fullText = false)
    assert(md.contains("**Topics:** [steppe](/#topic=steppe)"), md)
    assert(md.contains("**Regions:** [anatolia](/#place=anatolia)"), md)
    assert(md.contains("**Tags:** [sea peoples](/#tag=sea_peoples)"), md)

  test("Mentioned in articles lists the wiki pages that cite the source"):
    val out = Markdown.sourceArticles(Vector(Backlink("article", "sea_peoples", Some("The Sea Peoples"))))
    assert(out.contains("## Mentioned in articles (1)"), out)
    assert(out.contains("[The Sea Peoples](/article/sea_peoples)"), out)
    assertEquals(Markdown.sourceArticles(Vector.empty), "")

  test("an outgoing edge to a compiled article links to it; an unknown target stays plain"):
    val rows = Vector(
      NeighborRow("out", "references", "bronze_age_collapse", None),
      NeighborRow("out", "references", "nothing_here", None))
    val out = Markdown.neighbors("x2020_y", rows, Set("bronze_age_collapse").contains)
    assert(out.contains("[bronze_age_collapse](/article/bronze_age_collapse)"), out)
    assert(out.contains("**nothing_here**"), out)

  test("stripSourcesSection removes a TRAILING hand-written list, not a mid-article section"):
    val body = "# T\n\nprose\n\n## Sources\n\n- [[a2015_x]]\n- [[b2016_y]]\n"
    assertEquals(ArticleMarkdown.stripSourcesSection(body).trim, "# T\n\nprose")
    val mid = "# T\n\n## Sources\n\nA paragraph arguing about sources.\n\n## Later\n\nmore\n"
    assertEquals(ArticleMarkdown.stripSourcesSection(mid), mid)
    assertEquals(ArticleMarkdown.stripSourcesSection("# T\n\nno list here\n"), "# T\n\nno list here\n")
