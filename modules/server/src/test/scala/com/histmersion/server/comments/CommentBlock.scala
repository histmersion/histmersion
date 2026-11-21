package com.histmersion
package server
package comments

final case class CommentBlock(language: SourceLanguage, parts: Vector[SourceComment]) derives CanEqual:
  def start: Int = parts.head.start
  def end: Int = parts.last.end
  def firstLine: Int = parts.head.firstLine
  def lastLine: Int = parts.last.lastLine
  def lines: Int = lastLine - firstLine + 1
  def text: String = parts.map(_.text).mkString("\n")
  def directive: Boolean = language.directive(parts.head.text)

  def kind: String =
    val head = parts.head.text
    if directive then "directive"
    else if head.startsWith("/**") || head.startsWith("\"") || head.startsWith("'") || head.startsWith("r\"") then "doc"
    else if head.startsWith("/*") || head.startsWith("<!--") then "block"
    else "line"

  def lineRange: String = if firstLine == lastLine then s"$firstLine" else s"$firstLine-$lastLine"

  def summary: String =
    text.linesIterator.map(_.trim).mkString(" ").replace('\t', ' ').take(CommentBlock.SummaryWidth)

object CommentBlock:
  val SummaryWidth: Int = 160

  def runs(language: SourceLanguage, text: String, comments: Vector[SourceComment]): Vector[CommentBlock] =
    val out = Vector.newBuilder[CommentBlock]
    var group = Vector.empty[SourceComment]
    def scriptHeader: Boolean = group.headOption.exists(_.text.trim == "# /// script")
    def headerClosed: Boolean = group.length > 1 && group.last.text.trim == "# ///"
    comments.foreach { comment =>
      val joins = group.lastOption.exists { previous =>
        val between = text.substring(previous.end, comment.start)
        val adjacent = between.forall(_.isWhitespace) && between.count(_ == '\n') <= 1
        if scriptHeader then !headerClosed && adjacent
        else
          adjacent && language.lineComment(previous.text) && language.lineComment(comment.text) &&
          !language.directive(previous.text) && !language.directive(comment.text)
      }
      if !joins && group.nonEmpty then
        out += CommentBlock(language, group)
        group = Vector.empty
      group = group :+ comment
    }
    if group.nonEmpty then out += CommentBlock(language, group)
    out.result()
