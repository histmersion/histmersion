package com.histmersion
package server

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.Confidence
import com.histmersion.api.research.{CitedSource, ReviewBadge, ReviewFinding, ReviewGoal, ReviewRound, ReviewState}
import com.histmersion.api.library.Source
import com.histmersion.api.library.Finding
import com.histmersion.api.research.Article
import com.histmersion.api.text.WikiLinks
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Doi
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.domain.Title
import com.histmersion.api.automation.Activity
import com.histmersion.api.instance.BuildId
import com.histmersion.api.Html.html
import com.histmersion.server.instance.WebBundle
import com.histmersion.lint.{LintRule, tolerated}

/** The server's HTML pages, pure: the anchor/chip/badge atoms and the typed page models rendered from them. No database
  * — the link resolver is a Map.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class PagesSuite extends munit.FunSuite:

  private val cline = Source.Ref(
    Citekey("cline2014_year"),
    (Some("10.1515/9781400849987")).map(Doi.apply),
    (List("Eric H. Cline")).map(api.library.AuthorName.apply),
    (Some(2014)).map(Year.apply),
    api.library.Title("1177 BC: The Year Civilization Collapsed"),
    Source.Status.Current
  )
  private val drews = Source.Ref(
    Citekey("drews1993_end"),
    None,
    (List("Robert Drews")).map(api.library.AuthorName.apply),
    (Some(1993)).map(Year.apply),
    api.library.Title("The End of the Bronze Age"),
    Source.Status.Contested
  )
  private val topic = Article.Ref(
    Article.Slug("bronze_age_collapse"),
    Article.Title("The Bronze Age Collapse"),
    Some(Article.SummaryText("Around 1200 BCE."))
  )
  private val links: WikiLinks.Occurrence.Target => Option[LinkTarget] =
    Map[WikiLinks.Occurrence.Target, LinkTarget](
      WikiLinks.Occurrence.Target("cline2014_year") -> cline,
      WikiLinks.Occurrence.Target("cline2014") -> cline,
      WikiLinks.Occurrence.Target("drews1993_end") -> drews,
      WikiLinks.Occurrence.Target("bronze_age_collapse") -> topic
    ).get

  // ── atoms ─────────────────────────────────────────────────────────────────────────────────────

  test("a source reference is ONE anchor shape, labelled by DOI when there is one, else by citekey"):
    assertEquals(
      HtmlRender.sourceRef(cline).markup,
      """<a class="src-ref" data-ck="cline2014_year" data-doi="10.1515/9781400849987" """ +
        """href="/source/cline2014_year">10.1515/9781400849987</a>"""
    )
    assertEquals(
      HtmlRender.sourceRef(drews).markup,
      """<a class="src-ref" data-ck="drews1993_end" href="/source/drews1993_end">drews1993_end</a>"""
    )

  test("an article reference carries data-slug and the title"):
    assertEquals(
      HtmlRender.articleRef(topic).markup,
      """<a class="art-ref" data-slug="bronze_age_collapse" href="/article/bronze_age_collapse">The Bronze Age Collapse</a>"""
    )

  test("values from the database are escaped in every attribute and text node"):
    val evil = cline.modify(_.citekey).setTo(Citekey("x\"><script>")).modify(_.doi).setTo(Some(Doi("\"onmouseover=1")))
    val html = HtmlRender.sourceRef(evil, HtmlRender.Label("<b>")).markup
    assert(!html.contains("<script>") && !html.contains("<b>") && !html.contains("\"onmouseover"), html)

  test("the readable label is author-year, or the title when there are no authors"):
    assertEquals(HtmlRender.readable(cline), "Cline 2014")
    assertEquals(HtmlRender.readable(cline.modify(_.authors).setTo(Nil)).value: String, cline.title.value: String)

  test("a chip never shows an underscore, and links the raw value into the web UI"):
    val c = HtmlRender.chip(ChipField.Tag, HtmlRender.Label("sea_peoples")).markup
    assert(c.contains(">sea peoples</a>"), c)
    assert(c.contains("""href="/#tag=sea_peoples""""), c)
    assert(c.contains("""data-field="tag""""), c)
    assert(
      !HtmlRender
        .chips(ChipField.Topic, List(HtmlRender.Label("bronze_age_collapse"), HtmlRender.Label("trade_routes")))
        .markup
        .replaceAll("<[^>]*>", "")
        .contains("_")
    )

  test("a confidence badge is an icon plus its level's class, which the stylesheet colours"):
    Confidence.values.foreach { c =>
      val b = HtmlRender.confidenceBadge(c).markup
      assert(b.contains("<svg"), b)
      assert(b.contains(s"conf-badge conf-${c.toString.toLowerCase}"), b)
      assert(!b.contains("style="), s"the colour is the stylesheet's: $b")
    }

  test("a finding shows its badge, place and period"):
    val f = Finding.Row(
      Finding.Claim("Ugarit destroyed"),
      Some(Place.Region("Ugarit")),
      Some(Longitude(35.78)),
      Some(Latitude(35.60)),
      Some(Year(-1190)),
      Some(Year(-1185)),
      None,
      Nil,
      Some(Confidence.Medium)
    )
    val html = HtmlRender.finding(f).markup
    assert(html.contains("conf-medium"), html)
    assert(html.contains("Ugarit (35.60, 35.78)"), html)
    assert(html.contains("1190 BCE–1185 BCE"), html)

  // ── prose ─────────────────────────────────────────────────────────────────────────────────────

  test("prose wikilinks become typed anchors: source (readable label), article (title), unresolved (text)"):
    val out = Pages
      .linkProse(
        FullText.Shown
          .Markdown("[[cline2014]] and [[bronze_age_collapse]] and [[nothing_here]] and [[drews1993_end|Drews]]"),
        links
      )
      .value
    assert(out.contains(HtmlRender.sourceRef(cline, HtmlRender.Label("Cline 2014")).markup), out)
    assert(out.contains(HtmlRender.articleRef(topic).markup), out)
    assert(out.contains("""<span class="dangling" title="unresolved link">nothing here</span>"""), out)
    assert(out.contains(">Drews</a>"), out)

  // ── article page ──────────────────────────────────────────────────────────────────────────────

  private def articlePage(body: Article.Body, cited: List[CitedSource] = Nil) =
    ArticlePage(
      Article.Row(
        Article.Slug("sea_peoples"),
        Article.Title("The Sea Peoples"),
        Article.Kind.Wire("concept"),
        body,
        None,
        List(Article.Tags("sea_peoples")),
        List(Article.Regions("Levant")),
        Some(Year(-1200)),
        Some(Year(-1150))
      ),
      Nil,
      cited
    )

  test("citations are numbered by first appearance; 'Not used' entries get the same anchor, no number"):
    val body = Article.Body("""# The Sea Peoples
        |
        |Drews argued it [[drews1993_end]]; Cline disagreed [[cline2014]], again [[drews1993_end]].
        |
        |## Not used
        |
        |- **[[cline2014_year]]**: already cited above.
        |- **[[drews1993_end]]**: superseded.
        |""".stripMargin)
    val html = Pages
      .article(
        articlePage(
          body,
          List(
            CitedSource(
              Citekey("cline2014_year"),
              Some(cline.title),
              (Some(2014)).map(Year.apply),
              Some(Source.Status.Current),
              cline.authors,
              cline.doi
            ),
            CitedSource(
              Citekey("drews1993_end"),
              Some(drews.title),
              (Some(1993)).map(Year.apply),
              Some(Source.Status.Contested),
              drews.authors
            )
          )
        ),
        links
      )
      .markup
    assert(
      html.contains(
        HtmlRender
          .sourceRef(
            drews,
            HtmlRender.Label("Drews 1993")
          )
          .markup + """<sup class="cite-num"><a class="cite-jump" href="#cite-1">[1]</a></sup>"""
      ),
      html
    )
    assert(
      html.contains(
        HtmlRender
          .sourceRef(
            cline,
            HtmlRender.Label("Cline 2014")
          )
          .markup + """<sup class="cite-num"><a class="cite-jump" href="#cite-2">[2]</a></sup>"""
      ),
      html
    )
    // under "Not used": the same anchor (so the same preview), bold, and no number
    assert(
      html.contains(
        "<strong>" + HtmlRender.sourceRef(cline, HtmlRender.Label("Cline 2014")).markup + "</strong>: already cited"
      ),
      html
    )
    assertEquals("href=\"#cite-".r.findAllIn(html).size, 3, html)
    // the footer: numbered entries in number order, each with its identifier
    val footer = html.drop(html.indexOf("Sources cited"))
    assert(footer.indexOf("id=\"cite-1\"") < footer.indexOf("id=\"cite-2\""), footer)
    assert(footer.contains("""<a class="doi" href="https://doi.org/10.1515/9781400849987""""), footer)
    assert(footer.contains("""<code class="citekey">drews1993_end</code>"""), footer)

  test("the article's own title heading is rendered once, with the meta line under it"):
    val html = Pages.article(articlePage(Article.Body("# The Sea Peoples\n\nprose")), links).markup
    assertEquals("<h1>".r.findAllIn(html).size, 1, html)
    assert(html.contains("""<p class="article-meta-line">concept · """), html)
    assert(html.contains(">sea peoples</a>"), html)

  test("a trailing hand-written Sources list is dropped under the generated footer"):
    val html = Pages
      .article(
        articlePage(
          Article.Body("# T\n\nprose [[drews1993_end]]\n\n## Sources\n\n- [[drews1993_end]]\n"),
          List(
            CitedSource(
              Citekey("drews1993_end"),
              Some(drews.title),
              (Some(1993)).map(Year.apply),
              Some(Source.Status.Contested),
              drews.authors
            )
          )
        ),
        links
      )
      .markup
    assertEquals("<h2[^>]*>Sources".r.findAllIn(html).size, 1, html)
    assert(html.contains("""<h2 id="sources-cited">Sources cited ("""), html)

  // ── source page ───────────────────────────────────────────────────────────────────────────────

  private val sourcePage = SourcePage(
    cline,
    Some(SourcePage.Venue("Princeton UP")),
    Some(SourcePage.PeriodStart(-1250)),
    Some(SourcePage.PeriodEnd(-1100)),
    List(SourcePage.Topics("bronze_age_collapse")),
    List(SourcePage.Regions("Levant")),
    List(SourcePage.Tags("sea_peoples")),
    Some(domain.library.Source.Summary("A synthesis.")),
    None,
    Nil,
    Nil,
    Nil,
    FullText.Hidden
  )

  test("no citations: no section at all, and no agent-worded 'No references recorded' text"):
    val html = Pages.source(sourcePage, links).markup
    assert(!html.contains("Cites") && !html.contains("Cited by"), html)
    assert(!html.contains("No references recorded"), html)
    assert(html.contains("""<span class="lbl">DOI</span> <a class="doi""""), html)
    assert(!html.replaceAll("<[^>]*>", "").contains("_"), "no underscore reaches the reader: " + html)

  // found in the browser: a finding's region printed raw ("south_caucasus (41.00, 45.00)")
  test("a finding's region, tags and significance reach the reader without underscores"):
    val f = Finding.Row(
      Finding.Claim("Proto-Indo-European split early"),
      Some(Place.Region("south_caucasus")),
      Some(Longitude(45.0)),
      Some(Latitude(41.0)),
      Some(Year(-8120)),
      Some(Year(-6000)),
      Some(Finding.PeriodName("early_neolithic")),
      List(Finding.Tags("farming_hypothesis")),
      Some(Confidence.High),
      Some(Finding.Significance("key_evidence"))
    )
    val html = Pages.source(sourcePage.modify(_.findings).setTo(List(f)), links).markup
    assert(html.contains("south caucasus (41.00, 45.00)"), html)
    // Library's reading pane links "All N findings" here
    assert(html.contains("""<h2 id="findings">Findings (1)</h2>"""), html)
    assert(!html.replaceAll("<[^>]*>", "").contains("_"), "no underscore reaches the reader: " + html)

  test("Cites / Cited by merge typed edges, unresolved edges and the articles citing the source"):
    val p = sourcePage
      .modify(_.cites)
      .setTo(
        List(
          Citation.ToSource(drews, Citation.ToSource.Kind("challenges")),
          Citation.Unresolved(Citation.Unresolved.Id("ghost2019_x"), Citation.Unresolved.Kind("references"))
        )
      )
      .modify(_.citedBy)
      .setTo(List(Citation.ToArticle(topic, None)))
    val html = Pages.source(p, links).markup
    assert(html.contains("""<h2 id="cites">Cites (2)</h2>"""), html)
    assert(html.contains("""<h2 id="cited-by">Cited by (1)</h2>"""), html)
    assert(html.contains(HtmlRender.sourceRef(drews, HtmlRender.Label("Drews 1993")).markup), html)
    assert(html.contains("""<span class="edge-kind">challenges</span>"""), html)
    assert(html.contains("not in the corpus yet"), html)
    assert(html.contains(HtmlRender.articleRef(topic).markup), html)

  test("the pages pass the allow-list unchanged, so the web UI's window (which cleans what it fetches) loses nothing"):
    val p = sourcePage
      .modify(_.cites)
      .setTo(
        List(
          Citation.ToSource(drews, Citation.ToSource.Kind("challenges")),
          Citation.Unresolved(Citation.Unresolved.Id("ghost2019_x"), Citation.Unresolved.Kind("references"))
        )
      )
      .modify(_.citedBy)
      .setTo(List(Citation.ToArticle(topic, None)))
    val pages = List(
      Pages.source(p, links).markup,
      Pages
        .article(
          articlePage(
            Article.Body(
              "# The Sea Peoples\n\nProse [[cline2014_year]] and [[bronze_age_collapse]] and [[ghost]].\n\n" +
                "| a | b |\n|:--|--:|\n| 1 | 2 |\n\n![Figure 1](assets/f.png)\n\n`code` *em* **strong**\n\n## Not used\n\n" +
                "- **[[drews1993_end]]**: why"
            )
          ).modify(_.review).setTo(Some(review)),
          links
        )
        .markup,
      Pages.reviewHistory(review).markup
    )
    pages.foreach(html => assertEquals(api.Html.sanitize(html).markup, html))

  test("a standalone page loads the Scala.js bundle and names its page kind"):
    val bundle = WebBundle(BuildId("0123456789abcdef0123456789abcdef"))
    val doc = ArticleHtml.page(Title("T"), html"<p>x</p>", PageKind.Source, bundle).markup
    assert(doc.contains("""<script src="/web/app.bundle.js?v=0123456789abcdef0123456789abcdef"></script>"""), doc)
    assert(doc.contains("""data-page="source""""), doc)
    assert(!doc.contains("article.js") && !doc.contains("citations.js"), doc)

  // ── review ────────────────────────────────────────────────────────────────────────────────────

  private val review = Article.ReviewView(
    Article.Slug("sea_peoples"),
    ReviewBadge(
      ReviewState.MajorsOpen,
      ReviewBadge.Label("1 major open"),
      ReviewBadge.Detail("Reviewed: 1 MAJOR finding is still open."),
      openMajors = ReviewBadge.OpenMajors(1),
      findings = ReviewBadge.Findings(2),
      runs = ReviewBadge.Runs(1)
    ),
    rounds = List(
      ReviewRound(
        ReviewRound.Run("run-a"),
        ReviewRound.RunNumber(1),
        ReviewRound.Round(1),
        Some(ReviewRound.StartedAt("2026-10-01T10:00:00Z")),
        List(
          ReviewFinding(
            ReviewFinding.Id("R1"),
            ReviewFinding.Reviewer("rules"),
            ReviewFinding.Severity("major"),
            ReviewFinding.Kind("decorative-citation"),
            ReviewFinding.Where("Background ¶2"),
            ReviewFinding.Problem("a <cited> claim"),
            ReviewFinding.Fix(""),
            ReviewFinding.Quote(""),
            ReviewFinding.Outcome("rejected"),
            note = ReviewFinding.Note("the record supports it"),
            closedBy = Some(Activity.Actor("fixer")),
            closedRound = Some(ReviewRound.Round(1)),
            closedRunNumber = Some(ReviewRound.RunNumber(1))
          ),
          ReviewFinding(
            ReviewFinding.Id("S1"),
            ReviewFinding.Reviewer("skeptic"),
            ReviewFinding.Severity("major"),
            ReviewFinding.Kind("omitted-critique"),
            ReviewFinding.Where(""),
            ReviewFinding.Problem("the 2019 critique is missing"),
            ReviewFinding.Fix(""),
            ReviewFinding.Quote(""),
            ReviewFinding.Outcome("open")
          )
        )
      )
    ),
    goals = List(
      ReviewGoal(
        ReviewGoal.Slug("sea-peoples-goal"),
        ReviewGoal.Title("The Sea Peoples & the collapse"),
        ReviewGoal.Status("compiled")
      )
    )
  )

  test("the article page shows the review badge with its meaning, and the goals that link it, under the title"):
    val html = Pages
      .article(articlePage(Article.Body("# The Sea Peoples\n\nText.")).modify(_.review).setTo(Some(review)), links)
      .markup
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
    assert(
      !Pages
        .article(articlePage(Article.Body("# The Sea Peoples\n\nText.")), links)
        .markup
        .contains("article-review-line")
    )

  test("the review history: collapsed, counted, run by run, each finding with reviewer, kind, place and fate"):
    val html = Pages.reviewHistory(review).markup
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
    assertEquals(Pages.reviewHistory(review.modify(_.rounds).setTo(Nil)).markup, "")
    assertEquals(Pages.readableUtc("2026-10-01T10:00"), "2026-10-01T10:00")
