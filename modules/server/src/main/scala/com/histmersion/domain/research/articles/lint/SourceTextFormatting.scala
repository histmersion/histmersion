package com.histmersion.domain.research.articles.lint

import com.histmersion.api.{FormatField, FormatIssue}
import com.histmersion.domain.library.sources.SourceFormatting
import com.histmersion.server.{LinkTarget, MarkdownLint}

/** A source's summary and notes checked with the prose lint the articles are checked with, their `[[links]]` resolved
  * by `links` (the corpus's sources and articles).
  */
final class SourceTextFormatting(links: List[String] => String => Option[LinkTarget]) extends SourceFormatting:
  def issues(summary: Option[String], notes: Option[String]): List[FormatIssue] =
    MarkdownLint.ofSource(summary, notes, links(MarkdownLint.lookups(summary.toList ++ notes.toList)))

  def fix(text: String, field: FormatField): (String, List[String]) = MarkdownLint.fix(text, field)

  def refusal(issues: List[FormatIssue]): Option[String] = MarkdownLint.refusal(issues)
