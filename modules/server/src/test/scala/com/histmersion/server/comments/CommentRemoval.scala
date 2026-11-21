package com.histmersion
package server
package comments

final case class CommentRemoval(text: String, chosen: Vector[CommentBlock]):
  import CommentRemoval.*

  private def lineStartOf(offset: Int): Int = text.lastIndexOf('\n', offset - 1) + 1

  private def lineEndOf(offset: Int): Int =
    val n = text.indexOf('\n', offset)
    if n < 0 then text.length else n

  private def blank(from: Int, until: Int): Boolean = text.substring(from, until).forall(_.isWhitespace)

  private def span(comment: SourceComment): Span =
    val lineStart = lineStartOf(comment.start)
    val lineEnd = lineEndOf(comment.end)
    if blank(lineStart, comment.start) && blank(comment.end, lineEnd) then
      Span(lineStart, math.min(lineEnd + 1, text.length), wholeLines = true)
    else if blank(comment.end, lineEnd) then
      var s = comment.start
      while s > lineStart && (text.charAt(s - 1) == ' ' || text.charAt(s - 1) == '\t') do s -= 1
      Span(s, comment.end, wholeLines = false)
    else
      var e = comment.end
      while e < lineEnd && (text.charAt(e) == ' ' || text.charAt(e) == '\t') do e += 1
      Span(comment.start, e, wholeLines = false)

  private lazy val spans: Vector[Span] =
    chosen
      .flatMap(_.parts)
      .map(span)
      .sortBy(_.start)
      .foldLeft(Vector.empty[Span]) { (merged, next) =>
        merged.lastOption match
          case Some(last) if next.start <= last.end =>
            merged.init :+ Span(last.start, math.max(last.end, next.end), last.wholeLines)
          case _ => merged :+ next
      }

  private lazy val removed: (String, Int) =
    val out = new StringBuilder
    var joins = Vector.empty[Int]
    var copied = 0
    spans.foreach { s =>
      out.append(text.substring(copied, s.start))
      if s.wholeLines then joins = joins :+ out.length
      copied = s.end
    }
    out.append(text.substring(copied))
    var separators = 0
    joins.reverseIterator.foreach { join =>
      val before = if join == 0 then "" else out.substring(out.lastIndexOf("\n", join - 2) + 1, join)
      val nextEnd = out.indexOf("\n", join)
      val next = if nextEnd < 0 then out.substring(join) else out.substring(join, nextEnd)
      if before.isBlank && next.isBlank && nextEnd >= 0 then
        out.delete(join, nextEnd + 1)
        separators += 1
      else if before.isBlank && join > 0 && closers.exists(next.trim.startsWith) then
        val beforeStart = out.lastIndexOf("\n", join - 2) + 1
        out.delete(beforeStart, join)
        separators += 1
    }
    (out.toString, separators)

  def result: String = removed._1

  def separatorLinesShed: Int = removed._2

  def linesShed: Int = lineCount(text) - lineCount(result)

object CommentRemoval:
  val closers: List[String] = List("}", ")", "]")

  final case class Span(start: Int, end: Int, wholeLines: Boolean)

  def lineCount(text: String): Int = if text.isEmpty then 0 else text.linesIterator.size
