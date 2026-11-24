package com.histmersion
package domain.catalogue.evidence

import com.histmersion.util.ocr.{Chunk, Chunker}
import com.histmersion.util.text.Anchor
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** Finding a quoted passage again in another text — the W3C Web Annotation TextQuoteSelector (`exact` + `prefix` +
  * `suffix`), as an attestation stores it (`quote`/`prefix`/`suffix`). Used when a merge keeps one OCR body and the
  * other side's attestations must point into it (docs/research-merge-import.md §5.3–§5.4). Pure.
  *
  * The quote is looked for exactly; failing that, with every run of whitespace read as one space (two OCR runs rarely
  * agree on line breaks). Where it occurs more than once, the occurrence whose surroundings agree longest with the
  * prefix and suffix wins, then the one nearest the old offset. Not found is `None` — the caller must report it, never
  * guess.
  */
object TextAnchor:
  type ChunkChars = ChunkChars.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object ChunkChars extends Newtype[Int, Pure]
  type Prefix = Prefix.T
  object Prefix extends Newtype[String, Pure]
  type Quote = Quote.T
  object Quote extends Newtype[String, Pure]
  type Suffix = Suffix.T
  object Suffix extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type ReanchorText = Text
  val ReanchorText: Text.type = Text
  type ReanchorHint = ReanchorHint.T
  object ReanchorHint extends Newtype[Int, Pure]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def reanchor(
      text: ReanchorText,
      quote: Quote,
      prefix: Option[Prefix],
      suffix: Option[Suffix],
      hint: Option[ReanchorHint]
  ): Option[Anchor] =
    val body = text.value
    val exactQuote = quote.value
    if exactQuote.isEmpty then None
    else
      val exact = occurrences(body, exactQuote).map(i => anchorAt(i, i + exactQuote.length))
      val found =
        if exact.nonEmpty then exact
        else
          val (norm, back) = collapse(body)
          val q = collapse(exactQuote)._1.trim
          if q.isEmpty then Nil
          else occurrences(norm, q).map(i => anchorAt(back(i), back(i + q.length - 1) + 1))
      best(body, found, prefix.fold("")(_.value), suffix.fold("")(_.value), hint.map(_.value))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def anchorAt(start: Int, end: Int): Anchor = Anchor(Anchor.Start(start), Anchor.End(end))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def best(
      text: String,
      found: List[Anchor],
      prefix: String,
      suffix: String,
      hint: Option[Int]
  ): Option[Anchor] =
    found match
      case Nil        => None
      case one :: Nil => Some(one)
      case many       =>
        Some(many.minBy { a =>
          val agree =
            commonSuffix(text.substring(0, a.start.value), prefix) + commonPrefix(text.substring(a.end.value), suffix)
          (-agree, hint.fold(0)(h => math.abs(a.start.value - h)), a.start.value: Int)
        })

  /** Every start index of `needle` in `hay`, overlapping ones included. */
  private def occurrences(hay: String, needle: String): List[Int] =
    if needle.isEmpty then Nil
    else Iterator.iterate(hay.indexOf(needle))(i => hay.indexOf(needle, i + 1)).takeWhile(_ >= 0).toList

  /** The text with every whitespace run as one space, and for each of its characters the index it came from in the
    * original.
    */
  private def collapse(s: String): (String, Array[Int]) =
    val sb = StringBuilder()
    val back = Array.newBuilder[Int]
    var inWs = false // one pass over the characters: whether the previous one was whitespace
    s.indices.foreach { i =>
      val c = s.charAt(i)
      if c.isWhitespace then
        if !inWs then { sb.append(' '); back += i }
        inWs = true
      else
        sb.append(c); back += i
        inWs = false
    }
    (sb.toString, back.result())

  private def commonSuffix(a: String, b: String): Int =
    a.reverseIterator.zip(b.reverseIterator).takeWhile((x, y) => x == y).length

  private def commonPrefix(a: String, b: String): Int =
    a.iterator.zip(b.iterator).takeWhile((x, y) => x == y).length

  /** The FTS chunk (`Chunker.chunk(text, chunkChars)`) a passage at `start` sits in: the quote's occurrence at `start`
    * is the n-th in the text, and the chunks are searched for the n-th one in document order. `None` when the chunking
    * cannot tell (a quote cut by a chunk boundary).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def chunkOf(text: Text, anchor: Anchor, chunkChars: ChunkChars): Option[Chunk.Ord] =
    if chunkChars.value <= 0 then None
    else
      val body = text.value
      val quote = body.substring(anchor.start.value, anchor.end.value)
      val nth = occurrences(body.substring(0, anchor.start.value), quote).size
      Chunker
        .chunk(Chunker.ChunkMarkdown(body), Chunker.ChunkMaxChars(chunkChars.value))
        .iterator
        .flatMap(c => occurrences(c.body.value, quote).map(_ => c.ord))
        .drop(nth)
        .nextOption()
