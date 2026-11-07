package com.histmersion.server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.vladsch.flexmark.html.HtmlRenderer

/** flexmark, configured once for every server page. */
object MarkdownHtml:

  private val renderer = HtmlRenderer.builder(MarkdownFlavor.options).build()

  def render(md: com.histmersion.server.FullText.Shown.Markdown): com.histmersion.server.HtmlRender.Html = renderer.render(MarkdownFlavor.parser.parse(md))
