package com.histmersion.domain.research.articles.lint

import com.histmersion.api.checks.{FormatField, FormatIssue}
import com.histmersion.domain.library.sources.SourceFormatting
import com.histmersion.server.{LinkTarget, MarkdownLint}

/** A source's summary and notes checked with the prose lint the articles are checked with, their `[[links]]` resolved
  * by `links` (the corpus's sources and articles).
  */
final class SourceTextFormatting(links: List[String] => String => Option[LinkTarget]) extends SourceFormatting:
  def issues(summary: Option[com.histmersion.domain.research.articles.lint.SourceTextFormatting.IssuesSummary], notes: Option[com.histmersion.domain.research.articles.lint.SourceTextFormatting.IssuesNotes]): List[FormatIssue] =
    MarkdownLint.ofSource(summary, notes, links(MarkdownLint.lookups(summary.toList ++ notes.toList)))

  def fix(text: com.histmersion.domain.research.articles.lint.SourceTextFormatting.FixText, field: FormatField): (com.histmersion.domain.research.articles.lint.SourceTextFormatting.FixResultPart1, List[com.histmersion.domain.research.articles.lint.SourceTextFormatting.FixResultPart2]) = MarkdownLint.fix(text, field)

  def refusal(issues: List[FormatIssue]): Option[com.histmersion.domain.research.articles.lint.SourceTextFormatting.RefusalResult] = MarkdownLint.refusal(issues)

object SourceTextFormatting:
  type IssuesSummary = IssuesSummary.T
  object IssuesSummary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IssuesNotes = IssuesNotes.T
  object IssuesNotes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixResultPart1 = FixResultPart1.T
  object FixResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixResultPart2 = FixResultPart2.T
  object FixResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixText = FixText.T
  object FixText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefusalResult = RefusalResult.T
  object RefusalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
