package com.histmersion.server

class ArticleSuite extends munit.FunSuite:
  test("wikilinks resolve to /source links, aliased or not"):
    assertEquals(Article.resolveLinks("see [[a1_b]] and [[c2_d|the D paper]]"),
      "see [a1_b](/source/a1_b) and [the D paper](/source/c2_d)")
  test("non-citekey brackets untouched"):
    assertEquals(Article.resolveLinks("[[Not A Key]]"), "[[Not A Key]]")
