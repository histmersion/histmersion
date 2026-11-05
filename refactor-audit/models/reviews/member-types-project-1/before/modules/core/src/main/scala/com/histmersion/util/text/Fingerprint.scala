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
  private val quotes: Map[Char, String] = Map('’' -> "'", '‘' -> "'")

  /** Lower case, diacritics removed (`Güterbock` → `guterbock`, `López` → `lopez`): the shared [[LatinFold]] table
    * first (it also maps the letters NFD does not decompose — `ß`, `ł`, `ø`, `þ`), then NFD for every other script.
    */
  def fold(s: String): String =
    val pre = LatinFold.fold(s).flatMap(c => quotes.getOrElse(c, c.toString))
    Normalizer.normalize(pre, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT)

  /** Folded alphanumeric tokens, in order. */
  def tokens(s: String): List[String] =
    fold(s).split("[^\\p{L}\\p{N}]+").toList.filter(_.nonEmpty)

  /** The fingerprint of `s`. */
  def of(s: String): Fingerprint = Fingerprint(tokens(s).distinct.sorted.mkString(" "))
