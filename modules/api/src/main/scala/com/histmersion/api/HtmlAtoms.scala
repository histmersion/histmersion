package com.histmersion.api

import com.histmersion.api.library.Confidence

/** Cross-platform (no JVM `URLEncoder`): the web UI runs it under Scala.js too. */
object HtmlAtoms:

  type EscResult = EscResult.T
  object EscResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EscS = EscS.T
  object EscS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SegmentResult = SegmentResult.T
  object SegmentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SegmentS = SegmentS.T
  object SegmentS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceLinkResult = SourceLinkResult.T
  object SourceLinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceLinkCitekey = SourceLinkCitekey.T
  object SourceLinkCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceLinkDoi = SourceLinkDoi.T
  object SourceLinkDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceLinkLabel = SourceLinkLabel.T
  object SourceLinkLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleLinkResult = ArticleLinkResult.T
  object ArticleLinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleLinkSlug = ArticleLinkSlug.T
  object ArticleLinkSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleLinkLabel = ArticleLinkLabel.T
  object ArticleLinkLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConfidenceBadgeResult = ConfidenceBadgeResult.T
  object ConfidenceBadgeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def esc(s: com.histmersion.api.HtmlAtoms.EscS): com.histmersion.api.HtmlAtoms.EscResult =
    _root_.com.histmersion.api.HtmlAtoms.EscResult((s).value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"))

  def segment(s: com.histmersion.api.HtmlAtoms.SegmentS): com.histmersion.api.HtmlAtoms.SegmentResult =
    val unreserved = (c: Char) => c.isLetterOrDigit && c < 128 || "-._~".contains(c)
    _root_.com.histmersion.api.HtmlAtoms.SegmentResult((s).value.flatMap { c =>
      if unreserved(c) then c.toString
      else c.toString.getBytes("UTF-8").map(b => f"%%${b & 0xff}%02X").mkString
    })

  def sourceLink(citekey: com.histmersion.api.HtmlAtoms.SourceLinkCitekey, doi: Option[com.histmersion.api.HtmlAtoms.SourceLinkDoi], label: com.histmersion.api.HtmlAtoms.SourceLinkLabel): com.histmersion.api.HtmlAtoms.SourceLinkResult =
    val doiAttr = doi.map((_).value.trim).filter(_.nonEmpty).fold("")(d => s""" data-doi="${esc(_root_.com.histmersion.api.HtmlAtoms.EscS(d))}"""")
    _root_.com.histmersion.api.HtmlAtoms.SourceLinkResult(s"""<a class="src-ref" data-ck="${esc(_root_.com.histmersion.api.HtmlAtoms.EscS((citekey).value))}"$doiAttr href="/source/${esc(_root_.com.histmersion.api.HtmlAtoms.EscS((segment(_root_.com.histmersion.api.HtmlAtoms.SegmentS((citekey).value))).value))}">${esc(
        _root_.com.histmersion.api.HtmlAtoms.EscS((label).value)
      )}</a>""")

  def articleLink(slug: com.histmersion.api.HtmlAtoms.ArticleLinkSlug, label: com.histmersion.api.HtmlAtoms.ArticleLinkLabel): com.histmersion.api.HtmlAtoms.ArticleLinkResult =
    _root_.com.histmersion.api.HtmlAtoms.ArticleLinkResult(s"""<a class="art-ref" data-slug="${esc(_root_.com.histmersion.api.HtmlAtoms.EscS((slug).value))}" href="/article/${esc(_root_.com.histmersion.api.HtmlAtoms.EscS((segment(_root_.com.histmersion.api.HtmlAtoms.SegmentS((slug).value))).value))}">${esc(_root_.com.histmersion.api.HtmlAtoms.EscS((label).value))}</a>""")

  def confidenceBadge(c: Confidence): com.histmersion.api.HtmlAtoms.ConfidenceBadgeResult =
    val wire = c.toString.toLowerCase
    _root_.com.histmersion.api.HtmlAtoms.ConfidenceBadgeResult(s"""<span class="conf-badge conf-$wire" title="${ConfidenceStyle.label(c)}">""" +
      s"""${ConfidenceStyle.icon(c)}<span class="conf-text">$wire</span></span>""")
