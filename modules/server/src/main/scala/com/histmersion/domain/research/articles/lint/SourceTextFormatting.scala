package com.histmersion
package domain.research.articles.lint

import com.histmersion.api.checks.{FormatField, FormatIssue}
import com.histmersion.domain.library.sources.SourceFormatting
import com.histmersion.server.{LinkTarget, MarkdownLint}
import com.histmersion.api.text.WikiLinks
import com.histmersion.domain.library.Source
import com.histmersion.domain.library.sources.SourceNote

/** A source's summary and notes checked with the prose lint the articles are checked with, their `[[links]]` resolved
  * by `links` (the corpus's sources and articles).
  */
final class SourceTextFormatting(
    links: List[WikiLinks.Occurrence.Target] => WikiLinks.Occurrence.Target => Option[LinkTarget]
) extends SourceFormatting:
  def issues(summary: Option[Source.Summary], notes: Option[SourceNote.Text]): List[FormatIssue] =
    MarkdownLint.ofSource(
      summary,
      notes,
      links(
        MarkdownLint.lookups((summary.map(_.value).toList ++ notes.map(_.value).toList).map(MarkdownLint.Scan.Text(_)))
      )
    )

  def fix(text: Source.Text, field: FormatField): (Source.Text, List[Source.Text]) =
    val (fixed, done) = MarkdownLint.fix(MarkdownLint.Scan.Text(text.value), field)
    (Source.Text(fixed.value), done.map(d => Source.Text(d.value)))

  def refusal(issues: List[FormatIssue]): Option[SourceFormatting.Problem] =
    MarkdownLint.refusal(issues).map(p => SourceFormatting.Problem(p.value))
