package com.histmersion
package db
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.{ListPatch, Write}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.Migrate
import com.histmersion.domain.research.articles.ArticleNote
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.{Filter, Source}
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage
import com.histmersion.util.database.given
import com.histmersion.config.DbConfig
import com.histmersion.domain.research.Slug
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.config.Limits
import com.histmersion.domain.library.search.Search
import com.histmersion.domain.library.sources.Citekeys
import com.histmersion.lint.{tolerated, LintRule}

/** The article store against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise. Covers the acceptance lines
  * of plan §5.2 stream 2.7 that need a database: put/patch, the link graph maintained from the body, backlinks in both
  * directions, the cited-sources footer, and FTS.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ArticleRepoSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val ds =
    val base = AppConfig.load().db
    val d = testPool(base.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  private val slug = Slug("zz_article_sea")

  /** A source to cite, so a `[[citekey]]` in an article body is classifiable as a source link. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def seedSource(ck: String, notes: Option[Source.Notes] = None) =
    SourceFixtures.put(
      ds,
      Source(
        citekey = Citekey(ck),
        title = api.library.Title(s"Title of $ck"),
        authors = (List("A")).map(api.library.AuthorName.apply),
        doi = None,
        year = (Some(2014)).map(Year.apply),
        venue = None,
        status = Source.Status.Foundational,
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
      Limits.ChunkChars(4000)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def article(body: Article.Body, s: Article.Slug = Article.Slug(slug.value)) = Article(
    slug = s,
    title = Article.Title("The Sea Peoples"),
    kind = Article.KindValue("concept"),
    body = body,
    summary = Some(Article.SummaryText("A loose label.")),
    tags = List(Article.Tags("collapse"), Article.Tags("sea-peoples")),
    regions = List(Article.Regions("Levant")),
    period = Some(Period(Year(-1220), Year(-1100)))
  )

  private def reset(): Unit =
    List(slug, Slug("zz_article_other"), Slug("zz_article_tmp"), Slug("zz_article_myth")).foreach(s =>
      ArticleFixtures.delete(ds, s)
    )

  test("put → get round-trip, inclusive int8range bounds and a fresh updatedAt token"):
    reset()
    val token = ArticleFixtures.put(ds, article(Article.Body("# The Sea Peoples\n\nbody")))
    val r = ArticleFixtures.get(ds, slug).get
    assertEquals(r.title, "The Sea Peoples")
    assertEquals(r.kind, "concept")
    assertEquals((r.periodStart, r.periodEnd), (Some(-1220L), Some(-1100L)))
    assertEquals(r.tags, List("collapse", "sea-peoples"))
    assertEquals(r.updatedAt, Some(token))
    assert(token.value.endsWith("Z"), token)
    assertEquals(r.creator.map(_.who), Some("a person"))
    assertEquals(r.creator.flatMap(_.surface).map(_.shown), Some("the server itself"))
    assert(r.createdAt.exists(_.shown.endsWith("Z")), r.createdAt)

  test("text[] columns round-trip as real arrays: a comma inside one value stays one value"):
    // the read used to be `array_to_string(tags, ', ')` split on ',' — "trade, long-distance" came
    // back as two tags, and list/get/load disagreed with each other
    reset()
    val tags = List("trade, long-distance", "sea-peoples")
    val _ = ArticleFixtures.put(
      ds,
      article(Article.Body("# T\n\nbody"))
        .modify(_.tags)
        .setTo(tags.map(Article.Tags(_)))
        .modify(_.regions)
        .setTo(List(Article.Regions("Syria, coast")))
    )
    assertEquals(ArticleFixtures.get(ds, slug).get.tags, tags)
    assertEquals(ArticleFixtures.load(ds, slug).get.regions, List("Syria, coast"))
    assertEquals(
      ArticleFixtures
        .list(ds, Filter(), PlaceScope.empty, None, None, ArticleFixtures.Limit(50), Offset(0))
        .articles
        .find(_.slug.value == slug.value)
        .map(_.tags),
      Some(tags)
    )
    val _ = seedSource("zz_comma2020_authors")
    val _ = SourceFixtures.put(
      ds,
      SourceFixtures
        .load(ds, domain.library.Citekey("zz_comma2020_authors"))
        .get
        .copy(authors =
          List(
            api.library.AuthorName("Stocker, Sharon R."),
            api.library.AuthorName("Davis, Jack L.")
          )
        ),
      Limits.ChunkChars(4000)
    )
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\n[[zz_comma2020_authors]]")))
    assertEquals(
      ArticleFixtures.sourcesCited(ds, slug).map(_.authors),
      List(List("Stocker, Sharon R.", "Davis, Jack L."))
    )
    assertEquals(
      SourceFixtures.get(ds, domain.library.Citekey("zz_comma2020_authors")).map(_._1.authors),
      Some(List("Stocker, Sharon R.", "Davis, Jack L."))
    )

  test("the link graph is written from the body: a known citekey is a source, everything else an article"):
    reset()
    val _ = seedSource("zz_cline2014_year")
    val _ = ArticleFixtures.put(
      ds,
      article(Article.Body("# T\n\n[[zz_cline2014_year]] and [[bronze_age_collapse]] and [[zz_ghost2019_absent]]"))
    )
    assertEquals(ArticleFixtures.sourcesCited(ds, slug).map(_.citekey), List("zz_cline2014_year"))
    val cited = ArticleFixtures.sourcesCited(ds, slug).head
    assertEquals(
      (cited.title, cited.year, cited.status),
      (Some("Title of zz_cline2014_year"), Some(2014L), Some(Source.Status.Foundational))
    )
    // the article link is stored too — it is what makes the target's backlinks work
    val _ =
      ArticleFixtures.put(ds, article(Article.Body("# Other\n\n[[zz_article_sea]]"), Article.Slug("zz_article_other")))
    // article backlinks only: a source note left behind by another test in this suite may also
    // mention this slug (that path is asserted separately, below)
    assertEquals(
      ArticleFixtures.backlinks(ds, slug).filter(_.kind.value == "article").map(_.id),
      List("zz_article_other")
    )

  test("a rewritten body replaces the links, it does not accumulate them"):
    reset()
    val _ = seedSource("zz_cline2014_year")
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\n[[zz_cline2014_year]]")))
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\nno links at all")))
    assertEquals(ArticleFixtures.sourcesCited(ds, slug), Nil)

  test("backlinks also come from a SOURCE note that mentions the article"):
    reset()
    val _ = seedSource("zz_notes2020_link", notes = Some(Source.Notes(s"See [[${slug.value}]] for the argument.")))
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\nbody")))
    val back = ArticleFixtures.backlinks(ds, slug)
    assert(back.exists(b => b.kind.value == "source" && b.id.value == "zz_notes2020_link"), back.toString)

  test("patch touches only what it names, and append extends the prose"):
    reset()
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\noriginal prose")))
    val r = ArticleFixtures.patch(ds, slug, Article.Patch(tags = Some(ListPatch(add = List(Write.ListItem("levant"))))))
    assert(r.isRight, r.toString)
    val a1 = ArticleFixtures.load(ds, slug).get
    assertEquals(a1.tags, List("collapse", "sea-peoples", "levant"))
    assertEquals(a1.body, "# T\n\noriginal prose")

    val _ = ArticleFixtures.patch(
      ds,
      slug,
      Article.Patch(append = Some(Article.Patch.Append("## More\n\n[[zz_ghost2019_absent]]")))
    )
    val a2 = ArticleFixtures.load(ds, slug).get
    assert(a2.body.value.contains("original prose") && a2.body.value.contains("## More"), a2.body)
    assertEquals(a2.tags, a1.tags)

  test("patch rewrites the link graph too"):
    reset()
    val _ = seedSource("zz_cline2014_year")
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\nno links")))
    assertEquals(ArticleFixtures.sourcesCited(ds, slug), Nil)
    val _ = ArticleFixtures.patch(ds, slug, Article.Patch(append = Some(Article.Patch.Append("[[zz_cline2014_year]]"))))
    assertEquals(ArticleFixtures.sourcesCited(ds, slug).map(_.citekey), List("zz_cline2014_year"))

  test("a stale ifUpdatedAt is refused with the precondition code, not silently applied"):
    reset()
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\nbody")))
    val e =
      ArticleFixtures
        .patch(
          ds,
          slug,
          Article.Patch(summary = Some(Article.SummaryText("x")), ifUpdatedAt = Some(Article.UpdatedAt("stale")))
        )
        .left
        .toOption
        .get
    assertEquals(e.code, Some(Write.CodePrecondition))
    assertEquals(ArticleFixtures.load(ds, slug).get.summary, Some("A loose label."))

  test("patching an unknown slug is not_found"):
    assertEquals(
      ArticleFixtures
        .patch(ds, Slug("zz_no_such_article"), Article.Patch(summary = Some(Article.SummaryText("x"))))
        .left
        .toOption
        .get
        .code,
      Some(Write.CodeNotFound)
    )

  test("list: kind and FTS (`text`) over title / summary / body"):
    reset()
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\nthe Medinet Habu reliefs of Ramesses III")))
    val _ = ArticleFixtures.put(
      ds,
      article(Article.Body("# Other\n\nunrelated"), Article.Slug("zz_article_other"))
        .modify(_.title)
        .setTo(Article.Title("Something else"))
        .modify(_.tags)
        .setTo(List(Article.Tags("other")))
        .modify(_.summary)
        .setTo(Some(Article.SummaryText("nothing here")))
    )

    def slugs(q: Option[Search.Query] = None, kind: Option[domain.research.Article.Kind] = None) =
      ArticleFixtures
        .list(ds, Filter(), PlaceScope.empty, kind, q, ArticleFixtures.Limit(50), Offset(0))
        .articles
        .map(_.slug.value)
        .filter(_.startsWith("zz_article"))

    assertEquals(slugs(q = Some(Search.Query("Medinet"))), List(slug.value: String)) // body (weight C)
    assertEquals(slugs(q = Some(Search.Query("\"Sea Peoples\""))), List(slug.value: String)) // title phrase (weight A)
    assertEquals(slugs(q = Some(Search.Query("loose"))), List(slug.value: String)) // summary (weight B)
    assert(slugs(kind = Some(domain.research.Article.Kind("concept"))).contains(slug.value))
    assertEquals(slugs(q = Some(Search.Query("zzznothingmatchesthis"))), Nil)

  test("list: an unfinished word still finds the title (the web UI tab types into this)"):
    reset()
    val _ = ArticleFixtures.put(ds, article(Article.Body("# The Sea Peoples\n\nbody")))
    assert(
      ArticleFixtures
        .list(ds, Filter(), PlaceScope.empty, None, Some(Search.Query("Peopl")), ArticleFixtures.Limit(50), Offset(0))
        .articles
        .exists(_.slug.value == slug.value)
    )

  test("list: total ignores limit/offset"):
    reset()
    // a title nothing else in the suite (or in a suite sharing this database) uses: `total` counts
    // every matching row, so a fixture left behind by another test would make this flaky
    val onlyMine =
      (b: Article.Body, s: Article.Slug) => article(b, s).modify(_.title).setTo(Article.Title("zz total marker"))
    val _ = ArticleFixtures.put(ds, onlyMine(Article.Body("# T\n\nbody"), Article.Slug(slug.value)))
    val _ = ArticleFixtures.put(ds, onlyMine(Article.Body("# T2\n\nbody"), Article.Slug("zz_article_other")))
    val r =
      ArticleFixtures.list(
        ds,
        Filter(title = Some(Filter.Title("ZZ TOTAL marker"))),
        PlaceScope.empty,
        Some(domain.research.Article.Kind("concept")),
        None,
        ArticleFixtures.Limit(1),
        Offset(0)
      )
    assertEquals(r.articles.size, 1)
    assertEquals((r.total.value: Int), 2)
    assertEquals(
      r.kinds,
      List(Article.KindCount(Article.Kind.Wire("concept"), Article.KindCount.Count(2))),
      "the per-kind counts ignore limit/offset too"
    )
    val myth =
      onlyMine(Article.Body("# T3\n\nbody"), Article.Slug("zz_article_myth")).copy(kind = Article.KindValue("myth"))
    val _ = ArticleFixtures.put(ds, myth)
    val all = ArticleFixtures.list(
      ds,
      Filter(title = Some(Filter.Title("ZZ TOTAL marker"))),
      PlaceScope.empty,
      None,
      None,
      ArticleFixtures.Limit(1),
      Offset(0)
    )
    assertEquals(
      ((all.total.value: Int), all.kinds),
      (
        3,
        List(
          Article.KindCount(Article.Kind.Wire("concept"), Article.KindCount.Count(2)),
          Article.KindCount(Article.Kind.Wire("myth"), Article.KindCount.Count(1))
        )
      )
    )

  test("delete removes the article and its links"):
    reset()
    val _ = seedSource("zz_cline2014_year")
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\n[[zz_cline2014_year]]")))
    assert(ArticleFixtures.delete(ds, slug))
    assertEquals(ArticleFixtures.get(ds, slug), None)
    assertEquals(ArticleFixtures.sourcesCited(ds, slug), Nil) // ON DELETE CASCADE took the links with it
    assert(!ArticleFixtures.delete(ds, slug))

  test("load → render → parse → put round-trips byte-identically"):
    reset()
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\nbody with [[zz_ghost2019_absent]]")))
    val note1 = ArticleNote.render(ArticleFixtures.load(ds, slug).get)
    val _ = ArticleFixtures.put(ds, ArticleNote.parse(note1, Article.Slug("zz"), Article.KindValue("zz")).get)
    assertEquals(ArticleNote.render(ArticleFixtures.load(ds, slug).get), note1)

  test("slugs and updatedAtMillis cover the whole wiki (the seed guard reads them)"):
    reset()
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\nbody")))
    assert(ArticleFixtures.slugs(ds).contains(slug))
    assert(ArticleFixtures.updatedAtMillis(ds).get(slug).exists(_.value > 0L))

  // ── article versions (V013) ─────────────────────────────────────────────────────────────────

  test("put → patch → patch creates two versions; listVersions and getVersion return them"):
    reset()
    // 1. Create the article (first put — no previous row, so no version record)
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\noriginal body")))
    assertEquals(ArticleFixtures.listVersions(ds, slug).size, 0, "a fresh article has no previous versions")

    // 2. Patch it — rewrites the body; the ORIGINAL is now version 1
    val _ = ArticleFixtures.patch(ds, slug, Article.Patch(body = Some(Article.Body("# T\n\nrewritten body"))))
    val v1 = ArticleFixtures.listVersions(ds, slug)
    assertEquals(v1.size, 1, "after one patch, one previous version")
    assertEquals(v1.head.slug, slug.value: String)
    assert(v1.head.wordCount.value > 0, s"word count should be positive, got ${v1.head.wordCount}")

    val full1 = ArticleFixtures.getVersion(ds, slug, ArticleFixtures.VersionId(v1.head.id.value))
    assert(full1.isDefined, "getVersion should find the version by id")
    assertEquals(full1.get.body, "# T\n\noriginal body")
    assertEquals(full1.get.slug, slug.value: String)

    // 3. Patch again — the "rewritten body" becomes version 2
    val _ = ArticleFixtures.patch(ds, slug, Article.Patch(body = Some(Article.Body("# T\n\nthird version body"))))
    val v2 = ArticleFixtures.listVersions(ds, slug)
    assertEquals(v2.size, 2, "after two patches, two previous versions")
    // newest first
    val full2 = ArticleFixtures.getVersion(ds, slug, ArticleFixtures.VersionId(v2.head.id.value))
    assertEquals(full2.get.body, "# T\n\nrewritten body")

    // 4. The current article has the third body
    assertEquals(ArticleFixtures.load(ds, slug).get.body, "# T\n\nthird version body")

  test("put on an existing article also creates a version (the body being replaced)"):
    reset()
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\nfirst body")))
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\nsecond body")))
    val vs = ArticleFixtures.listVersions(ds, slug)
    assertEquals(vs.size, 1)
    assertEquals(
      ArticleFixtures.getVersion(ds, slug, ArticleFixtures.VersionId(vs.head.id.value)).get.body,
      "# T\n\nfirst body"
    )

  test("getVersion returns None for a non-existent version id"):
    reset()
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\nbody")))
    assertEquals(ArticleFixtures.getVersion(ds, slug, ArticleFixtures.VersionId(999999L)), None)

  test("deleting an article cascades to its versions"):
    reset()
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\nfirst")))
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\nsecond")))
    assert(ArticleFixtures.listVersions(ds, slug).nonEmpty)
    val _ = ArticleFixtures.delete(ds, slug)
    assertEquals(ArticleFixtures.listVersions(ds, slug), Nil)

  test("#958: the filter and the reasons are stored and read back; a patch merges reasons, an empty filter clears"):
    reset()
    val f = Article.Filter(
      query = Some(Filter.Q("sea")),
      topics = List(Filter.Topics("collapse")),
      status = List(Filter.Status("current")),
      from = Some(Year(-1300)),
      to = Some(Year(-1100)),
      extraQueries = List(Article.Filter.ExtraQueries("Medinet Habu"))
    )
    val _ = ArticleFixtures.put(
      ds,
      article(Article.Body("# T\n\nbody"))
        .modify(_.filter)
        .setTo(Some(f))
        .modify(_.reasons)
        .setTo(Map(Citekey("zz_cov2001_egypt") -> Coverage.Rationale("about Egypt")))
    )
    val a = ArticleFixtures.load(ds, slug).get
    assertEquals(
      (a.filter, a.reasons.view.mapValues(w => s"$w").toMap),
      (Some(f), Map("zz_cov2001_egypt" -> "about Egypt"))
    )
    val _ = ArticleFixtures.patch(
      ds,
      slug,
      Article.Patch(reasons =
        List(
          Coverage.Reason(Citekey("zz_cov2002_hatti"), Coverage.Rationale("about Hatti")),
          Coverage.Reason(Citekey("zz_cov2001_egypt"), Coverage.Rationale(""))
        )
      )
    )
    assertEquals(
      ArticleFixtures.load(ds, slug).get.reasons.view.mapValues(w => s"$w").toMap,
      Map("zz_cov2002_hatti" -> "about Hatti")
    )
    assertEquals(ArticleFixtures.load(ds, slug).get.filter, Some(f), "a patch that does not name the filter keeps it")
    val _ = ArticleFixtures.patch(ds, slug, Article.Patch(filter = Some(Article.Filter())))
    assertEquals(ArticleFixtures.load(ds, slug).get.filter, None)
    // an article written before V066 (no filter, no reasons) reads as such
    val _ = ArticleFixtures.put(ds, article(Article.Body("# T\n\nbody"), Article.Slug("zz_article_other")))
    assertEquals(
      ArticleFixtures
        .load(ds, Slug("zz_article_other"))
        .map(x => (x.filter, x.reasons.view.mapValues(w => s"$w").toMap)),
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
      article(Article.Body("# T\n\nbody"))
        .modify(_.filter)
        .setTo(Some(Article.Filter(tags = List(Article.Filter.Tags("x")))))
        .modify(_.reasons)
        .setTo(Map(Citekey("zz_covren2001_old") -> Coverage.Rationale("about Egypt")))
    )
    assert(
      SourceFixtures
        .rename(
          ds,
          domain.library.Citekey("zz_covren2001_old"),
          Citekeys.RenameToNewRaw("zz_covren2001_new")
        )
        .isRight
    )
    assertEquals(
      ArticleFixtures.load(ds, slug).get.reasons.view.mapValues(w => s"$w").toMap,
      Map("zz_covren2001_new" -> "about Egypt")
    )
    wipe()
