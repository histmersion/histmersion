package com.histmersion
package server

import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.files.*
import sttp.shared.Identity
import sttp.model.{Header, MediaType, StatusCode}
import sttp.tapir.json.circe.*
import com.histmersion.api.Plural
import com.histmersion.config.*
import com.histmersion.domain.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.Title

/** Pure helpers for the article pages. */
object ArticleHtml:
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  /** HTML-escape a value destined for an attribute or text node. */
  type MatchNavMatches = MatchNavMatches.T
  object MatchNavMatches extends Newtype[Int, Pure]

  def esc(s: HtmlRender.Text): HtmlRender.Html = HtmlRender.esc(s)

  private def link(ck: Citekey, full: Boolean, q: Option[HtmlRender.Query]): HtmlRender.Html =
    esc(HtmlRender.Text(sourceUrl(ck, full, fragment = false, q).value))

  /** `/source/{ck}` with the page's own query parameters (unescaped — for a header or `esc`). */
  def sourceUrl(ck: Citekey, full: Boolean, fragment: Boolean, q: Option[HtmlRender.Query]): Url =
    val params = List(
      if full then Some("full=true") else None,
      if fragment then Some("fragment=true") else None,
      q.map(t => "q=" + java.net.URLEncoder.encode(t.value, "UTF-8"))
    ).flatten
    Url(
      s"/source/${java.net.URLEncoder.encode(ck.value, "UTF-8")}" + (if params.isEmpty then ""
                                                                      else params.mkString("?", "&", ""))
    )

  def toggle(ck: Citekey, full: Boolean, q: Option[HtmlRender.Query] = None): HtmlRender.Html =
    if full then HtmlRender.Html(s"""<p class="toggle"><a href="${link(ck, false, q).value}">▲ hide full text</a></p>""")
    else HtmlRender.Html(s"""<p class="toggle"><a href="${link(ck, true, q).value}">▼ show full text (whole paper)</a></p>""")

  /** The match counter + jump control for `?q=`; empty when nothing matched (or no query). the web bundle's page entry
    * point wires the link and the `n` shortcut to the `<mark id="hl…">` elements — and the control NAMES that shortcut,
    * because a keyboard shortcut nobody is told about is a shortcut nobody uses (item 14).
    */
  def matchNav(matches: MatchNavMatches): HtmlRender.Html =
    if matches.value <= 0 then HtmlRender.Html("")
    else
      HtmlRender.Html(
        s"""<p class="toggle match-nav" data-matches="${matches.value}">${Plural.count(Plural.Count(matches.value.toLong), Plural.CountSingular("match"), Plural.CountPlural("matches"))} · """ +
          """<a href="#hl1" class="next-match">next ▼</a> <span class="kbd-hint">(n)</span></p>"""
      )

  /** `title` is DATA — a source's or an article's stored title, which an agent wrote — so it is escaped here rather
    * than at every call site. `bodyHtml` is already-rendered HTML and is not.
    */
  def page(title: Title, bodyHtml: HtmlRender.Html, kind: PageKind): HtmlRender.Html =
    HtmlRender.Html(s"""<!doctype html><html lang="en"><head><meta charset="utf-8">
       |<meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(
        HtmlRender.Text(title.value)
      )} · Histmersion</title><link rel="icon" href="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 32 32%22%3E%3Ccircle cx=%2216%22 cy=%2216%22 r=%2214%22 fill=%22%231f2433%22/%3E%3Cpath d=%22M6 20c5-7 15-7 20 0%22 stroke=%22%236aa3ff%22 stroke-width=%223%22 fill=%22none%22/%3E%3Ccircle cx=%2216%22 cy=%2212%22 r=%223%22 fill=%22%23f2c14e%22/%3E%3C/svg%3E">
       |$ThemeScript<link rel="stylesheet" href="/web/style.css"></head><body class="article-page" data-page="${kind.wire}">
       |<div class="nav"><a href="/">← Histmersion</a></div>
       |<main id="page-content">$bodyHtml</main>
       |<script src="$BundlePath"></script>
       |</body></html>""".stripMargin)

  /** The reader's theme (the web UI's `Theme`, kept in `localStorage`), applied before the stylesheet paints so a page
    * never flashes the other theme first; the stylesheet follows the system without it.
    */
  val ThemeScript =
    """<script>try{var t=localStorage.getItem("histmersion.theme");if(t==="light"||t==="dark")document.documentElement.setAttribute("data-theme",t)}catch(e){}</script>"""

  /** The Scala.js bundle — the same file the web UI's `index.html` loads, which carries every page's entry point
    * (`histmersionWeb` dispatches on the path).
    */
  val BundlePath = "/web/app.bundle.js?v=2"
