package com.histmersion
package server

import com.histmersion.api.{Html, Plural}
import com.histmersion.api.Html.html
import com.histmersion.config.*
import com.histmersion.domain.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.Title
import com.histmersion.server.instance.WebBundle
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** Pure helpers for the article pages. */
@movesTo(FeaturePlace.Server)
object ArticleHtml:
  type Url = Url.T
  object Url extends Newtype[String, Pure]

  type MatchNavMatches = MatchNavMatches.T
  object MatchNavMatches extends Newtype[Int, Pure]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def esc(s: HtmlRender.Text): Html = HtmlRender.esc(s)

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def toggle(ck: Citekey, full: Boolean, q: Option[HtmlRender.Query] = None): Html =
    val to = sourceUrl(ck, !full, fragment = false, q)
    if full then html"""<p class="toggle"><a href="$to">▲ hide full text</a></p>"""
    else html"""<p class="toggle"><a href="$to">▼ show full text (whole paper)</a></p>"""

  /** The match counter + jump control for `?q=`; empty when nothing matched (or no query). the web bundle's page entry
    * point wires the link and the `n` shortcut to the `<mark id="hl…">` elements — and the control NAMES that shortcut,
    * because a keyboard shortcut nobody is told about is a shortcut nobody uses (item 14).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def matchNav(matches: MatchNavMatches): Html =
    if matches.value <= 0 then Html.empty
    else
      val count =
        Plural.count(Plural.Count(matches.value.toLong), Plural.CountSingular("match"), Plural.CountPlural("matches"))
      html"""<p class="toggle match-nav" data-matches="$matches">$count · """ ++
        html"""<a href="#hl1" class="next-match">next ▼</a> <span class="kbd-hint">(n)</span></p>"""

  private val nl = Html.text("\n")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def page(title: Title, bodyHtml: Html, kind: PageKind, bundle: WebBundle): Html =
    Html.join(
      List(
        html"""<!doctype html><html lang="en"><head><meta charset="utf-8">""",
        html"""<meta name="viewport" content="width=device-width,initial-scale=1"><title>$title · Histmersion</title>""" ++
          html"""<link rel="icon" href="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 32 32%22%3E%3Ccircle cx=%2216%22 cy=%2216%22 r=%2214%22 fill=%22%231f2433%22/%3E%3Cpath d=%22M6 20c5-7 15-7 20 0%22 stroke=%22%236aa3ff%22 stroke-width=%223%22 fill=%22none%22/%3E%3Ccircle cx=%2216%22 cy=%2212%22 r=%223%22 fill=%22%23f2c14e%22/%3E%3C/svg%3E">""",
        html"""$ThemeScript<link rel="stylesheet" href="/web/style.css"></head><body class="article-page" data-page="${kind.wire}">""",
        html"""<div class="nav"><a href="/">← Histmersion</a></div>""",
        html"""<main id="page-content">$bodyHtml</main>""",
        html"""<script src="${bundle.path}"></script>""",
        html"""</body></html>"""
      ),
      nl
    )

  type Shell = (Title, Html, PageKind) => Html

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def shell(bundle: WebBundle): Shell = page(_, _, _, bundle)

  /** The reader's theme (the web UI's `Theme`, kept in `localStorage`), applied before the stylesheet paints so a page
    * never flashes the other theme first; the stylesheet follows the system without it. A file, not an inline script:
    * the Content-Security-Policy runs none.
    */
  val ThemeScript: Html = html"""<script src="/web/theme.js"></script>"""
