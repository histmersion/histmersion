package com.histmersion.db

/** `ArticleRepo.listWhere` is a pure function of the query parameters — asserted here without a
  * database, like `FilterSql` on the source side. */
class ArticleSqlSuite extends munit.FunSuite:

  test("no parameters selects everything"):
    assertEquals(ArticleRepo.listWhere(None, None, None).text, "TRUE")
    assertEquals(ArticleRepo.listWhere(Some("  "), Some(""), None).text, "TRUE")

  test("kind is an exact, case-insensitive match on one bound parameter"):
    val s = ArticleRepo.listWhere(Some("Concept"), None, None)
    assertEquals(s.text, "lower(a.kind) = ?")
    assertEquals(s.params, List(Param.S("concept")))

  test("tag is membership in the array, case-insensitively"):
    val s = ArticleRepo.listWhere(None, None, Some("sea-peoples"))
    assert(s.text.contains("unnest(a.tags)"), s.text)
    assertEquals(s.params, List(Param.S("sea-peoples")))

  test("q is FTS over the article vector OR a title substring, with % escaped"):
    val s = ArticleRepo.listWhere(None, Some("100%_sea"), None)
    assert(s.text.contains("a.tsv @@ websearch_to_tsquery('english', ?)"), s.text)
    assert(s.text.contains("a.title ILIKE ?"), s.text)
    assertEquals(s.params, List(Param.S("100%_sea"), Param.S("%100\\%\\_sea%")))

  test("parameters stay in textual order when the parts are combined"):
    val s = ArticleRepo.listWhere(Some("report"), Some("gods"), Some("pie"))
    assertEquals(s.params, List(Param.S("report"), Param.S("pie"), Param.S("gods"), Param.S("%gods%")))
    assertEquals(s.text.count(_ == '?'), 4)
