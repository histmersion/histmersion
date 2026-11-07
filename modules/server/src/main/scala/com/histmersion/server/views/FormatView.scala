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
      if in.fix.value then
        MarkdownLint.fix(
          MarkdownLint.Scan.Text(in.text.value),
          in.field,
          MarkdownLint.Context(article = MarkdownLint.Context.Article(in.field == FormatField.Body))
        )
      else (MarkdownLint.Scan.Text(in.text.value), Nil)
    val issues = in.field match
      case FormatField.Body    => articles.formatting(com.histmersion.api.research.Article.Body(text.value), None)
      case FormatField.Summary =>
        MarkdownLint.ofSource(
          Some(com.histmersion.domain.library.Source.Summary(text.value)),
          None,
          articles.pageLinks(MarkdownLint.lookups(List(text)))
        )
      case FormatField.Notes => sources.formatIssues(None, Some(com.histmersion.api.library.Source.Notes(text.value)))
    FormatReport(
      com.histmersion.api.checks.FormatReport.Subject("text"),
      issues,
      done.map(r => FormatReport.Fixed(r.value)),
      Option.when(in.fix.value)(FormatReport.Text(text.value))
    )
