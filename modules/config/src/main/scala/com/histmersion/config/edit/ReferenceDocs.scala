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
    @tailrec def loop(j: com.histmersion.config.edit.ReferenceDocs.EnvLinesLoopJ, acc: List[com.histmersion.config.edit.ReferenceDocs.EnvLinesLoopAcc]): List[com.histmersion.config.edit.ReferenceDocs.EnvLinesLoopResult] =
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
    @tailrec def loop(j: com.histmersion.config.edit.ReferenceDocs.LeadingLoopJ, acc: List[com.histmersion.config.edit.ReferenceDocs.LeadingLoopAcc]): List[com.histmersion.config.edit.ReferenceDocs.LeadingLoopResult] =
      if j < 0 || !isCommentOnly(lines(j)) || commentColumn(lines(j)) > indent + ContinuationIndent then acc
      else loop(j - 1, commentText(lines(j)) :: acc)
    loop(i - 1, Nil)

  /** The comment-only lines under line `i`, indented past the key: the end-of-line comment goes on there. */
  private def continuations(i: com.histmersion.config.edit.ReferenceDocs.ContinuationsI, indent: com.histmersion.config.edit.ReferenceDocs.ContinuationsIndent): List[com.histmersion.config.edit.ReferenceDocs.ContinuationsResult] =
    @tailrec def loop(j: com.histmersion.config.edit.ReferenceDocs.ContinuationsLoopJ, acc: List[com.histmersion.config.edit.ReferenceDocs.ContinuationsLoopAcc]): List[com.histmersion.config.edit.ReferenceDocs.ContinuationsLoopResult] =
      if j >= lines.size || !isCommentOnly(lines(j)) || commentColumn(lines(j)) <= indent + ContinuationIndent then
        acc.reverse
      else loop(j + 1, commentText(lines(j)) :: acc)
    loop(i + 1, Nil)

object ReferenceDocs:
  type EnvLinesLoopResult = EnvLinesLoopResult.T
  object EnvLinesLoopResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvLinesLoopJ = EnvLinesLoopJ.T
  object EnvLinesLoopJ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvLinesLoopAcc = EnvLinesLoopAcc.T
  object EnvLinesLoopAcc extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeadingLoopResult = LeadingLoopResult.T
  object LeadingLoopResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeadingLoopJ = LeadingLoopJ.T
  object LeadingLoopJ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeadingLoopAcc = LeadingLoopAcc.T
  object LeadingLoopAcc extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContinuationsLoopResult = ContinuationsLoopResult.T
  object ContinuationsLoopResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContinuationsLoopJ = ContinuationsLoopJ.T
  object ContinuationsLoopJ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContinuationsLoopAcc = ContinuationsLoopAcc.T
  object ContinuationsLoopAcc extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommentStartLoopResult = CommentStartLoopResult.T
  object CommentStartLoopResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommentStartLoopI = CommentStartLoopI.T
  object CommentStartLoopI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommentStartLoopQuoted = CommentStartLoopQuoted.T
  object CommentStartLoopQuoted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
  type ContentL = _root_.com.histmersion.config.edit.ReferenceDocs.CommentStartL
  val ContentL: _root_.com.histmersion.config.edit.ReferenceDocs.CommentStartL.type = _root_.com.histmersion.config.edit.ReferenceDocs.CommentStartL
  type IsCommentOnlyResult = IsCommentOnlyResult.T
  object IsCommentOnlyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsCommentOnlyL = _root_.com.histmersion.config.edit.ReferenceDocs.CommentStartL
  val IsCommentOnlyL: _root_.com.histmersion.config.edit.ReferenceDocs.CommentStartL.type = _root_.com.histmersion.config.edit.ReferenceDocs.CommentStartL
  type CommentColumnResult = CommentColumnResult.T
  object CommentColumnResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommentColumnL = _root_.com.histmersion.config.edit.ReferenceDocs.CommentStartL
  val CommentColumnL: _root_.com.histmersion.config.edit.ReferenceDocs.CommentStartL.type = _root_.com.histmersion.config.edit.ReferenceDocs.CommentStartL
  type CommentTextResult = CommentTextResult.T
  object CommentTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommentTextL = _root_.com.histmersion.config.edit.ReferenceDocs.CommentStartL
  val CommentTextL: _root_.com.histmersion.config.edit.ReferenceDocs.CommentStartL.type = _root_.com.histmersion.config.edit.ReferenceDocs.CommentStartL
  type TrailingCommentResult = TrailingCommentResult.T
  object TrailingCommentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TrailingCommentL = _root_.com.histmersion.config.edit.ReferenceDocs.CommentStartL
  val TrailingCommentL: _root_.com.histmersion.config.edit.ReferenceDocs.CommentStartL.type = _root_.com.histmersion.config.edit.ReferenceDocs.CommentStartL
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
    @tailrec def loop(i: com.histmersion.config.edit.ReferenceDocs.CommentStartLoopI, quoted: com.histmersion.config.edit.ReferenceDocs.CommentStartLoopQuoted): Option[com.histmersion.config.edit.ReferenceDocs.CommentStartLoopResult] =
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
