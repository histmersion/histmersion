package com.histmersion
package domain.automation.worklists

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.exchange.BundleScope
import com.histmersion.util.data.exchange.BundleSql.*

import com.augustnagro.magnum.*
import io.scalaland.chimney.dsl.*
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.automation.Queue
import com.histmersion.util.database.{StoredCreator, Timestamps}

/** What a bundle carries of the work lists: their items, read as the bundle's lines by uid, and the import's step that
  * writes them.
  */
private[worklists] object QueueBundleRepo:

  def queue(scope: BundleScope)(using DbCon): Vector[Bundle.QueueItem] =
    sql"""SELECT q.uid, q.origin, q.list, q.text, q.topic, q.status, q.reason,
                       ${Timestamps.iso(sql"q.created_at")}, ${Timestamps.iso(sql"q.done_at")}, ${Timestamps.iso(
        sql"q.updated_at"
      )}, q.creator::text
                FROM queue_items q WHERE ${where(scope, sql"q.uid", sql"q.updated_at")} ORDER BY q.uid"""
      .query[BQueueRow]
      .run()
      .map { r =>
        Bundle.QueueItem(
          r.uid,
          r.origin,
          Bundle.QueueItem.List(r.list.value),
          Bundle.QueueItem.Text(r.text.value),
          r.topic.map(t => Bundle.QueueItem.Topic(t.value)),
          Bundle.QueueItem.Status(r.status.value),
          r.reason.transformInto[Option[Queue.Rationale]],
          Bundle.QueueItem.CreatedAt(r.createdAt.value),
          r.doneAt.map(d => Bundle.QueueItem.DoneAt(d.value)),
          Bundle.QueueItem.UpdatedAt(r.updatedAt.value),
          creator = StoredCreator.document(r.creator)
        )
      }

  def queueItemsIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.es.queue.filter(q => w.writes(q.uid)).foreach { q =>
      sql"""INSERT INTO queue_items (uid, origin, list, text, topic, status, reason, created_at, done_at, updated_at,
                                     creator, actor)
            VALUES (${q.uid}, ${q.origin}, ${q.list}, ${q.text}, ${q.topic}, ${q.status}, ${q.reason},
              ${q.createdAt}::timestamptz, ${q.doneAt}::timestamptz, ${q.updatedAt}::timestamptz,
              ${StoredCreator.carried(
          q.creator
        )}::jsonb, CASE WHEN ${q.topic} = 'agent review' THEN 'agents' ELSE 'person' END)
            ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, list = EXCLUDED.list,
              text = EXCLUDED.text, topic = EXCLUDED.topic, status = EXCLUDED.status,
              reason = EXCLUDED.reason, created_at = EXCLUDED.created_at, done_at = EXCLUDED.done_at,
              updated_at = EXCLUDED.updated_at""".update.run()
    }
