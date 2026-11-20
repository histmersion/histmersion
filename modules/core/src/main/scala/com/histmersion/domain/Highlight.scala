package com.histmersion
package domain

import io.github.iltotore.iron.constraint.any.Pure

/** What to highlight of a search in a rendered page — pure, no parser, no dependencies.
  *
  * Used by `GET /source/{ck}?q=…` so a reader lands on the passage that matched: the page marks each match of
  * [[pattern]] in its TEXT (api's `Html.marked`), copying tag names, attributes such as `href="/source/egypt"` and
  * entities (`&amp;`) as they are, so markup can never be corrupted by a term that happens to occur in it. Each match
  * becomes `<mark id="hl<n>">…</mark>`; the ids are what the web bundle's `InPage.nextMatch` steps through.
  */
object Highlight:
  type Term = Term.T
  object Term extends Newtype[String, Pure]

  /** Terms worth highlighting, from a `websearch_to_tsquery` style query: `"quoted phrases"` stay phrases, `-excluded`
    * terms and the `or`/`and` operators are dropped. Longest first, so a phrase wins over its own words.
    */
  def terms(q: Term): List[Term] =
    val phrase = """(-?)"([^"]*)"""".r
    val src = Option(q).fold("")(_.value)
    val phrases = phrase.findAllMatchIn(src).filter(_.group(1).isEmpty).map(_.group(2)).toList
    val bare = phrase.replaceAllIn(src, " ").split("""\s+""").toList
    val words = bare.filterNot(_.startsWith("-")).map(_.replaceAll("""^\W+|\W+$""", ""))
    (phrases ++ words)
      .map(_.trim)
      .filter(t => t.nonEmpty && !Operators(t.toLowerCase))
      .distinctBy(_.toLowerCase)
      .sortBy(-_.length)
      .map(Term(_))

  private val Operators = Set("or", "and", "not")

  /** What a page marks for the query `q`: none when it holds no term worth highlighting. Case-insensitive, any term at
    * a word start, extended over the rest of the word so a stemmed hit ("egypt" → "Egyptian") is highlighted whole;
    * phrases allow any run of whitespace.
    */
  def pattern(q: Term): Option[scala.util.matching.Regex] =
    terms(q) match
      case Nil => None
      case ts  =>
        val alts = ts
          .map(t => java.util.regex.Pattern.quote(t.value))
          .map(_.replaceAll("""\s+""", """\\E\\s+\\Q"""))
          .mkString("|")
        Some(s"""(?iu)\\b(?:$alts)\\w*""".r)
