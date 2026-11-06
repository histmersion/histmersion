package com.histmersion.config.edit

import scala.annotation.tailrec

/** What reference.conf says about a setting, read from its text: the comment lines just above the key, the comment at
  * the end of its line and of its environment line (`key = ${?VAR}` right under it), and the comment lines under those
  * that continue them (indented to the comments' column), and the variable it names. Pure.
  */
final class ReferenceDocs(lines: IndexedSeq[ReferenceDocs.Line]):
  import ReferenceDocs.*

  /** The documentation of the key `leaf` defined at line `line` (1-based). */
  def doc(line: Int, leaf: String): String =
    val i = line - 1
    if i < 0 || i >= lines.size then ""
    else
      val indent = indentOf(lines(i).value)
      val trailing = (i :: envLines(i, leaf)).flatMap(j => trailingComment(lines(j).value).toList ++ continuations(j, indent))
      clean(leading(i, indent) ++ trailing)

  /** The environment variable the key `leaf` at `line` names on the lines right under it. */
  def envVar(line: Int, leaf: String): Option[com.histmersion.config.edit.SettingField.EnvVar] =
    envLines(line - 1, leaf).flatMap(j => EnvLine.findFirstMatchIn(content(lines(j).value)).map(m => SettingField.EnvVar(m.group(2)))).headOption

  /** The `leaf = ${?VAR}` lines directly under line `i`. */
  private def envLines(i: Int, leaf: String): List[Int] =
    @tailrec def loop(j: Int, acc: List[Int]): List[Int] =
      if j >= lines.size then acc.reverse
      else
        EnvLine.findFirstMatchIn(content(lines(j).value)) match
          case Some(m) if m.group(1) == leaf => loop(j + 1, j :: acc)
          case _ if isCommentOnly(lines(j).value) && commentColumn(lines(j).value) > indentOf(lines(i).value) + ContinuationIndent =>
            loop(j + 1, acc)
          case _ => acc.reverse
    loop(i + 1, Nil)

  /** The comment-only lines right above line `i` that are not a continuation of the line above them. */
  private def leading(i: Int, indent: Int): List[String] =
    @tailrec def loop(j: Int, acc: List[String]): List[String] =
      if j < 0 || !isCommentOnly(lines(j).value) || commentColumn(lines(j).value) > indent + ContinuationIndent then acc
      else loop(j - 1, commentText(lines(j).value) :: acc)
    loop(i - 1, Nil)

  /** The comment-only lines under line `i`, indented past the key: the end-of-line comment goes on there. */
  private def continuations(i: Int, indent: Int): List[String] =
    @tailrec def loop(j: Int, acc: List[String]): List[String] =
      if j >= lines.size || !isCommentOnly(lines(j).value) || commentColumn(lines(j).value) <= indent + ContinuationIndent then
        acc.reverse
      else loop(j + 1, commentText(lines(j).value) :: acc)
    loop(i + 1, Nil)

object ReferenceDocs:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Line = Line.T
  object Line extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(text: com.histmersion.config.edit.ReferenceDocs.Text): ReferenceDocs = new ReferenceDocs(text.value.split("\n", -1).toIndexedSeq.map(Line(_)))

  /** A comment-only line indented this much past its key's column continues the comment above it. */
  private val ContinuationIndent = 8

  private val EnvLine = """^\s*([A-Za-z0-9_-]+)\s*[=:]\s*\$\{\?([A-Za-z_][A-Za-z0-9_]*)\}\s*$""".r.unanchored

  private def indentOf(l: String): Int = l.takeWhile(_ == ' ').length

  /** Where the comment starts in `l` — `#` or `//` outside a quoted string — if it has one. */
  private def commentStart(l: String): Option[Int] =
    @tailrec def loop(i: Int, quoted: Boolean): Option[Int] =
      if i >= l.length then None
      else
        l.charAt(i) match
          case '"' if i == 0 || l.charAt(i - 1) != '\\'                     => loop(i + 1, !quoted)
          case '#' if !quoted                                               => Some(i)
          case '/' if !quoted && i + 1 < l.length && l.charAt(i + 1) == '/' => Some(i)
          case _                                                            => loop(i + 1, quoted)
    loop(0, quoted = false)

  private def content(l: String): String = commentStart(l).fold(l)(l.take(_))

  private def isCommentOnly(l: String): Boolean = commentStart(l).exists(i => l.take(i).trim.isEmpty)

  private def commentColumn(l: String): Int = commentStart(l).getOrElse(0)

  private def commentText(l: String): String =
    commentStart(l).fold("")(i => l.drop(i).dropWhile(c => c == '#' || c == '/').trim)

  private def trailingComment(l: String): Option[String] =
    commentStart(l).filter(i => l.take(i).trim.nonEmpty).map(_ => commentText(l)).filter(_.nonEmpty)

  /** The comment lines as one text: a divider's rule (`── … ──`) dropped, lines joined by spaces. */
  private def clean(parts: List[String]): String =
    parts.map(_.replaceAll("─+", "").trim).filter(_.nonEmpty).mkString(" ").replaceAll("\\s+", " ").trim
