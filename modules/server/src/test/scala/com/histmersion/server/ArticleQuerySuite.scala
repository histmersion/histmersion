package com.histmersion
package server

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.Highlight
import com.histmersion.domain.library.Citekey

/** The `?q=` article surface (plan §1.4): links keep the query, the match control appears only when something matched,
  * and highlighting survives the flexmark output (links included).
  */
class ArticleQuerySuite extends munit.FunSuite:

  test("the full-text toggle carries ?q= through, url-encoded"):
    assertEquals(
      ArticleHtml.toggle(Citekey("abc_d"), false, Some(HtmlRender.Query("sea peoples"))),
      """<p class="toggle"><a href="/source/abc_d?full=true&amp;q=sea+peoples">▼ show full text (whole paper)</a></p>"""
    )
    assertEquals(
      ArticleHtml.toggle(Citekey("abc_d"), true, Some(HtmlRender.Query("q&a"))),
      """<p class="toggle"><a href="/source/abc_d?q=q%26a">▲ hide full text</a></p>"""
    )

  test("without a query the toggle is exactly what it was before"):
    assertEquals(
      ArticleHtml.toggle(Citekey("abc_d"), false),
      """<p class="toggle"><a href="/source/abc_d?full=true">▼ show full text (whole paper)</a></p>"""
    )

  test("match control: hidden at zero, singular at one, points at the first mark"):
    assertEquals(ArticleHtml.matchNav(ArticleHtml.MatchNavMatches(0)), "")
    assert(ArticleHtml.matchNav(ArticleHtml.MatchNavMatches(1)).value.contains("1 match ·"), ArticleHtml.matchNav(ArticleHtml.MatchNavMatches(1)))
    val nav = ArticleHtml.matchNav(ArticleHtml.MatchNavMatches(7))
    assert(nav.value.contains("""data-matches="7""""), nav)
    assert(nav.value.contains("7 matches"), nav)
    assert(nav.value.contains("""href="#hl1""""), nav)

  test("highlighting a rendered article never touches the citekey links it contains"):
    val html = Highlight.Html("""<p>See <a href="/source/egypt_2020">egypt_2020</a> on Egypt.</p>""")
    val (out, n) = Highlight(html, Highlight.ApplyQ("egypt"))
    assertEquals(n, 2) // the link TEXT and the word — not the href
    assert(out.value.contains("""<a href="/source/egypt_2020">"""), out)
