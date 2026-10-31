package com.histmersion.domain.automation.worklists

import com.histmersion.api.{ApiError, Queue, Write}
import com.histmersion.api.Queue.{Kind, QueueAdd, QueueImport, QueueImported, QueueItem, QueuePatch}
import com.histmersion.util.data.maintenance.WriteGate

import java.time.{Clock, LocalDate}

/** The work lists' operations as REST, MCP and the CLI reach them: the list named by its word, the inputs checked, a
  * write refused during maintenance. A LOG item given no topic joins today's entry (`## 2026-08-25`, the UTC date of
  * `clock`), which is how an appended worklog line behaves; ACQUIRE and TASKS items have no topic until one is given.
  */
final class WorkListControllers(queues: QueueService, maintenance: WriteGate, clock: Clock):

  def kindOf(list: String): Either[ApiError, Kind] =
    Kind.parse(list).toRight(ApiError(s"unknown queue list '$list' — one of: ${Kind.all.mkString(", ")}"))

  def add(list: String, in: QueueAdd): Either[ApiError, QueueItem] =
    maintenance.guard("queue/add"):
      kindOf(list).flatMap { k =>
        if in.text.trim.isEmpty then Left(ApiError("queue item text is empty"))
        else
          val topic = in.topic.map(_.trim).filter(_.nonEmpty) match
            case some @ Some(_)        => some
            case None if k == Kind.Log => Some(LocalDate.now(clock).toString)
            case None                  => None
          // both are 400s, not 500s: `created_at` reaches Postgres as `?::timestamptz` (a malformed
          // one used to surface as a driver error) and `status` was written unchecked.
          for
            status <- Queue.statusOf(in.status).left.map(ApiError(_))
            createdAt <- Queue.createdAtOf(in.createdAt).left.map(ApiError(_))
          yield queues.add(k, in.copy(topic = topic, status = status, createdAt = createdAt))
      }

  def patch(id: Int, p: QueuePatch): Either[ApiError, QueueItem] =
    maintenance.guard("queue/patch"):
      val checked =
        for
          status <- Queue.statusOf(p.status)
          _ <- Queue.textOf(p.text)
        yield p.copy(status = status)
      checked.left.map(ApiError(_)).flatMap(c => queues.patch(QueueItemId(id), c).left.map(WorkListControllers.error))

  def delete(id: Int): Either[ApiError, QueueItem] =
    maintenance.guard("queue/delete")(queues.delete(QueueItemId(id)).left.map(WorkListControllers.error))

  /** The list rendered as its `meta/<FILE>.md`. Notes are included — they ARE the file's prose. */
  def exportText(list: String): Either[ApiError, String] =
    kindOf(list).map(k => Queue.render(k, queues.list(k, QueueScope.All)))

  def importText(list: String, req: QueueImport): Either[ApiError, QueueImported] =
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
  def error(e: QueueError): ApiError = e match
    case QueueError.NotFound(_) => ApiError(e.message, Some(Write.CodeNotFound))
