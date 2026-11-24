package com.histmersion
package util.text

import com.histmersion.api.LatinFold
import io.github.iltotore.iron.*

import java.text.Normalizer
import com.histmersion.lint.{LintRule, tolerated}

/** OpenRefine's key-collision fingerprint of a text: its folded tokens, de-duplicated, sorted, joined. Two labels with
  * one fingerprint are one label written two ways.
  */
type Fingerprint = Fingerprint.T

object Fingerprint extends Newtype[String, Pure]:

  /** Typographic quotes, folded to the ASCII apostrophe. */
  type Folded = Folded.T
  @tolerated(LintRule.G47Root, "pending G47 (no FQN noise)")
  object Folded extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FoldS = FoldS.T
  @tolerated(LintRule.G47Root, "pending G47 (no FQN noise)")
  object FoldS extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Token = Token.T
  @tolerated(LintRule.G47Root, "pending G47 (no FQN noise)")
  object Token extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type TokensS = FoldS
  val TokensS: FoldS.type = FoldS
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type OfS = FoldS
  val OfS: FoldS.type = FoldS

  private val quotes: Map[Char, String] = Map('’' -> "'", '‘' -> "'")

  /** Lower case, diacritics removed (`Güterbock` → `guterbock`, `López` → `lopez`): the shared [[LatinFold]] table
    * first (it also maps the letters NFD does not decompose — `ß`, `ł`, `ø`, `þ`), then NFD for every other script.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def fold(s: FoldS): Folded =
    val pre = LatinFold.fold(s.value).flatMap(c => quotes.getOrElse(c, c.toString))
    Folded(
      Normalizer.normalize(pre, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT)
    )

  /** Folded alphanumeric tokens, in order. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tokens(s: TokensS): List[Token] =
    fold(s).value.split("[^\\p{L}\\p{N}]+").toList.filter(_.nonEmpty).map(Token(_))

  /** The fingerprint of `s`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(s: OfS): Fingerprint = Fingerprint(tokens(s).map(_.value).distinct.sorted.mkString(" "))
