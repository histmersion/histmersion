package com.histmersion.api

import com.histmersion.values.Confidence

/** Cross-platform (no JVM `URLEncoder`): the web UI runs it under Scala.js too. */
object HtmlAtoms:

  def esc(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

  def segment(s: String): String =
    val unreserved = (c: Char) => c.isLetterOrDigit && c < 128 || "-._~".contains(c)
    s.flatMap { c =>
      if unreserved(c) then c.toString
      else c.toString.getBytes("UTF-8").map(b => f"%%${b & 0xff}%02X").mkString
    }

  def sourceLink(citekey: String, doi: Option[String], label: String): String =
    val doiAttr = doi.map(_.trim).filter(_.nonEmpty).fold("")(d => s""" data-doi="${esc(d)}"""")
    s"""<a class="src-ref" data-ck="${esc(citekey)}"$doiAttr href="/source/${esc(segment(citekey))}">${esc(
        label
      )}</a>"""

  def articleLink(slug: String, label: String): String =
    s"""<a class="art-ref" data-slug="${esc(slug)}" href="/article/${esc(segment(slug))}">${esc(label)}</a>"""

  def confidenceBadge(c: Confidence): String =
    val wire = c.toString.toLowerCase
    s"""<span class="conf-badge conf-$wire" title="${ConfidenceStyle.label(c)}">""" +
      s"""${ConfidenceStyle.icon(c)}<span class="conf-text">$wire</span></span>"""
