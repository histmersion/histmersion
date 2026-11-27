package com.histmersion
package api

import com.histmersion.util.problem.Problem
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.SearchMarkdown
import com.histmersion.api.library.SourcesMarkdown
import com.histmersion.api.research.BacklinksMarkdown

import com.histmersion.api.library.Confidence
import com.histmersion.api.library.{Hit, NeighborRow, NeighborTarget, SearchHit, SearchResult, TimeBucket}
import com.histmersion.api.research.{ArticleMarkdown, Backlink}
import com.histmersion.api.library.Source
import com.histmersion.api.library.Finding
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.library.AuthorName
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Doi
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.library.Search
import com.histmersion.api.personal.UnreadPage
import com.histmersion.api.library.Edge
import com.histmersion.api.research.Article
import com.histmersion.lint.{tolerated, LintRule}

/** [[SourcesMarkdown.source]] — what MCP `get_source` returns and what the HTML article page renders. Pure. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
class MarkdownSuite extends munit.FunSuite:

  private val detail = Source.Detail(
    citekey = Citekey("cline2014_collapse"),
    title = Title("1177 BC"),
    authors = (List("Eric H. Cline")).map(AuthorName.apply),
    year = (Some(2014)).map(Year.apply),
    venue = (Some("PUP")).map(Source.Venue.apply),
    status = Source.Status.Current,
    topics = List(Source.Topics("collapse")),
    regions = List(Source.Regions("Levant")),
    tags = List(Source.Tags("sea-peoples")),
    periodStart = Some(Year(-1300)),
    periodEnd = Some(Year(-1100)),
    summary = Some(Source.Summary("Systems collapse.")),
    doi = (Some("10.1/x")).map(Doi.apply),
    ocrMarkdown = Some(Source.OcrMarkdown("BODY TEXT"))
  )

  private val finding = Finding.Row(
    Finding.Claim("Ugarit destroyed"),
    Some(Place.Region("Ugarit")),
    Some(Longitude(35.78)),
    Some(Latitude(35.6)),
    Some(Year(-1200)),
    Some(Year(-1180)),
    None,
    List(Finding.Tags("destruction")),
    Some(Confidence.High)
  )

  test("notes are rendered under a Notes heading, after the summary and before the findings"):
    val md =
      SourcesMarkdown.source(
        detail.modify(_.notes).setTo(Some(Source.Notes("  Our article about it.  "))),
        Vector(finding),
        fullText = false
      )
    assert(md.contains("## Notes\n\nOur article about it."), md)
    assert(md.indexOf("Systems collapse.") < md.indexOf("## Notes"), md)
    assert(md.indexOf("## Notes") < md.indexOf("## Findings (1)"), md)

  test("no notes column, no heading"):
    val md = SourcesMarkdown.source(detail, Vector(finding), fullText = false)
    assert(!md.contains("## Notes"), md)
    assertEquals(
      SourcesMarkdown
        .source(detail.modify(_.notes).setTo(Some(Source.Notes("   "))), Vector.empty, false)
        .contains("Notes"),
      false
    )

  test("notes come before the full text, which stays last"):
    val md =
      SourcesMarkdown.source(detail.modify(_.notes).setTo(Some(Source.Notes("N"))), Vector.empty, fullText = true)
    assert(md.indexOf("## Notes") < md.indexOf("BODY TEXT"), md)

  test("the header still carries the metadata, topics, regions and DOI"):
    val md =
      SourcesMarkdown.source(detail.modify(_.notes).setTo(Some(Source.Notes("N"))), Vector.empty, fullText = false)
    assert(md.contains("# 1177 BC"), md)
    assert(md.contains("[Eric H. Cline](/#author=Eric%20H.%20Cline)"), md)
    assert(md.contains("2014 · PUP · current · 1300 BCE–1100 BCE"), md)
    // item 4: metadata is now a set of links back into the web UI, not prose
    assert(md.contains("**Topics:** [collapse](/#topic=collapse)"), md)
    assert(md.contains("**DOI:** 10.1/x"), md)

  test("searchResults(SearchResult) renders the FTS snippet quote line with its heading"):
    val hit = SearchHit(
      Citekey("k1_a"),
      Title("T"),
      (Some(2020)).map(Year.apply),
      Source.Status.Current,
      Nil,
      Nil,
      None,
      None,
      Some(Source.Summary("sum")),
      snippet = Some(Search.Snippet("…the **Uluburun** wreck…")),
      matchedHeading = Some(SearchHit.MatchedHeading("CHAPTER 3"))
    )
    val md = SearchMarkdown.searchResults(
      SearchResult(RowCount(1), List(Hit(hit, Nil, List(Hit.Rationale("full text matches 'x'")))))
    )
    assert(md.contains("> _CHAPTER 3_ — …the **Uluburun** wreck…"), md)

  // ── paging footer (A6) ────────────────────────────────────────────────────────────────────────

  test("pagingFooter shows next and full set when the page does not cover the whole result"):
    val f = Markdown.pagingFooter(OffsetPage(RowCount(54), Offset(0), RowCount(20)))
    assert(f.contains("showing 1–20 of 54"), f)
    assert(f.contains("next: offset=20"), f)
    assert(f.contains("full set: limit=54"), f)

  test("pagingFooter is empty when everything fits on one page"):
    assertEquals(Markdown.pagingFooter(OffsetPage(RowCount(10), Offset(0), RowCount(10))), "")

  test("pagingFooter omits next when showing the last page"):
    val f = Markdown.pagingFooter(OffsetPage(RowCount(54), Offset(40), RowCount(14)))
    assert(f.contains("showing 41–54 of 54"), f)
    assert(!f.contains("next:"), f)

  test("searchResults(SearchResult) includes paging footer when total > hits"):
    val hits = (1 to 3).map { i =>
      val sh = SearchHit(
        Citekey(s"k$i"),
        Title(s"T$i"),
        (Some(2020)).map(Year.apply),
        Source.Status.Current,
        Nil,
        Nil,
        None,
        None,
        None
      )
      Hit(sh, Nil, Nil)
    }.toList
    val md = SearchMarkdown.searchResults(SearchResult(RowCount(50), hits))
    assert(md.contains("showing 1–3 of 50"), md)
    assert(md.contains("next: offset=3"), md)

  test("searchResults: the footer follows the page's own offset — every page, not just the first (#1798)"):
    val hits = (1 to 3).map { i =>
      Hit(
        SearchHit(
          Citekey(s"k$i"),
          Title(s"T$i"),
          (Some(2020)).map(Year.apply),
          Source.Status.Current,
          Nil,
          Nil,
          None,
          None,
          None
        ),
        Nil,
        Nil
      )
    }.toList
    val md = SearchMarkdown.searchResults(SearchResult.of(SearchResult.Total(50), hits, UnreadPage.Offset(20)))
    assert(md.startsWith("### 3 of 50 source(s)"), md)
    assert(md.contains("showing 21–23 of 50 — next: offset=23"), md)
    assertEquals(
      SearchResult.of(SearchResult.Total(50), hits, UnreadPage.Offset(20)).next.map(n => (n.value: Int)),
      Some(23)
    )
    assertEquals(SearchResult.of(SearchResult.Total(23), hits, UnreadPage.Offset(20)).next, None)

  test("searchResults(SearchResult) omits paging footer when everything fits"):
    val hits = (1 to 3).map { i =>
      val sh = SearchHit(
        Citekey(s"k$i"),
        Title(s"T$i"),
        (Some(2020)).map(Year.apply),
        Source.Status.Current,
        Nil,
        Nil,
        None,
        None,
        None
      )
      Hit(sh, Nil, Nil)
    }.toList
    val md = SearchMarkdown.searchResults(SearchResult(RowCount(3), hits))
    assert(!md.contains("showing"), md)

  test("missing year is rendered as n.d."):
    val md = SourcesMarkdown.source(detail.modify(_.year).setTo(None), Vector.empty, false)
    assert(md.contains("n.d."), md)
    assert(!md.contains("?"), md)

  // ── histogram collapse (A16) ──────────────────────────────────────────────────────────────────

  test("collapseHistogram leaves short histograms unchanged"):
    val buckets = (1 to 5)
      .map(i => TimeBucket(TimeBucket.Start(i * 100L), TimeBucket.End(i * 100 + 99L), TimeBucket.Count(i)))
      .toList
    val result = SearchMarkdown.collapseHistogram(buckets)
    assertEquals(result.size, 5)
    assert(result.forall(_.merged.value == 1))

  test("collapseHistogram collapses runs of identical counts when > 12 rows"):
    val buckets = (1 to 20)
      .map(i =>
        TimeBucket(
          TimeBucket.Start(i * 100L),
          TimeBucket.End(i * 100 + 99L),
          if i <= 10 then TimeBucket.Count(2) else TimeBucket.Count(5)
        )
      )
      .toList
    val result = SearchMarkdown.collapseHistogram(buckets)
    assert(result.sizeIs < 20, s"expected fewer rows, got ${result.size}")
    assertEquals(result.head.count, 2)
    assertEquals(result.head.merged, 10)
    assertEquals(result(1).count, 5)
    assertEquals(result(1).merged, 10)

/** The source page's two-way hops (UX item 4) and the article's de-duplicated footer (item 23). */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class MarkdownHopsSuite extends munit.FunSuite:

  private val src = Source.Detail(
    citekey = Citekey("x2020_y"),
    title = Title("T"),
    authors = (List("A")).map(AuthorName.apply),
    year = (Some(2020)).map(Year.apply),
    venue = None,
    status = Source.Status.Current,
    topics = List(Source.Topics("steppe"), Source.Topics("migration")),
    regions = List(Source.Regions("anatolia")),
    tags = List(Source.Tags("sea_peoples")),
    periodStart = Some(Year(-1500)),
    periodEnd = Some(Year(-1200)),
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
    val out = BacklinksMarkdown.sourceArticles(
      Vector(Backlink(Backlink.Kind("article"), Backlink.Id("sea_peoples"), Some(Backlink.Title("The Sea Peoples"))))
    )
    assert(out.contains("## Mentioned in articles (1)"), out)
    assert(out.contains("[The Sea Peoples](/article/sea_peoples)"), out)
    assertEquals(BacklinksMarkdown.sourceArticles(Vector.empty), "")

  test("neighbors renders each row by its classified target: source, article, unresolved"):
    val rows = Vector(
      NeighborRow(
        NeighborRow.Direction("out"),
        Edge.Kind.Challenges,
        Citekey("cline2014_year"),
        Some(Title("1177 BC")),
        Some(Source.Status.Current)
      ),
      NeighborRow(
        NeighborRow.Direction("in"),
        Edge.Kind.References,
        Citekey("drews1993_end"),
        Some(Title("The End of the Bronze Age"))
      ),
      NeighborRow(
        NeighborRow.Direction("out"),
        Edge.Kind.Updates,
        Citekey("bronze_age_collapse"),
        Some(Title("Bronze Age collapse")),
        target = NeighborTarget.Article
      ),
      NeighborRow(
        NeighborRow.Direction("out"),
        Edge.Kind.Refutes,
        Citekey("ghost2019_absent"),
        None,
        target = NeighborTarget.Unresolved
      )
    )
    val out = SourcesMarkdown.neighbors(Citekey("x2020_y"), rows)
    assert(out.contains("**Outgoing (this → other)**\n- _challenges_ → [[cline2014_year]] — 1177 BC _(current)_"), out)
    assert(out.contains("**Incoming (other → this)**\n- _references_ → [[drews1993_end]]"), out)
    assert(out.contains("[Bronze Age collapse](/article/bronze_age_collapse)"), out)
    assert(out.contains("**Not in the corpus yet**\n- _refutes_ → **ghost2019_absent**"), out)
    // an article or unresolved row is never rendered as a source wikilink
    assert(!out.contains("[[bronze_age_collapse]]") && !out.contains("[[ghost2019_absent]]"), out)

  test("stripSourcesSection removes a TRAILING hand-written list, not a mid-article section"):
    val body = "# T\n\nprose\n\n## Sources\n\n- [[a2015_x]]\n- [[b2016_y]]\n"
    assertEquals(ArticleMarkdown.stripSourcesSection(Article.Body(body)).value.trim, "# T\n\nprose")
    val mid = "# T\n\n## Sources\n\nA paragraph arguing about sources.\n\n## Later\n\nmore\n"
    assertEquals(ArticleMarkdown.stripSourcesSection(Article.Body(mid)), mid)
    assertEquals(ArticleMarkdown.stripSourcesSection(Article.Body("# T\n\nno list here\n")), "# T\n\nno list here\n")
