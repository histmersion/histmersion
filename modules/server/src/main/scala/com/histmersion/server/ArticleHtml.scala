package com.histmersion.server

import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.files.*
import sttp.shared.Identity
import sttp.model.{Header, MediaType, StatusCode}
import sttp.tapir.json.circe.*
import com.histmersion.api.Plural
import com.histmersion.config.*
import com.histmersion.domain.*

/** Pure helpers for the article pages. */
object ArticleHtml:
  /** HTML-escape a value destined for an attribute or text node. */
  type EscResult = EscResult.T
  object EscResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EscS = EscS.T
  object EscS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkResult = LinkResult.T
  object LinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkCk = LinkCk.T
  object LinkCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkFull = LinkFull.T
  object LinkFull extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkQ = LinkQ.T
  object LinkQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceUrlResult = SourceUrlResult.T
  object SourceUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceUrlCk = SourceUrlCk.T
  object SourceUrlCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceUrlFull = SourceUrlFull.T
  object SourceUrlFull extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceUrlFragment = SourceUrlFragment.T
  object SourceUrlFragment extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceUrlQ = SourceUrlQ.T
  object SourceUrlQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToggleResult = ToggleResult.T
  object ToggleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToggleCk = _root_.com.histmersion.server.ArticleHtml.LinkCk
  val ToggleCk: _root_.com.histmersion.server.ArticleHtml.LinkCk.type = _root_.com.histmersion.server.ArticleHtml.LinkCk
  type ToggleFull = ToggleFull.T
  object ToggleFull extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToggleQ = _root_.com.histmersion.server.ArticleHtml.LinkQ
  val ToggleQ: _root_.com.histmersion.server.ArticleHtml.LinkQ.type = _root_.com.histmersion.server.ArticleHtml.LinkQ
  type MatchNavResult = MatchNavResult.T
  object MatchNavResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchNavMatches = MatchNavMatches.T
  object MatchNavMatches extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageResult = PageResult.T
  object PageResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageTitle = PageTitle.T
  object PageTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageBodyHtml = PageBodyHtml.T
  object PageBodyHtml extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def esc(s: com.histmersion.server.ArticleHtml.EscS): com.histmersion.server.ArticleHtml.EscResult = HtmlRender.esc(s)

  private def link(ck: com.histmersion.server.ArticleHtml.LinkCk, full: com.histmersion.server.ArticleHtml.LinkFull, q: Option[com.histmersion.server.ArticleHtml.LinkQ]): com.histmersion.server.ArticleHtml.LinkResult =
    esc(sourceUrl(ck, full, fragment = false, q))

  /** `/source/{ck}` with the page's own query parameters (unescaped — for a header or `esc`). */
  def sourceUrl(ck: com.histmersion.server.ArticleHtml.SourceUrlCk, full: com.histmersion.server.ArticleHtml.SourceUrlFull, fragment: com.histmersion.server.ArticleHtml.SourceUrlFragment, q: Option[com.histmersion.server.ArticleHtml.SourceUrlQ]): com.histmersion.server.ArticleHtml.SourceUrlResult =
    val params = List(
      if full then Some("full=true") else None,
      if fragment then Some("fragment=true") else None,
      q.map(t => "q=" + java.net.URLEncoder.encode(t, "UTF-8"))
    ).flatten
    s"/source/${java.net.URLEncoder.encode(ck, "UTF-8")}" + (if params.isEmpty then ""
                                                             else params.mkString("?", "&", ""))

  def toggle(ck: com.histmersion.server.ArticleHtml.ToggleCk, full: com.histmersion.server.ArticleHtml.ToggleFull, q: Option[com.histmersion.server.ArticleHtml.ToggleQ] = None): com.histmersion.server.ArticleHtml.ToggleResult =
    if full then s"""<p class="toggle"><a href="${link(ck, false, q)}">▲ hide full text</a></p>"""
    else s"""<p class="toggle"><a href="${link(ck, true, q)}">▼ show full text (whole paper)</a></p>"""

  /** The match counter + jump control for `?q=`; empty when nothing matched (or no query). the web bundle's page entry
    * point wires the link and the `n` shortcut to the `<mark id="hl…">` elements — and the control NAMES that shortcut,
    * because a keyboard shortcut nobody is told about is a shortcut nobody uses (item 14).
    */
  def matchNav(matches: com.histmersion.server.ArticleHtml.MatchNavMatches): com.histmersion.server.ArticleHtml.MatchNavResult =
    if matches <= 0 then ""
    else
      s"""<p class="toggle match-nav" data-matches="$matches">${Plural.count(matches, "match", "matches")} · """ +
        """<a href="#hl1" class="next-match">next ▼</a> <span class="kbd-hint">(n)</span></p>"""

  /** `title` is DATA — a source's or an article's stored title, which an agent wrote — so it is escaped here rather
    * than at every call site. `bodyHtml` is already-rendered HTML and is not.
    */
  def page(title: com.histmersion.server.ArticleHtml.PageTitle, bodyHtml: com.histmersion.server.ArticleHtml.PageBodyHtml, kind: PageKind): com.histmersion.server.ArticleHtml.PageResult =
    s"""<!doctype html><html lang="en"><head><meta charset="utf-8">
       |<meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(
        title
      )} · Histmersion</title><link rel="icon" href="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 32 32%22%3E%3Ccircle cx=%2216%22 cy=%2216%22 r=%2214%22 fill=%22%231f2433%22/%3E%3Cpath d=%22M6 20c5-7 15-7 20 0%22 stroke=%22%236aa3ff%22 stroke-width=%223%22 fill=%22none%22/%3E%3Ccircle cx=%2216%22 cy=%2212%22 r=%223%22 fill=%22%23f2c14e%22/%3E%3C/svg%3E">
       |$ThemeScript<link rel="stylesheet" href="/web/style.css"></head><body class="article-page" data-page="${kind.wire}">
       |<div class="nav"><a href="/">← Histmersion</a></div>
       |<main id="page-content">$bodyHtml</main>
       |<script src="$BundlePath"></script>
       |</body></html>""".stripMargin

  /** The reader's theme (the web UI's `Theme`, kept in `localStorage`), applied before the stylesheet paints so a page
    * never flashes the other theme first; the stylesheet follows the system without it.
    */
  val ThemeScript =
    """<script>try{var t=localStorage.getItem("histmersion.theme");if(t==="light"||t==="dark")document.documentElement.setAttribute("data-theme",t)}catch(e){}</script>"""

  /** The Scala.js bundle — the same file the web UI's `index.html` loads, which carries every page's entry point
    * (`histmersionWeb` dispatches on the path).
    */
  val BundlePath = "/web/app.bundle.js?v=2"
