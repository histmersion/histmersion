package com.histmersion.domain.automation.worklists

import com.histmersion.api.Queue
import com.histmersion.domain.library.sources.referrers.*

import com.histmersion.util.database.given

import com.augustnagro.magnum.*

/** The work lists' items that mention a source (reported, never changed), and the log's entry of its deletion. */
final class WorkListSourceReferrer extends SourceReferrer:

  override def report(source: SourceKeys)(using DbCon): DeleteReport =
    val needles = source.keys.map(_.toLowerCase)
    DeleteReport(mentions =
      sql"""SELECT id, text, coalesce(reason, '') FROM queue_items
            WHERE status <> 'note'
              AND EXISTS (SELECT 1 FROM unnest($needles::text[]) AS n(needle)
                          WHERE strpos(lower(text || ' ' || coalesce(reason, '')), n.needle) > 0)
            ORDER BY id"""
        .query[(Int, String, String)]
        .run()
        .toList
        .collect { case (id, text, reason) if source.linkedIn(text + "\n" + reason) => id }
    )

  override def recorded(source: SourceKeys, line: String, day: String)(using DbCon): Option[Int] =
    Some(QueueRepo.insert(Queue.Kind.Log, Queue.QueueAdd(line, topic = Some(day))).id)
