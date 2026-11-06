package com.histmersion.api

import com.histmersion.api.library.Confidence

/** Cross-platform (no JVM `URLEncoder`): the web UI runs it under Scala.js too. */
object HtmlAtoms:


  def esc(s: String): String =
    ((s).value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"))

  def segment(s: String): String =
    val unreserved = (c: Char) => c.isLetterOrDigit && c < 128 || "-._~".contains(c)
    ((s).value.flatMap { c =>
      if unreserved(c) then c.toString
      else c.toString.getBytes("UTF-8").map(b => f"%%${b & 0xff}%02X").mkString
    })

  def sourceLink(citekey: com.histmersion.api.library.Citekey, doi: Option[com.histmersion.api.library.Doi], label: String): String =
    val doiAttr = doi.map((_).value.trim).filter(_.nonEmpty).fold("")(d => s""" data-doi="${esc((d))}"""")
    (s"""<a class="src-ref" data-ck="${esc(((citekey).value))}"$doiAttr href="/source/${esc(((segment(((citekey).value))).value))}">${esc(
        ((label).value)
      )}</a>""")

  def articleLink(slug: com.histmersion.api.research.ReviewGoal.Slug, label: String): String =
    (s"""<a class="art-ref" data-slug="${esc(((slug).value))}" href="/article/${esc(((segment(((slug).value))).value))}">${esc(((label).value))}</a>""")

  def confidenceBadge(c: Confidence): String =
    val wire = c.toString.toLowerCase
    (s"""<span class="conf-badge conf-$wire" title="${ConfidenceStyle.label(c)}">""" +
      s"""${ConfidenceStyle.icon(c)}<span class="conf-text">$wire</span></span>""")
