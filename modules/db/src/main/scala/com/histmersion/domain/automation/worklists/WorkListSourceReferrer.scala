package com.histmersion
package domain.automation.worklists

import com.histmersion.api.automation.Queue
import com.histmersion.domain.Creator
import com.histmersion.domain.library.sources.referrers.*

import com.histmersion.util.database.given

import com.augustnagro.magnum.*

/** The work lists' items that mention a source (reported, never changed), and the log's entry of its deletion. */
final class WorkListSourceReferrer extends SourceReferrer:

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

  override def recorded(source: SourceKeys, line: SourceReferrers.Line, day: SourceReferrers.Day)(using
      DbCon
  ): Option[SourceReferrers.Entry] =
    Some(
      SourceReferrers.Entry(
        QueueRepo
          .insert(
            Queue.Kind.Log,
            Queue.QueueAdd(Queue.QueueItem.Text(line.value), topic = Some(Queue.QueueItem.Topic(day.value))),
            Creator.New.Manual(Creator.Surface.Internal)
          )
          .id
          .value
      )
    )
