package com.histmersion.domain

import java.util.Locale

/** Plain text out of the bits of HTML a provider's metadata carries (Commons' `Artist` is a link to a user page). Pure.
  */
object MediaText:
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

  def plain(html: String, max: Int = 300): String =
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
    if text.length > max then text.take(max - 1).trim + "…" else text

  /** Pure: a URL without its `utm_*` tracking parameters (Commons adds them to every file URL). */
  def stripTracking(url: String): String =
    url.split("\\?", 2) match
      case Array(base, query) =>
        val kept = query.split("&").filterNot(p => p.toLowerCase(Locale.ROOT).startsWith("utm_") || p.isEmpty)
        if kept.isEmpty then base else base + "?" + kept.mkString("&")
      case _ => url

  /** Pure: a link-safe file name for a provider's file title (`File:Lions Gate.jpg` → `Lions_Gate.jpg`). */
  def safeName(title: String): String =
    val n = title.stripPrefix("File:").trim.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("_+", "_")
    if n.isEmpty then "file" else n.take(180)
