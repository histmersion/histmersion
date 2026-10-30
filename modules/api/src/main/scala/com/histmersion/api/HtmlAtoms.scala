package com.histmersion.api

import com.histmersion.values.Confidence

/** The HTML atoms both front ends emit as STRINGS — the server's pages (`server.HtmlRender`) and the web UI's map
  * popups — so a source link or a confidence mark is one markup, byte for byte, wherever it is drawn. Cross-platform
  * (no JVM `URLEncoder`), pure.
  */
object HtmlAtoms:

  def esc(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

  /** Percent-encode one URL path segment (UTF-8), keeping the RFC 3986 unreserved characters. */
  def segment(s: String): String =
    val unreserved = (c: Char) => c.isLetterOrDigit && c < 128 || "-._~".contains(c)
    s.flatMap { c =>
      if unreserved(c) then c.toString
      else c.toString.getBytes("UTF-8").map(b => f"%%${b & 0xff}%02X").mkString
    }

  /** THE source anchor: `<a class="src-ref" data-ck data-doi href="/source/ck">label</a>`. */
  def sourceLink(citekey: String, doi: Option[String], label: String): String =
    val doiAttr = doi.map(_.trim).filter(_.nonEmpty).fold("")(d => s""" data-doi="${esc(d)}"""")
    s"""<a class="src-ref" data-ck="${esc(citekey)}"$doiAttr href="/source/${esc(segment(citekey))}">${esc(
        label
      )}</a>"""

  /** THE article anchor: `<a class="art-ref" data-slug href="/article/slug">label</a>`. */
  def articleLink(slug: String, label: String): String =
    s"""<a class="art-ref" data-slug="${esc(slug)}" href="/article/${esc(segment(slug))}">${esc(label)}</a>"""

  /** THE confidence mark: icon + label. Its colour is the stylesheet's (`.conf-badge.conf-<level>`, the
    * `--conf-<level>` tokens: the ramp's midpoint desaturated as the map's dots are).
    */
  def confidenceBadge(c: Confidence): String =
    val wire = c.toString.toLowerCase
    s"""<span class="conf-badge conf-$wire" title="${ConfidenceStyle.label(c)}">""" +
      s"""${ConfidenceStyle.icon(c)}<span class="conf-text">$wire</span></span>"""
