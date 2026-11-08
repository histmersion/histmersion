package com.histmersion
package util.text

import com.histmersion.api.LatinFold
import io.github.iltotore.iron.*

import java.text.Normalizer

/** OpenRefine's key-collision fingerprint of a text: its folded tokens, de-duplicated, sorted, joined. Two labels with
  * one fingerprint are one label written two ways.
  */
type Fingerprint = Fingerprint.T

object Fingerprint extends RefinedType[String, Pure]:

  /** Typographic quotes, folded to the ASCII apostrophe. */
  type FoldResult = FoldResult.T
  object FoldResult extends RefinedType[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FoldS = FoldS.T
  object FoldS extends RefinedType[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TokensResult = TokensResult.T
  object TokensResult extends RefinedType[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TokensS = FoldS
  val TokensS: FoldS.type = FoldS
  type OfS = FoldS
  val OfS: FoldS.type = FoldS

  private val quotes: Map[Char, String] = Map('’' -> "'", '‘' -> "'")

  /** Lower case, diacritics removed (`Güterbock` → `guterbock`, `López` → `lopez`): the shared [[LatinFold]] table
    * first (it also maps the letters NFD does not decompose — `ß`, `ł`, `ø`, `þ`), then NFD for every other script.
    */
  def fold(s: FoldS): FoldResult =
    val pre = LatinFold.fold(s.value).flatMap(c => quotes.getOrElse(c, c.toString))
    FoldResult(Normalizer.normalize(pre, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT))

  /** Folded alphanumeric tokens, in order. */
  def tokens(s: TokensS): List[TokensResult] =
    fold(s).value.split("[^\\p{L}\\p{N}]+").toList.filter(_.nonEmpty).map(TokensResult(_))

  /** The fingerprint of `s`. */
  def of(s: OfS): Fingerprint = Fingerprint(tokens(s).map(_.value).distinct.sorted.mkString(" "))
