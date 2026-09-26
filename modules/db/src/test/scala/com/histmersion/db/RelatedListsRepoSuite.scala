package com.histmersion.db

import com.histmersion.core.*

/** #406 against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise. The Articles and Events
  * lists are the items RELATED to the sources the global filter selects: an article that cites a
  * matching source is listed, one citing none is not; an event is listed when its source matches and
  * its own span overlaps the period; the title filter narrows each list by the item's own title. */
class RelatedListsRepoSuite extends munit.FunSuite:
  private val testDb                = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val ds =
    val base = AppConfig.load().db
    val d    = Db.dataSource(base.copy(url = testDb.get))
    Migrate.run(d); d

  private val Tag = "zz-rel-tag"

  private def source(ck: String, tags: List[String], status: SourceStatus, period: Period, author: String) =
    SourceRepo.put(ds, Source(
      citekey = ck, title = s"Source title $ck", authors = List(author), doi = None, year = Some(2010),
      venue = None, status = status, topics = Nil, regions = Nil, tags = tags, period = Some(period),
      summary = None, citedByCount = None, influentialCitations = None, provenance = None,
      calibreId = None), 4000)

  private def article(slug: String, title: String, body: String) =
    ArticleRepo.put(ds, Article(slug = slug, title = title, kind = "concept", body = body,
      summary = None, tags = Nil, regions = Nil, period = None))

  private def event(id: String, label: String, year: Long, citekey: Option[String]) =
    EventRepo.put(ds, id, EventPut(label = label, timeSpan = TimeSpan(Some(year), None, None, Some(year)),
      citekey = citekey))

  override def beforeAll(): Unit =
    if testDb.isDefined then
      val _ = source("zz_rel_match", List(Tag), SourceStatus.Current, Period(-1300, -1200), "Zzrelauthor")
      val _ = source("zz_rel_other", Nil, SourceStatus.Contested, Period(-600, -500), "Someone")
      val _ = article("zz_rel_cites_match", "Zz Rel Kadesh article", "# K\n\nSee [[zz_rel_match]].")
      val _ = article("zz_rel_cites_other", "Zz Rel other article", "# O\n\nSee [[zz_rel_other]].")
      val _ = article("zz_rel_cites_none", "Zz Rel lonely article", "# L\n\nNo citation at all.")
      event("zz_rel_ev_match", "Zz Rel battle of Kadesh", -1274, Some("zz_rel_match"))
      event("zz_rel_ev_late", "Zz Rel late raid", -800, Some("zz_rel_match"))
      event("zz_rel_ev_other", "Zz Rel other battle", -1274, Some("zz_rel_other"))
      event("zz_rel_ev_nosrc", "Zz Rel unsourced flood", -1250, None)

  override def afterAll(): Unit =
    if testDb.isDefined then
      List("zz_rel_cites_match", "zz_rel_cites_other", "zz_rel_cites_none").foreach(s => ArticleRepo.delete(ds, s))
      List("zz_rel_ev_match", "zz_rel_ev_late", "zz_rel_ev_other", "zz_rel_ev_nosrc").foreach(e => EventRepo.delete(ds, e))

  private def articles(f: Filter): Set[String] =
    ArticleRepo.list(ds, f, PlaceScope.empty, None, None, 500, 0).articles.map(_.slug)
      .filter(_.startsWith("zz_rel_")).toSet

  private def events(f: Filter, text: Option[String] = None): Set[String] =
    EventRepo.list(ds, f, PlaceScope.empty, text, None, None, None, 500, 0).events.map(_.id)
      .filter(_.startsWith("zz_rel_")).toSet

  test("an empty filter lists every article and every event"):
    assertEquals(articles(Filter()), Set("zz_rel_cites_match", "zz_rel_cites_other", "zz_rel_cites_none"))
    assertEquals(events(Filter()), Set("zz_rel_ev_match", "zz_rel_ev_late", "zz_rel_ev_other", "zz_rel_ev_nosrc"))

  test("articles: one citing a matching source is listed, one citing none (or another) is not"):
    assertEquals(articles(Filter(tags = List(Tag))), Set("zz_rel_cites_match"))
    assertEquals(articles(Filter(authors = List("zzrelauth"))), Set("zz_rel_cites_match"))
    assertEquals(articles(Filter(status = List("contested"))), Set("zz_rel_cites_other"))

  test("articles: the period selects through the cited sources, as the Sources list does"):
    assertEquals(articles(Filter(period = Some(Period(-1250, -1100)))), Set("zz_rel_cites_match"))

  test("articles: title narrows the ARTICLE's own title, not its sources'"):
    assertEquals(articles(Filter(title = Some("kadesh"))), Set("zz_rel_cites_match"))
    assertEquals(articles(Filter(title = Some("Source title"))), Set.empty[String])
    assertEquals(articles(Filter(title = Some("lonely"), tags = List(Tag))), Set.empty[String])

  test("events: listed when the source matches; an event outside the period is excluded"):
    assertEquals(events(Filter(tags = List(Tag))), Set("zz_rel_ev_match", "zz_rel_ev_late"))
    assertEquals(events(Filter(tags = List(Tag), period = Some(Period(-1300, -1200)))), Set("zz_rel_ev_match"))

  test("events: a period alone matches the event's own span, with or without a source"):
    assertEquals(events(Filter(period = Some(Period(-1300, -1200)))),
      Set("zz_rel_ev_match", "zz_rel_ev_other", "zz_rel_ev_nosrc"))

  test("events: title narrows the label; text still searches label and description"):
    assertEquals(events(Filter(title = Some("KADESH"))), Set("zz_rel_ev_match"))
    assertEquals(events(Filter(title = Some("battle"), status = List("contested"))), Set("zz_rel_ev_other"))
    assertEquals(events(Filter(), text = Some("unsourced")), Set("zz_rel_ev_nosrc"))

  test("sources: title narrows the source list by its own title"):
    val r = SourceRepo.sources(ds, Filter(title = Some("title zz_rel_m"), limit = 50), 20, PlaceScope.empty)
    assertEquals(r.hits.map(_.source.citekey), List("zz_rel_match"))
