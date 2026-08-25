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
