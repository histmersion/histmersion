package com.histmersion.domain.automation.worklists

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Write
import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Queue.{Kind, QueueAdd, QueueImport, QueueImported, QueueItem, QueuePatch}
import com.histmersion.util.data.maintenance.WriteGate

import java.time.{Clock, LocalDate}
import com.histmersion.api.Api

/** The work lists' operations as REST, MCP and the CLI reach them: the list named by its word, the inputs checked, a
  * write refused during maintenance. A LOG item given no topic joins today's entry (`## 2026-08-25`, the UTC date of
  * `clock`), which is how an appended worklog line behaves; ACQUIRE and TASKS items have no topic until one is given.
  */
final class WorkListControllers(queues: QueueService, maintenance: WriteGate, clock: Clock):

  def kindOf(list: String): Either[Api.Error, Kind] =
    Kind.parse(list).toRight(Api.Error(s"unknown queue list '$list' — one of: ${Kind.all.mkString(", ")}"))

  def add(list: String, in: QueueAdd): Either[Api.Error, QueueItem] =
    maintenance.guard("queue/add"):
      kindOf(list).flatMap { k =>
        if in.text.trim.isEmpty then Left(Api.Error("queue item text is empty"))
        else
          val topic = in.topic.map(_.trim).filter(_.nonEmpty) match
            case some @ Some(_)        => some
            case None if k == Kind.Log => Some(LocalDate.now(clock).toString)
            case None                  => None
          // both are 400s, not 500s: `created_at` reaches Postgres as `?::timestamptz` (a malformed
          // one used to surface as a driver error) and `status` was written unchecked.
          for
            status <- Queue.statusOf(in.status).left.map(Api.Error(_))
            createdAt <- Queue.createdAtOf(in.createdAt).left.map(Api.Error(_))
          yield queues.add(k, in.copy(topic = topic, status = status, createdAt = createdAt))
      }

  def patch(id: Int, p: QueuePatch): Either[Api.Error, QueueItem] =
    maintenance.guard("queue/patch"):
      val checked =
        for
          status <- Queue.statusOf(p.status)
          _ <- Queue.textOf(p.text)
        yield p.modify(_.status).setTo(status)
      checked.left.map(Api.Error(_)).flatMap(c => queues.patch(QueueItemId(id), c).left.map(WorkListControllers.error))

  def delete(id: Int): Either[Api.Error, QueueItem] =
    maintenance.guard("queue/delete")(queues.delete(QueueItemId(id)).left.map(WorkListControllers.error))

  /** The list rendered as its `meta/<FILE>.md`. Notes are included — they ARE the file's prose. */
  def exportText(list: String): Either[Api.Error, String] =
    kindOf(list).map(k => Queue.render(k, queues.list(k, QueueScope.All)))

  def importText(list: String, req: QueueImport): Either[Api.Error, QueueImported] =
    maintenance.guard("queue/import"):
      kindOf(list).map { k =>
        val rows = Queue.parse(k, req.text)
        val counts = queues.replace(k, rows, ImportMode.of(req.replace))
        val notes = rows.count(_.status.contains(Queue.Note))
        QueueImported(k.label, counts.written - notes, notes, counts.removed)
      }

  /** Every list at once — what `histmersion export <dir>` writes alongside the source notes. */
  def files(): List[(String, String)] =
    Kind.values.toList.map(k => k.file -> Queue.render(k, queues.list(k, QueueScope.All)))

object WorkListControllers:
  def error(e: QueueError): Api.Error = e match
    case QueueError.NotFound(_) => Api.Error(e.message, Some(Write.CodeNotFound))
