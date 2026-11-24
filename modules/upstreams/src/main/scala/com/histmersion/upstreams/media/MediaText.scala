package com.histmersion
package upstreams.media
import java.util.Locale
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.upstreams.landing.RepositoryLanding
import com.histmersion.plugin.WorkKey
import com.histmersion.lint.{LintRule, tolerated}

/** Plain text out of the bits of HTML a provider's metadata carries (Commons' `Artist` is a link to a user page). Pure.
  */
object MediaText:
  type FileName = FileName.T
  object FileName extends Newtype[String, Pure]
  type Html = Html.T
  @tolerated(LintRule.HtmlNewtype, "pending G96 (one markup type, api.Html)")
  object Html extends Newtype[String, Pure]
  type PlainText = PlainText.T
  object PlainText extends Newtype[String, Pure]
  type PlainMax = PlainMax.T
  object PlainMax extends Newtype[Int, Pure]

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

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def plain(html: Html, max: PlainMax = PlainMax(300)): PlainText =
    val noTags = Tag.replaceAllIn(Option(html).map(_.value).getOrElse(""), " ")
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
    if text.length > (max).value then PlainText(text.take((max).value - 1).trim + "…") else PlainText(text)

  /** Pure: a URL without its `utm_*` tracking parameters (Commons adds them to every file URL). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def stripTracking(url: RepositoryLanding.Candidate.Url): RepositoryLanding.Candidate.Url =
    (url).value.split("\\?", 2) match
      case Array(base, query) =>
        val kept = query.split("&").filterNot(p => p.toLowerCase(Locale.ROOT).startsWith("utm_") || p.isEmpty)
        if kept.isEmpty then RepositoryLanding.Candidate.Url(base)
        else RepositoryLanding.Candidate.Url(base + "?" + kept.mkString("&"))
      case _ => RepositoryLanding.Candidate.Url((url).value)

  /** Pure: a link-safe file name for a provider's file title (`File:Lions Gate.jpg` → `Lions_Gate.jpg`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def safeName(title: WorkKey.Title): FileName =
    val n = (title).value.stripPrefix("File:").trim.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("_+", "_")
    if n.isEmpty then FileName("file") else FileName(n.take(180))
