package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.vladsch.flexmark.html.HtmlRenderer

/** flexmark, configured once for every server page. */
object MarkdownHtml:
  private val renderer = HtmlRenderer.builder(MarkdownFlavor.options).build()

  def render(md: String): String = renderer.render(MarkdownFlavor.parser.parse(md))
