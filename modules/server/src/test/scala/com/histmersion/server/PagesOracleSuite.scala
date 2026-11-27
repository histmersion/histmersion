package com.histmersion
package server

import hearth.kindlings.optics.syntax.*

import java.nio.file.{Files, Path}

import com.histmersion.domain.*
import com.histmersion.api.library.Confidence
import com.histmersion.api.research.{CitedSource, ReviewBadge, ReviewFinding, ReviewGoal, ReviewRound, ReviewState}
import com.histmersion.api.research.Backlink
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
import com.histmersion.api.checks.{FormatField, FormatIssue, FormatRule, FormatSeverity}
import com.histmersion.domain.Title
import com.histmersion.api.automation.Activity
import com.histmersion.api.instance.BuildId
import com.histmersion.server.instance.WebBundle
import com.histmersion.lint.{tolerated, LintRule}

class PagesOracleSuite extends munit.FunSuite:

  private val file = Path.of("modules/server/src/test/resources/oracle/pages/pages.txt")
  private val update = sys.env.get("HISTMERSION_UPDATE_ORACLE").contains("1")

  private val cline = Source.Ref(
    Citekey("cline2014_year"),
    Some(Doi("10.1515/9781400849987")),
    List(api.library.AuthorName("Eric H. Cline")),
    Some(Year(2014)),
    api.library.Title("1177 BC: The Year \"Civilization\" Collapsed & <after>"),
    Source.Status.Current
  )
  private val drews = Source.Ref(
    Citekey("drews1993_end"),
    None,
    List(api.library.AuthorName("Robert Drews")),
    Some(Year(1993)),
    api.library.Title("The End of the Bronze Age"),
    Source.Status.Contested
  )
  private val anon = Source.Ref(
    Citekey("anon_tablets"),
    None,
    Nil,
    None,
    api.library.Title("Ugarit tablets <RS 18.147> & \"letters\""),
    Source.Status.Superseded
  )
  private val topic = Article.Ref(
    Article.Slug("bronze_age_collapse"),
    Article.Title("The Bronze Age \"Collapse\" & after"),
    Some(Article.SummaryText("Around 1200 BCE."))
  )
  private val links: WikiLinks.Occurrence.Target => Option[LinkTarget] =
    Map[WikiLinks.Occurrence.Target, LinkTarget](
      WikiLinks.Occurrence.Target("cline2014_year") -> cline,
      WikiLinks.Occurrence.Target("cline2014") -> cline,
      WikiLinks.Occurrence.Target("drews1993_end") -> drews,
      WikiLinks.Occurrence.Target("anon_tablets") -> anon,
      WikiLinks.Occurrence.Target("bronze_age_collapse") -> topic
    ).get

  private val bundle = WebBundle(BuildId("0123456789abcdef0123456789abcdef"))

  private val findings = List(
    Finding.Row(
      Finding.Claim("Ugarit destroyed \"by fire\" & <sea raiders>"),
      Some(Place.Region("north_levant")),
      Some(Longitude(35.78)),
      Some(Latitude(35.60)),
      Some(Year(-1190)),
      Some(Year(-1185)),
      Some(Finding.PeriodName("late_bronze_age")),
      List(Finding.Tags("sea_peoples")),
      Some(Confidence.Medium),
      Some(Finding.Significance("key_evidence"))
    ),
    Finding.Row(Finding.Claim("A claim with no place"), None, None, None, None, None, None, Nil, None),
    Finding.Row(
      Finding.Claim("Contested"),
      Some(Place.Region("Hatti")),
      None,
      None,
      Some(Year(-1200)),
      None,
      None,
      Nil,
      Some(Confidence.Contested)
    )
  )

  private val issues = List(
    FormatIssue(
      FormatRule.UnclosedFence,
      FormatSeverity.Error,
      FormatField.Summary,
      FormatIssue.Line(3),
      FormatIssue.Column(1),
      FormatIssue.Excerpt("```scala & <b>"),
      FormatIssue.Message("the fence is never closed: the rest reads as \"code\""),
      Some(FormatIssue.Fix("```"))
    ),
    FormatIssue(
      FormatRule.IndentedCode,
      FormatSeverity.Warning,
      FormatField.Body,
      FormatIssue.Line(7),
      FormatIssue.Column(5),
      FormatIssue.Excerpt(""),
      FormatIssue.Message("indented four spaces")
    )
  )

  private val source = SourcePage(
    cline,
    Some(SourcePage.Venue("Princeton UP & <Co>")),
    Some(SourcePage.PeriodStart(-1250)),
    Some(SourcePage.PeriodEnd(-1100)),
    List(SourcePage.Topics("bronze_age_collapse"), SourcePage.Topics("trade & \"routes\"")),
    List(SourcePage.Regions("Levant")),
    List(SourcePage.Tags("sea_peoples")),
    Some(
      SourcePage.Summary(
        "A synthesis of [[drews1993_end]] and [[anon_tablets]], see [[bronze_age_collapse|the article]] and " +
          "[[nothing_here]]. A <b>bold</b> & \"quoted\" word.\n\n![Figure 1](figures/fig_1.png)"
      )
    ),
    Some(
      SourcePage.Notes("Notes with a [link](https://example.org/?a=1&b=2) and an ![image](https://example.org/i.png).")
    ),
    findings,
    List(
      Citation.ToSource(drews, Citation.ToSource.Kind("challenges")),
      Citation.ToSource(anon, Citation.ToSource.Kind("references")),
      Citation.Unresolved(Citation.Unresolved.Id("ghost2019_x"), Citation.Unresolved.Kind("superseded_by"))
    ),
    List(Citation.ToArticle(topic, None), Citation.ToArticle(topic, Some(domain.library.Edge.Kind.Wire("cites")))),
    FullText.Shown(
      FullText.Shown.Markdown(
        "# 1177 BC\n\nThe text, citing [[drews1993_end]].\n\n![Map](figures/map.png)\n\n| a | b |\n|---|---|\n| 1 | 2 |"
      )
    ),
    Some(SourcePage.Query("sea & \"peoples\"")),
    issues
  )

  private val review = Article.ReviewView(
    Article.Slug("sea_peoples"),
    ReviewBadge(
      ReviewState.MajorsOpen,
      ReviewBadge.Label("1 major open"),
      ReviewBadge.Detail("Reviewed: 1 MAJOR finding is still open & \"unresolved\"."),
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
            ReviewFinding.Problem("a <cited> claim & \"more\""),
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
        ReviewGoal.Title("The Sea Peoples & the \"collapse\""),
        ReviewGoal.Status("compiled")
      ),
      ReviewGoal(ReviewGoal.Slug("other goal"), ReviewGoal.Title(""), ReviewGoal.Status("no-such-stage"))
    )
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val article = ArticlePage(
    Article.Row(
      Article.Slug("sea_peoples"),
      Article.Title("The Sea Peoples & <others>"),
      Article.Kind.Wire("concept"),
      Article.Body(
        """# The Sea "Peoples" & <others>
          |
          |Drews argued it [[drews1993_end]]; Cline disagreed [[cline2014]], again [[drews1993_end]]. The tablets
          |[[anon_tablets]] and [[bronze_age_collapse]] and [[ghost]]. A <b>bold</b> & "quoted" word.
          |
          || a | b |
          ||:--|--:|
          || 1 | 2 |
          |
          |## Not used
          |
          |- **[[cline2014_year]]**: already cited above.
          |
          |## Sources
          |
          |- [[drews1993_end]]
          |""".stripMargin
      ),
      None,
      List(Article.Tags("sea_peoples"), Article.Tags("trade & \"routes\"")),
      List(Article.Regions("Levant")),
      Some(Year(-1200)),
      Some(Year(-1150))
    ),
    List(
      Backlink(Backlink.Kind("article"), Backlink.Id("bronze_age_collapse"), Some(Backlink.Title("Collapse & after"))),
      Backlink(Backlink.Kind(Article.Links.TargetSource), Backlink.Id("cline2014_year"), None)
    ),
    List(
      CitedSource(
        Citekey("cline2014_year"),
        Some(cline.title),
        Some(Year(2014)),
        Some(Source.Status.Current),
        cline.authors,
        cline.doi
      ),
      CitedSource(
        Citekey("drews1993_end"),
        Some(drews.title),
        Some(Year(1993)),
        Some(Source.Status.Contested),
        drews.authors
      ),
      CitedSource(Citekey("missing2001_x"), None, None, None, Nil)
    ),
    issues,
    None,
    Some(review)
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def pages: String =
    val atoms = List(
      "chips" -> s"${HtmlRender.chips(ChipField.Tag, List("sea_peoples", " trade & \"routes\" ", "", "sea_peoples").map(HtmlRender.Label(_)))}",
      "statuses" -> Source.Status.values.map(s => s"${HtmlRender.statusBadge(s)}").mkString("\n"),
      "confidences" -> Confidence.values.map(c => s"${HtmlRender.confidenceBadge(c)}").mkString("\n"),
      "identifiers" -> s"${HtmlRender.identifier(cline)}\n${HtmlRender.identifier(anon)}",
      "readable" -> s"${HtmlRender.readable(cline)}\n${HtmlRender.readable(anon)}",
      "dangling" -> s"${HtmlRender.dangling(HtmlRender.Label("<ghost> & \"x\""))}",
      "findings" -> findings.map(f => s"${HtmlRender.finding(f)}").mkString("\n"),
      "match nav" -> List(0, 1, 7).map(n => s"${ArticleHtml.matchNav(ArticleHtml.MatchNavMatches(n))}").mkString("\n"),
      "toggles" -> List(
        s"${ArticleHtml.toggle(domain.library.Citekey("abc_d"), false)}",
        s"${ArticleHtml.toggle(domain.library.Citekey("abc_d"), true, Some(HtmlRender.Query("q&a \"x\"")))}"
      ).mkString("\n"),
      "format notices" -> List(
        s"${Pages.formatNotice(issues, "These notes", None)}",
        s"${Pages.formatNotice(issues.take(1), "This article", Some(Article.Slug("sea_peoples")))}",
        s"${Pages.formatNotice(Nil, "This article", None)}"
      ).mkString("\n"),
      "review line" -> s"${Pages.reviewLine(review)}",
      "review history" -> s"${Pages.reviewHistory(review)}"
    )
    val whole = List(
      "source page" -> s"${ArticleHtml.page(Title(cline.title.value), Pages.source(source, links), PageKind.Source, bundle)}",
      "source page, its text hidden" ->
        s"${Pages.source(source.modify(_.fullText).setTo(FullText.Hidden).modify(_.query).setTo(None), links)}",
      "source page, its text missing" ->
        s"${Pages.source(source.modify(_.fullText).setTo(FullText.Missing).modify(_.formatting).setTo(Nil), links)}",
      "article page" ->
        s"${ArticleHtml.page(Title(article.article.title.value), Pages.article(article, links), PageKind.Article, bundle)}",
      "article page, no review, nothing cited" ->
        s"${Pages.article(article.modify(_.review).setTo(None).modify(_.cited).setTo(Nil), links)}"
    )
    (atoms ++ whole).map((name, text) => s"== $name\n$text\n").mkString("\n")

  test("the pages' bytes are the recorded ones"):
    val now = pages
    if update then
      Files.createDirectories(file.getParent)
      val _ = Files.writeString(file, now)
    else
      assert(Files.exists(file), s"$file is missing; write it with HISTMERSION_UPDATE_ORACLE=1")
      assertEquals(now, Files.readString(file))
