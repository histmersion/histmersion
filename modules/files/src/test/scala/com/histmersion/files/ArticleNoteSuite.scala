package com.histmersion.files

import com.histmersion.api.*
import com.histmersion.api.given
import com.histmersion.values.SourceStatus

/** `wiki/<kind>/<slug>.md` ⇄ [[Article]] (plan §5.2 stream 2.7). The shapes asserted here are the
  * ones the existing research wiki actually writes — that is what "the 40+ existing articles import"
  * means in the acceptance line. */
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
    val a = ArticleNote.parse(wikiNote, "fallback", "unknown").get
    assertEquals(a.slug, "sea_peoples")
    assertEquals(a.kind, "concept")
    assertEquals(a.title, "The Sea Peoples")
    assertEquals(a.regions, List("Eastern Mediterranean", "Egypt", "Levant", "Cyprus", "Anatolia", "Aegean"))
    assertEquals(a.period, Some(Period(-1220, -1100)))
    assert(a.body.startsWith("# The Sea Peoples"), a.body.take(40))
    assert(a.body.contains("[[cline2014_year]]"), "the body keeps its wikilinks verbatim")

  test("the italic lede becomes the summary when the frontmatter has none"):
    val a = ArticleNote.parse(wikiNote, "x", "concept").get
    assert(a.summary.exists(_.startsWith("A loose label")), a.summary.toString)
    assert(!a.summary.exists(_.contains("*")), a.summary.toString)

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
    val a = ArticleNote.parse(note, "gods_and_migrations", "report").get
    assertEquals(a.slug, "gods_and_migrations")     // no `slug:` key → the filename
    assertEquals(a.kind, "report")
    assertEquals(a.summary, Some("Which gods came from the Near East vs from PIE?"))
    assertEquals(a.tags, List("mycenaean_religion", "pie_religion", "Vedic", "Greek"))

  test("the directory is the fallback kind; frontmatter `type` wins"):
    val bare = "---\nslug: divine_twins\n---\n\n# The Divine Twins\n"
    assertEquals(ArticleNote.parse(bare, "divine_twins", "myth").get.kind, "myth")

  test("no frontmatter fence at all is not an article note"):
    assertEquals(ArticleNote.parse("# Just Markdown\n", "x", "concept"), None)

  test("render → parse → render is byte-stable (the export/import round trip)"):
    val a = Article(
      slug = "sea_peoples", title = "The Sea Peoples", kind = "concept",
      body = "# The Sea Peoples\n\nMedinet Habu [[cline2014_year]].",
      summary = Some("A loose label."), tags = List("sea-peoples", "collapse"),
      regions = List("Levant", "Egypt"), period = Some(Period(-1220, -1100)))
    val once  = ArticleNote.render(a)
    val again = ArticleNote.render(ArticleNote.parse(once, "zz", "zz").get)
    assertEquals(again, once)
    assert(once.startsWith("---\ntype: concept\n"), once.take(60))
    assert(once.contains("period_start: -1220"), once)

  test("render carries no timestamp, so an unchanged article exports identically every time"):
    val a = Article("x_y", "T", "concept", "# T", updatedAt = Some("2026-08-25T10:00:00.000000Z"))
    assert(!ArticleNote.render(a).contains("2026-08-25"), ArticleNote.render(a))

  test("the lede skips a leading image embed line and never invents a summary"):
    assertEquals(Articles.lede("# T\n\n"), None)
    assertEquals(Articles.lede("# T\n\n![[map.png]]\n"), None)

  test("the lede strips a MATCHED emphasis pair only"):
    assertEquals(Articles.lede("# T\n\n*A lede.*\n"), Some("A lede."))
    assertEquals(Articles.lede("# T\n\n_A lede._\n"), Some("A lede."))
    assertEquals(Articles.lede("# T\n\n**A lede.**\n"), Some("A lede."))
    assertEquals(Articles.lede("# T\n\nsome *emphasis* inside\n"), Some("some *emphasis* inside"))

  test("#958: the filter and reasons travel in the frontmatter; the generated sections are exported and dropped on import"):
    val a = Article(
      slug = "theseus_origins", title = "Theseus", kind = "myth", body = "# Theseus\n\nAn Attic hero [[walker1995_theseus]].",
      summary = Some("Where the hero Theseus came from."),
      filter = Some(ArticleFilter(query = Some("Theseus"), topics = List("greek_myth"), from = Some(-1500), to = Some(-400),
        extraQueries = List("synoikism"))),
      reasons = Map("graves2017_greek" -> "retells the myths", "nilsson1927_minoan" -> "Attic origin only in passing"))
    val c = ArticleCoverage.compute(a.slug, a.filter.get, 1, List(CoverageSource("graves2017_greek", "Greek Myths", Some(2017),
      SourceStatus.Current)), Set.empty, List("walker1995_theseus"), a.reasons, Nil, Nil)
    val text = ArticleNote.render(a, Some(ArticleCoverage.exported(c)))
    assert(text.contains("filter:\n  query: Theseus\n  topics:\n  - greek_myth\n  from: -1500\n  to: -400\n  extra_queries:\n  - synoikism\n"), text)
    assert(text.contains("reasons:\n  graves2017_greek: retells the myths\n"), text)
    assert(text.contains("## Not used") && text.contains(ArticleCoverage.GeneratedStart) && text.contains("_Method: "), text)
    val back = ArticleNote.parse(text, "zz", "zz").get
    assertEquals(back.body, a.body, "the generated sections never reach the stored body")
    assertEquals((back.filter, back.reasons), (a.filter, a.reasons))
    // and the round trip stays byte-stable
    assertEquals(ArticleNote.render(back, Some(ArticleCoverage.exported(c))), text)
    // an article with none exports exactly as before
    assert(!ArticleNote.render(a.copy(filter = None, reasons = Map.empty)).contains("filter:"))
