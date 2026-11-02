package com.histmersion.db

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.values.SourceStatus

/** The article store against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise. Covers the acceptance lines
  * of plan §5.2 stream 2.7 that need a database: put/patch, the link graph maintained from the body, backlinks in both
  * directions, the cited-sources footer, and FTS.
  */
class ArticleRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val ds =
    val base = AppConfig.load().db
    val d = testPool(base.copy(url = testDb.get))
    Migrate.run(d); d

  private val slug = "zz_article_sea"

  /** A source to cite, so a `[[citekey]]` in an article body is classifiable as a source link. */
  private def seedSource(ck: String, notes: Option[String] = None) =
    SourceFixtures.put(
      ds,
      Source(
        citekey = ck,
        title = s"Title of $ck",
        authors = List("A"),
        doi = None,
        year = Some(2014),
        venue = None,
        status = SourceStatus.Foundational,
        topics = Nil,
        regions = Nil,
        tags = Nil,
        period = None,
        summary = None,
        citedByCount = None,
        influentialCitations = None,
        provenance = None,
        calibreId = None,
        notes = notes
      ),
      4000
    )

  private def article(body: String, s: String = slug) = Article(
    slug = s,
    title = "The Sea Peoples",
    kind = "concept",
    body = body,
    summary = Some("A loose label."),
    tags = List("collapse", "sea-peoples"),
    regions = List("Levant"),
    period = Some(Period(-1220, -1100))
  )

  private def reset(): Unit =
    List(slug, "zz_article_other", "zz_article_tmp", "zz_article_myth").foreach(s => ArticleFixtures.delete(ds, s))

  test("put → get round-trip, inclusive int8range bounds and a fresh updatedAt token"):
    reset()
    val token = ArticleFixtures.put(ds, article("# The Sea Peoples\n\nbody"))
    val r = ArticleFixtures.get(ds, slug).get
    assertEquals(r.title, "The Sea Peoples")
    assertEquals(r.kind, "concept")
    assertEquals((r.periodStart, r.periodEnd), (Some(-1220L), Some(-1100L)))
    assertEquals(r.tags, List("collapse", "sea-peoples"))
    assertEquals(r.updatedAt, Some(token))
    assert(token.endsWith("Z"), token)

  test("text[] columns round-trip as real arrays: a comma inside one value stays one value"):
    // the read used to be `array_to_string(tags, ', ')` split on ',' — "trade, long-distance" came
    // back as two tags, and list/get/load disagreed with each other
    reset()
    val tags = List("trade, long-distance", "sea-peoples")
    val _ = ArticleFixtures.put(ds, article("# T\n\nbody").copy(tags = tags, regions = List("Syria, coast")))
    assertEquals(ArticleFixtures.get(ds, slug).get.tags, tags)
    assertEquals(ArticleFixtures.load(ds, slug).get.regions, List("Syria, coast"))
    assertEquals(
      ArticleFixtures.list(ds, Filter(), PlaceScope.empty, None, None, 50, 0).articles.find(_.slug == slug).map(_.tags),
      Some(tags)
    )
    val _ = seedSource("zz_comma2020_authors")
    val _ = SourceFixtures.put(
      ds,
      SourceFixtures
        .load(ds, "zz_comma2020_authors")
        .get
        .copy(authors = List("Stocker, Sharon R.", "Davis, Jack L.")),
      4000
    )
    val _ = ArticleFixtures.put(ds, article("# T\n\n[[zz_comma2020_authors]]"))
    assertEquals(
      ArticleFixtures.sourcesCited(ds, slug).map(_.authors),
      List(List("Stocker, Sharon R.", "Davis, Jack L."))
    )
    assertEquals(
      SourceFixtures.get(ds, "zz_comma2020_authors").map(_._1.authors),
      Some(List("Stocker, Sharon R.", "Davis, Jack L."))
    )

  test("the link graph is written from the body: a known citekey is a source, everything else an article"):
    reset()
    val _ = seedSource("zz_cline2014_year")
    val _ = ArticleFixtures.put(
      ds,
      article("# T\n\n[[zz_cline2014_year]] and [[bronze_age_collapse]] and [[zz_ghost2019_absent]]")
    )
    assertEquals(ArticleFixtures.sourcesCited(ds, slug).map(_.citekey), List("zz_cline2014_year"))
    val cited = ArticleFixtures.sourcesCited(ds, slug).head
    assertEquals(
      (cited.title, cited.year, cited.status),
      (Some("Title of zz_cline2014_year"), Some(2014), Some(SourceStatus.Foundational))
    )
    // the article link is stored too — it is what makes the target's backlinks work
    val _ = ArticleFixtures.put(ds, article("# Other\n\n[[zz_article_sea]]", "zz_article_other"))
    // article backlinks only: a source note left behind by another test in this suite may also
    // mention this slug (that path is asserted separately, below)
    assertEquals(ArticleFixtures.backlinks(ds, slug).filter(_.kind == "article").map(_.id), List("zz_article_other"))

  test("a rewritten body replaces the links, it does not accumulate them"):
    reset()
    val _ = seedSource("zz_cline2014_year")
    val _ = ArticleFixtures.put(ds, article("# T\n\n[[zz_cline2014_year]]"))
    val _ = ArticleFixtures.put(ds, article("# T\n\nno links at all"))
    assertEquals(ArticleFixtures.sourcesCited(ds, slug), Nil)

  test("backlinks also come from a SOURCE note that mentions the article"):
    reset()
    val _ = seedSource("zz_notes2020_link", notes = Some(s"See [[$slug]] for the argument."))
    val _ = ArticleFixtures.put(ds, article("# T\n\nbody"))
    val back = ArticleFixtures.backlinks(ds, slug)
    assert(back.exists(b => b.kind == "source" && b.id == "zz_notes2020_link"), back.toString)

  test("patch touches only what it names, and append extends the prose"):
    reset()
    val _ = ArticleFixtures.put(ds, article("# T\n\noriginal prose"))
    val r = ArticleFixtures.patch(ds, slug, ArticlePatch(tags = Some(ListPatch(add = List("levant")))))
    assert(r.isRight, r.toString)
    val a1 = ArticleFixtures.load(ds, slug).get
    assertEquals(a1.tags, List("collapse", "sea-peoples", "levant"))
    assertEquals(a1.body, "# T\n\noriginal prose")

    val _ = ArticleFixtures.patch(ds, slug, ArticlePatch(append = Some("## More\n\n[[zz_ghost2019_absent]]")))
    val a2 = ArticleFixtures.load(ds, slug).get
    assert(a2.body.contains("original prose") && a2.body.contains("## More"), a2.body)
    assertEquals(a2.tags, a1.tags)

  test("patch rewrites the link graph too"):
    reset()
    val _ = seedSource("zz_cline2014_year")
    val _ = ArticleFixtures.put(ds, article("# T\n\nno links"))
    assertEquals(ArticleFixtures.sourcesCited(ds, slug), Nil)
    val _ = ArticleFixtures.patch(ds, slug, ArticlePatch(append = Some("[[zz_cline2014_year]]")))
    assertEquals(ArticleFixtures.sourcesCited(ds, slug).map(_.citekey), List("zz_cline2014_year"))

  test("a stale ifUpdatedAt is refused with the precondition code, not silently applied"):
    reset()
    val _ = ArticleFixtures.put(ds, article("# T\n\nbody"))
    val e =
      ArticleFixtures.patch(ds, slug, ArticlePatch(summary = Some("x"), ifUpdatedAt = Some("stale"))).left.toOption.get
    assertEquals(e.code, Some(Write.CodePrecondition))
    assertEquals(ArticleFixtures.load(ds, slug).get.summary, Some("A loose label."))

  test("patching an unknown slug is not_found"):
    assertEquals(
      ArticleFixtures.patch(ds, "zz_no_such_article", ArticlePatch(summary = Some("x"))).left.toOption.get.code,
      Some(Write.CodeNotFound)
    )

  test("list: kind and FTS (`text`) over title / summary / body"):
    reset()
    val _ = ArticleFixtures.put(ds, article("# T\n\nthe Medinet Habu reliefs of Ramesses III"))
    val _ = ArticleFixtures.put(
      ds,
      article("# Other\n\nunrelated", "zz_article_other")
        .copy(title = "Something else", tags = List("other"), summary = Some("nothing here"))
    )

    def slugs(q: Option[String] = None, kind: Option[String] = None) =
      ArticleFixtures
        .list(ds, Filter(), PlaceScope.empty, kind, q, 50, 0)
        .articles
        .map(_.slug)
        .filter(_.startsWith("zz_article"))

    assertEquals(slugs(q = Some("Medinet")), List(slug)) // body (weight C)
    assertEquals(slugs(q = Some("\"Sea Peoples\"")), List(slug)) // title phrase (weight A)
    assertEquals(slugs(q = Some("loose")), List(slug)) // summary (weight B)
    assert(slugs(kind = Some("concept")).contains(slug))
    assertEquals(slugs(q = Some("zzznothingmatchesthis")), Nil)

  test("list: an unfinished word still finds the title (the web UI tab types into this)"):
    reset()
    val _ = ArticleFixtures.put(ds, article("# The Sea Peoples\n\nbody"))
    assert(
      ArticleFixtures.list(ds, Filter(), PlaceScope.empty, None, Some("Peopl"), 50, 0).articles.exists(_.slug == slug)
    )

  test("list: total ignores limit/offset"):
    reset()
    // a title nothing else in the suite (or in a suite sharing this database) uses: `total` counts
    // every matching row, so a fixture left behind by another test would make this flaky
    val onlyMine = (b: String, s: String) => article(b, s).copy(title = "zz total marker")
    val _ = ArticleFixtures.put(ds, onlyMine("# T\n\nbody", slug))
    val _ = ArticleFixtures.put(ds, onlyMine("# T2\n\nbody", "zz_article_other"))
    val r =
      ArticleFixtures.list(ds, Filter(title = Some("ZZ TOTAL marker")), PlaceScope.empty, Some("concept"), None, 1, 0)
    assertEquals(r.articles.size, 1)
    assertEquals(r.total, 2)
    assertEquals(r.kinds, List(ArticleKindCount("concept", 2)), "the per-kind counts ignore limit/offset too")
    val myth = onlyMine("# T3\n\nbody", "zz_article_myth").copy(kind = "myth")
    val _ = ArticleFixtures.put(ds, myth)
    val all = ArticleFixtures.list(ds, Filter(title = Some("ZZ TOTAL marker")), PlaceScope.empty, None, None, 1, 0)
    assertEquals((all.total, all.kinds), (3, List(ArticleKindCount("concept", 2), ArticleKindCount("myth", 1))))

  test("delete removes the article and its links"):
    reset()
    val _ = seedSource("zz_cline2014_year")
    val _ = ArticleFixtures.put(ds, article("# T\n\n[[zz_cline2014_year]]"))
    assert(ArticleFixtures.delete(ds, slug))
    assertEquals(ArticleFixtures.get(ds, slug), None)
    assertEquals(ArticleFixtures.sourcesCited(ds, slug), Nil) // ON DELETE CASCADE took the links with it
    assert(!ArticleFixtures.delete(ds, slug))

  test("load → render → parse → put round-trips byte-identically"):
    reset()
    val _ = ArticleFixtures.put(ds, article("# T\n\nbody with [[zz_ghost2019_absent]]"))
    val note1 = ArticleNote.render(ArticleFixtures.load(ds, slug).get)
    val _ = ArticleFixtures.put(ds, ArticleNote.parse(note1, "zz", "zz").get)
    assertEquals(ArticleNote.render(ArticleFixtures.load(ds, slug).get), note1)

  test("slugs and updatedAtMillis cover the whole wiki (the seed guard reads them)"):
    reset()
    val _ = ArticleFixtures.put(ds, article("# T\n\nbody"))
    assert(ArticleFixtures.slugs(ds).contains(slug))
    assert(ArticleFixtures.updatedAtMillis(ds).get(slug).exists(_ > 0L))

  // ── article versions (V013) ─────────────────────────────────────────────────────────────────

  test("put → patch → patch creates two versions; listVersions and getVersion return them"):
    reset()
    // 1. Create the article (first put — no previous row, so no version record)
    val _ = ArticleFixtures.put(ds, article("# T\n\noriginal body"))
    assertEquals(ArticleFixtures.listVersions(ds, slug).size, 0, "a fresh article has no previous versions")

    // 2. Patch it — rewrites the body; the ORIGINAL is now version 1
    val _ = ArticleFixtures.patch(ds, slug, ArticlePatch(body = Some("# T\n\nrewritten body")))
    val v1 = ArticleFixtures.listVersions(ds, slug)
    assertEquals(v1.size, 1, "after one patch, one previous version")
    assertEquals(v1.head.slug, slug)
    assert(v1.head.wordCount > 0, s"word count should be positive, got ${v1.head.wordCount}")

    val full1 = ArticleFixtures.getVersion(ds, slug, v1.head.id)
    assert(full1.isDefined, "getVersion should find the version by id")
    assertEquals(full1.get.body, "# T\n\noriginal body")
    assertEquals(full1.get.slug, slug)

    // 3. Patch again — the "rewritten body" becomes version 2
    val _ = ArticleFixtures.patch(ds, slug, ArticlePatch(body = Some("# T\n\nthird version body")))
    val v2 = ArticleFixtures.listVersions(ds, slug)
    assertEquals(v2.size, 2, "after two patches, two previous versions")
    // newest first
    val full2 = ArticleFixtures.getVersion(ds, slug, v2.head.id)
    assertEquals(full2.get.body, "# T\n\nrewritten body")

    // 4. The current article has the third body
    assertEquals(ArticleFixtures.load(ds, slug).get.body, "# T\n\nthird version body")

  test("put on an existing article also creates a version (the body being replaced)"):
    reset()
    val _ = ArticleFixtures.put(ds, article("# T\n\nfirst body"))
    val _ = ArticleFixtures.put(ds, article("# T\n\nsecond body"))
    val vs = ArticleFixtures.listVersions(ds, slug)
    assertEquals(vs.size, 1)
    assertEquals(ArticleFixtures.getVersion(ds, slug, vs.head.id).get.body, "# T\n\nfirst body")

  test("getVersion returns None for a non-existent version id"):
    reset()
    val _ = ArticleFixtures.put(ds, article("# T\n\nbody"))
    assertEquals(ArticleFixtures.getVersion(ds, slug, 999999L), None)

  test("deleting an article cascades to its versions"):
    reset()
    val _ = ArticleFixtures.put(ds, article("# T\n\nfirst"))
    val _ = ArticleFixtures.put(ds, article("# T\n\nsecond"))
    assert(ArticleFixtures.listVersions(ds, slug).nonEmpty)
    val _ = ArticleFixtures.delete(ds, slug)
    assertEquals(ArticleFixtures.listVersions(ds, slug), Nil)

  test("#958: the filter and the reasons are stored and read back; a patch merges reasons, an empty filter clears"):
    reset()
    val f = ArticleFilter(
      query = Some("sea"),
      topics = List("collapse"),
      status = List("current"),
      from = Some(-1300),
      to = Some(-1100),
      extraQueries = List("Medinet Habu")
    )
    val _ = ArticleFixtures.put(
      ds,
      article("# T\n\nbody").copy(filter = Some(f), reasons = Map("zz_cov2001_egypt" -> "about Egypt"))
    )
    val a = ArticleFixtures.load(ds, slug).get
    assertEquals((a.filter, a.reasons), (Some(f), Map("zz_cov2001_egypt" -> "about Egypt")))
    val _ = ArticleFixtures.patch(
      ds,
      slug,
      ArticlePatch(reasons =
        List(CoverageReason("zz_cov2002_hatti", "about Hatti"), CoverageReason("zz_cov2001_egypt", ""))
      )
    )
    assertEquals(ArticleFixtures.load(ds, slug).get.reasons, Map("zz_cov2002_hatti" -> "about Hatti"))
    assertEquals(ArticleFixtures.load(ds, slug).get.filter, Some(f), "a patch that does not name the filter keeps it")
    val _ = ArticleFixtures.patch(ds, slug, ArticlePatch(filter = Some(ArticleFilter())))
    assertEquals(ArticleFixtures.load(ds, slug).get.filter, None)
    // an article written before V066 (no filter, no reasons) reads as such
    val _ = ArticleFixtures.put(ds, article("# T\n\nbody", "zz_article_other"))
    assertEquals(
      ArticleFixtures.load(ds, "zz_article_other").map(x => (x.filter, x.reasons)),
      Some((None, Map.empty[String, String]))
    )

  test("#958: renaming a source moves a reason keyed by its citekey"):
    reset()
    import com.augustnagro.magnum.*
    def wipe(): Unit = connect(ds):
      sql"DELETE FROM source_aliases WHERE alias LIKE 'zz_covren%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zz_covren%'".update.run()
    wipe()
    seedSource("zz_covren2001_old")
    val _ = ArticleFixtures.put(
      ds,
      article("# T\n\nbody")
        .copy(filter = Some(ArticleFilter(tags = List("x"))), reasons = Map("zz_covren2001_old" -> "about Egypt"))
    )
    assert(SourceFixtures.rename(ds, "zz_covren2001_old", "zz_covren2001_new").isRight)
    assertEquals(ArticleFixtures.load(ds, slug).get.reasons, Map("zz_covren2001_new" -> "about Egypt"))
    wipe()
