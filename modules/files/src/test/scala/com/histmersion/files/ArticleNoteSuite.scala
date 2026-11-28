package com.histmersion
package files
import com.histmersion.util.problem.Problem

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.given
import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.domain.research.articles.ArticleNote
import com.histmersion.api.catalogue.Period
import com.histmersion.api.research.Article
import com.histmersion.api.library.Source
import com.histmersion.api.research.Coverage
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Filter
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.lint.{tolerated, LintRule}

/** `wiki/<kind>/<slug>.md` ⇄ [[Article]] (plan §5.2 stream 2.7). The shapes asserted here are the ones the existing
  * research wiki actually writes — that is what "the 40+ existing articles import" means in the acceptance line.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ArticleNoteSuite extends munit.FunSuite:

  // exactly the head of wiki/concepts/sea_peoples.md
  private val wikiNote =
    """---
      |type: concept
      |slug: sea_peoples
      |regions: [Eastern Mediterranean, Egypt, Levant, Cyprus, Anatolia, Aegean]
      |period_start: -1220
      |period_end: -1100
      |status: active
      |---
      |
      |# The Sea Peoples
      |
      |*A loose label for the coalitions named in Egyptian texts as agents of the destructions
      |around 1200 BCE.*
      |
      |## The textual record
      |
      |The canonical source is Medinet Habu [[cline2014_year]].
      |""".stripMargin

  test("a real wiki article parses: kind, slug, regions, period, title from the heading"):
    val a = ArticleNote.parse(wikiNote, Article.Slug("fallback"), Article.KindValue("unknown")).get
    assertEquals(a.slug, "sea_peoples")
    assertEquals(a.kind, "concept")
    assertEquals(a.title, "The Sea Peoples")
    assertEquals(a.regions, List("Eastern Mediterranean", "Egypt", "Levant", "Cyprus", "Anatolia", "Aegean"))
    assertEquals(a.period, Some(Period(Year(-1220), Year(-1100))))
    assert(a.body.value.startsWith("# The Sea Peoples"), a.body.value.take(40))
    assert(a.body.value.contains("[[cline2014_year]]"), "the body keeps its wikilinks verbatim")

  test("the italic lede becomes the summary when the frontmatter has none"):
    val a = ArticleNote.parse(wikiNote, Article.Slug("x"), Article.KindValue("concept")).get
    assert(a.summary.exists(_.value.startsWith("A loose label")), a.summary.toString)
    assert(!a.summary.exists(_.value.contains("*")), a.summary.toString)

  test("a report's `question` is its summary; topics and cultures become tags"):
    val note =
      """---
        |type: report
        |question: "Which gods came from the Near East vs from PIE?"
        |topics: [mycenaean_religion, pie_religion]
        |cultures: [Vedic, Greek]
        |---
        |
        |# The Two Sources of the Gods
        |""".stripMargin
    val a = ArticleNote.parse(note, Article.Slug("gods_and_migrations"), Article.KindValue("report")).get
    assertEquals(a.slug, "gods_and_migrations") // no `slug:` key → the filename
    assertEquals(a.kind, "report")
    assertEquals(a.summary, Some("Which gods came from the Near East vs from PIE?"))
    assertEquals(a.tags, List("mycenaean_religion", "pie_religion", "Vedic", "Greek"))

  test("the directory is the fallback kind; frontmatter `type` wins"):
    val bare = "---\nslug: divine_twins\n---\n\n# The Divine Twins\n"
    assertEquals(ArticleNote.parse(bare, Article.Slug("divine_twins"), Article.KindValue("myth")).get.kind, "myth")

  test("no frontmatter fence at all is not an article note"):
    assertEquals(ArticleNote.parse("# Just Markdown\n", Article.Slug("x"), Article.KindValue("concept")), None)

  test("render → parse → render is byte-stable (the export/import round trip)"):
    val a = Article(
      slug = Article.Slug("sea_peoples"),
      title = Article.Title("The Sea Peoples"),
      kind = Article.KindValue("concept"),
      body = Article.Body("# The Sea Peoples\n\nMedinet Habu [[cline2014_year]]."),
      summary = Some(Article.SummaryText("A loose label.")),
      tags = List(Article.Tags("sea-peoples"), Article.Tags("collapse")),
      regions = List(Article.Regions("Levant"), Article.Regions("Egypt")),
      period = Some(Period(Year(-1220), Year(-1100)))
    )
    val once = ArticleNote.render(a)
    val again = ArticleNote.render(ArticleNote.parse(once, Article.Slug("zz"), Article.KindValue("zz")).get)
    assertEquals(again, once)
    assert(once.startsWith("---\ntype: concept\n"), once.take(60))
    assert(once.contains("period_start: -1220"), once)

  test("render carries no timestamp, so an unchanged article exports identically every time"):
    val a = Article(
      Article.Slug("x_y"),
      Article.Title("T"),
      Article.KindValue("concept"),
      Article.Body("# T"),
      updatedAt = Some(Article.UpdatedAt("2026-08-25T10:00:00.000000Z"))
    )
    assert(!ArticleNote.render(a).contains("2026-08-25"), ArticleNote.render(a))

  test("the lede skips a leading image embed line and never invents a summary"):
    assertEquals(Article.lede(Article.Body("# T\n\n")), None)
    assertEquals(Article.lede(Article.Body("# T\n\n![[map.png]]\n")), None)

  test("the lede strips a MATCHED emphasis pair only"):
    assertEquals(Article.lede(Article.Body("# T\n\n*A lede.*\n")), Some("A lede."))
    assertEquals(Article.lede(Article.Body("# T\n\n_A lede._\n")), Some("A lede."))
    assertEquals(Article.lede(Article.Body("# T\n\n**A lede.**\n")), Some("A lede."))
    assertEquals(Article.lede(Article.Body("# T\n\nsome *emphasis* inside\n")), Some("some *emphasis* inside"))

  test(
    "#958: the filter and reasons travel in the frontmatter; the generated sections are exported and dropped on import"
  ):
    val a = Article(
      slug = Article.Slug("theseus_origins"),
      title = Article.Title("Theseus"),
      kind = Article.KindValue("myth"),
      body = Article.Body("# Theseus\n\nAn Attic hero [[walker1995_theseus]]."),
      summary = Some(Article.SummaryText("Where the hero Theseus came from.")),
      filter = Some(
        Article.Filter(
          query = Some(Filter.Q("Theseus")),
          topics = List(Filter.Topics("greek_myth")),
          from = Some(Year(-1500)),
          to = Some(Year(-400)),
          extraQueries = List(Article.Filter.ExtraQueries("synoikism"))
        )
      ),
      reasons = Map(
        Citekey("graves2017_greek") -> Coverage.Rationale("retells the myths"),
        Citekey("nilsson1927_minoan") -> Coverage.Rationale("Attic origin only in passing")
      )
    )
    val c = Article.Coverage.compute(
      a.slug,
      a.filter.get,
      Article.Coverage.ComputeTotal(1),
      List(Coverage.Source(Citekey("graves2017_greek"), Title("Greek Myths"), Some(Year(2017)), Source.Status.Current)),
      Set.empty,
      List(Citekey("walker1995_theseus")),
      a.reasons,
      Nil,
      Nil
    )
    val text = ArticleNote.render(a, Some(Article.Coverage.exported(c).value))
    assert(
      text.contains(
        "filter:\n  query: Theseus\n  topics:\n  - greek_myth\n  from: -1500\n  to: -400\n  extra_queries:\n  - synoikism\n"
      ),
      text
    )
    assert(text.contains("reasons:\n  graves2017_greek: retells the myths\n"), text)
    assert(
      text.contains("## Not used") && text.contains(Article.Coverage.GeneratedStart) && text.contains("_Method: "),
      text
    )
    val back = ArticleNote.parse(text, Article.Slug("zz"), Article.KindValue("zz")).get
    assertEquals(back.body, a.body, "the generated sections never reach the stored body")
    assertEquals((back.filter, back.reasons), (a.filter, a.reasons))
    // and the round trip stays byte-stable
    assertEquals(ArticleNote.render(back, Some(Article.Coverage.exported(c).value)), text)
    // an article with none exports exactly as before
    assert(!ArticleNote.render(a.modify(_.filter).setTo(None).modify(_.reasons).setTo(Map.empty)).contains("filter:"))
