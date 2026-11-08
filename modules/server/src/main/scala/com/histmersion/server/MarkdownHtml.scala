package com.histmersion
package server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.vladsch.flexmark.html.HtmlRenderer

/** flexmark, configured once for every server page. */
object MarkdownHtml:

  private val renderer = HtmlRenderer.builder(MarkdownFlavor.options).build()

  def render(md: FullText.Shown.Markdown): HtmlRender.Html = HtmlRender.Html(renderer.render(MarkdownFlavor.parser.parse(md.value)))
