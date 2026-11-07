package com.histmersion.db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.library.sources.SourceCheckFixtures
import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.catalogue.places.GeocodeSettings

import com.histmersion.api.GeoPoint
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.checks.Lint.*

import com.augustnagro.magnum.*
import com.histmersion.api.library.Confidence
import com.histmersion.util.database.Migrate
import com.histmersion.api.catalogue.{Latitude, Longitude, Period}
import com.histmersion.api.checks.{FormatIssue, FormatRule, FormatSeverity, Lint}
import com.histmersion.api.library.{Edge, Finding, Source}
import com.histmersion.api.research.Article
import com.histmersion.util.database.given

/** Every lint rule, against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise.
  *
  * The fixtures are deliberately one source per defect, all under a `zzlint_` prefix, so the assertions can ignore
  * whatever else the test database happens to hold.
  */
class LintRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(testDb.get))
    Migrate.run(d)
    d

  private val Prefix = "zzlint_"

  private lazy val geo =
    GeocodeSettings(
      GeocodeSettings.PlaceholderMin(cfg.places.geocodePlaceholderMin.value),
      GeocodeSettings.RepairKm(cfg.places.geocodeRepairKm.value),
      GeocodeSettings.ToleranceKm(cfg.places.pointToleranceKm.value)
    )

  private def source(
      citekey: String,
      status: com.histmersion.api.library.Source.Status = com.histmersion.api.library.Source.Status.Current,
      regions: List[Source.Regions] = List(Source.Regions("Anatolia")),
      period: Option[Period] = Some(Period(com.histmersion.api.catalogue.Year(-2000), com.histmersion.api.catalogue.Year(-1500))),
      summary: Option[Source.Summary] = Some(Source.Summary("a summary")),
      doi: Option[String] = None,
      body: Option[Source.OcrMarkdown] = None,
      findings: List[Finding] = Nil,
      edges: List[Edge] = Nil
  ) = Source(
    citekey = com.histmersion.api.library.Citekey(citekey),
    title = com.histmersion.api.library.Title(s"Fixture $citekey"),
    authors = (List("A")).map(com.histmersion.api.library.AuthorName.apply),
    doi = (doi).map(com.histmersion.api.library.Doi.apply),
    year = (Some(2020)).map(com.histmersion.api.catalogue.Year.apply),
    venue = None,
    status = status,
    topics = List(com.histmersion.api.library.Source.Topics("t")),
    regions = regions,
    tags = Nil,
    period = period,
    summary = summary,
    citedByCount = None,
    influentialCitations = None,
    provenance = None,
    calibreId = None,
    ocrMarkdown = body,
    findings = findings,
    edges = edges
  )

  private def finding(id: Finding.Id, ck: String, period: Option[Period], region: Option[Finding.Region], geo: Option[GeoPoint]) =
    Finding(id, com.histmersion.api.library.Citekey(ck), com.histmersion.api.library.Finding.Claim("a claim"), region, geo, period, None, Nil, Some(Confidence.High))

  /** Fresh fixtures for every test — the rules are order-independent, but a half-written corpus from a previous failure
    * must not leak into the next assertion.
    */
  override def beforeEach(context: BeforeEach): Unit =
    connect(ds)(sql"DELETE FROM sources WHERE citekey LIKE ${Prefix + "%"}".update.run())
    connect(ds)(sql"DELETE FROM edges WHERE src LIKE ${Prefix + "%"}".update.run())

  private def put(s: Source): Unit = SourceFixtures.upsert(ds, s, cfg.limits.chunkChars)

  /** A stand-in for the server's flexmark check (the db module has no renderer): every `**` is a problem, so the
    * formatting rules' SQL and wiring are tested here, the check in `MarkdownLintSuite`.
    */
  private val checker: FormatChecker = (text, field, article) =>
    if text.contains("**") then
      List(
        FormatIssue(
          FormatRule.Emphasis,
          FormatSeverity.Error,
          field,
          com.histmersion.api.checks.FormatIssue.Line(1),
          com.histmersion.api.checks.FormatIssue.Column(text.indexOf("**") + 1),
          if article then com.histmersion.api.checks.FormatIssue.Excerpt("article") else com.histmersion.api.checks.FormatIssue.Excerpt("not an article"),
          com.histmersion.api.checks.FormatIssue.Message("unpaired")
        )
      )
    else Nil

  private def run(rule: Lint.Rule.Name): List[LintItem] =
    val rep =
      SourceCheckFixtures.run(
        ds,
        List(Lint.rule(rule).get),
        Lint.ItemLimit(cfg.lint.maxItems.value),
        Lint.ItemLimit(cfg.lint.suggestions.value),
        checker,
        geo
      )
    rep.rules.head.items.filter(i => i.citekey.value.startsWith(Prefix))

  private def keys(rule: Lint.Rule.Name): List[String] = run(rule).map(_.citekey.value).distinct

  /** The rules the catalogue and the media register themselves (`domain.catalogue.CatalogueData`,
    * `domain.media.MediaData`): their own suites run them.
    */
  private val OwnRules: Set[Lint.Rule.Name] =
    Set(
      Lint.PlaceKindRule,
      Lint.PlaceGridPoint,
      Lint.PlaceImportedPointArea,
      Lint.EventCorrectionConflict,
      Lint.MediaLicenceRule
    ).map(Lint.Rule.Name(_))

  test("every rule of the data management's has SQL behind it"):
    Lint.names.filterNot(OwnRules).foreach(n => assert(SourceCheckFixtures.sqlOf(n).nonEmpty, s"$n has no statement"))

  test("every rule executes against the real schema"):
    val rep = SourceCheckFixtures.run(ds, Lint.all, Lint.ItemLimit(cfg.lint.maxItems.value), Lint.ItemLimit(cfg.lint.suggestions.value), checker, geo)
    assertEquals(rep.rules.map(_.rule), Lint.names)

  test("no-findings / no-period / no-regions / empty-summary / unassessed-status"):
    put(
      source(
        Prefix + "bare",
        status = com.histmersion.api.library.Source.Status.Unassessed,
        regions = Nil,
        period = None,
        summary = Some(Source.Summary("   "))
      )
    )
    put(
      source(
        Prefix + "full",
        findings = List(
          finding(
            Finding.Id(Prefix + "full#0"),
            Prefix + "full",
            Some(Period(com.histmersion.api.catalogue.Year(-1800), com.histmersion.api.catalogue.Year(-1700))),
            Some(Finding.Region("Anatolia")),
            Some(GeoPoint(Longitude(32.0), Latitude(39.0)))
          )
        )
      )
    )
    List(NoFindings, NoPeriod, NoRegions, EmptySummary, UnassessedStatus).foreach { rule =>
      assertEquals(keys(Lint.Rule.Name(rule)), List(Prefix + "bare"), s"$rule")
    }

  test("no-findings carries the title, so the report is actionable without a second lookup"):
    put(source(Prefix + "bare", findings = Nil))
    assert(run(Lint.Rule.Name(NoFindings)).head.detail.value.contains("Fixture"), run(Lint.Rule.Name(NoFindings)).head.detail)

  test("dangling-edges finds targets that are not citekeys and suggests the closest"):
    put(
      source(
        Prefix + "src",
        edges = List(
          Edge(com.histmersion.api.library.Citekey(Prefix + "src"), com.histmersion.api.library.Citekey(Prefix + "target_full"), com.histmersion.api.library.Edge.Kind.References), // resolves
          Edge(com.histmersion.api.library.Citekey(Prefix + "src"), com.histmersion.api.library.Citekey(Prefix + "target"), com.histmersion.api.library.Edge.Kind.Cites), // short form
          Edge(com.histmersion.api.library.Citekey(Prefix + "src"), com.histmersion.api.library.Citekey(Prefix + "vanished"), com.histmersion.api.library.Edge.Kind.Refutes)
        )
      )
    ) // dead
    put(source(Prefix + "target_full"))
    val items = run(Lint.Rule.Name(DanglingEdges))
    assertEquals(items.size, 2)
    assert(items.exists(_.detail.value.contains(s"cites → ${Prefix}target")), items.toString)
    assert(items.exists(_.detail.value.contains(s"closest: ${Prefix}target_full")), items.toString)
    assert(items.exists(_.detail.value.contains("refutes → " + Prefix + "vanished")), items.toString)

  test("unresolved-short-citekeys is the fixable subset, and `lint --fix` really fixes it"):
    put(source(Prefix + "src", edges = List(Edge(com.histmersion.api.library.Citekey(Prefix + "src"), com.histmersion.api.library.Citekey(Prefix + "target"), com.histmersion.api.library.Edge.Kind.Cites))))
    put(source(Prefix + "target_full"))
    assertEquals(run(Lint.Rule.Name(UnresolvedShortCitekeys)).size, 1)
    assert(run(Lint.Rule.Name(UnresolvedShortCitekeys)).head.detail.value.contains("resolves to"))
    val _ = SourceFixtures.resolveEdges(ds)
    assertEquals(run(Lint.Rule.Name(UnresolvedShortCitekeys)), Nil)
    assertEquals(run(Lint.Rule.Name(DanglingEdges)), Nil)

  test("orphan-sources: no edges in, no edges out"):
    put(source(Prefix + "lonely"))
    put(source(Prefix + "linked", edges = List(Edge(com.histmersion.api.library.Citekey(Prefix + "linked"), com.histmersion.api.library.Citekey(Prefix + "lonely"), com.histmersion.api.library.Edge.Kind.Cites))))
    val ks = keys(Lint.Rule.Name(OrphanSources))
    assert(!ks.contains(Prefix + "lonely"), "an incoming edge is enough")
    assert(!ks.contains(Prefix + "linked"), "an outgoing edge is enough")
    put(source(Prefix + "island"))
    assert(keys(Lint.Rule.Name(OrphanSources)).contains(Prefix + "island"))

  test("duplicate-doi flags every member of the group"):
    put(source(Prefix + "a", doi = Some("10.1234/SHARED")))
    put(source(Prefix + "b", doi = Some(" 10.1234/shared ")))
    put(source(Prefix + "c", doi = Some("10.1234/unique")))
    assertEquals(keys(Lint.Rule.Name(DuplicateDoi)), List(Prefix + "a", Prefix + "b"))

  test("duplicate-sha256 reports nothing while the column is unpopulated, and finds a pair when set"):
    put(source(Prefix + "a"))
    put(source(Prefix + "b"))
    assertEquals(keys(Lint.Rule.Name(DuplicateSha256)), Nil)
    connect(ds):
      sql"UPDATE sources SET sha256 = 'deadbeef' WHERE citekey LIKE ${Prefix + "%"}".update.run()
    assertEquals(keys(Lint.Rule.Name(DuplicateSha256)), List(Prefix + "a", Prefix + "b"))

  test("stale-chunks: a body with no chunks, cleared by the fix"):
    put(source(Prefix + "body", body = Some(Source.OcrMarkdown("# Heading\n\n" + "sea peoples pottery. " * 200))))
    assertEquals(keys(Lint.Rule.Name(StaleChunks)), Nil, "upsert indexes as it writes")
    connect(ds)(sql"DELETE FROM source_chunks WHERE citekey = ${Prefix + "body"}".update.run())
    assertEquals(keys(Lint.Rule.Name(StaleChunks)), List(Prefix + "body"))
    // NB: `reindexStale` is corpus-wide, so it also sweeps whatever other suites left stale in this
    // shared test database. That is exactly why it is best-effort per source; the assertion is about
    // THIS suite's fixture.
    val r = SourceCheckFixtures.reindexStale(ds, com.histmersion.util.ocr.Chunker.ChunkMaxChars(cfg.limits.chunkChars.value), com.histmersion.domain.library.sources.SourceRepairs.MaxChars(cfg.limits.reindexChars.value))
    assert(r.chunks.value > 0, s"reindexStale wrote no chunks: $r")
    assertEquals(keys(Lint.Rule.Name(StaleChunks)), Nil)

  test("reindexStale reports nothing left once it has swept everything stale"):
    val _ = SourceCheckFixtures.reindexStale(ds, com.histmersion.util.ocr.Chunker.ChunkMaxChars(cfg.limits.chunkChars.value), com.histmersion.domain.library.sources.SourceRepairs.MaxChars(cfg.limits.reindexChars.value))
    val r = SourceCheckFixtures.reindexStale(ds, com.histmersion.util.ocr.Chunker.ChunkMaxChars(cfg.limits.chunkChars.value), com.histmersion.domain.library.sources.SourceRepairs.MaxChars(cfg.limits.reindexChars.value))
    assertEquals(r.remaining, 0)

  test("undated-findings and unlocated-findings, named by finding id"):
    val ck = Prefix + "f"
    put(
      source(
        ck,
        findings = List(
          finding(Finding.Id(s"$ck#0"), ck, None, Some(Finding.Region("Anatolia")), Some(GeoPoint(Longitude(32.0), Latitude(39.0)))), // undated
          finding(Finding.Id(s"$ck#1"), ck, Some(Period(com.histmersion.api.catalogue.Year(-1800), com.histmersion.api.catalogue.Year(-1700))), None, None), // unlocated
          finding(Finding.Id(s"$ck#2"), ck, Some(Period(com.histmersion.api.catalogue.Year(-1800), com.histmersion.api.catalogue.Year(-1700))), Some(Finding.Region("Anatolia")), Some(GeoPoint(Longitude(32.0), Latitude(39.0))))
        )
      )
    )
    assertEquals(run(Lint.Rule.Name(UndatedFindings)).map(_.detail.value).filter(_.startsWith(s"$ck#0")).size, 1)
    assertEquals(run(Lint.Rule.Name(UnlocatedFindings)).map(_.detail.value).filter(_.startsWith(s"$ck#1")).size, 1)
    assert(!run(Lint.Rule.Name(UnlocatedFindings)).exists(_.detail.value.startsWith(s"$ck#2")))
    // a region name alone is enough to be located
    assert(!run(Lint.Rule.Name(UnlocatedFindings)).exists(_.detail.value.startsWith(s"$ck#0")))

  test("the item cap trims the list but never the count"):
    (1 to 5).foreach(i => put(source(s"${Prefix}many$i", findings = Nil)))
    val rep =
      SourceCheckFixtures.run(
        ds,
        List(Lint.rule(Lint.Rule.Name(NoFindings)).get),
        maxItems = com.histmersion.api.checks.Lint.ItemLimit(2),
        suggestions = com.histmersion.api.checks.Lint.ItemLimit(3),
        format = checker,
        geocode = geo
      )
    assertEquals(rep.rules.head.items.size, 2)
    assert(rep.rules.head.count.value >= 5, rep.rules.head.count.toString)

  test("article-markdown and note-markdown check every article body and summary, every source's summary and notes"):
    val slug = Prefix + "fmt_article"
    connect(ds)(sql"DELETE FROM articles WHERE slug = $slug".update.run())
    ArticleFixtures.put(ds, Article(Article.Slug(slug), com.histmersion.api.research.Article.Title("Fmt"), com.histmersion.api.research.Article.KindValue("concept"), com.histmersion.api.research.Article.Body("# Fmt\n\nA ** bad line."), Some(com.histmersion.api.research.Article.SummaryText("a fine summary"))))
    put(source(Prefix + "fmt_note", summary = Some(Source.Summary("a **bad summary"))).modify(_.notes).setTo(Some(Source.Notes("fine notes"))))
    put(source(Prefix + "fmt_clean", summary = Some(Source.Summary("clean"))).modify(_.notes).setTo(Some(Source.Notes("clean"))))
    try
      val art = run(Lint.Rule.Name(ArticleFormat))
      assertEquals(art.map(_.citekey), List(slug))
      assert(art.head.detail.value.startsWith("body L1:"), art.head.detail)
      assert(art.head.detail.value.contains("emphasis"), art.head.detail)
      assert(art.head.detail.value.contains("`article`"), "the body is checked AS an article: " + art.head.detail)
      val notes = run(Lint.Rule.Name(NoteFormat))
      assertEquals(notes.map(_.citekey), List(Prefix + "fmt_note"))
      assert(notes.head.detail.value.startsWith("summary L1:3"), notes.head.detail)
      assert(notes.head.detail.value.contains("`not an article`"), notes.head.detail)
    finally connect(ds)(sql"DELETE FROM articles WHERE slug = $slug".update.run())
