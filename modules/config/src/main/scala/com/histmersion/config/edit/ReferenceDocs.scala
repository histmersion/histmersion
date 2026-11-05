package com.histmersion.config.edit

import scala.annotation.tailrec

/** What reference.conf says about a setting, read from its text: the comment lines just above the key, the comment at
  * the end of its line and of its environment line (`key = ${?VAR}` right under it), and the comment lines under those
  * that continue them (indented to the comments' column), and the variable it names. Pure.
  */
final class ReferenceDocs(lines: IndexedSeq[ReferenceDocs.Lines]):
  import ReferenceDocs.*

  /** The documentation of the key `leaf` defined at line `line` (1-based). */
  def doc(line: com.histmersion.config.edit.ReferenceDocs.DocLine, leaf: com.histmersion.config.edit.ReferenceDocs.DocLeaf): com.histmersion.config.edit.ReferenceDocs.DocResult =
    val i = line - 1
    if i < 0 || i >= lines.size then ""
    else
      val indent = indentOf(lines(i))
      val trailing = (i :: envLines(i, leaf)).flatMap(j => trailingComment(lines(j)).toList ++ continuations(j, indent))
      clean(leading(i, indent) ++ trailing)

  /** The environment variable the key `leaf` at `line` names on the lines right under it. */
  def envVar(line: com.histmersion.config.edit.ReferenceDocs.EnvVarLine, leaf: com.histmersion.config.edit.ReferenceDocs.EnvVarLeaf): Option[com.histmersion.config.edit.ReferenceDocs.EnvVarResult] =
    envLines(line - 1, leaf).flatMap(j => EnvLine.findFirstMatchIn(content(lines(j))).map(_.group(2))).headOption

  /** The `leaf = ${?VAR}` lines directly under line `i`. */
  private def envLines(i: com.histmersion.config.edit.ReferenceDocs.EnvLinesI, leaf: com.histmersion.config.edit.ReferenceDocs.EnvLinesLeaf): List[com.histmersion.config.edit.ReferenceDocs.EnvLinesResult] =
    @tailrec def loop(j: Int, acc: List[Int]): List[Int] =
      if j >= lines.size then acc.reverse
      else
        EnvLine.findFirstMatchIn(content(lines(j))) match
          case Some(m) if m.group(1) == leaf => loop(j + 1, j :: acc)
          case _ if isCommentOnly(lines(j)) && commentColumn(lines(j)) > indentOf(lines(i)) + ContinuationIndent =>
            loop(j + 1, acc)
          case _ => acc.reverse
    loop(i + 1, Nil)

  /** The comment-only lines right above line `i` that are not a continuation of the line above them. */
  private def leading(i: com.histmersion.config.edit.ReferenceDocs.LeadingI, indent: com.histmersion.config.edit.ReferenceDocs.LeadingIndent): List[com.histmersion.config.edit.ReferenceDocs.LeadingResult] =
    @tailrec def loop(j: Int, acc: List[String]): List[String] =
      if j < 0 || !isCommentOnly(lines(j)) || commentColumn(lines(j)) > indent + ContinuationIndent then acc
      else loop(j - 1, commentText(lines(j)) :: acc)
    loop(i - 1, Nil)

  /** The comment-only lines under line `i`, indented past the key: the end-of-line comment goes on there. */
  private def continuations(i: com.histmersion.config.edit.ReferenceDocs.ContinuationsI, indent: com.histmersion.config.edit.ReferenceDocs.ContinuationsIndent): List[com.histmersion.config.edit.ReferenceDocs.ContinuationsResult] =
    @tailrec def loop(j: Int, acc: List[String]): List[String] =
      if j >= lines.size || !isCommentOnly(lines(j)) || commentColumn(lines(j)) <= indent + ContinuationIndent then
        acc.reverse
      else loop(j + 1, commentText(lines(j)) :: acc)
    loop(i + 1, Nil)

object ReferenceDocs:
  type Lines = Lines.T
  object Lines extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type DocResult = DocResult.T
  object DocResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DocLine = DocLine.T
  object DocLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DocLeaf = DocLeaf.T
  object DocLeaf extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvVarResult = EnvVarResult.T
  object EnvVarResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvVarLine = EnvVarLine.T
  object EnvVarLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvVarLeaf = EnvVarLeaf.T
  object EnvVarLeaf extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvLinesResult = EnvLinesResult.T
  object EnvLinesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvLinesI = EnvLinesI.T
  object EnvLinesI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvLinesLeaf = EnvLinesLeaf.T
  object EnvLinesLeaf extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeadingResult = LeadingResult.T
  object LeadingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeadingI = LeadingI.T
  object LeadingI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeadingIndent = LeadingIndent.T
  object LeadingIndent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContinuationsResult = ContinuationsResult.T
  object ContinuationsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContinuationsI = ContinuationsI.T
  object ContinuationsI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContinuationsIndent = ContinuationsIndent.T
  object ContinuationsIndent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyText = ApplyText.T
  object ApplyText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IndentOfResult = IndentOfResult.T
  object IndentOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IndentOfL = IndentOfL.T
  object IndentOfL extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommentStartResult = CommentStartResult.T
  object CommentStartResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommentStartL = CommentStartL.T
  object CommentStartL extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContentResult = ContentResult.T
  object ContentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContentL = ContentL.T
  object ContentL extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsCommentOnlyResult = IsCommentOnlyResult.T
  object IsCommentOnlyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsCommentOnlyL = IsCommentOnlyL.T
  object IsCommentOnlyL extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommentColumnResult = CommentColumnResult.T
  object CommentColumnResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommentColumnL = CommentColumnL.T
  object CommentColumnL extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommentTextResult = CommentTextResult.T
  object CommentTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommentTextL = CommentTextL.T
  object CommentTextL extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TrailingCommentResult = TrailingCommentResult.T
  object TrailingCommentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TrailingCommentL = TrailingCommentL.T
  object TrailingCommentL extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CleanResult = CleanResult.T
  object CleanResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CleanParts = CleanParts.T
  object CleanParts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(text: com.histmersion.config.edit.ReferenceDocs.ApplyText): ReferenceDocs = new ReferenceDocs(text.split("\n", -1).toIndexedSeq)

  /** A comment-only line indented this much past its key's column continues the comment above it. */
  private val ContinuationIndent = 8

  private val EnvLine = """^\s*([A-Za-z0-9_-]+)\s*[=:]\s*\$\{\?([A-Za-z_][A-Za-z0-9_]*)\}\s*$""".r.unanchored

  private def indentOf(l: com.histmersion.config.edit.ReferenceDocs.IndentOfL): com.histmersion.config.edit.ReferenceDocs.IndentOfResult = l.takeWhile(_ == ' ').length

  /** Where the comment starts in `l` — `#` or `//` outside a quoted string — if it has one. */
  private def commentStart(l: com.histmersion.config.edit.ReferenceDocs.CommentStartL): Option[com.histmersion.config.edit.ReferenceDocs.CommentStartResult] =
    @tailrec def loop(i: Int, quoted: Boolean): Option[Int] =
      if i >= l.length then None
      else
        l.charAt(i) match
          case '"' if i == 0 || l.charAt(i - 1) != '\\'                     => loop(i + 1, !quoted)
          case '#' if !quoted                                               => Some(i)
          case '/' if !quoted && i + 1 < l.length && l.charAt(i + 1) == '/' => Some(i)
          case _                                                            => loop(i + 1, quoted)
    loop(0, quoted = false)

  private def content(l: com.histmersion.config.edit.ReferenceDocs.ContentL): com.histmersion.config.edit.ReferenceDocs.ContentResult = commentStart(l).fold(l)(l.take(_))

  private def isCommentOnly(l: com.histmersion.config.edit.ReferenceDocs.IsCommentOnlyL): com.histmersion.config.edit.ReferenceDocs.IsCommentOnlyResult = commentStart(l).exists(i => l.take(i).trim.isEmpty)

  private def commentColumn(l: com.histmersion.config.edit.ReferenceDocs.CommentColumnL): com.histmersion.config.edit.ReferenceDocs.CommentColumnResult = commentStart(l).getOrElse(0)

  private def commentText(l: com.histmersion.config.edit.ReferenceDocs.CommentTextL): com.histmersion.config.edit.ReferenceDocs.CommentTextResult =
    commentStart(l).fold("")(i => l.drop(i).dropWhile(c => c == '#' || c == '/').trim)

  private def trailingComment(l: com.histmersion.config.edit.ReferenceDocs.TrailingCommentL): Option[com.histmersion.config.edit.ReferenceDocs.TrailingCommentResult] =
    commentStart(l).filter(i => l.take(i).trim.nonEmpty).map(_ => commentText(l)).filter(_.nonEmpty)

  /** The comment lines as one text: a divider's rule (`── … ──`) dropped, lines joined by spaces. */
  private def clean(parts: List[com.histmersion.config.edit.ReferenceDocs.CleanParts]): com.histmersion.config.edit.ReferenceDocs.CleanResult =
    parts.map(_.replaceAll("─+", "").trim).filter(_.nonEmpty).mkString(" ").replaceAll("\\s+", " ").trim
