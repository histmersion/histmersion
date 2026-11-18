package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.library.search.FilterSql

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.config.*
import com.histmersion.domain.*
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
    assertEquals(where().sqlString, "TRUE")
    assertEquals(where(Some(Article.Kind("  ")), Some(Search.Query(""))).sqlString, "TRUE")
    // sort and paging are not criteria
    assertEquals(
      where(f = Filter(sort = Sort.Year, limit = Filter.Limit(5), offset = Filter.Offset(10))).sqlString,
      "TRUE"
    )

  test("kind is an exact, case-insensitive match on one bound parameter"):
    val s = where(kind = Some(Article.Kind("Concept")))
    assertEquals(s.sqlString, "lower(a.kind) = ?")
    assertEquals(s.params, List("concept"))

  test("a source tag selects the articles CITING a tagged source, through /api/sources' own predicate"):
    val f = Filter(tags = List(Filter.Tags("sea-peoples")))
    val s = where(f = f)
    assertEquals(
      s.sqlString,
      "EXISTS (SELECT 1 FROM article_links al WHERE al.src_slug = a.slug AND al.target_kind = 'source'" +
        " AND al.target = ANY(ARRAY(SELECT s.citekey FROM sources s WHERE " + FilterSql.where(f).sqlString + ")))"
    )
    assertEquals(s.params, FilterSql.where(f).params)

  test("title is the ARTICLE's title, never part of the source set"):
    val s = where(f = Filter(title = Some(Filter.Title("50%_sea"))))
    assertEquals(s.sqlString, "a.title ILIKE ? ESCAPE '\\'")
    assertEquals(s.params, List("%50\\%\\_sea%"))

  test("text is FTS over the article vector OR a title substring, with % escaped"):
    val s = where(text = Some(Search.Query("100%_sea")))
    assert(s.sqlString.contains("a.tsv @@ websearch_to_tsquery('english', ?)"), s.sqlString)
    assert(s.sqlString.contains("a.title ILIKE ?"), s.sqlString)
    assertEquals(s.params, List("100%_sea", "%100\\%\\_sea%"))

  test("parameters stay in textual order when the parts are combined"):
    val s = where(
      Some(Article.Kind("report")),
      Some(Search.Query("gods")),
      Filter(status = List(Filter.Status("current")), title = Some(Filter.Title("pie")))
    )
    assertEquals(
      s.params,
      List(
        "current",
        "%pie%",
        "report",
        "gods",
        "%gods%"
      )
    )
    assertEquals(s.sqlString.count(_ == '?'), 5)
