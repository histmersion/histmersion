package com.histmersion.server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.checks.FormatIssue
import com.histmersion.api.library.Finding
import com.histmersion.api.library.Source

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
  object Venue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodStart = PeriodStart.T
  object PeriodStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodEnd = PeriodEnd.T
  object PeriodEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Topics = Topics.T
  object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Regions = Regions.T
  object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Summary = Summary.T
  object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Notes = Notes.T
  object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Query = Query.T
  object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
