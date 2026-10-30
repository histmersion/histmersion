package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.util.data.{DataHolder, MutableDataSet}

/** The ONE Markdown dialect the server reads: CommonMark plus GFM pipe tables, configured the way GitHub and Obsidian
  * read them (the delimiter row must match the header's column count; a short row is padded, a long one cut). Both the
  * pages ([[MarkdownHtml]]) and the formatting lint ([[MarkdownLint]]) parse with this, so "the lint says it will not
  * render" and "the page does not render it" are the same fact.
  */
object MarkdownFlavor:
  val options: DataHolder =
    val o = new MutableDataSet()
    o.set(Parser.EXTENSIONS, java.util.List.of(TablesExtension.create()))
    o.set(TablesExtension.COLUMN_SPANS, java.lang.Boolean.FALSE)
    o.set(TablesExtension.APPEND_MISSING_COLUMNS, java.lang.Boolean.TRUE)
    o.set(TablesExtension.DISCARD_EXTRA_COLUMNS, java.lang.Boolean.TRUE)
    o.set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, java.lang.Boolean.TRUE)
    o.toImmutable

  val parser: Parser = Parser.builder(options).build()
