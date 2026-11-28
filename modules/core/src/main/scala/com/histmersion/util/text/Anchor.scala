package com.histmersion
package util.text

import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.util.ocr.Chunk
import com.histmersion.util.ocr.Chunker

import io.github.iltotore.iron.constraint.any.Pure

final case class Anchor(start: Anchor.Start, end: Anchor.End)

object Anchor:
  type Start = Start.T
  object Start extends Newtype[Int, Pure]
  type End = End.T
  object End extends Newtype[Int, Pure]

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
  private def anchorAt(start: Int, end: Int): Anchor = Anchor(Start(start), End(end))

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

  private def occurrences(hay: String, needle: String): List[Int] =
    if needle.isEmpty then Nil
    else Iterator.iterate(hay.indexOf(needle))(i => hay.indexOf(needle, i + 1)).takeWhile(_ >= 0).toList

  private def collapse(s: String): (String, Array[Int]) =
    val sb = StringBuilder()
    val back = Array.newBuilder[Int]
    var inWs = false
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
