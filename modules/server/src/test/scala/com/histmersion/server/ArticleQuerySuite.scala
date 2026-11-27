package com.histmersion
package server

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.Highlight
import com.histmersion.domain.library.Citekey
import com.histmersion.lint.{tolerated, LintRule}

/** The `?q=` article surface (plan §1.4): links keep the query, the match control appears only when something matched,
  * and highlighting survives the flexmark output (links included).
  */
@tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
class ArticleQuerySuite extends munit.FunSuite:

  test("the full-text toggle carries ?q= through, url-encoded"):
    assertEquals(
      ArticleHtml.toggle(Citekey("abc_d"), false, Some(HtmlRender.Query("sea peoples"))).markup,
      """<p class="toggle"><a href="/source/abc_d?full=true&amp;q=sea+peoples">▼ show full text (whole paper)</a></p>"""
    )
    assertEquals(
      ArticleHtml.toggle(Citekey("abc_d"), true, Some(HtmlRender.Query("q&a"))).markup,
      """<p class="toggle"><a href="/source/abc_d?q=q%26a">▲ hide full text</a></p>"""
    )

  test("without a query the toggle is exactly what it was before"):
    assertEquals(
      ArticleHtml.toggle(Citekey("abc_d"), false).markup,
      """<p class="toggle"><a href="/source/abc_d?full=true">▼ show full text (whole paper)</a></p>"""
    )

  test("match control: hidden at zero, singular at one, points at the first mark"):
    assertEquals(ArticleHtml.matchNav(ArticleHtml.MatchNavMatches(0)).markup, "")
    val one = ArticleHtml.matchNav(ArticleHtml.MatchNavMatches(1)).markup
    assert(one.contains("1 match ·"), one)
    val nav = ArticleHtml.matchNav(ArticleHtml.MatchNavMatches(7)).markup
    assert(nav.contains("""data-matches="7""""), nav)
    assert(nav.contains("7 matches"), nav)
    assert(nav.contains("""href="#hl1""""), nav)

  test("highlighting a rendered article never touches the citekey links it contains"):
    val html = MarkdownHtml.render(FullText.Shown.Markdown("See [egypt_2020](/source/egypt_2020) on Egypt."))
    val (out, n) = Highlight.pattern(Highlight.Term("egypt")).fold((html, 0))(api.Html.marked(html, _))
    assertEquals(n, 2) // the link TEXT and the word — not the href
    assert(out.markup.contains("""<a href="/source/egypt_2020">"""), out.markup)
