package com.histmersion
package server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Html
import com.vladsch.flexmark.html.HtmlRenderer

/** flexmark, configured once for every server page. flexmark copies raw HTML through, and the pages rely on it (the
  * wikilink anchors are spliced in as inline HTML before it runs), so what it writes goes through the allow-list
  * ([[Html.sanitize]]): a `<script>`, an `on…` attribute or a `javascript:` link in a stored text is shown as text or
  * dropped, never run.
  */
object MarkdownHtml:

  private val renderer = HtmlRenderer.builder(MarkdownFlavor.options).build()

  def render(md: FullText.Shown.Markdown): Html =
    Html.sanitize(renderer.render(MarkdownFlavor.parser.parse(md.value)))
