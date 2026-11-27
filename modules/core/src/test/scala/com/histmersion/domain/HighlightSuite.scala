package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.Html
import com.histmersion.api.given
import com.histmersion.config.given
import com.histmersion.lint.{tolerated, LintRule}

class HighlightSuite extends munit.FunSuite:

  private def page(markup: String): Html = Html.sanitize(markup)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def marked(html: Html, q: String): (Html, Int) =
    Highlight.pattern(Highlight.Term(q)).fold((html, 0))(Html.marked(html, _))

  test("pattern: none for a query with no term worth highlighting"):
    assertEquals(Highlight.pattern(Highlight.Term("")), None)
    assertEquals(Highlight.pattern(Highlight.Term("-only or and")), None)
    assert(Highlight.pattern(Highlight.Term("sea")).isDefined)

  test("terms: bare words, quoted phrases, longest first"):
    assertEquals(
      Highlight.terms(Highlight.Term("""collapse "sea peoples" bronze""")),
      List("sea peoples", "collapse", "bronze")
    )

  test("terms: -excluded terms and the boolean operators are dropped"):
    assertEquals(Highlight.terms(Highlight.Term("""sea -egypt or bronze""")), List("bronze", "sea"))
    assertEquals(Highlight.terms(Highlight.Term("""collapse -"new kingdom"""")), List("collapse"))

  test("terms: punctuation trimmed, duplicates folded case-insensitively, empty query is empty"):
    assertEquals(Highlight.terms(Highlight.Term("(Hittite), hittite!")), List("Hittite"))
    assertEquals(Highlight.terms(Highlight.Term("   ")), Nil)
    assertEquals(Highlight.terms(Highlight.Term("")), Nil)

  test("marks wrap text hits and count them"):
    val (html, n) = marked(page("<p>The Sea Peoples raided.</p>"), ("sea"))
    assertEquals(n, 1)
    assertEquals(html.markup, """<p>The <mark id="hl1">Sea</mark> Peoples raided.</p>""")

  test("match extends over the rest of the word, so a stemmed hit reads whole"):
    val (html, n) = marked(page("<p>Egyptian records</p>"), ("egypt"))
    assertEquals(n, 1)
    assert(html.markup.contains("""<mark id="hl1">Egyptian</mark>"""), html.markup)

  test("attributes and tag names are never touched"):
    val (html, n) = marked(page("""<a href="/source/egypt" class="egypt">Egypt</a>"""), ("egypt"))
    assertEquals(n, 1)
    assertEquals(html.markup, """<a href="/source/egypt" class="egypt"><mark id="hl1">Egypt</mark></a>""")

  test("phrases match across whitespace and newlines, and win over their own words"):
    val (html, n) = marked(page("<p>the sea\n  peoples came</p>"), (""""sea peoples""""))
    assertEquals(n, 1)
    assert(html.markup.contains("<mark id=\"hl1\">sea\n  peoples</mark>"), html.markup)

  test("ids are sequential across the whole document"):
    val (html, n) = marked(page("<p>sea</p><p>sea</p><p>sea</p>"), ("sea"))
    assertEquals(n, 3)
    assertEquals(
      """id="hl\d"""".r.findAllIn(html.markup).toList,
      List("""id="hl1"""", """id="hl2"""", """id="hl3"""")
    )

  test("entities are left alone even when a term matches inside them"):
    val (html, n) = marked(page("<p>A &amp; B</p>"), ("amp"))
    assertEquals(n, 0)
    assertEquals(html.markup, "<p>A &amp; B</p>")

  test("a page's script and style, shown as text by the cleaner, are text: their words are marked as any other"):
    val (html, n) = marked(page("""<style>.egypt{}</style><p>Egypt</p>"""), ("egypt"))
    assertEquals(n, 2)
    assert(html.markup.startsWith("""&lt;style&gt;.<mark id="hl1">egypt</mark>{}&lt;/style&gt;"""), html.markup)

  test("no query, no terms, or no match leaves the html byte-identical"):
    val src = page("<p>nothing here</p>")
    List("", "-only", "absent").foreach { q =>
      val (html, n) = marked(src, q)
      assertEquals((html.markup, n), (src.markup, 0), q)
    }

  test("regex metacharacters in the query are literal"):
    val (html, n) = marked(page("<p>a.b and axb</p>"), ("a.b"))
    assertEquals(n, 1)
    assert(html.markup.contains("""<mark id="hl1">a.b</mark>"""), html.markup)
