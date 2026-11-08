package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

class HighlightSuite extends munit.FunSuite:

  test("terms: bare words, quoted phrases, longest first"):
    assertEquals(Highlight.terms(Highlight.Term("""collapse "sea peoples" bronze""")), List("sea peoples", "collapse", "bronze"))

  test("terms: -excluded terms and the boolean operators are dropped"):
    assertEquals(Highlight.terms(Highlight.Term("""sea -egypt or bronze""")), List("bronze", "sea"))
    assertEquals(Highlight.terms(Highlight.Term("""collapse -"new kingdom"""")), List("collapse"))

  test("terms: punctuation trimmed, duplicates folded case-insensitively, empty query is empty"):
    assertEquals(Highlight.terms(Highlight.Term("(Hittite), hittite!")), List("Hittite"))
    assertEquals(Highlight.terms(Highlight.Term("   ")), Nil)
    assertEquals(Highlight.terms(Highlight.Term("")), Nil)

  test("marks wrap text hits and count them"):
    val (html, n) = Highlight(Highlight.ApplyHtml("<p>The Sea Peoples raided.</p>"), Highlight.ApplyQ("sea"))
    assertEquals(n, 1)
    assertEquals(html, """<p>The <mark id="hl1">Sea</mark> Peoples raided.</p>""")

  test("match extends over the rest of the word, so a stemmed hit reads whole"):
    val (html, n) = Highlight(Highlight.ApplyHtml("<p>Egyptian records</p>"), Highlight.ApplyQ("egypt"))
    assertEquals(n, 1)
    assert(html.value.contains("""<mark id="hl1">Egyptian</mark>"""), html)

  test("attributes and tag names are never touched"):
    val (html, n) = Highlight(Highlight.ApplyHtml("""<a href="/source/egypt" class="egypt">Egypt</a>"""), Highlight.ApplyQ("egypt"))
    assertEquals(n, 1)
    assertEquals(html, """<a href="/source/egypt" class="egypt"><mark id="hl1">Egypt</mark></a>""")

  test("phrases match across whitespace and newlines, and win over their own words"):
    val (html, n) = Highlight(Highlight.ApplyHtml("<p>the sea\n  peoples came</p>"), Highlight.ApplyQ(""""sea peoples""""))
    assertEquals(n, 1)
    assert(html.value.contains("<mark id=\"hl1\">sea\n  peoples</mark>"), html)

  test("ids are sequential across the whole document"):
    val (html, n) = Highlight(Highlight.ApplyHtml("<p>sea</p><p>sea</p><p>sea</p>"), Highlight.ApplyQ("sea"))
    assertEquals(n, 3)
    assertEquals("""id="hl\d"""".r.findAllIn(html.value).toList, List("""id="hl1"""", """id="hl2"""", """id="hl3""""))

  test("entities are left alone even when a term matches inside them"):
    val (html, n) = Highlight(Highlight.ApplyHtml("<p>A &amp; B</p>"), Highlight.ApplyQ("amp"))
    assertEquals(n, 0)
    assertEquals(html, "<p>A &amp; B</p>")

  test("script and style content is skipped"):
    val src = Highlight.ApplyHtml("""<style>.egypt{color:red}</style><script>var egypt=1;</script><p>Egypt</p>""")
    val (html, n) = Highlight(src, Highlight.ApplyQ("egypt"))
    assertEquals(n, 1)
    assert(html.value.startsWith("""<style>.egypt{color:red}</style><script>var egypt=1;</script>"""), html)

  test("no query, no terms, or no match leaves the html byte-identical"):
    val src = Highlight.ApplyHtml("<p>nothing here</p>")
    assertEquals(Highlight(src, Highlight.ApplyQ("")), (src, 0))
    assertEquals(Highlight(src, Highlight.ApplyQ("-only")), (src, 0))
    assertEquals(Highlight(src, Highlight.ApplyQ("absent")), (src, 0))

  test("regex metacharacters in the query are literal"):
    val (html, n) = Highlight(Highlight.ApplyHtml("<p>a.b and axb</p>"), Highlight.ApplyQ("a.b"))
    assertEquals(n, 1)
    assert(html.value.contains("""<mark id="hl1">a.b</mark>"""), html)

  test("unterminated tag does not lose text"):
    val (html, _) = Highlight(Highlight.ApplyHtml("<p>sea</p><b"), Highlight.ApplyQ("sea"))
    assert(html.value.endsWith("</p><b"), html)
