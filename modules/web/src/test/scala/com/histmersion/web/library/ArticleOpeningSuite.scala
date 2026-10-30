package com.histmersion.web.library

class ArticleOpeningSuite extends munit.FunSuite:

  test("the first paragraph of prose, past the title, lists and quotations"):
    val body = "# The Sea Peoples\n\n- a list\n- of points\n\n> a quotation\n\nThe **Sea Peoples** raided the coast " +
      "[[cline2014_year]], as the [Medinet Habu reliefs](https://example.org) show.\n\n## Next\n\nMore."
    assertEquals(ArticleOpening.of(body, 600),
      Some("The Sea Peoples raided the coast, as the Medinet Habu reliefs show."))

  test("a labelled wikilink reads as its label"):
    assertEquals(ArticleOpening.plain("see [[sea-peoples|the raiders]] and `code`"), "see the raiders and code")

  test("a long paragraph is cut at a word, with an ellipsis; no prose at all is none"):
    val long = ("word " * 200).trim
    val cut  = ArticleOpening.of(long, 50).get
    assert(cut.endsWith("…") && cut.length <= 51 && !cut.contains("wor…"), cut)
    assertEquals(ArticleOpening.of("# Title\n\n## Only headings", 100), None)
