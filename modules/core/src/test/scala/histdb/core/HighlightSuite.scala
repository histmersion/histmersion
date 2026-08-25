package histdb.core

class HighlightSuite extends munit.FunSuite:

  test("terms: bare words, quoted phrases, longest first"):
    assertEquals(Highlight.terms("""collapse "sea peoples" bronze"""),
      List("sea peoples", "collapse", "bronze"))

  test("terms: -excluded terms and the boolean operators are dropped"):
    assertEquals(Highlight.terms("""sea -egypt or bronze"""), List("bronze", "sea"))
    assertEquals(Highlight.terms("""collapse -"new kingdom""""), List("collapse"))

  test("terms: punctuation trimmed, duplicates folded case-insensitively, empty query is empty"):
    assertEquals(Highlight.terms("(Hittite), hittite!"), List("Hittite"))
    assertEquals(Highlight.terms("   "), Nil)
    assertEquals(Highlight.terms(""), Nil)

  test("marks wrap text hits and count them"):
    val (html, n) = Highlight("<p>The Sea Peoples raided.</p>", "sea")
    assertEquals(n, 1)
    assertEquals(html, """<p>The <mark id="hl1">Sea</mark> Peoples raided.</p>""")

  test("match extends over the rest of the word, so a stemmed hit reads whole"):
    val (html, n) = Highlight("<p>Egyptian records</p>", "egypt")
    assertEquals(n, 1)
    assert(html.contains("""<mark id="hl1">Egyptian</mark>"""), html)

  test("attributes and tag names are never touched"):
    val (html, n) = Highlight("""<a href="/source/egypt" class="egypt">Egypt</a>""", "egypt")
    assertEquals(n, 1)
    assertEquals(html, """<a href="/source/egypt" class="egypt"><mark id="hl1">Egypt</mark></a>""")

  test("phrases match across whitespace and newlines, and win over their own words"):
    val (html, n) = Highlight("<p>the sea\n  peoples came</p>", """"sea peoples"""")
    assertEquals(n, 1)
    assert(html.contains("<mark id=\"hl1\">sea\n  peoples</mark>"), html)

  test("ids are sequential across the whole document"):
    val (html, n) = Highlight("<p>sea</p><p>sea</p><p>sea</p>", "sea")
    assertEquals(n, 3)
    assertEquals("""id="hl\d"""".r.findAllIn(html).toList, List("""id="hl1"""", """id="hl2"""", """id="hl3""""))

  test("entities are left alone even when a term matches inside them"):
    val (html, n) = Highlight("<p>A &amp; B</p>", "amp")
    assertEquals(n, 0)
    assertEquals(html, "<p>A &amp; B</p>")

  test("script and style content is skipped"):
    val src = """<style>.egypt{color:red}</style><script>var egypt=1;</script><p>Egypt</p>"""
    val (html, n) = Highlight(src, "egypt")
    assertEquals(n, 1)
    assert(html.startsWith("""<style>.egypt{color:red}</style><script>var egypt=1;</script>"""), html)

  test("no query, no terms, or no match leaves the html byte-identical"):
    val src = "<p>nothing here</p>"
    assertEquals(Highlight(src, ""), (src, 0))
    assertEquals(Highlight(src, "-only"), (src, 0))
    assertEquals(Highlight(src, "absent"), (src, 0))

  test("regex metacharacters in the query are literal"):
    val (html, n) = Highlight("<p>a.b and axb</p>", "a.b")
    assertEquals(n, 1)
    assert(html.contains("""<mark id="hl1">a.b</mark>"""), html)

  test("unterminated tag does not lose text"):
    val (html, _) = Highlight("<p>sea</p><b", "sea")
    assert(html.endsWith("</p><b"), html)
