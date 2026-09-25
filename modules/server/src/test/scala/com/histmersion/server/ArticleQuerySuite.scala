package com.histmersion.server

import com.histmersion.core.Highlight

/** The `?q=` article surface (plan §1.4): links keep the query, the match control appears only when
  * something matched, and highlighting survives the flexmark output (links included). */
class ArticleQuerySuite extends munit.FunSuite:

  test("the full-text toggle carries ?q= through, url-encoded"):
    assertEquals(Article.toggle("abc_d", false, Some("sea peoples")),
      """<p class="toggle"><a href="/source/abc_d?full=true&amp;q=sea+peoples">▼ show full text (whole paper)</a></p>""")
    assertEquals(Article.toggle("abc_d", true, Some("q&a")),
      """<p class="toggle"><a href="/source/abc_d?q=q%26a">▲ hide full text</a></p>""")

  test("without a query the toggle is exactly what it was before"):
    assertEquals(Article.toggle("abc_d", false),
      """<p class="toggle"><a href="/source/abc_d?full=true">▼ show full text (whole paper)</a></p>""")

  test("match control: hidden at zero, singular at one, points at the first mark"):
    assertEquals(Article.matchNav(0), "")
    assert(Article.matchNav(1).contains("1 match ·"), Article.matchNav(1))
    val nav = Article.matchNav(7)
    assert(nav.contains("""data-matches="7""""), nav)
    assert(nav.contains("7 matches"), nav)
    assert(nav.contains("""href="#hl1""""), nav)

  test("highlighting a rendered article never touches the citekey links it contains"):
    val html = """<p>See <a href="/source/egypt_2020">egypt_2020</a> on Egypt.</p>"""
    val (out, n) = Highlight(html, "egypt")
    assertEquals(n, 2) // the link TEXT and the word — not the href
    assert(out.contains("""<a href="/source/egypt_2020">"""), out)
