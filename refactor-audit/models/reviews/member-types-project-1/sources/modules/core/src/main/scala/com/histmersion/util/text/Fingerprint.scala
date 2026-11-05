package com.histmersion.util.text

import com.histmersion.api.LatinFold
import io.github.iltotore.iron.*

import java.text.Normalizer

/** OpenRefine's key-collision fingerprint of a text: its folded tokens, de-duplicated, sorted, joined. Two labels with
  * one fingerprint are one label written two ways.
  */
type Fingerprint = Fingerprint.T

object Fingerprint extends RefinedType[String, Pure]:

  /** Typographic quotes, folded to the ASCII apostrophe. */
  type QuotesPart1 = QuotesPart1.T
  object QuotesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Char, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuotesPart2 = QuotesPart2.T
  object QuotesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FoldResult = FoldResult.T
  object FoldResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FoldS = FoldS.T
  object FoldS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TokensResult = TokensResult.T
  object TokensResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TokensS = TokensS.T
  object TokensS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfS = OfS.T
  object OfS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val quotes: Map[com.histmersion.util.text.Fingerprint.QuotesPart1, com.histmersion.util.text.Fingerprint.QuotesPart2] = Map('’' -> "'", '‘' -> "'")

  /** Lower case, diacritics removed (`Güterbock` → `guterbock`, `López` → `lopez`): the shared [[LatinFold]] table
    * first (it also maps the letters NFD does not decompose — `ß`, `ł`, `ø`, `þ`), then NFD for every other script.
    */
  def fold(s: com.histmersion.util.text.Fingerprint.FoldS): com.histmersion.util.text.Fingerprint.FoldResult =
    val pre = LatinFold.fold(s).flatMap(c => quotes.getOrElse(c, c.toString))
    Normalizer.normalize(pre, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT)

  /** Folded alphanumeric tokens, in order. */
  def tokens(s: com.histmersion.util.text.Fingerprint.TokensS): List[com.histmersion.util.text.Fingerprint.TokensResult] =
    fold(s).split("[^\\p{L}\\p{N}]+").toList.filter(_.nonEmpty)

  /** The fingerprint of `s`. */
  def of(s: com.histmersion.util.text.Fingerprint.OfS): Fingerprint = Fingerprint(tokens(s).distinct.sorted.mkString(" "))
