package com.histmersion
package domain.automation.worklists

import com.histmersion.util.problem.{Legacy, Problem}
import io.scalaland.chimney.dsl.*
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Write
import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Queue.{Kind, QueueAdd, QueueImport, QueueImported, QueueItem, QueuePatch}
import com.histmersion.util.data.maintenance.WriteGate

import java.time.{Clock, LocalDate}
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure

/** The work lists' operations as REST, MCP and the CLI reach them: the list named by its word, the inputs checked, a
  * write refused during maintenance. A LOG item given no topic joins today's entry (`## 2026-08-25`, the UTC date of
  * `clock`), which is how an appended worklog line behaves; ACQUIRE and TASKS items have no topic until one is given.
  */
final class WorkListControllers(queues: QueueService, maintenance: WriteGate, clock: Clock):

  def kindOf(list: WorkListControllers.KindOfList): Either[Api.Error, Kind] =
    Kind
      .parse(Kind.Label(list.value))
      .toRight(
        WorkListControllers.bad(
          Problem.Unclassified(Legacy.Text(s"unknown queue list '$list' — one of: ${Kind.all.mkString(", ")}"))
        )
      )

  def add(list: WorkListControllers.ListName, in: QueueAdd): Either[Api.Error, QueueItem] =
    maintenance.guard(WriteGate.OperationName("queue/add")):
      kindOf(WorkListControllers.KindOfList(list.value)).flatMap { k =>
        if in.text.value.trim.isEmpty then
          Left(WorkListControllers.bad(Problem.Unclassified(Legacy.Text("queue item text is empty"))))
        else
          val topic = in.topic.map(_.value.trim).filter(_.nonEmpty).map(QueueItem.Topic(_)) match
            case some @ Some(_)        => some
            case None if k == Kind.Log => Some(QueueItem.Topic(LocalDate.now(clock).toString))
            case None                  => None
          // both are 400s, not 500s: `created_at` reaches Postgres as `?::timestamptz` (a malformed
          // one used to surface as a driver error) and `status` was written unchecked.
          for
            status <- Queue.statusOf(in.status).left.map(p => WorkListControllers.bad(p))
            createdAt <- Queue
              .createdAtOf(in.createdAt)
              .left
              .map(p => WorkListControllers.bad(p))
          yield queues.add(
            k,
            in.modify(_.topic).setTo(topic).modify(_.status).setTo(status).modify(_.createdAt).setTo(createdAt)
          )
      }

  def patch(id: QueueItemId, p: QueuePatch): Either[Api.Error, QueueItem] =
    maintenance.guard(WriteGate.OperationName("queue/patch")):
      val checked =
        for
          status <- Queue.statusOf(p.status)
          _ <- Queue.textOf(p.text)
        yield p.modify(_.status).setTo(status)
      checked.left
        .map(p => WorkListControllers.bad(p))
        .flatMap(c => queues.patch(id, c).left.map(WorkListControllers.error))

  def delete(id: QueueItemId): Either[Api.Error, QueueItem] =
    maintenance.guard(WriteGate.OperationName("queue/delete"))(queues.delete(id).left.map(WorkListControllers.error))

  /** The list rendered as its `meta/<FILE>.md`. Notes are included — they ARE the file's prose. */
  def exportText(list: Kind.Label): Either[Api.Error, Queue.Markdown] =
    kindOf(WorkListControllers.KindOfList(list.value)).map(k => Queue.render(k, queues.list(k, QueueScope.All)))

  def importText(list: Kind.Label, req: QueueImport): Either[Api.Error, QueueImported] =
    maintenance.guard(WriteGate.OperationName("queue/import")):
      kindOf(WorkListControllers.KindOfList(list.value)).map { k =>
        val rows = Queue.parse(k, Queue.FileText(req.text.value))
        val counts = queues.replace(k, rows, ImportMode.of(req.replace.value))
        val notes = rows.count(_.status.exists(_.value == Queue.Note))
        QueueImported(
          k.label,
          QueueImported.Items(counts.written.value - notes),
          QueueImported.Notes(notes),
          QueueImported.Replaced(counts.removed.value)
        )
      }

  /** Every list at once — what `histmersion export <dir>` writes alongside the source notes. */
  def files(): List[(Kind.File, Queue.Markdown)] =
    Kind.values.toList.map(k => k.file -> Queue.render(k, queues.list(k, QueueScope.All)))

object WorkListControllers:
  type ListName = ListName.T
  object ListName extends Newtype[String, Pure]
  type KindOfList = KindOfList.T
  object KindOfList extends Newtype[String, Pure]

  private def bad(message: Problem): Api.Error = Api.Error(message)

  def error(e: QueueError): Api.Error = e match
    case QueueError.NotFound(_) =>
      Api.Error(e.problem, Some(Api.Error.Code(Write.CodeNotFound)))
