package com.histmersion
package server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.checks.FormatIssue
import com.histmersion.api.library.Finding
import com.histmersion.api.library.Source
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.sources.SourceNote
import com.histmersion.domain.research.articles.ArticleTools

/** Everything a `/source/{citekey}` page shows, typed. Built by [[PageBuilder]], rendered by [[Pages.source]] —
  * Markdown bodies stay Markdown until rendering, so the page model is testable without flexmark in the way.
  */
final case class SourcePage(
    ref: Source.Ref,
    venue: Option[SourcePage.Venue],
    periodStart: Option[SourcePage.PeriodStart],
    periodEnd: Option[SourcePage.PeriodEnd],
    topics: List[SourcePage.Topics],
    regions: List[SourcePage.Regions],
    tags: List[SourcePage.Tags],
    summary: Option[SourcePage.Summary],
    notes: Option[SourcePage.Notes],
    findings: List[Finding.Row],
    cites: List[Citation],
    citedBy: List[Citation],
    fullText: FullText,
    query: Option[SourcePage.Query] = None,
    /** The summary's and notes' formatting problems — the page says so above them. */
    formatting: List[FormatIssue] = Nil
)

object SourcePage:
  type Venue = Venue.T
  object Venue extends Newtype[String, Pure]
  type PeriodStart = PeriodStart.T
  object PeriodStart extends Newtype[Long, Pure]
  type PeriodEnd = PeriodEnd.T
  object PeriodEnd extends Newtype[Long, Pure]
  type Topics = Topics.T
  object Topics extends Newtype[String, Pure]
  type Regions = Regions.T
  object Regions extends Newtype[String, Pure]
  type Tags = Tags.T
  object Tags extends Newtype[String, Pure]
  export com.histmersion.domain.library.Source.Summary
  export SourceNote.Text as Notes
  export ArticleTools.ListArticlesInput.Query
