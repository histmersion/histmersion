package com.histmersion.server

import com.histmersion.core.*

/** The server's HTML pages, pure: the anchor/chip/badge atoms and the typed page models rendered
  * from them. No database — the link resolver is a Map. */
class PagesSuite extends munit.FunSuite:

  private val cline = SourceRef("cline2014_year", Some("10.1515/9781400849987"), List("Eric H. Cline"),
    Some(2014), "1177 BC: The Year Civilization Collapsed", SourceStatus.Current)
  private val drews = SourceRef("drews1993_end", None, List("Robert Drews"), Some(1993),
    "The End of the Bronze Age", SourceStatus.Contested)
  private val topic = ArticleRef("bronze_age_collapse", "The Bronze Age Collapse", Some("Around 1200 BCE."))
  private val links: String => Option[LinkTarget] =
    Map[String, LinkTarget]("cline2014_year" -> cline, "cline2014" -> cline, "drews1993_end" -> drews,
      "bronze_age_collapse" -> topic).get

  // ── atoms ─────────────────────────────────────────────────────────────────────────────────────

  test("a source reference is ONE anchor shape, labelled by DOI when there is one, else by citekey"):
    assertEquals(HtmlRender.sourceRef(cline),
      """<a class="src-ref" data-ck="cline2014_year" data-doi="10.1515/9781400849987" """ +
        """href="/source/cline2014_year">10.1515/9781400849987</a>""")
    assertEquals(HtmlRender.sourceRef(drews),
      """<a class="src-ref" data-ck="drews1993_end" href="/source/drews1993_end">drews1993_end</a>""")

  test("an article reference carries data-slug and the title"):
    assertEquals(HtmlRender.articleRef(topic),
      """<a class="art-ref" data-slug="bronze_age_collapse" href="/article/bronze_age_collapse">The Bronze Age Collapse</a>""")

  test("values from the database are escaped in every attribute and text node"):
    val evil = cline.copy(citekey = "x\"><script>", doi = Some("\"onmouseover=1"))
    val html = HtmlRender.sourceRef(evil, "<b>")
    assert(!html.contains("<script>") && !html.contains("<b>") && !html.contains("\"onmouseover"), html)

  test("the readable label is author-year, or the title when there are no authors"):
    assertEquals(HtmlRender.readable(cline), "Cline 2014")
    assertEquals(HtmlRender.readable(cline.copy(authors = Nil)), cline.title)

  test("a chip never shows an underscore, and links the raw value into the web UI"):
    val c = HtmlRender.chip(ChipField.Tag, "sea_peoples")
    assert(c.contains(">sea peoples</a>"), c)
    assert(c.contains("""href="/#tag=sea_peoples""""), c)
    assert(c.contains("""data-field="tag""""), c)
    assert(!HtmlRender.chips(ChipField.Topic, List("bronze_age_collapse", "trade_routes"))
      .replaceAll("<[^>]*>", "").contains("_"))

  test("a confidence badge is an icon plus the map's colour for that level"):
    Confidence.values.foreach { c =>
      val b = HtmlRender.confidenceBadge(c)
      assert(b.contains("<svg"), b)
      assert(b.contains(s"color:${ConfidenceStyle.color(c)}"), b)
      assert(b.contains(c.toString.toLowerCase), b)
    }
    // high is the map's undesaturated base colour; contested is visibly greyer
    assertEquals(ConfidenceStyle.color(Confidence.High), "rgb(33,145,140)")
    assertNotEquals(ConfidenceStyle.color(Confidence.Contested), ConfidenceStyle.color(Confidence.High))

  test("a finding shows its badge, place and period"):
    val f = FindingRow("Ugarit destroyed", Some("Ugarit"), Some(35.78), Some(35.60), Some(-1190), Some(-1185),
      None, Nil, Some(Confidence.Medium))
    val html = HtmlRender.finding(f)
    assert(html.contains("conf-medium"), html)
    assert(html.contains("Ugarit (35.60, 35.78)"), html)
    assert(html.contains("1190 BCE–1185 BCE"), html)

  // ── prose ─────────────────────────────────────────────────────────────────────────────────────

  test("prose wikilinks become typed anchors: source (readable label), article (title), unresolved (text)"):
    val out = Pages.linkProse("[[cline2014]] and [[bronze_age_collapse]] and [[nothing_here]] and [[drews1993_end|Drews]]", links)
    assert(out.contains(HtmlRender.sourceRef(cline, "Cline 2014")), out)
    assert(out.contains(HtmlRender.articleRef(topic)), out)
    assert(out.contains("""<span class="dangling" title="unresolved link">nothing here</span>"""), out)
    assert(out.contains(">Drews</a>"), out)

  // ── article page ──────────────────────────────────────────────────────────────────────────────

  private def articlePage(body: String, cited: List[CitedSource] = Nil) =
    ArticlePage(ArticleRow("sea_peoples", "The Sea Peoples", "concept", body, None, List("sea_peoples"),
      List("Levant"), Some(-1200), Some(-1150)), Nil, cited)

  test("citations are numbered by first appearance; 'Not used' entries get the same anchor, no number"):
    val body =
      """# The Sea Peoples
        |
        |Drews argued it [[drews1993_end]]; Cline disagreed [[cline2014]], again [[drews1993_end]].
        |
        |## Not used
        |
        |- **[[cline2014_year]]**: already cited above.
        |- **[[drews1993_end]]**: superseded.
        |""".stripMargin
    val html = Pages.article(articlePage(body, List(
      CitedSource("cline2014_year", Some(cline.title), Some(2014), Some(SourceStatus.Current), cline.authors, cline.doi),
      CitedSource("drews1993_end", Some(drews.title), Some(1993), Some(SourceStatus.Contested), drews.authors))), links)
    assert(html.contains(HtmlRender.sourceRef(drews, "Drews 1993") + """<sup class="cite-num"><a class="cite-jump" href="#cite-1">[1]</a></sup>"""), html)
    assert(html.contains(HtmlRender.sourceRef(cline, "Cline 2014") + """<sup class="cite-num"><a class="cite-jump" href="#cite-2">[2]</a></sup>"""), html)
    // under "Not used": the same anchor (so the same preview), bold, and no number
    assert(html.contains("<strong>" + HtmlRender.sourceRef(cline, "Cline 2014") + "</strong>: already cited"), html)
    assertEquals("href=\"#cite-".r.findAllIn(html).size, 3, html)
    // the footer: numbered entries in number order, each with its identifier
    val footer = html.drop(html.indexOf("Sources cited"))
    assert(footer.indexOf("id=\"cite-1\"") < footer.indexOf("id=\"cite-2\""), footer)
    assert(footer.contains("""<a class="doi" href="https://doi.org/10.1515/9781400849987""""), footer)
    assert(footer.contains("""<code class="citekey">drews1993_end</code>"""), footer)

  test("the article's own title heading is rendered once, with the meta line under it"):
    val html = Pages.article(articlePage("# The Sea Peoples\n\nprose"), links)
    assertEquals("<h1>".r.findAllIn(html).size, 1, html)
    assert(html.contains("""<p class="article-meta-line">concept · """), html)
    assert(html.contains(">sea peoples</a>"), html)

  test("a trailing hand-written Sources list is dropped under the generated footer"):
    val html = Pages.article(articlePage("# T\n\nprose [[drews1993_end]]\n\n## Sources\n\n- [[drews1993_end]]\n",
      List(CitedSource("drews1993_end", Some(drews.title), Some(1993), Some(SourceStatus.Contested), drews.authors))), links)
    assertEquals("<h2>Sources".r.findAllIn(html).size, 1, html)

  // ── source page ───────────────────────────────────────────────────────────────────────────────

  private val sourcePage = SourcePage(cline, Some("Princeton UP"), Some(-1250), Some(-1100),
    List("bronze_age_collapse"), List("Levant"), List("sea_peoples"), Some("A synthesis."), None,
    Nil, Nil, Nil, FullText.Hidden)

  test("no citations: no section at all, and no agent-worded 'No references recorded' text"):
    val html = Pages.source(sourcePage, links)
    assert(!html.contains("Cites") && !html.contains("Cited by"), html)
    assert(!html.contains("No references recorded"), html)
    assert(html.contains("""<span class="lbl">DOI</span> <a class="doi""""), html)
    assert(!html.replaceAll("<[^>]*>", "").contains("_"), "no underscore reaches the reader: " + html)

  // found in the browser: a finding's region printed raw ("south_caucasus (41.00, 45.00)")
  test("a finding's region, tags and significance reach the reader without underscores"):
    val f = FindingRow("Proto-Indo-European split early", Some("south_caucasus"), Some(45.0), Some(41.0),
      Some(-8120), Some(-6000), Some("early_neolithic"), List("farming_hypothesis"), Some(Confidence.High),
      Some("key_evidence"))
    val html = Pages.source(sourcePage.copy(findings = List(f)), links)
    assert(html.contains("south caucasus (41.00, 45.00)"), html)
    assert(!html.replaceAll("<[^>]*>", "").contains("_"), "no underscore reaches the reader: " + html)

  test("Cites / Cited by merge typed edges, unresolved edges and the articles citing the source"):
    val p = sourcePage.copy(
      cites = List(Citation.ToSource(drews, "challenges"), Citation.Unresolved("ghost2019_x", "references")),
      citedBy = List(Citation.ToArticle(topic, None)))
    val html = Pages.source(p, links)
    assert(html.contains("<h2>Cites (2)</h2>"), html)
    assert(html.contains("<h2>Cited by (1)</h2>"), html)
    assert(html.contains(HtmlRender.sourceRef(drews, "Drews 1993")), html)
    assert(html.contains("""<span class="edge-kind">challenges</span>"""), html)
    assert(html.contains("not in the corpus yet"), html)
    assert(html.contains(HtmlRender.articleRef(topic)), html)

  test("a standalone page loads the Scala.js bundle and names its page kind"):
    val doc = com.histmersion.server.Article.page("T", "<p>x</p>", PageKind.Source)
    assert(doc.contains("""<script src="/web/app.bundle.js"""), doc)
    assert(doc.contains("""data-page="source""""), doc)
    assert(!doc.contains("article.js") && !doc.contains("citations.js"), doc)
