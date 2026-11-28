package com.histmersion
package api.automation.queue

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Creation
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Queue.Rationale
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Display
import com.histmersion.Newtype
import com.histmersion.api.automation.queue.Kind
import com.histmersion.api.library.Citekey
import com.histmersion.api.automation.Queue.Done
import com.histmersion.api.automation.queue.QueuePatch
import com.histmersion.api.automation.Queue.fulfilledReason
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.util.problem.Problem.TypeName
import com.histmersion.api.automation.Queue.Open

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class QueueItem(
    id: QueueItem.Id,
    list: QueueItem.List,
    text: QueueItem.Text,
    topic: Option[QueueItem.Topic],
    status: QueueItem.Status,
    reason: Option[Rationale] = None,
    createdAt: Option[QueueItem.CreatedAt] = None,
    doneAt: Option[QueueItem.DoneAt] = None,
    goal: Option[Goal.Slug] = None,
    createdBy: Option[QueueItem.CreatedBy] = None,
    closedBy: Option[QueueItem.ClosedBy] = None,
    creator: Option[Creation] = None,
    actor: Option[Creation.Actor] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object QueueItem:
  type Id = Id.T
  object Id extends Newtype[Int, Pure]:
    given Display[T] = Display(id => s"${id.value}")
  export Kind.Label as List
  type Text = Text.T
  object Text extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Topic = Topic.T
  object Topic extends Newtype[String, Pure]
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]
  type DoneAt = DoneAt.T
  object DoneAt extends Newtype[String, Pure]
  type CreatedBy = CreatedBy.T
  object CreatedBy extends Newtype[String, Pure]
  type ClosedBy = ClosedBy.T
  object ClosedBy extends Newtype[String, Pure]

  extension (item: QueueItem)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fulfil(citekey: Citekey, by: Option[QueueItem.ClosedBy] = None): QueuePatch =
      QueuePatch(
        status = Some(QueueItem.Status(Done)),
        reason = Some(fulfilledReason(item.reason, citekey)),
        closedBy = by
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fulfillable(id: QueueItem.Id, found: Option[QueueItem]): Either[Problem, QueueItem] =
    found match
      case None => Left(Problem.NotFound.Entity(TypeName.of(QueueItem.Id), Raw(s"$id")))
      case Some(i) if i.list != Kind.Acquire.label =>
        Left(
          Problem.Conflict.WrongState(
            TypeName.of(QueueItem.Id),
            Raw(s"$id"),
            Problem.Conflict.State(s"${i.list}"),
            Vector(Problem.Conflict.State(s"${Kind.Acquire.label}"))
          )
        )
      case Some(i) if i.status != QueueItem.Status(Open) =>
        Left(
          Problem.Conflict.WrongState(
            TypeName.of(QueueItem.Id),
            Raw(s"$id"),
            Problem.Conflict.State(s"${i.status}"),
            Vector(Problem.Conflict.State(s"$Open"))
          )
        )
      case Some(i) => Right(i)

  extension (cur: QueueItem)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def applyPatch(p: QueuePatch): QueueItem =
      def clearable[A](v: Option[A], old: Option[A])(text: A => String, make: String => A): Option[A] =
        v.fold(old)(x => Some(text(x).trim).filter(_.nonEmpty).map(make))
      val status =
        p.status.map(_.value.trim.toLowerCase).filter(_.nonEmpty).map(x => QueueItem.Status(x)).getOrElse(cur.status)
      val closedBy =
        if status != QueueItem.Status(Done) then None
        else
          p.closedBy
            .map(_.value.trim)
            .filter(_.nonEmpty)
            .map(x => QueueItem.ClosedBy(x))
            .orElse(Option.when(cur.status == QueueItem.Status(Done))(cur.closedBy).flatten)
      cur
        .modify(_.status)
        .setTo(status)
        .modify(_.text)
        .setTo(p.text.getOrElse(cur.text))
        .modify(_.topic)
        .setTo(clearable(p.topic, cur.topic)(t => (t).value, x => QueueItem.Topic(x)))
        .modify(_.reason)
        .setTo(clearable(p.reason, cur.reason)(t => s"$t", x => Rationale(s"$x")))
        .modify(_.goal)
        .setTo(clearable(p.goal, cur.goal)(t => (t).value, x => Goal.Slug(x)))
        .modify(_.closedBy)
        .setTo(closedBy)
        .modify(_.actor)
        .setTo(p.actor.orElse(cur.actor))
