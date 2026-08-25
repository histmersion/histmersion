package histdb.db

import histdb.core.*
import com.augustnagro.magnum.*

/** Full-text search against a real Postgres (HISTDB_TEST_DB_URL); skipped otherwise.
  * Covers the point of plan §1.4: bodies far bigger than tsvector's 1 MB per-row limit are searchable
  * because they are chunked, and every hit carries the passage that matched. */
class FtsSuite extends munit.FunSuite:
  private val testDb                = sys.env.get("HISTDB_TEST_DB_URL")
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = Db.dataSource(cfg.db.copy(url = testDb.get))
    Migrate.run(d)
    d

  private def chunkChars = cfg.limits.chunkChars
  private def words      = cfg.limits.snippetWords

  private def source(ck: String, title: String, summary: String, body: String) =
    Source(
      citekey = ck, title = title, authors = List("Tester"), doi = None, year = Some(1999), venue = None,
      status = SourceStatus.Current, topics = Nil, regions = Nil, tags = Nil, period = None,
      summary = Some(summary), citedByCount = Some(0), influentialCitations = None,
      provenance = None, calibreId = None, ocrMarkdown = Some(body))

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
    SourceRepo.upsert(ds, source("zz_fts_big", "Aegean stratigraphy", "A summary with no query terms.", big), chunkChars)
    SourceRepo.upsert(ds, source("zz_fts_egypt", "Delta excavations", "Another summary.",
      "## Medinet Habu\n\nThe sea peoples are named in Egypt, in the reliefs of Ramesses III."), chunkChars)
    SourceRepo.upsert(ds, source("zz_fts_title", "The Sea Peoples reconsidered", "Historiography.",
      "## Body\n\nThis body never mentions the raiders by name; it discusses pottery."), chunkChars)

  private def search(q: String) = SourceRepo.search(ds, Some(q), 20, words)

  test("a >1 MB body is indexed and a body-only phrase is found, with snippet and heading"):
    fixture
    val hits = search("\"sea peoples\"")
    val big  = hits.find(_.citekey == "zz_fts_big")
    assert(big.isDefined, hits.map(_.citekey))
    assertEquals(big.get.matchedHeading, Some("Ugarit destruction"))
    val snip = big.get.snippet.getOrElse("")
    assert(snip.contains("**"), snip)                        // ts_headline StartSel/StopSel
    assert(snip.toLowerCase.contains("peoples"), snip)
    assert(snip.length < 2000, snip.length)                  // a fragment, not the whole chunk

  test("-not excludes a source whose body carries the excluded term"):
    fixture
    val all      = search("\"sea peoples\"").map(_.citekey)
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
    val hits = SourceRepo.search(ds, None, 5, words)
    assert(hits.nonEmpty)
    assert(hits.forall(h => h.snippet.isEmpty && h.matchedHeading.isEmpty))

  test("websearch operators: bare words are AND-ed within a vector, `or` alternates"):
    fixture
    assert(search("sea peoples reconsidered").map(_.citekey).contains("zz_fts_title"))
    assert(!search("sea peoples zzzznotaword").map(_.citekey).contains("zz_fts_title"))
    assert(search("zzzznotaword or stratigraphy").map(_.citekey).contains("zz_fts_big"))

  test("reindex rebuilds every chunk from ocr_markdown"):
    fixture
    transact(ds)(sql"DELETE FROM source_chunks".update.run())
    assert(!search("\"sea peoples\"").exists(_.citekey == "zz_fts_big"))
    val r = SourceRepo.reindexAll(ds, chunkChars)
    assert(r.sources > 0 && r.chunks > r.sources, r)
    assert(search("\"sea peoples\"").exists(_.citekey == "zz_fts_big"))
