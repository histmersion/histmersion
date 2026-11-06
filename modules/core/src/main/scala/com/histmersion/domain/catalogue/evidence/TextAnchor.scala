package com.histmersion.domain.catalogue.evidence

import com.histmersion.util.ocr.Chunker
import com.histmersion.util.text.Anchor

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
  object ChunkChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Prefix = Prefix.T
  object Prefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Quote = Quote.T
  object Quote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Suffix = Suffix.T
  object Suffix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ReanchorText = _root_.com.histmersion.domain.catalogue.evidence.TextAnchor.Text
  val ReanchorText: _root_.com.histmersion.domain.catalogue.evidence.TextAnchor.Text.type = _root_.com.histmersion.domain.catalogue.evidence.TextAnchor.Text
  type ReanchorHint = _root_.com.histmersion.Offset
  val ReanchorHint: _root_.com.histmersion.Offset.type = _root_.com.histmersion.Offset

  def reanchor(
      text: com.histmersion.domain.catalogue.evidence.TextAnchor.ReanchorText,
      quote: com.histmersion.domain.catalogue.evidence.TextAnchor.Quote,
      prefix: Option[com.histmersion.domain.catalogue.evidence.TextAnchor.Prefix],
      suffix: Option[com.histmersion.domain.catalogue.evidence.TextAnchor.Suffix],
      hint: Option[com.histmersion.domain.catalogue.evidence.TextAnchor.ReanchorHint]
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
      best(text, found, prefix.getOrElse(com.histmersion.domain.catalogue.evidence.TextAnchor.Prefix("")), suffix.getOrElse(com.histmersion.domain.catalogue.evidence.TextAnchor.Suffix("")), hint)

  private def best(
      text: com.histmersion.domain.catalogue.evidence.TextAnchor.Text,
      found: List[Anchor],
      prefix: com.histmersion.domain.catalogue.evidence.TextAnchor.Prefix,
      suffix: com.histmersion.domain.catalogue.evidence.TextAnchor.Suffix,
      hint: Option[com.histmersion.Offset]
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
  def occurrences(hay: com.histmersion.domain.catalogue.evidence.TextAnchor.Text, needle: String): List[Int] =
    if needle.isEmpty then Nil
    else Iterator.iterate(hay.indexOf(needle))(i => hay.indexOf(needle, i + 1)).takeWhile(_ >= 0).toList

  /** The text with every whitespace run as one space, and for each of its characters the index it came from in the
    * original.
    */
  private def collapse(s: com.histmersion.domain.catalogue.evidence.TextAnchor.Text): (String, Array[Int]) =
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
  def chunkOf(text: com.histmersion.domain.catalogue.evidence.TextAnchor.Text, anchor: Anchor, chunkChars: com.histmersion.domain.catalogue.evidence.TextAnchor.ChunkChars): Option[com.histmersion.domain.catalogue.evidence.AttestationPlan.AnchorTry.ChunkOrd] =
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
