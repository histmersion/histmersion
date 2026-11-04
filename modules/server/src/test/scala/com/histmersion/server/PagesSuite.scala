package com.histmersion.server

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.Confidence
import com.histmersion.api.research.{CitedSource, ReviewBadge, ReviewFinding, ReviewGoal, ReviewRound, ReviewState}
import com.histmersion.api.library.Source
import com.histmersion.api.library.Finding
import com.histmersion.api.research.Article

/** The server's HTML pages, pure: the anchor/chip/badge atoms and the typed page models rendered from them. No database
  * — the link resolver is a Map.
  */
class PagesSuite extends munit.FunSuite:

  private val cline = com.histmersion.api.library.Source.Ref(
    "cline2014_year",
    Some("10.1515/9781400849987"),
    List("Eric H. Cline"),
    Some(2014),
    "1177 BC: The Year Civilization Collapsed",
    Source.Status.Current
  )
  private val drews = com.histmersion.api.library.Source.Ref(
    "drews1993_end",
    None,
    List("Robert Drews"),
    Some(1993),
    "The End of the Bronze Age",
    Source.Status.Contested
  )
  private val topic = Article.Ref("bronze_age_collapse", "The Bronze Age Collapse", Some("Around 1200 BCE."))
  private val links: String => Option[LinkTarget] =
    Map[String, LinkTarget](
      "cline2014_year" -> cline,
      "cline2014" -> cline,
      "drews1993_end" -> drews,
      "bronze_age_collapse" -> topic
    ).get

  // ── atoms ─────────────────────────────────────────────────────────────────────────────────────

  test("a source reference is ONE anchor shape, labelled by DOI when there is one, else by citekey"):
    assertEquals(
      HtmlRender.sourceRef(cline),
      """<a class="src-ref" data-ck="cline2014_year" data-doi="10.1515/9781400849987" """ +
        """href="/source/cline2014_year">10.1515/9781400849987</a>"""
    )
    assertEquals(
      HtmlRender.sourceRef(drews),
      """<a class="src-ref" data-ck="drews1993_end" href="/source/drews1993_end">drews1993_end</a>"""
    )

  test("an article reference carries data-slug and the title"):
    assertEquals(
      HtmlRender.articleRef(topic),
      """<a class="art-ref" data-slug="bronze_age_collapse" href="/article/bronze_age_collapse">The Bronze Age Collapse</a>"""
    )

  test("values from the database are escaped in every attribute and text node"):
    val evil = cline.modify(_.citekey).setTo("x\"><script>").modify(_.doi).setTo(Some("\"onmouseover=1"))
    val html = HtmlRender.sourceRef(evil, "<b>")
    assert(!html.contains("<script>") && !html.contains("<b>") && !html.contains("\"onmouseover"), html)

  test("the readable label is author-year, or the title when there are no authors"):
    assertEquals(HtmlRender.readable(cline), "Cline 2014")
    assertEquals(HtmlRender.readable(cline.modify(_.authors).setTo(Nil)), cline.title)

  test("a chip never shows an underscore, and links the raw value into the web UI"):
    val c = HtmlRender.chip(ChipField.Tag, "sea_peoples")
    assert(c.contains(">sea peoples</a>"), c)
    assert(c.contains("""href="/#tag=sea_peoples""""), c)
    assert(c.contains("""data-field="tag""""), c)
    assert(
      !HtmlRender
        .chips(ChipField.Topic, List("bronze_age_collapse", "trade_routes"))
        .replaceAll("<[^>]*>", "")
        .contains("_")
    )

  test("a confidence badge is an icon plus its level's class, which the stylesheet colours"):
    Confidence.values.foreach { c =>
      val b = HtmlRender.confidenceBadge(c)
      assert(b.contains("<svg"), b)
      assert(b.contains(s"conf-badge conf-${c.toString.toLowerCase}"), b)
      assert(!b.contains("style="), s"the colour is the stylesheet's: $b")
    }

  test("a finding shows its badge, place and period"):
    val f = Finding.Row(
      "Ugarit destroyed",
      Some("Ugarit"),
      Some(35.78),
      Some(35.60),
      Some(-1190),
      Some(-1185),
      None,
      Nil,
      Some(Confidence.Medium)
    )
    val html = HtmlRender.finding(f)
    assert(html.contains("conf-medium"), html)
    assert(html.contains("Ugarit (35.60, 35.78)"), html)
    assert(html.contains("1190 BCE–1185 BCE"), html)

  // ── prose ─────────────────────────────────────────────────────────────────────────────────────

  test("prose wikilinks become typed anchors: source (readable label), article (title), unresolved (text)"):
    val out = Pages.linkProse(
      "[[cline2014]] and [[bronze_age_collapse]] and [[nothing_here]] and [[drews1993_end|Drews]]",
      links
    )
    assert(out.contains(HtmlRender.sourceRef(cline, "Cline 2014")), out)
    assert(out.contains(HtmlRender.articleRef(topic)), out)
    assert(out.contains("""<span class="dangling" title="unresolved link">nothing here</span>"""), out)
    assert(out.contains(">Drews</a>"), out)

  // ── article page ──────────────────────────────────────────────────────────────────────────────

  private def articlePage(body: String, cited: List[CitedSource] = Nil) =
    ArticlePage(
      Article.Row(
        "sea_peoples",
        "The Sea Peoples",
        "concept",
        body,
        None,
        List("sea_peoples"),
        List("Levant"),
        Some(-1200),
        Some(-1150)
      ),
      Nil,
      cited
    )

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
    val html = Pages.article(
      articlePage(
        body,
        List(
          CitedSource(
            "cline2014_year",
            Some(cline.title),
            Some(2014),
            Some(Source.Status.Current),
            cline.authors,
            cline.doi
          ),
          CitedSource("drews1993_end", Some(drews.title), Some(1993), Some(Source.Status.Contested), drews.authors)
        )
      ),
      links
    )
    assert(
      html.contains(
        HtmlRender.sourceRef(
          drews,
          "Drews 1993"
        ) + """<sup class="cite-num"><a class="cite-jump" href="#cite-1">[1]</a></sup>"""
      ),
      html
    )
    assert(
      html.contains(
        HtmlRender.sourceRef(
          cline,
          "Cline 2014"
        ) + """<sup class="cite-num"><a class="cite-jump" href="#cite-2">[2]</a></sup>"""
      ),
      html
    )
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
    val html = Pages.article(
      articlePage(
        "# T\n\nprose [[drews1993_end]]\n\n## Sources\n\n- [[drews1993_end]]\n",
        List(CitedSource("drews1993_end", Some(drews.title), Some(1993), Some(Source.Status.Contested), drews.authors))
      ),
      links
    )
    assertEquals("<h2[^>]*>Sources".r.findAllIn(html).size, 1, html)
    assert(html.contains("""<h2 id="sources-cited">Sources cited ("""), html)

  // ── source page ───────────────────────────────────────────────────────────────────────────────

  private val sourcePage = SourcePage(
    cline,
    Some("Princeton UP"),
    Some(-1250),
    Some(-1100),
    List("bronze_age_collapse"),
    List("Levant"),
    List("sea_peoples"),
    Some("A synthesis."),
    None,
    Nil,
    Nil,
    Nil,
    FullText.Hidden
  )

  test("no citations: no section at all, and no agent-worded 'No references recorded' text"):
    val html = Pages.source(sourcePage, links)
    assert(!html.contains("Cites") && !html.contains("Cited by"), html)
    assert(!html.contains("No references recorded"), html)
    assert(html.contains("""<span class="lbl">DOI</span> <a class="doi""""), html)
    assert(!html.replaceAll("<[^>]*>", "").contains("_"), "no underscore reaches the reader: " + html)

  // found in the browser: a finding's region printed raw ("south_caucasus (41.00, 45.00)")
  test("a finding's region, tags and significance reach the reader without underscores"):
    val f = Finding.Row(
      "Proto-Indo-European split early",
      Some("south_caucasus"),
      Some(45.0),
      Some(41.0),
      Some(-8120),
      Some(-6000),
      Some("early_neolithic"),
      List("farming_hypothesis"),
      Some(Confidence.High),
      Some("key_evidence")
    )
    val html = Pages.source(sourcePage.modify(_.findings).setTo(List(f)), links)
    assert(html.contains("south caucasus (41.00, 45.00)"), html)
    // Library's reading pane links "All N findings" here
    assert(html.contains("""<h2 id="findings">Findings (1)</h2>"""), html)
    assert(!html.replaceAll("<[^>]*>", "").contains("_"), "no underscore reaches the reader: " + html)

  test("Cites / Cited by merge typed edges, unresolved edges and the articles citing the source"):
    val p = sourcePage.modify(
      _.cites).setTo(List(Citation.ToSource(drews, "challenges"), Citation.Unresolved("ghost2019_x", "references"))).modify(_.citedBy).setTo(List(Citation.ToArticle(topic, None))
    )
    val html = Pages.source(p, links)
    assert(html.contains("""<h2 id="cites">Cites (2)</h2>"""), html)
    assert(html.contains("""<h2 id="cited-by">Cited by (1)</h2>"""), html)
    assert(html.contains(HtmlRender.sourceRef(drews, "Drews 1993")), html)
    assert(html.contains("""<span class="edge-kind">challenges</span>"""), html)
    assert(html.contains("not in the corpus yet"), html)
    assert(html.contains(HtmlRender.articleRef(topic)), html)

  test("a standalone page loads the Scala.js bundle and names its page kind"):
    val doc = com.histmersion.server.ArticleHtml.page("T", "<p>x</p>", PageKind.Source)
    assert(doc.contains("""<script src="/web/app.bundle.js"""), doc)
    assert(doc.contains("""data-page="source""""), doc)
    assert(!doc.contains("article.js") && !doc.contains("citations.js"), doc)

  // ── review ────────────────────────────────────────────────────────────────────────────────────

  private val review = Article.ReviewView(
    "sea_peoples",
    ReviewBadge(
      ReviewState.MajorsOpen,
      "1 major open",
      "Reviewed: 1 MAJOR finding is still open.",
      openMajors = 1,
      findings = 2,
      runs = 1
    ),
    rounds = List(
      ReviewRound(
        "run-a",
        1,
        1,
        Some("2026-10-01T10:00:00Z"),
        List(
          ReviewFinding(
            "R1",
            "rules",
            "major",
            "decorative-citation",
            "Background ¶2",
            "a <cited> claim",
            "",
            "",
            "rejected",
            note = "the record supports it",
            closedBy = Some("fixer"),
            closedRound = Some(1),
            closedRunNumber = Some(1)
          ),
          ReviewFinding(
            "S1",
            "skeptic",
            "major",
            "omitted-critique",
            "",
            "the 2019 critique is missing",
            "",
            "",
            "open"
          )
        )
      )
    ),
    goals = List(ReviewGoal("sea-peoples-goal", "The Sea Peoples & the collapse", "compiled"))
  )

  test("the article page shows the review badge with its meaning, and the goals that link it, under the title"):
    val html = Pages.article(articlePage("# The Sea Peoples\n\nText.").modify(_.review).setTo(Some(review)), links)
    assert(
      html.contains(
        """<span class="job-state review-state state-majors-open" title="Reviewed: 1 MAJOR finding is still open.">1 major open</span>"""
      ),
      html
    )
    assert(html.indexOf("article-review-line") < html.indexOf("<p>Text.</p>"), html)
    assert(
      html.contains("""<a class="goal-ref" href="/research/goals/sea-peoples-goal" title="sea-peoples-goal">"""),
      html
    )
    assert(
      html.contains(
        """<span class="job-state goal-status state-compiled" title="compiled: its articles are written; not all have passed review yet">Compiled</span> The Sea Peoples &amp; the collapse</a>"""
      ),
      html
    )
    assert(!Pages.article(articlePage("# The Sea Peoples\n\nText."), links).contains("article-review-line"))

  test("the review history: collapsed, counted, run by run, each finding with reviewer, kind, place and fate"):
    val html = Pages.reviewHistory(review)
    assert(
      html.startsWith(
        """<details class="review-history"><summary>Review history: 2 findings over 1 run, 1 still open</summary>"""
      ),
      html
    )
    assert(
      html.contains("<h3>Run 1, round 1 <span class=\"muted\">· run started 2026-10-01 10:00 UTC</span></h3>"),
      html
    )
    assert(
      html.contains(
        """<b>R1</b> <span class="muted">rules reviewer · decorative-citation · Background ¶2</span> — a &lt;cited&gt; claim"""
      ),
      html
    )
    assert(
      html.contains(
        """<div class="review-outcome">rejected by fixer in run 1, round 1: the record supports it</div>"""
      ),
      html
    )
    assert(
      html.contains(
        """<li class="review-finding outcome-open"><span class="review-sev sev-major">major</span> <b>S1</b> <span class="muted">skeptic · omitted-critique</span>"""
      ),
      html
    )
    assertEquals(Pages.reviewHistory(review.modify(_.rounds).setTo(Nil)), "")
    assertEquals(Pages.readableUtc("2026-10-01T10:00"), "2026-10-01T10:00")
