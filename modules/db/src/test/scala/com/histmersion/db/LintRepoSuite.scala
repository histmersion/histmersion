package com.histmersion.db

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
import com.histmersion.api.catalogue.Period
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
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d)
    d

  private val Prefix = "zzlint_"

  private lazy val geo =
    GeocodeSettings(cfg.places.geocodePlaceholderMin, cfg.places.geocodeRepairKm, cfg.places.pointToleranceKm)

  private def source(
      citekey: String,
      status: com.histmersion.api.library.Source.Status = com.histmersion.api.library.Source.Status.Current,
      regions: List[String] = List("Anatolia"),
      period: Option[Period] = Some(Period(-2000, -1500)),
      summary: Option[String] = Some("a summary"),
      doi: Option[String] = None,
      body: Option[String] = None,
      findings: List[Finding] = Nil,
      edges: List[Edge] = Nil
  ) = Source(
    citekey = citekey,
    title = s"Fixture $citekey",
    authors = List("A"),
    doi = doi,
    year = Some(2020),
    venue = None,
    status = status,
    topics = List("t"),
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

  private def finding(id: String, ck: String, period: Option[Period], region: Option[String], geo: Option[GeoPoint]) =
    Finding(id, ck, "a claim", region, geo, period, None, Nil, Some(Confidence.High))

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
          1,
          text.indexOf("**") + 1,
          if article then "article" else "not an article",
          "unpaired"
        )
      )
    else Nil

  private def run(rule: String): List[LintItem] =
    val rep =
      SourceCheckFixtures.run(ds, List(Lint.rule(rule).get), cfg.lint.maxItems, cfg.lint.suggestions, checker, geo)
    rep.rules.head.items.filter(i => i.citekey.startsWith(Prefix))

  private def keys(rule: String): List[String] = run(rule).map(_.citekey).distinct

  /** The rules the catalogue and the media register themselves (`domain.catalogue.CatalogueData`,
    * `domain.media.MediaData`): their own suites run them.
    */
  private val OwnRules =
    Set(
      Lint.PlaceKindRule,
      Lint.PlaceGridPoint,
      Lint.PlaceImportedPointArea,
      Lint.EventCorrectionConflict,
      Lint.MediaLicenceRule
    )

  test("every rule of the data management's has SQL behind it"):
    Lint.names.filterNot(OwnRules).foreach(n => assert(SourceCheckFixtures.sqlOf(n).nonEmpty, s"$n has no statement"))

  test("every rule executes against the real schema"):
    val rep = SourceCheckFixtures.run(ds, Lint.all, cfg.lint.maxItems, cfg.lint.suggestions, checker, geo)
    assertEquals(rep.rules.map(_.rule), Lint.names)

  test("no-findings / no-period / no-regions / empty-summary / unassessed-status"):
    put(
      source(
        Prefix + "bare",
        status = com.histmersion.api.library.Source.Status.Unassessed,
        regions = Nil,
        period = None,
        summary = Some("   ")
      )
    )
    put(
      source(
        Prefix + "full",
        findings = List(
          finding(
            Prefix + "full#0",
            Prefix + "full",
            Some(Period(-1800, -1700)),
            Some("Anatolia"),
            Some(GeoPoint(32.0, 39.0))
          )
        )
      )
    )
    List(NoFindings, NoPeriod, NoRegions, EmptySummary, UnassessedStatus).foreach { rule =>
      assertEquals(keys(rule), List(Prefix + "bare"), s"$rule")
    }

  test("no-findings carries the title, so the report is actionable without a second lookup"):
    put(source(Prefix + "bare", findings = Nil))
    assert(run(NoFindings).head.detail.contains("Fixture"), run(NoFindings).head.detail)

  test("dangling-edges finds targets that are not citekeys and suggests the closest"):
    put(
      source(
        Prefix + "src",
        edges = List(
          Edge(Prefix + "src", Prefix + "target_full", "references"), // resolves
          Edge(Prefix + "src", Prefix + "target", "cites"), // short form
          Edge(Prefix + "src", Prefix + "vanished", "refutes")
        )
      )
    ) // dead
    put(source(Prefix + "target_full"))
    val items = run(DanglingEdges)
    assertEquals(items.size, 2)
    assert(items.exists(_.detail.contains(s"cites → ${Prefix}target")), items.toString)
    assert(items.exists(_.detail.contains(s"closest: ${Prefix}target_full")), items.toString)
    assert(items.exists(_.detail.contains("refutes → " + Prefix + "vanished")), items.toString)

  test("unresolved-short-citekeys is the fixable subset, and `lint --fix` really fixes it"):
    put(source(Prefix + "src", edges = List(Edge(Prefix + "src", Prefix + "target", "cites"))))
    put(source(Prefix + "target_full"))
    assertEquals(run(UnresolvedShortCitekeys).size, 1)
    assert(run(UnresolvedShortCitekeys).head.detail.contains("resolves to"))
    val _ = SourceFixtures.resolveEdges(ds)
    assertEquals(run(UnresolvedShortCitekeys), Nil)
    assertEquals(run(DanglingEdges), Nil)

  test("orphan-sources: no edges in, no edges out"):
    put(source(Prefix + "lonely"))
    put(source(Prefix + "linked", edges = List(Edge(Prefix + "linked", Prefix + "lonely", "cites"))))
    val ks = keys(OrphanSources)
    assert(!ks.contains(Prefix + "lonely"), "an incoming edge is enough")
    assert(!ks.contains(Prefix + "linked"), "an outgoing edge is enough")
    put(source(Prefix + "island"))
    assert(keys(OrphanSources).contains(Prefix + "island"))

  test("duplicate-doi flags every member of the group"):
    put(source(Prefix + "a", doi = Some("10.1234/SHARED")))
    put(source(Prefix + "b", doi = Some(" 10.1234/shared ")))
    put(source(Prefix + "c", doi = Some("10.1234/unique")))
    assertEquals(keys(DuplicateDoi), List(Prefix + "a", Prefix + "b"))

  test("duplicate-sha256 reports nothing while the column is unpopulated, and finds a pair when set"):
    put(source(Prefix + "a"))
    put(source(Prefix + "b"))
    assertEquals(keys(DuplicateSha256), Nil)
    connect(ds):
      sql"UPDATE sources SET sha256 = 'deadbeef' WHERE citekey LIKE ${Prefix + "%"}".update.run()
    assertEquals(keys(DuplicateSha256), List(Prefix + "a", Prefix + "b"))

  test("stale-chunks: a body with no chunks, cleared by the fix"):
    put(source(Prefix + "body", body = Some("# Heading\n\n" + "sea peoples pottery. " * 200)))
    assertEquals(keys(StaleChunks), Nil, "upsert indexes as it writes")
    connect(ds)(sql"DELETE FROM source_chunks WHERE citekey = ${Prefix + "body"}".update.run())
    assertEquals(keys(StaleChunks), List(Prefix + "body"))
    // NB: `reindexStale` is corpus-wide, so it also sweeps whatever other suites left stale in this
    // shared test database. That is exactly why it is best-effort per source; the assertion is about
    // THIS suite's fixture.
    val r = SourceCheckFixtures.reindexStale(ds, cfg.limits.chunkChars, cfg.limits.reindexChars)
    assert(r.chunks > 0, s"reindexStale wrote no chunks: $r")
    assertEquals(keys(StaleChunks), Nil)

  test("reindexStale reports nothing left once it has swept everything stale"):
    val _ = SourceCheckFixtures.reindexStale(ds, cfg.limits.chunkChars, cfg.limits.reindexChars)
    val r = SourceCheckFixtures.reindexStale(ds, cfg.limits.chunkChars, cfg.limits.reindexChars)
    assertEquals(r.remaining, 0)

  test("undated-findings and unlocated-findings, named by finding id"):
    val ck = Prefix + "f"
    put(
      source(
        ck,
        findings = List(
          finding(s"$ck#0", ck, None, Some("Anatolia"), Some(GeoPoint(32.0, 39.0))), // undated
          finding(s"$ck#1", ck, Some(Period(-1800, -1700)), None, None), // unlocated
          finding(s"$ck#2", ck, Some(Period(-1800, -1700)), Some("Anatolia"), Some(GeoPoint(32.0, 39.0)))
        )
      )
    )
    assertEquals(run(UndatedFindings).map(_.detail).filter(_.startsWith(s"$ck#0")).size, 1)
    assertEquals(run(UnlocatedFindings).map(_.detail).filter(_.startsWith(s"$ck#1")).size, 1)
    assert(!run(UnlocatedFindings).exists(_.detail.startsWith(s"$ck#2")))
    // a region name alone is enough to be located
    assert(!run(UnlocatedFindings).exists(_.detail.startsWith(s"$ck#0")))

  test("the item cap trims the list but never the count"):
    (1 to 5).foreach(i => put(source(s"${Prefix}many$i", findings = Nil)))
    val rep =
      SourceCheckFixtures.run(
        ds,
        List(Lint.rule(NoFindings).get),
        maxItems = 2,
        suggestions = 3,
        format = checker,
        geocode = geo
      )
    assertEquals(rep.rules.head.items.size, 2)
    assert(rep.rules.head.count >= 5, rep.rules.head.count.toString)

  test("article-markdown and note-markdown check every article body and summary, every source's summary and notes"):
    val slug = Prefix + "fmt_article"
    connect(ds)(sql"DELETE FROM articles WHERE slug = $slug".update.run())
    ArticleFixtures.put(ds, Article(slug, "Fmt", "concept", "# Fmt\n\nA ** bad line.", Some("a fine summary")))
    put(source(Prefix + "fmt_note", summary = Some("a **bad summary")).copy(notes = Some("fine notes")))
    put(source(Prefix + "fmt_clean", summary = Some("clean")).copy(notes = Some("clean")))
    try
      val art = run(ArticleFormat)
      assertEquals(art.map(_.citekey), List(slug))
      assert(art.head.detail.startsWith("body L1:"), art.head.detail)
      assert(art.head.detail.contains("emphasis"), art.head.detail)
      assert(art.head.detail.contains("`article`"), "the body is checked AS an article: " + art.head.detail)
      val notes = run(NoteFormat)
      assertEquals(notes.map(_.citekey), List(Prefix + "fmt_note"))
      assert(notes.head.detail.startsWith("summary L1:3"), notes.head.detail)
      assert(notes.head.detail.contains("`not an article`"), notes.head.detail)
    finally connect(ds)(sql"DELETE FROM articles WHERE slug = $slug".update.run())
