package com.histmersion
package domain.library.sources

import com.histmersion.domain.Problem
import com.histmersion.api.checks.{FormatField, FormatIssue}
import com.histmersion.domain.library.Source
import io.github.iltotore.iron.constraint.any.Pure

/** How a source's agent-written Markdown (its summary and notes) will display: what will not display as meant, its
  * wording-safe repair, and the refusal a strict write gets for a formatting error. Research implements it with its
  * prose lint, whose links resolve against the corpus.
  */
trait SourceFormatting:
  def issues(summary: Option[Source.Summary], notes: Option[SourceNote.Text]): List[FormatIssue]

  /** The text repaired, with what was repaired. */
  def fix(text: Source.Text, field: FormatField): (Source.Text, List[Source.Text])

  /** Why a strict write is refused, when `issues` holds an error. */
  def refusal(issues: List[FormatIssue]): Option[Problem]
