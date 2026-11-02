package com.histmersion.api

import com.histmersion.api.library.SearchMarkdown
import com.histmersion.api.library.SourcesMarkdown
import com.histmersion.api.research.BacklinksMarkdown

import com.histmersion.values.SourceStatus
import com.histmersion.domain.library.Confidence
import com.histmersion.api.library.{
  FindingRow,
  Hit,
  NeighborRow,
  NeighborTarget,
  SearchHit,
  SearchResult,
  SourceDetail,
  TimeBucket
}
import com.histmersion.api.research.{ArticleMarkdown, Backlink}

/** [[SourcesMarkdown.source]] — what MCP `get_source` returns and what the HTML article page renders. Pure. */
class MarkdownSuite extends munit.FunSuite:

  private val detail = SourceDetail(
    citekey = "cline2014_collapse",
    title = "1177 BC",
    authors = List("Eric H. Cline"),
    year = Some(2014),
    venue = Some("PUP"),
    status = SourceStatus.Current,
    topics = List("collapse"),
    regions = List("Levant"),
    tags = List("sea-peoples"),
    periodStart = Some(-1300),
    periodEnd = Some(-1100),
    summary = Some("Systems collapse."),
    doi = Some("10.1/x"),
    ocrMarkdown = Some("BODY TEXT")
  )

  private val finding = FindingRow(
    "Ugarit destroyed",
    Some("Ugarit"),
    Some(35.78),
    Some(35.6),
    Some(-1200),
    Some(-1180),
    None,
    List("destruction"),
    Some(Confidence.High)
  )

  test("notes are rendered under a Notes heading, after the summary and before the findings"):
    val md =
      SourcesMarkdown.source(detail.copy(notes = Some("  Our article about it.  ")), Vector(finding), fullText = false)
    assert(md.contains("## Notes\n\nOur article about it."), md)
    assert(md.indexOf("Systems collapse.") < md.indexOf("## Notes"), md)
    assert(md.indexOf("## Notes") < md.indexOf("## Findings (1)"), md)

  test("no notes column, no heading"):
    val md = SourcesMarkdown.source(detail, Vector(finding), fullText = false)
    assert(!md.contains("## Notes"), md)
    assertEquals(SourcesMarkdown.source(detail.copy(notes = Some("   ")), Vector.empty, false).contains("Notes"), false)

  test("notes come before the full text, which stays last"):
    val md = SourcesMarkdown.source(detail.copy(notes = Some("N")), Vector.empty, fullText = true)
    assert(md.indexOf("## Notes") < md.indexOf("BODY TEXT"), md)

  test("the header still carries the metadata, topics, regions and DOI"):
    val md = SourcesMarkdown.source(detail.copy(notes = Some("N")), Vector.empty, fullText = false)
    assert(md.contains("# 1177 BC"), md)
    assert(md.contains("[Eric H. Cline](/#author=Eric%20H.%20Cline)"), md)
    assert(md.contains("2014 · PUP · current · 1300 BCE–1100 BCE"), md)
    // item 4: metadata is now a set of links back into the web UI, not prose
    assert(md.contains("**Topics:** [collapse](/#topic=collapse)"), md)
    assert(md.contains("**DOI:** 10.1/x"), md)

  test("searchResults(SearchResult) renders the FTS snippet quote line with its heading"):
    val hit = SearchHit(
      "k1_a",
      "T",
      Some(2020),
      SourceStatus.Current,
      Nil,
      Nil,
      None,
      None,
      Some("sum"),
      snippet = Some("…the **Uluburun** wreck…"),
      matchedHeading = Some("CHAPTER 3")
    )
    val md = SearchMarkdown.searchResults(SearchResult(1, List(Hit(hit, Nil, List("full text matches 'x'")))))
    assert(md.contains("> _CHAPTER 3_ — …the **Uluburun** wreck…"), md)

  // ── paging footer (A6) ────────────────────────────────────────────────────────────────────────

  test("pagingFooter shows next and full set when the page does not cover the whole result"):
    val f = Markdown.pagingFooter(OffsetPage(54, 0, 20))
    assert(f.contains("showing 1–20 of 54"), f)
    assert(f.contains("next: offset=20"), f)
    assert(f.contains("full set: limit=54"), f)

  test("pagingFooter is empty when everything fits on one page"):
    assertEquals(Markdown.pagingFooter(OffsetPage(10, 0, 10)), "")

  test("pagingFooter omits next when showing the last page"):
    val f = Markdown.pagingFooter(OffsetPage(54, 40, 14))
    assert(f.contains("showing 41–54 of 54"), f)
    assert(!f.contains("next:"), f)

  test("searchResults(SearchResult) includes paging footer when total > hits"):
    val hits = (1 to 3).map { i =>
      val sh = SearchHit(s"k$i", s"T$i", Some(2020), SourceStatus.Current, Nil, Nil, None, None, None)
      Hit(sh, Nil, Nil)
    }.toList
    val md = SearchMarkdown.searchResults(SearchResult(50, hits))
    assert(md.contains("showing 1–3 of 50"), md)
    assert(md.contains("next: offset=3"), md)

  test("searchResults: the footer follows the page's own offset — every page, not just the first (#1798)"):
    val hits = (1 to 3).map { i =>
      Hit(SearchHit(s"k$i", s"T$i", Some(2020), SourceStatus.Current, Nil, Nil, None, None, None), Nil, Nil)
    }.toList
    val md = SearchMarkdown.searchResults(SearchResult.of(50, hits, 20))
    assert(md.startsWith("### 3 of 50 source(s)"), md)
    assert(md.contains("showing 21–23 of 50 — next: offset=23"), md)
    assertEquals(SearchResult.of(50, hits, 20).next, Some(23))
    assertEquals(SearchResult.of(23, hits, 20).next, None)

  test("searchResults(SearchResult) omits paging footer when everything fits"):
    val hits = (1 to 3).map { i =>
      val sh = SearchHit(s"k$i", s"T$i", Some(2020), SourceStatus.Current, Nil, Nil, None, None, None)
      Hit(sh, Nil, Nil)
    }.toList
    val md = SearchMarkdown.searchResults(SearchResult(3, hits))
    assert(!md.contains("showing"), md)

  test("missing year is rendered as n.d."):
    val md = SourcesMarkdown.source(detail.copy(year = None), Vector.empty, false)
    assert(md.contains("n.d."), md)
    assert(!md.contains("?"), md)

  // ── histogram collapse (A16) ──────────────────────────────────────────────────────────────────

  test("collapseHistogram leaves short histograms unchanged"):
    val buckets = (1 to 5).map(i => TimeBucket(i * 100L, i * 100 + 99, i)).toList
    val result = SearchMarkdown.collapseHistogram(buckets)
    assertEquals(result.size, 5)
    assert(result.forall(_.merged == 1))

  test("collapseHistogram collapses runs of identical counts when > 12 rows"):
    val buckets = (1 to 20).map(i => TimeBucket(i * 100L, i * 100 + 99, if i <= 10 then 2 else 5)).toList
    val result = SearchMarkdown.collapseHistogram(buckets)
    assert(result.sizeIs < 20, s"expected fewer rows, got ${result.size}")
    assertEquals(result.head.count, 2)
    assertEquals(result.head.merged, 10)
    assertEquals(result(1).count, 5)
    assertEquals(result(1).merged, 10)

/** The source page's two-way hops (UX item 4) and the article's de-duplicated footer (item 23). */
class MarkdownHopsSuite extends munit.FunSuite:

  private val src = SourceDetail(
    citekey = "x2020_y",
    title = "T",
    authors = List("A"),
    year = Some(2020),
    venue = None,
    status = SourceStatus.Current,
    topics = List("steppe", "migration"),
    regions = List("anatolia"),
    tags = List("sea_peoples"),
    periodStart = Some(-1500),
    periodEnd = Some(-1200),
    summary = None,
    doi = None,
    ocrMarkdown = None
  )

  test("chipLinks points at the web UI hash keys the UI actually reads"):
    assertEquals(
      Markdown.chipLinks("steppe, migration", "topic"),
      "[steppe](/#topic=steppe), [migration](/#topic=migration)"
    )

  test("chipLinks encodes a value that needs it"):
    assert(Markdown.chipLinks("near east", "tag").contains("(/#tag=near%20east)"))

  test("topics, regions AND tags are all rendered, each as its own chip key"):
    val md = SourcesMarkdown.source(src, Vector.empty, fullText = false)
    assert(md.contains("**Topics:** [steppe](/#topic=steppe)"), md)
    assert(md.contains("**Regions:** [anatolia](/#place=anatolia)"), md)
    assert(md.contains("**Tags:** [sea peoples](/#tag=sea_peoples)"), md)

  test("Mentioned in articles lists the wiki pages that cite the source"):
    val out = BacklinksMarkdown.sourceArticles(Vector(Backlink("article", "sea_peoples", Some("The Sea Peoples"))))
    assert(out.contains("## Mentioned in articles (1)"), out)
    assert(out.contains("[The Sea Peoples](/article/sea_peoples)"), out)
    assertEquals(BacklinksMarkdown.sourceArticles(Vector.empty), "")

  test("neighbors renders each row by its classified target: source, article, unresolved"):
    val rows = Vector(
      NeighborRow("out", "challenges", "cline2014_year", Some("1177 BC"), Some(SourceStatus.Current)),
      NeighborRow("in", "references", "drews1993_end", Some("The End of the Bronze Age")),
      NeighborRow(
        "out",
        "updates",
        "bronze_age_collapse",
        Some("Bronze Age collapse"),
        target = NeighborTarget.Article
      ),
      NeighborRow("out", "refutes", "ghost2019_absent", None, target = NeighborTarget.Unresolved)
    )
    val out = SourcesMarkdown.neighbors("x2020_y", rows)
    assert(out.contains("**Outgoing (this → other)**\n- _challenges_ → [[cline2014_year]] — 1177 BC _(current)_"), out)
    assert(out.contains("**Incoming (other → this)**\n- _references_ → [[drews1993_end]]"), out)
    assert(out.contains("[Bronze Age collapse](/article/bronze_age_collapse)"), out)
    assert(out.contains("**Not in the corpus yet**\n- _refutes_ → **ghost2019_absent**"), out)
    // an article or unresolved row is never rendered as a source wikilink
    assert(!out.contains("[[bronze_age_collapse]]") && !out.contains("[[ghost2019_absent]]"), out)

  test("stripSourcesSection removes a TRAILING hand-written list, not a mid-article section"):
    val body = "# T\n\nprose\n\n## Sources\n\n- [[a2015_x]]\n- [[b2016_y]]\n"
    assertEquals(ArticleMarkdown.stripSourcesSection(body).trim, "# T\n\nprose")
    val mid = "# T\n\n## Sources\n\nA paragraph arguing about sources.\n\n## Later\n\nmore\n"
    assertEquals(ArticleMarkdown.stripSourcesSection(mid), mid)
    assertEquals(ArticleMarkdown.stripSourcesSection("# T\n\nno list here\n"), "# T\n\nno list here\n")
