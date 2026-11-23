package com.histmersion
package domain.automation.worklists

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentences
import io.scalaland.chimney.dsl.*
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Write
import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Queue.{Kind, QueueAdd, QueueImport, QueueImported, QueueItem, QueuePatch}
import com.histmersion.util.data.maintenance.WriteGate

import java.time.{Clock, LocalDate}
import com.histmersion.api.Api
import com.histmersion.domain.Creator
import com.histmersion.util.nonempty.NonEmptyVector
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Provenance

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

  import WorkListControllers.{actorOf, creatorOf, surfaceOf, Imported, ListName}

  def addOverRest(list: ListName, in: QueueAdd): Either[Api.Error, QueueItem] =
    surfaceOf(in.via).flatMap(creatorOf(in.createdBy, _)).left.map(Api.Error(_)).flatMap(record(list, in, _))

  def addOverMcp(list: ListName, in: QueueAdd): Either[Api.Error, QueueItem] =
    creatorOf(in.createdBy, Creator.Surface.Mcp).left.map(Api.Error(_)).flatMap(record(list, in, _))

  def record(list: ListName, in: QueueAdd, creator: Creator.New): Either[Api.Error, QueueItem] =
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
            in.modify(_.topic).setTo(topic).modify(_.status).setTo(status).modify(_.createdAt).setTo(createdAt),
            creator
          )
      }

  def patch(id: QueueItemId, p: QueuePatch): Either[Api.Error, QueueItem] =
    maintenance.guard(WriteGate.OperationName("queue/patch")):
      val checked =
        for
          status <- Queue.statusOf(p.status)
          _ <- Queue.textOf(p.text)
          actor <- actorOf(p.actor)
        yield p.modify(_.status).setTo(status).modify(_.actor).setTo(actor)
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
        val counts = queues.replace(k, rows, ImportMode.of(req.replace.value), Imported)
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

  private val Imported: Creator.New = Creator.New.Manual(Creator.Surface.Rest)

  private val surfaces: List[(Provenance.Via, Creator.Surface)] =
    List(
      Provenance.Rest -> Creator.Surface.Rest,
      Provenance.Cli -> Creator.Surface.Cli,
      Provenance.Web -> Creator.Surface.Web
    )

  def surfaceOf(via: Option[Provenance.Via]): Either[Problem, Creator.Surface] =
    via.fold(Right(Creator.Surface.Rest)) { v =>
      surfaces
        .collectFirst { case (w, s) if w == v => s }
        .toRight(
          Problem.Invalid.Unknown(
            Problem.TypeName.of(Provenance.Via),
            Problem.Raw.of(v),
            Provenance.Vias.map(Problem.Raw.of(_)).toVector,
            Vector(Problem.Field("via"))
          )
        )
    }

  def actorOf(raw: Option[Provenance.Actor]): Either[Problem, Option[Provenance.Actor]] =
    raw match
      case None                                     => Right(None)
      case Some(a) if Provenance.Actors.contains(a) => Right(Some(a))
      case Some(a)                                  =>
        Left(
          Problem.Invalid.Unknown(
            Problem.TypeName.of(Provenance.Actor),
            Problem.Raw.of(a),
            Provenance.Actors.map(Problem.Raw.of(_)).toVector,
            Vector(Problem.Field("actor"))
          )
        )

  def creatorOf(by: Option[Provenance.By], surface: Creator.Surface): Either[Problem, Creator.New] =
    by match
      case None if surface == Creator.Surface.Mcp => Left(Problem.Invalid.Missing.here(Problem.Field("createdBy")))
      case None                                   => Right(Creator.New.Manual(surface))
      case Some(b)                                =>
        for
          plugin <- named(b.plugin, Problem.Field("plugin"))(Creator.Plugin.Name.from(_))
          workflow <- named(b.workflow, Problem.Field("workflow"))(Creator.Workflow.Name.from(_))
          agent <- agentOf(b)
        yield Creator.New.Automated(plugin, workflow, agent)

  private def named[R, A](raw: Option[R], field: Problem.Field)(
      check: R => Either[Newtype.Refusal, A]
  ): Either[Problem, A] =
    raw
      .toRight(Problem.Invalid.Missing(field, Vector(CreatedByField)))
      .flatMap(check(_).left.map(Problem.Invalid.malformed))

  private val CreatedByField = Problem.Field("createdBy")

  private def agentOf(b: Provenance.By): Either[Problem, Creator.Agent] =
    (b.agentDefinition, b.provider, b.model) match
      case (None, None, None)          => Right(Creator.Agent.Code)
      case (Some(d), Some(p), Some(m)) =>
        for
          definition <- Creator.Definition.Name.from(d).left.map(Problem.Invalid.malformed)
          provider <- Creator.Provider.Name.from(p).left.map(Problem.Invalid.malformed)
          model <- Creator.LanguageModel.Name.from(m).left.map(Problem.Invalid.malformed)
        yield Creator.Agent.Model(definition, provider, model)
      case _ =>
        Left(
          Problem.Invalid.Inconsistent(
            NonEmptyVector.of(Problem.Field("agentDefinition"), Problem.Field("provider"), Problem.Field("model")),
            Problem.Invalid.Exclusion.Together,
            Vector(CreatedByField)
          )
        )

  /** The code of a refusal for an item that is not there. */
  private val NotFound: Api.Error.Code = Api.Error.Code(Write.CodeNotFound)

  extension (lists: WorkListControllers)
    /** A line the server writes on the worklog itself (a digest, a sweep's summary, a migration): `in` added to `log`.
      * A refusal (a restore holds the database, the text is empty) is logged with why, never dropped.
      */
    def logLine(in: QueueAdd, creator: Creator.New): Unit =
      lists
        .record(ListName("log"), in, creator)
        .left
        .foreach(e => scribe.warn(s"a worklog line was not written: ${e.problems.sentences.mkString("; ")}"))

    /** An item removed from its list by the server; one already gone is no news, any other refusal is logged. */
    def removeItem(id: QueueItemId): Unit =
      lists.delete(id) match
        case Left(e) if !e.code.contains(NotFound) =>
          scribe.warn(s"a work-list item was not removed: ${e.problems.sentences.mkString("; ")}")
        case _ => ()

  def error(e: QueueError): Api.Error = e match
    case QueueError.NotFound(_) =>
      Api.Error(e.problem, Some(NotFound))
