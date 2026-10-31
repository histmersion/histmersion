package com.histmersion.domain

import com.histmersion.util.ocr.Chunker

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

  /** Where the passage is: `[start, end)` character offsets into the text. */
  final case class Anchor(start: Int, end: Int)

  def reanchor(
      text: String,
      quote: String,
      prefix: Option[String],
      suffix: Option[String],
      hint: Option[Int]
  ): Option[Anchor] =
    if quote.isEmpty then None
    else
      val exact = occurrences(text, quote).map(i => Anchor(i, i + quote.length))
      val found =
        if exact.nonEmpty then exact
        else
          val (norm, back) = collapse(text)
          val q = collapse(quote)._1.trim
          if q.isEmpty then Nil
          else occurrences(norm, q).map(i => Anchor(back(i), back(i + q.length - 1) + 1))
      best(text, found, prefix.getOrElse(""), suffix.getOrElse(""), hint)

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
          val agree = commonSuffix(text.substring(0, a.start), prefix) + commonPrefix(text.substring(a.end), suffix)
          (-agree, hint.fold(0)(h => math.abs(a.start - h)), a.start)
        })

  /** Every start index of `needle` in `hay`, overlapping ones included. */
  def occurrences(hay: String, needle: String): List[Int] =
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
  def chunkOf(text: String, anchor: Anchor, chunkChars: Int): Option[Int] =
    if chunkChars <= 0 then None
    else
      val quote = text.substring(anchor.start, anchor.end)
      val nth = occurrences(text.substring(0, anchor.start), quote).size
      Chunker
        .chunk(text, chunkChars)
        .iterator
        .flatMap(c => occurrences(c.body, quote).map(_ => c.ord))
        .drop(nth)
        .nextOption()
