package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.library.search.FilterSql

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.Param
import com.histmersion.api.library.{Filter, Sort}
import com.histmersion.util.database.given
import com.histmersion.domain.research.Article
import com.histmersion.domain.library.search.Search

/** `ArticleFixtures.listWhere` is a pure function of the query parameters — asserted here without a database, like
  * `FilterSql` on the source side.
  */
class ArticleSqlSuite extends munit.FunSuite:

  private def where(kind: Option[Article.Kind] = None, text: Option[Search.Query] = None, f: Filter = Filter()) =
    ArticleFixtures.listWhere(f, PlaceScope.empty, kind, text)

  test("no parameters selects everything"):
    assertEquals(where().text, "TRUE")
    assertEquals(where(Some(Article.Kind("  ")), Some(Search.Query(""))).text, "TRUE")
    // sort and paging are not criteria
    assertEquals(where(f = Filter(sort = Sort.Year, limit = Filter.Limit(5), offset = Filter.Offset(10))).text, "TRUE")

  test("kind is an exact, case-insensitive match on one bound parameter"):
    val s = where(kind = Some(Article.Kind("Concept")))
    assertEquals(s.text, "lower(a.kind) = ?")
    assertEquals(s.params, List(Param.S(Param.S.V("concept"))))

  test("a source tag selects the articles CITING a tagged source, through /api/sources' own predicate"):
    val f = Filter(tags = List(Filter.Tags("sea-peoples")))
    val s = where(f = f)
    assertEquals(
      s.text,
      "EXISTS (SELECT 1 FROM article_links al WHERE al.src_slug = a.slug AND al.target_kind = 'source'" +
        " AND al.target = ANY(ARRAY(SELECT s.citekey FROM sources s WHERE " + FilterSql.where(f).text + ")))"
    )
    assertEquals(s.params, FilterSql.where(f).params)

  test("title is the ARTICLE's title, never part of the source set"):
    val s = where(f = Filter(title = Some(Filter.Title("50%_sea"))))
    assertEquals(s.text, "a.title ILIKE ? ESCAPE '\\'")
    assertEquals(s.params, List(Param.S(Param.S.V("%50\\%\\_sea%"))))

  test("text is FTS over the article vector OR a title substring, with % escaped"):
    val s = where(text = Some(Search.Query("100%_sea")))
    assert(s.text.value.contains("a.tsv @@ websearch_to_tsquery('english', ?)"), s.text)
    assert(s.text.value.contains("a.title ILIKE ?"), s.text)
    assertEquals(s.params, List(Param.S(Param.S.V("100%_sea")), Param.S(Param.S.V("%100\\%\\_sea%"))))

  test("parameters stay in textual order when the parts are combined"):
    val s = where(
      Some(Article.Kind("report")),
      Some(Search.Query("gods")),
      Filter(status = List(Filter.Status("current")), title = Some(Filter.Title("pie")))
    )
    assertEquals(
      s.params,
      List(
        Param.S(Param.S.V("current")),
        Param.S(Param.S.V("%pie%")),
        Param.S(Param.S.V("report")),
        Param.S(Param.S.V("gods")),
        Param.S(Param.S.V("%gods%"))
      )
    )
    assertEquals(s.text.value.count(_ == '?'), 5)
