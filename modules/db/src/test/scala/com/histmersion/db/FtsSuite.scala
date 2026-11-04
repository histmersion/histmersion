package com.histmersion.db

import com.histmersion.domain.library.search.SearchFixtures
import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.config.*
import com.histmersion.domain.*
import com.augustnagro.magnum.*
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.api.library.{Filter, SearchHit, Source}
import com.histmersion.util.database.given

/** Full-text search against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise. Covers the point of plan
  * §1.4: bodies far bigger than tsvector's 1 MB per-row limit are searchable because they are chunked, and every hit
  * carries the passage that matched.
  */
class FtsSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d)
    d

  private def chunkChars = cfg.limits.chunkChars
  private def words = cfg.limits.snippetWords

  private def source(ck: String, title: String, summary: String, body: String) =
    Source(
      citekey = ck,
      title = title,
      authors = List("Tester"),
      doi = None,
      year = Some(1999),
      venue = None,
      status = com.histmersion.api.library.Source.Status.Current,
      topics = Nil,
      regions = Nil,
      tags = Nil,
      period = None,
      summary = Some(summary),
      citedByCount = Some(0),
      influentialCitations = None,
      provenance = None,
      calibreId = None,
      ocrMarkdown = Some(body)
    )

  /** ~0.75 MB of prose per call — two of them put the body past the ~1 MB a tsvector can hold. */
  private def filler(marker: String): String =
    val para = (1 to 65).map(i => s"$marker padding sentence $i about pottery and stratigraphy.").mkString(" ")
    (1 to 200).map(i => s"## Section $marker $i\n\n$para").mkString("\n\n")

  /** Built once (munit runs the tests of a suite in declaration order). */
  private lazy val fixture: Unit =
    List("zz_fts_big", "zz_fts_egypt", "zz_fts_title").foreach: ck =>
      transact(ds)(sql"DELETE FROM sources WHERE citekey = $ck".update.run())

    // > 1 MB body; the phrase appears exactly once, deep inside, under a known heading
    val big = filler("alpha") +
      "\n\n## Ugarit destruction\n\nThe sea peoples burned the harbour quarter in the twelfth century.\n\n" +
      filler("omega")
    assert(big.length > 1_100_000, big.length)
    SourceFixtures.upsert(
      ds,
      source("zz_fts_big", "Aegean stratigraphy", "A summary with no query terms.", big),
      chunkChars
    )
    SourceFixtures.upsert(
      ds,
      source(
        "zz_fts_egypt",
        "Delta excavations",
        "Another summary.",
        "## Medinet Habu\n\nThe sea peoples are named in Egypt, in the reliefs of Ramesses III."
      ),
      chunkChars
    )
    SourceFixtures.upsert(
      ds,
      source(
        "zz_fts_title",
        "The Sea Peoples reconsidered",
        "Historiography.",
        "## Body\n\nThis body never mentions the raiders by name; it discusses pottery."
      ),
      chunkChars
    )

  /** The ONE query path: `/api/sources` (`Filter.q` is the FTS predicate, relevance is `ts_rank_cd`, and the hit
    * carries the snippet of the best-matching chunk).
    */
  private def search(q: String): Vector[SearchHit] = listing(Filter(q = Some(q), limit = 20))
  private def listing(f: Filter): Vector[SearchHit] =
    SearchFixtures.sources(ds, f, words).hits.map(_.source).toVector

  test("a >1 MB body is indexed and a body-only phrase is found, with snippet and heading"):
    fixture
    val hits = search("\"sea peoples\"")
    val big = hits.find(_.citekey == "zz_fts_big")
    assert(big.isDefined, hits.map(_.citekey))
    assertEquals(big.get.matchedHeading, Some("Ugarit destruction"))
    val snip = big.get.snippet.getOrElse("")
    assert(snip.contains("**"), snip) // ts_headline StartSel/StopSel
    assert(snip.toLowerCase.contains("peoples"), snip)
    assert(snip.length < 2000, snip.length) // a fragment, not the whole chunk

  test("-not excludes a source whose body carries the excluded term"):
    fixture
    val all = search("\"sea peoples\"").map(_.citekey)
    val excluded = search("\"sea peoples\" -egypt").map(_.citekey)
    assert(all.contains("zz_fts_egypt"), all)
    assert(!excluded.contains("zz_fts_egypt"), excluded)
    assert(excluded.contains("zz_fts_big"), excluded)

  test("ranking prefers a title match over a body match"):
    fixture
    val hits = search("sea peoples").map(_.citekey).filter(_.startsWith("zz_fts_"))
    assertEquals(hits.headOption, Some("zz_fts_title"), hits)

  test("no query keeps the most-cited listing, with no snippet"):
    fixture
    val hits = listing(Filter(limit = 5))
    assert(hits.nonEmpty)
    assert(hits.forall(h => h.snippet.isEmpty && h.matchedHeading.isEmpty))

  test("websearch operators: bare words are AND-ed within a vector, `or` alternates"):
    fixture
    assert(search("sea peoples reconsidered").map(_.citekey).contains("zz_fts_title"))
    assert(!search("sea peoples zzzznotaword").map(_.citekey).contains("zz_fts_title"))
    assert(search("zzzznotaword or stratigraphy").map(_.citekey).contains("zz_fts_big"))

  test("reindex rebuilds every chunk from ocr_markdown"):
    fixture
    // scoped to THIS suite's citekeys (they sort together): the db suites run in parallel against one
    // database, so wiping/rewriting every chunk row would collide with another suite's write
    transact(ds)(sql"DELETE FROM source_chunks WHERE citekey LIKE 'zz_fts_%'".update.run())
    assert(!search("\"sea peoples\"").exists(_.citekey == "zz_fts_big"))
    val r = SourceFixtures.reindexChunks(ds, chunkChars, after = Some("zz_fts_a"), maxSources = Some(3))
    assert(r.sources == 3 && r.chunks > r.sources, r)
    assert(search("\"sea peoples\"").exists(_.citekey == "zz_fts_big"))

/** Planner/statistics upkeep against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise. */
class PlannerUpkeepSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val d = testPool(AppConfig.load().db.copy(url = testDb.get))
    Migrate.run(d)
    d

  private def one(sql: String): String =
    val c = ds.getConnection()
    try
      val rs = c.createStatement().executeQuery(sql)
      rs.next(); rs.getString(1)
    finally c.close()

  test("V023: the database carries the SSD page cost for every new session"):
    val cfg = one(
      "SELECT array_to_string(setconfig, ',') FROM pg_db_role_setting" +
        " WHERE setrole = 0 AND setdatabase = (SELECT oid FROM pg_database WHERE datname = current_database())"
    )
    assert(cfg.split(',').contains("random_page_cost=1.1"), cfg)

  test("ensureDatabaseSettings restores the page cost after a restore dropped it"):
    val c = ds.getConnection()
    try
      c.createStatement()
        .execute("DO $$ BEGIN EXECUTE format('ALTER DATABASE %I RESET random_page_cost', current_database()); END $$")
    finally c.close()
    Db.ensureDatabaseSettings(ds)
    val cfg = one(
      "SELECT array_to_string(setconfig, ',') FROM pg_db_role_setting" +
        " WHERE setrole = 0 AND setdatabase = (SELECT oid FROM pg_database WHERE datname = current_database())"
    )
    assert(cfg.split(',').contains("random_page_cost=1.1"), cfg)

  test("every session plans a prepared statement for its own values (no generic plan after 5 uses)"):
    val c = ds.getConnection()
    try
      c.createStatement()
        .execute("DO $$ BEGIN EXECUTE format('ALTER DATABASE %I RESET plan_cache_mode', current_database()); END $$")
    finally c.close()
    Db.ensureDatabaseSettings(ds)
    val cfg = one(
      "SELECT array_to_string(setconfig, ',') FROM pg_db_role_setting" +
        " WHERE setrole = 0 AND setdatabase = (SELECT oid FROM pg_database WHERE datname = current_database())"
    )
    assert(cfg.split(',').contains("plan_cache_mode=force_custom_plan"), cfg)
    // a NEW session sees it: the pool's old connections are evicted by Main after the call
    val pg = org.postgresql.ds.PGSimpleDataSource()
    pg.setUrl(TestDb.url.get)
    pg.setUser("histmersion")
    val fresh = pg.getConnection()
    try
      val rs = fresh.createStatement().executeQuery("SHOW plan_cache_mode")
      rs.next()
      assertEquals(rs.getString(1), "force_custom_plan")
    finally fresh.close()

  test("analyzeStale analyzes exactly the tables with no statistics, then finds none"):
    val _ = Db.analyzeStale(ds)
    assertEquals(Db.analyzeStale(ds), Nil)
    assertEquals(one(s"SELECT count(*) FROM (${Db.staleStatsSql}) t"), "0")
