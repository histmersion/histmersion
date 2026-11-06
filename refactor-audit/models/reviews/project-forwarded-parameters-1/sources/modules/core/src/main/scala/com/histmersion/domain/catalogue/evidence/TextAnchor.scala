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

  type ReanchorText = _root_.com.histmersion.domain.catalogue.evidence.TextAnchor.BestText
  val ReanchorText: _root_.com.histmersion.domain.catalogue.evidence.TextAnchor.BestText.type = _root_.com.histmersion.domain.catalogue.evidence.TextAnchor.BestText
  type ReanchorQuote = ReanchorQuote.T
  object ReanchorQuote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReanchorPrefix = ReanchorPrefix.T
  object ReanchorPrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReanchorSuffix = ReanchorSuffix.T
  object ReanchorSuffix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReanchorHint = _root_.com.histmersion.domain.catalogue.evidence.TextAnchor.BestHint
  val ReanchorHint: _root_.com.histmersion.domain.catalogue.evidence.TextAnchor.BestHint.type = _root_.com.histmersion.domain.catalogue.evidence.TextAnchor.BestHint
  type BestText = BestText.T
  object BestText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BestPrefix = BestPrefix.T
  object BestPrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BestSuffix = BestSuffix.T
  object BestSuffix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BestHint = BestHint.T
  object BestHint extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OccurrencesResult = OccurrencesResult.T
  object OccurrencesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OccurrencesHay = OccurrencesHay.T
  object OccurrencesHay extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OccurrencesNeedle = OccurrencesNeedle.T
  object OccurrencesNeedle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollapseResultPart1 = CollapseResultPart1.T
  object CollapseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollapseResultPart2 = CollapseResultPart2.T
  object CollapseResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollapseS = CollapseS.T
  object CollapseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommonSuffixResult = CommonSuffixResult.T
  object CommonSuffixResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommonSuffixA = CommonSuffixA.T
  object CommonSuffixA extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommonSuffixB = CommonSuffixB.T
  object CommonSuffixB extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommonPrefixResult = CommonPrefixResult.T
  object CommonPrefixResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommonPrefixA = CommonPrefixA.T
  object CommonPrefixA extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommonPrefixB = CommonPrefixB.T
  object CommonPrefixB extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChunkOfResult = ChunkOfResult.T
  object ChunkOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChunkOfText = ChunkOfText.T
  object ChunkOfText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChunkOfChunkChars = ChunkOfChunkChars.T
  object ChunkOfChunkChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def reanchor(
      text: com.histmersion.domain.catalogue.evidence.TextAnchor.ReanchorText,
      quote: com.histmersion.domain.catalogue.evidence.TextAnchor.ReanchorQuote,
      prefix: Option[com.histmersion.domain.catalogue.evidence.TextAnchor.ReanchorPrefix],
      suffix: Option[com.histmersion.domain.catalogue.evidence.TextAnchor.ReanchorSuffix],
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
      best(text, found, prefix.getOrElse(""), suffix.getOrElse(""), hint)

  private def best(
      text: com.histmersion.domain.catalogue.evidence.TextAnchor.BestText,
      found: List[Anchor],
      prefix: com.histmersion.domain.catalogue.evidence.TextAnchor.BestPrefix,
      suffix: com.histmersion.domain.catalogue.evidence.TextAnchor.BestSuffix,
      hint: Option[com.histmersion.domain.catalogue.evidence.TextAnchor.BestHint]
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
  def occurrences(hay: com.histmersion.domain.catalogue.evidence.TextAnchor.OccurrencesHay, needle: com.histmersion.domain.catalogue.evidence.TextAnchor.OccurrencesNeedle): List[com.histmersion.domain.catalogue.evidence.TextAnchor.OccurrencesResult] =
    if needle.isEmpty then Nil
    else Iterator.iterate(hay.indexOf(needle))(i => hay.indexOf(needle, i + 1)).takeWhile(_ >= 0).toList

  /** The text with every whitespace run as one space, and for each of its characters the index it came from in the
    * original.
    */
  private def collapse(s: com.histmersion.domain.catalogue.evidence.TextAnchor.CollapseS): (com.histmersion.domain.catalogue.evidence.TextAnchor.CollapseResultPart1, Array[com.histmersion.domain.catalogue.evidence.TextAnchor.CollapseResultPart2]) =
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

  private def commonSuffix(a: com.histmersion.domain.catalogue.evidence.TextAnchor.CommonSuffixA, b: com.histmersion.domain.catalogue.evidence.TextAnchor.CommonSuffixB): com.histmersion.domain.catalogue.evidence.TextAnchor.CommonSuffixResult =
    a.reverseIterator.zip(b.reverseIterator).takeWhile((x, y) => x == y).length

  private def commonPrefix(a: com.histmersion.domain.catalogue.evidence.TextAnchor.CommonPrefixA, b: com.histmersion.domain.catalogue.evidence.TextAnchor.CommonPrefixB): com.histmersion.domain.catalogue.evidence.TextAnchor.CommonPrefixResult =
    a.iterator.zip(b.iterator).takeWhile((x, y) => x == y).length

  /** The FTS chunk (`Chunker.chunk(text, chunkChars)`) a passage at `start` sits in: the quote's occurrence at `start`
    * is the n-th in the text, and the chunks are searched for the n-th one in document order. `None` when the chunking
    * cannot tell (a quote cut by a chunk boundary).
    */
  def chunkOf(text: com.histmersion.domain.catalogue.evidence.TextAnchor.ChunkOfText, anchor: Anchor, chunkChars: com.histmersion.domain.catalogue.evidence.TextAnchor.ChunkOfChunkChars): Option[com.histmersion.domain.catalogue.evidence.TextAnchor.ChunkOfResult] =
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
