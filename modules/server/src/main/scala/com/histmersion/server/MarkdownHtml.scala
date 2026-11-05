package com.histmersion.server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.vladsch.flexmark.html.HtmlRenderer

/** flexmark, configured once for every server page. */
object MarkdownHtml:
  type RenderResult = RenderResult.T
  object RenderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenderMd = RenderMd.T
  object RenderMd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val renderer = HtmlRenderer.builder(MarkdownFlavor.options).build()

  def render(md: com.histmersion.server.MarkdownHtml.RenderMd): com.histmersion.server.MarkdownHtml.RenderResult = renderer.render(MarkdownFlavor.parser.parse(md))
