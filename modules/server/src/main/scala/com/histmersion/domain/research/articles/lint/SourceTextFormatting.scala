package com.histmersion.domain.research.articles.lint

import com.histmersion.api.checks.{FormatField, FormatIssue}
import com.histmersion.domain.library.sources.SourceFormatting
import com.histmersion.server.{LinkTarget, MarkdownLint}

/** A source's summary and notes checked with the prose lint the articles are checked with, their `[[links]]` resolved
  * by `links` (the corpus's sources and articles).
  */
final class SourceTextFormatting(links: List[com.histmersion.domain.library.Citekey] => com.histmersion.domain.library.Citekey => Option[LinkTarget]) extends SourceFormatting:
  def issues(summary: Option[com.histmersion.domain.library.Source.Summary], notes: Option[com.histmersion.domain.library.sources.SourceNote.Text]): List[FormatIssue] =
    MarkdownLint.ofSource(
      summary,
      notes,
      links(MarkdownLint.lookups((summary.map(_.value).toList ++ notes.map(_.value).toList).map(MarkdownLint.Scan.Text(_))))
    )

  def fix(text: com.histmersion.domain.library.Source.Text, field: FormatField): (com.histmersion.domain.library.Source.Text, List[com.histmersion.domain.library.Source.Text]) =
    val (fixed, done) = MarkdownLint.fix(MarkdownLint.Scan.Text(text.value), field)
    (com.histmersion.domain.library.Source.Text(fixed.value), done.map(d => com.histmersion.domain.library.Source.Text(d.value)))

  def refusal(issues: List[FormatIssue]): Option[com.histmersion.domain.research.articles.lint.SourceTextFormatting.Problem] =
    MarkdownLint.refusal(issues).map(p => SourceTextFormatting.Problem(p.value))

object SourceTextFormatting:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
