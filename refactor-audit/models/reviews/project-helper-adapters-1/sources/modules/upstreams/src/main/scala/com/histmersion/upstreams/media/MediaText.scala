package com.histmersion.upstreams.media
import java.util.Locale

/** Plain text out of the bits of HTML a provider's metadata carries (Commons' `Artist` is a link to a user page). Pure.
  */
object MediaText:
  type PlainResult = PlainResult.T
  object PlainResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlainHtml = PlainHtml.T
  object PlainHtml extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlainMax = PlainMax.T
  object PlainMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StripTrackingResult = StripTrackingResult.T
  object StripTrackingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StripTrackingUrl = StripTrackingUrl.T
  object StripTrackingUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SafeNameResult = SafeNameResult.T
  object SafeNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SafeNameTitle = SafeNameTitle.T
  object SafeNameTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Tag = "<[^>]*>".r
  private val Dec = "&#([0-9]{1,7});".r
  private val Hex = "&#[xX]([0-9a-fA-F]{1,6});".r
  private val Named = Map(
    "&amp;" -> "&",
    "&lt;" -> "<",
    "&gt;" -> ">",
    "&quot;" -> "\"",
    "&#39;" -> "'",
    "&apos;" -> "'",
    "&nbsp;" -> " "
  )

  def plain(html: com.histmersion.upstreams.media.MediaText.PlainHtml, max: com.histmersion.upstreams.media.MediaText.PlainMax = _root_.com.histmersion.upstreams.media.MediaText.PlainMax(300)): com.histmersion.upstreams.media.MediaText.PlainResult =
    val noTags = Tag.replaceAllIn(Option(html).getOrElse(""), " ")
    val named = Named.foldLeft(noTags) { case (acc, (k, v)) => acc.replace(k, v) }
    val dec = Dec.replaceAllIn(
      named,
      m =>
        java.util.regex.Matcher
          .quoteReplacement(scala.util.Try(new String(Character.toChars(m.group(1).toInt))).getOrElse(""))
    )
    val hex = Hex.replaceAllIn(
      dec,
      m =>
        java.util.regex.Matcher.quoteReplacement(
          scala.util.Try(new String(Character.toChars(Integer.parseInt(m.group(1), 16)))).getOrElse("")
        )
    )
    val text = hex.replaceAll("\\s+", " ").trim
    if text.length > (max).value then _root_.com.histmersion.upstreams.media.MediaText.PlainResult(text.take((max).value - 1).trim + "…") else _root_.com.histmersion.upstreams.media.MediaText.PlainResult(text)

  /** Pure: a URL without its `utm_*` tracking parameters (Commons adds them to every file URL). */
  def stripTracking(url: com.histmersion.upstreams.media.MediaText.StripTrackingUrl): com.histmersion.upstreams.media.MediaText.StripTrackingResult =
    (url).value.split("\\?", 2) match
      case Array(base, query) =>
        val kept = query.split("&").filterNot(p => p.toLowerCase(Locale.ROOT).startsWith("utm_") || p.isEmpty)
        if kept.isEmpty then _root_.com.histmersion.upstreams.media.MediaText.StripTrackingResult(base) else _root_.com.histmersion.upstreams.media.MediaText.StripTrackingResult(base + "?" + kept.mkString("&"))
      case _ => _root_.com.histmersion.upstreams.media.MediaText.StripTrackingResult((url).value)

  /** Pure: a link-safe file name for a provider's file title (`File:Lions Gate.jpg` → `Lions_Gate.jpg`). */
  def safeName(title: com.histmersion.upstreams.media.MediaText.SafeNameTitle): com.histmersion.upstreams.media.MediaText.SafeNameResult =
    val n = (title).value.stripPrefix("File:").trim.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("_+", "_")
    if n.isEmpty then _root_.com.histmersion.upstreams.media.MediaText.SafeNameResult("file") else _root_.com.histmersion.upstreams.media.MediaText.SafeNameResult(n.take(180))
