package com.histmersion
package server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Html
import com.vladsch.flexmark.html.HtmlRenderer
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** flexmark, configured once for every server page. flexmark copies raw HTML through, and the pages rely on it (the
  * wikilink anchors are spliced in as inline HTML before it runs), so what it writes goes through the allow-list
  * ([[Html.sanitize]]): a `<script>`, an `on…` attribute or a `javascript:` link in a stored text is shown as text or
  * dropped, never run.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@movesTo(FeaturePlace.UtilCommon)
object MarkdownHtml:

  private val renderer = HtmlRenderer.builder(MarkdownFlavor.options).build()

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def render(md: FullText.Shown.Markdown): Html =
    Html.sanitize(renderer.render(MarkdownFlavor.parser.parse(md.value)))
