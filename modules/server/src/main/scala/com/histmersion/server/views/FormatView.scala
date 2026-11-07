package com.histmersion.server.views

import com.histmersion.api.checks.{FormatCheck, FormatField, FormatReport}
import com.histmersion.domain.library.sources.SourcesControllers
import com.histmersion.domain.research.articles.ArticlesControllers
import com.histmersion.server.MarkdownLint

/** `POST /api/lint/markdown`: Markdown that is not stored (yet), checked as the field it is about to be — an article's
  * body by research's rules, a summary against research's pages, a source's notes by the library's. With `fix`, the
  * repaired text comes back in `text`.
  */
final class FormatView(articles: => ArticlesControllers, sources: => SourcesControllers):
  def check(in: FormatCheck): FormatReport =
    val (text, done) =
      if in.fix then MarkdownLint.fix(in.text, in.field, MarkdownLint.Context(article = in.field == FormatField.Body))
      else (in.text, Nil)
    val issues = in.field match
      case FormatField.Body    => articles.formatting(text, None)
      case FormatField.Summary =>
        MarkdownLint.ofSource(Some(text), None, articles.pageLinks(MarkdownLint.lookups(List(text))))
      case FormatField.Notes => sources.formatIssues(None, Some(text))
    FormatReport(com.histmersion.api.checks.FormatReport.Subject("text"), issues, done, Option.when(in.fix)(text))
