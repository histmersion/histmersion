package com.histmersion.db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.library.search.SearchFixtures
import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.api.TimeSpan
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.Migrate
import com.histmersion.api.catalogue.{EventPut, EventSummary, Period, Year}
import com.histmersion.api.library.{Citekey, Filter, Source}
import com.histmersion.api.research.Article
import com.histmersion.util.database.given

/** #406 against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise. The Articles and Events lists are the
  * items RELATED to the sources the global filter selects: an article that cites a matching source is listed, one
  * citing none is not; an event is listed when its source matches and its own span overlaps the period; the title
  * filter narrows each list by the item's own title.
  */
class RelatedListsRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val ds =
    val base = AppConfig.load().db
    val d = testPool(base.modify(_.url).setTo(testDb.get))
    Migrate.run(d); d

  private val Tag = "zz-rel-tag"

  private def source(
      ck: String,
      tags: List[Source.Tags],
      status: com.histmersion.api.library.Source.Status,
      period: Period,
      author: String
  ) =
    SourceFixtures.put(
      ds,
      Source(
        citekey = com.histmersion.api.library.Citekey(ck),
        title = com.histmersion.api.library.Title(s"Source title $ck"),
        authors = (List(author)).map(com.histmersion.api.library.AuthorName.apply),
        doi = None,
        year = (Some(2010)).map(com.histmersion.api.catalogue.Year.apply),
        venue = None,
        status = status,
        topics = Nil,
        regions = Nil,
        tags = tags,
        period = Some(period),
        summary = None,
        citedByCount = None,
        influentialCitations = None,
        provenance = None,
        calibreId = None
      ),
      com.histmersion.config.Limits.ChunkChars(4000)
    )

  private def article(slug: Article.Slug, title: Article.Title, body: Article.Body) =
    ArticleFixtures.put(
      ds,
      Article(
        slug = slug,
        title = title,
        kind = com.histmersion.api.research.Article.KindValue("concept"),
        body = body,
        summary = None,
        tags = Nil,
        regions = Nil,
        period = None
      )
    )

  private def event(
      id: String,
      label: com.histmersion.api.catalogue.EventSummary.Label,
      year: com.histmersion.api.catalogue.Year,
      citekey: Option[com.histmersion.api.library.Citekey]
  ) =
    EventFixtures.put(
      ds,
      id,
      EventPut(label = label, timeSpan = TimeSpan(Some(year), None, None, Some(year)), citekey = citekey)
    )

  override def beforeAll(): Unit =
    if testDb.isDefined then
      val _ = source(
        "zz_rel_match",
        List(Source.Tags(Tag)),
        com.histmersion.api.library.Source.Status.Current,
        Period(com.histmersion.api.catalogue.Year(-1300), com.histmersion.api.catalogue.Year(-1200)),
        "Zzrelauthor"
      )
      val _ = source(
        "zz_rel_other",
        Nil,
        com.histmersion.api.library.Source.Status.Contested,
        Period(com.histmersion.api.catalogue.Year(-600), com.histmersion.api.catalogue.Year(-500)),
        "Someone"
      )
      val _ = article(Article.Slug("zz_rel_cites_match"), Article.Title("Zz Rel Kadesh article"), Article.Body("# K\n\nSee [[zz_rel_match]]."))
      val _ = article(Article.Slug("zz_rel_cites_other"), Article.Title("Zz Rel other article"), Article.Body("# O\n\nSee [[zz_rel_other]]."))
      val _ = article(Article.Slug("zz_rel_cites_none"), Article.Title("Zz Rel lonely article"), Article.Body("# L\n\nNo citation at all."))
      event("zz_rel_ev_match", EventSummary.Label("Zz Rel battle of Kadesh"), Year(-1274), Some(Citekey("zz_rel_match")))
      event("zz_rel_ev_late", EventSummary.Label("Zz Rel late raid"), Year(-800), Some(Citekey("zz_rel_match")))
      event("zz_rel_ev_other", EventSummary.Label("Zz Rel other battle"), Year(-1274), Some(Citekey("zz_rel_other")))
      event("zz_rel_ev_nosrc", EventSummary.Label("Zz Rel unsourced flood"), Year(-1250), None)

  override def afterSuite(): Unit =
    if testDb.isDefined then
      List("zz_rel_cites_match", "zz_rel_cites_other", "zz_rel_cites_none").foreach(s => ArticleFixtures.delete(ds, s))
      List("zz_rel_ev_match", "zz_rel_ev_late", "zz_rel_ev_other", "zz_rel_ev_nosrc").foreach(e =>
        EventFixtures.delete(ds, e)
      )

  private def articles(f: Filter): Set[String] =
    ArticleFixtures
      .list(ds, f, PlaceScope.empty, None, None, com.histmersion.domain.research.articles.ArticleRepo.Limit(500), com.histmersion.Offset(0))
      .articles
      .map(_.slug.value)
      .filter(_.startsWith("zz_rel_"))
      .toSet

  private def events(f: Filter, text: Option[String] = None): Set[String] =
    EventFixtures
      .list(ds, f, PlaceScope.empty, text, None, None, None, com.histmersion.PageSize(500), com.histmersion.Offset(0))
      .events
      .map(_.id.value)
      .filter(_.startsWith("zz_rel_"))
      .toSet

  test("an empty filter lists every article and every event"):
    assertEquals(articles(Filter()), Set("zz_rel_cites_match", "zz_rel_cites_other", "zz_rel_cites_none"))
    assertEquals(events(Filter()), Set("zz_rel_ev_match", "zz_rel_ev_late", "zz_rel_ev_other", "zz_rel_ev_nosrc"))

  test("articles: one citing a matching source is listed, one citing none (or another) is not"):
    assertEquals(articles(Filter(tags = List(Filter.Tags(Tag)))), Set("zz_rel_cites_match"))
    assertEquals(articles(Filter(authors = List(com.histmersion.api.library.Filter.Authors("zzrelauth")))), Set("zz_rel_cites_match"))
    assertEquals(articles(Filter(status = List(com.histmersion.api.library.Filter.Status("contested")))), Set("zz_rel_cites_other"))

  test("articles: the period selects through the cited sources, as the Sources list does"):
    assertEquals(articles(Filter(period = Some(Period(com.histmersion.api.catalogue.Year(-1250), com.histmersion.api.catalogue.Year(-1100))))), Set("zz_rel_cites_match"))

  test("articles: title narrows the ARTICLE's own title, not its sources'"):
    assertEquals(articles(Filter(title = Some(com.histmersion.api.library.Filter.Title("kadesh")))), Set("zz_rel_cites_match"))
    assertEquals(articles(Filter(title = Some(com.histmersion.api.library.Filter.Title("Source title")))), Set.empty[String])
    assertEquals(articles(Filter(title = Some(Filter.Title("lonely")), tags = List(Filter.Tags(Tag)))), Set.empty[String])

  test("events: listed when the source matches; an event outside the period is excluded"):
    assertEquals(events(Filter(tags = List(Filter.Tags(Tag)))), Set("zz_rel_ev_match", "zz_rel_ev_late"))
    assertEquals(events(Filter(tags = List(Filter.Tags(Tag)), period = Some(Period(Year(-1300), Year(-1200))))), Set("zz_rel_ev_match"))

  test("events: a period alone matches the event's own span, with or without a source"):
    assertEquals(
      events(Filter(period = Some(Period(com.histmersion.api.catalogue.Year(-1300), com.histmersion.api.catalogue.Year(-1200))))),
      Set("zz_rel_ev_match", "zz_rel_ev_other", "zz_rel_ev_nosrc")
    )

  test("events: title narrows the label; text still searches label and description"):
    assertEquals(events(Filter(title = Some(com.histmersion.api.library.Filter.Title("KADESH")))), Set("zz_rel_ev_match"))
    assertEquals(events(Filter(title = Some(com.histmersion.api.library.Filter.Title("battle")), status = List(com.histmersion.api.library.Filter.Status("contested")))), Set("zz_rel_ev_other"))
    assertEquals(events(Filter(), text = Some("unsourced")), Set("zz_rel_ev_nosrc"))

  test("sources: title narrows the source list by its own title"):
    val r = SearchFixtures.sources(ds, Filter(title = Some(com.histmersion.api.library.Filter.Title("title zz_rel_m")), limit = com.histmersion.api.library.Filter.Limit(50)), com.histmersion.domain.library.search.Search.SnippetWords(20), PlaceScope.empty)
    assertEquals(r.hits.map(_.source.citekey), List("zz_rel_match"))
