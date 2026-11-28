package com.histmersion
package domain.automation.worklists

import com.histmersion.api.automation.queue.Kind
import com.histmersion.api.automation.queue.QueueAdd
import com.histmersion.api.automation.queue.QueueItem

import com.histmersion.api.automation.Queue
import com.histmersion.domain.{CreatedAt, Creator}
import com.histmersion.domain.library.sources.referrers.*

import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The work lists' items that mention a source (reported, never changed), and the log's entry of its deletion. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "counts and removes the work list rows that point at a deleted source")
final class WorkListSourceReferrer extends SourceReferrer:

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  override def report(source: SourceKeys)(using DbCon): DeleteReport =
    val needles = source.keys.map(_.value.toLowerCase)
    DeleteReport(mentions =
      sql"""SELECT id, text, coalesce(reason, '') FROM queue_items
            WHERE status <> 'note'
              AND EXISTS (SELECT 1 FROM unnest($needles::text[]) AS n(needle)
                          WHERE strpos(lower(text || ' ' || coalesce(reason, '')), n.needle) > 0)
            ORDER BY id"""
        .query[(Int, String, String)]
        .run()
        .toList
        .collect {
          case (id, text, reason) if source.linkedIn(SourceKeys.Text(text + "\n" + reason)) => DeleteReport.Mentions(id)
        }
    )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  override def recorded(source: SourceKeys, line: SourceReferrers.Line, day: SourceReferrers.Day)(using
      DbCon,
      CreatedAt
  ): Option[SourceReferrers.Entry] =
    Some(
      SourceReferrers.Entry(
        QueueRepo
          .insert(
            Kind.Log,
            QueueAdd(QueueItem.Text(line.value), topic = Some(QueueItem.Topic(day.value))),
            Creator.New.Manual(Creator.Surface.Internal),
            summon[CreatedAt]
          )
          .id
          .value
      )
    )
