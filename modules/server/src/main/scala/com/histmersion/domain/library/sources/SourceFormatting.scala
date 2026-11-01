package com.histmersion.domain.library.sources

import com.histmersion.api.{FormatField, FormatIssue}

/** How a source's agent-written Markdown (its summary and notes) will display: what will not display as meant, its
  * wording-safe repair, and the refusal a strict write gets for a formatting error. Research implements it with its
  * prose lint, whose links resolve against the corpus.
  */
trait SourceFormatting:
  def issues(summary: Option[String], notes: Option[String]): List[FormatIssue]

  /** The text repaired, with what was repaired. */
  def fix(text: String, field: FormatField): (String, List[String])

  /** Why a strict write is refused, when `issues` holds an error. */
  def refusal(issues: List[FormatIssue]): Option[String]
