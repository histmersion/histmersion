package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

class HighlightSuite extends munit.FunSuite:

  test("terms: bare words, quoted phrases, longest first"):
    assertEquals(Highlight.terms("""collapse "sea peoples" bronze"""), List("sea peoples", "collapse", "bronze"))

  test("terms: -excluded terms and the boolean operators are dropped"):
    assertEquals(Highlight.terms("""sea -egypt or bronze"""), List("bronze", "sea"))
    assertEquals(Highlight.terms("""collapse -"new kingdom""""), List("collapse"))

  test("terms: punctuation trimmed, duplicates folded case-insensitively, empty query is empty"):
    assertEquals(Highlight.terms(com.histmersion.domain.Highlight.Term("(Hittite), hittite!")), List("Hittite"))
    assertEquals(Highlight.terms(com.histmersion.domain.Highlight.Term("   ")), Nil)
    assertEquals(Highlight.terms(com.histmersion.domain.Highlight.Term("")), Nil)

  test("marks wrap text hits and count them"):
    val (html, n) = Highlight(com.histmersion.domain.Highlight.ApplyHtml("<p>The Sea Peoples raided.</p>"), com.histmersion.domain.Highlight.ApplyQ("sea"))
    assertEquals(n, 1)
    assertEquals(html, """<p>The <mark id="hl1">Sea</mark> Peoples raided.</p>""")

  test("match extends over the rest of the word, so a stemmed hit reads whole"):
    val (html, n) = Highlight(com.histmersion.domain.Highlight.ApplyHtml("<p>Egyptian records</p>"), com.histmersion.domain.Highlight.ApplyQ("egypt"))
    assertEquals(n, 1)
    assert(html.contains("""<mark id="hl1">Egyptian</mark>"""), html)

  test("attributes and tag names are never touched"):
    val (html, n) = Highlight("""<a href="/source/egypt" class="egypt">Egypt</a>""", com.histmersion.domain.Highlight.ApplyQ("egypt"))
    assertEquals(n, 1)
    assertEquals(html, """<a href="/source/egypt" class="egypt"><mark id="hl1">Egypt</mark></a>""")

  test("phrases match across whitespace and newlines, and win over their own words"):
    val (html, n) = Highlight(com.histmersion.domain.Highlight.ApplyHtml("<p>the sea\n  peoples came</p>"), """"sea peoples"""")
    assertEquals(n, 1)
    assert(html.contains("<mark id=\"hl1\">sea\n  peoples</mark>"), html)

  test("ids are sequential across the whole document"):
    val (html, n) = Highlight(com.histmersion.domain.Highlight.ApplyHtml("<p>sea</p><p>sea</p><p>sea</p>"), com.histmersion.domain.Highlight.ApplyQ("sea"))
    assertEquals(n, 3)
    assertEquals("""id="hl\d"""".r.findAllIn(html).toList, List("""id="hl1"""", """id="hl2"""", """id="hl3""""))

  test("entities are left alone even when a term matches inside them"):
    val (html, n) = Highlight(com.histmersion.domain.Highlight.ApplyHtml("<p>A &amp; B</p>"), com.histmersion.domain.Highlight.ApplyQ("amp"))
    assertEquals(n, 0)
    assertEquals(html, "<p>A &amp; B</p>")

  test("script and style content is skipped"):
    val src = """<style>.egypt{color:red}</style><script>var egypt=1;</script><p>Egypt</p>"""
    val (html, n) = Highlight(src, com.histmersion.domain.Highlight.ApplyQ("egypt"))
    assertEquals(n, 1)
    assert(html.startsWith("""<style>.egypt{color:red}</style><script>var egypt=1;</script>"""), html)

  test("no query, no terms, or no match leaves the html byte-identical"):
    val src = "<p>nothing here</p>"
    assertEquals(Highlight(src, com.histmersion.domain.Highlight.ApplyQ("")), (src, 0))
    assertEquals(Highlight(src, com.histmersion.domain.Highlight.ApplyQ("-only")), (src, 0))
    assertEquals(Highlight(src, com.histmersion.domain.Highlight.ApplyQ("absent")), (src, 0))

  test("regex metacharacters in the query are literal"):
    val (html, n) = Highlight(com.histmersion.domain.Highlight.ApplyHtml("<p>a.b and axb</p>"), com.histmersion.domain.Highlight.ApplyQ("a.b"))
    assertEquals(n, 1)
    assert(html.contains("""<mark id="hl1">a.b</mark>"""), html)

  test("unterminated tag does not lose text"):
    val (html, _) = Highlight(com.histmersion.domain.Highlight.ApplyHtml("<p>sea</p><b"), com.histmersion.domain.Highlight.ApplyQ("sea"))
    assert(html.endsWith("</p><b"), html)
