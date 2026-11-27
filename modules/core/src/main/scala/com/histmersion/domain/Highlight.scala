package com.histmersion
package domain

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@movesTo(FeaturePlace.LibrarySearch)
object Highlight:
  type Term = Term.T
  object Term extends Newtype[String, Pure]

  /** Terms worth highlighting, from a `websearch_to_tsquery` style query: `"quoted phrases"` stay phrases, `-excluded`
    * terms and the `or`/`and` operators are dropped. Longest first, so a phrase wins over its own words.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pattern(q: Term): Option[scala.util.matching.Regex] =
    terms(q) match
      case Nil => None
      case ts  =>
        val alts = ts
          .map(t => java.util.regex.Pattern.quote(t.value))
          .map(_.replaceAll("""\s+""", """\\E\\s+\\Q"""))
          .mkString("|")
        Some(s"""(?iu)\\b(?:$alts)\\w*""".r)
