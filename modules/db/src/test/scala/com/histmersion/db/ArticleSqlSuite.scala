package com.histmersion.db

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.library.search.FilterSql

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.Param
import com.histmersion.api.library.{Filter, Sort}
import com.histmersion.util.database.given

/** `ArticleFixtures.listWhere` is a pure function of the query parameters — asserted here without a database, like
  * `FilterSql` on the source side.
  */
class ArticleSqlSuite extends munit.FunSuite:

  private def where(kind: Option[String] = None, text: Option[String] = None, f: Filter = Filter()) =
    ArticleFixtures.listWhere(f, PlaceScope.empty, kind, text)

  test("no parameters selects everything"):
    assertEquals(where().text, "TRUE")
    assertEquals(where(Some("  "), Some("")).text, "TRUE")
    // sort and paging are not criteria
    assertEquals(where(f = Filter(sort = Sort.Year, limit = com.histmersion.api.library.Filter.Limit(5), offset = com.histmersion.api.library.Filter.Offset(10))).text, "TRUE")

  test("kind is an exact, case-insensitive match on one bound parameter"):
    val s = where(kind = Some("Concept"))
    assertEquals(s.text, "lower(a.kind) = ?")
    assertEquals(s.params, List(Param.S(com.histmersion.util.database.Param.S.V("concept"))))

  test("a source tag selects the articles CITING a tagged source, through /api/sources' own predicate"):
    val f = Filter(tags = List(com.histmersion.api.library.Filter.Tags("sea-peoples")))
    val s = where(f = f)
    assertEquals(
      s.text,
      "EXISTS (SELECT 1 FROM article_links al WHERE al.src_slug = a.slug AND al.target_kind = 'source'" +
        " AND al.target = ANY(ARRAY(SELECT s.citekey FROM sources s WHERE " + FilterSql.where(f).text + ")))"
    )
    assertEquals(s.params, FilterSql.where(f).params)

  test("title is the ARTICLE's title, never part of the source set"):
    val s = where(f = Filter(title = Some(com.histmersion.api.library.Filter.Title("50%_sea"))))
    assertEquals(s.text, "a.title ILIKE ? ESCAPE '\\'")
    assertEquals(s.params, List(Param.S(com.histmersion.util.database.Param.S.V("%50\\%\\_sea%"))))

  test("text is FTS over the article vector OR a title substring, with % escaped"):
    val s = where(text = Some("100%_sea"))
    assert(s.text.contains("a.tsv @@ websearch_to_tsquery('english', ?)"), s.text)
    assert(s.text.contains("a.title ILIKE ?"), s.text)
    assertEquals(s.params, List(Param.S(com.histmersion.util.database.Param.S.V("100%_sea")), Param.S(com.histmersion.util.database.Param.S.V("%100\\%\\_sea%"))))

  test("parameters stay in textual order when the parts are combined"):
    val s = where(Some("report"), Some("gods"), Filter(status = List(com.histmersion.api.library.Filter.Status("current")), title = Some(com.histmersion.api.library.Filter.Title("pie"))))
    assertEquals(
      s.params,
      List(Param.S(com.histmersion.util.database.Param.S.V("current")), Param.S(com.histmersion.util.database.Param.S.V("%pie%")), Param.S(com.histmersion.util.database.Param.S.V("report")), Param.S(com.histmersion.util.database.Param.S.V("gods")), Param.S(com.histmersion.util.database.Param.S.V("%gods%")))
    )
    assertEquals(s.text.count(_ == '?'), 5)
