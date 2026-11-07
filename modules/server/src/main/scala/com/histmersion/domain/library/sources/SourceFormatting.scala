package com.histmersion.domain.library.sources

import com.histmersion.api.checks.{FormatField, FormatIssue}

/** How a source's agent-written Markdown (its summary and notes) will display: what will not display as meant, its
  * wording-safe repair, and the refusal a strict write gets for a formatting error. Research implements it with its
  * prose lint, whose links resolve against the corpus.
  */
trait SourceFormatting:
  def issues(summary: Option[com.histmersion.domain.library.sources.FullSourceRow.Summary], notes: Option[com.histmersion.domain.library.sources.SourceNote.Text]): List[FormatIssue]

  /** The text repaired, with what was repaired. */
  def fix(text: com.histmersion.domain.library.sources.SourceFormatting.Text, field: FormatField): (com.histmersion.domain.library.sources.SourceFormatting.FixResultPart1, List[com.histmersion.domain.library.sources.SourceFormatting.FixResultPart2])

  /** Why a strict write is refused, when `issues` holds an error. */
  def refusal(issues: List[FormatIssue]): Option[com.histmersion.domain.library.sources.SourceFormatting.Problem]

object SourceFormatting:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixResultPart1 = FixResultPart1.T
  object FixResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixResultPart2 = FixResultPart2.T
  object FixResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
