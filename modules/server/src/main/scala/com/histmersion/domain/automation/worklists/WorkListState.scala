package com.histmersion
package domain.automation.worklists

import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Queue.{Kind, QueueAdd, QueueItem}
import com.histmersion.domain.{Actor, Creator, Scheduling}
import com.histmersion.util.database.Jsonb

import hearth.kindlings.optics.syntax.*

import scala.annotation.tailrec

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, ZoneOffset}
import com.histmersion.domain.library.Doi
import com.histmersion.api.automation.Goal

/** The work lists held in memory: every item in id order, and the last id handed out. The rules are the database's: an
  * item is stamped when it is made (or keeps the instant it was imported with) and when it is first done, its times
  * read as `to_char` writes them (UTC, microseconds).
  */
final case class WorkListState(items: Vector[QueueItem], lastId: QueueItemId):

  def listed(kind: Kind, scope: QueueScope): Vector[QueueItem] =
    val ofKind = items.filter(_.list == kind.label)
    scope match
      case QueueScope.Open        => ofKind.filter(_.status.value == Queue.Open)
      case QueueScope.All         => ofKind
      case QueueScope.ForJob(job) =>
        ofKind.filter(i =>
          i.status.value != Queue.Note && (i.createdBy.exists(_.value == job.value) || i.closedBy.exists(
            _.value == job.value
          ))
        )
      case QueueScope.ForGoal(goal, linked, withDone) =>
        ofKind.filter(i =>
          i.status.value != Queue.Note && (withDone.value || i.status.value == Queue.Open) &&
            (i.goal.exists(_.value == goal.value) || linked.contains(QueueScope.ForGoal.Linked(i.id.value.toString)))
        )

  def get(id: QueueItemId): Option[QueueItem] = items.find(_.id.value == id.value)

  /** The items of `kind` whose text names `doi`, as the database's `strpos` and the DOI rule find them. */
  def naming(kind: Kind, doi: Doi): Vector[QueueItem] =
    val d = doi.value.trim.toLowerCase
    if d.isEmpty then Vector.empty
    else
      items.filter(i =>
        i.list == kind.label && i.text.value.toLowerCase
          .contains(d) && Scheduling.dois(i.text.value).exists(_.value == d)
      )

  /** `in` added to `kind` at `now`, as the database inserts it. */
  def added(kind: Kind, in: QueueAdd, creator: Creator.New, now: Instant): WorkListState =
    addedAll(kind, List(in), creator, now)

  /** Each of `ins` added to `kind` at `now`, in order. */
  def addedAll(kind: Kind, ins: List[QueueAdd], creator: Creator.New, now: Instant): WorkListState =
    @tailrec def loop(rest: List[QueueAdd], made: Vector[QueueItem], last: QueueItemId): WorkListState = rest match
      case Nil        => WorkListState(items ++ made, last)
      case in :: tail =>
        val next = last.next
        loop(tail, made :+ WorkListState.item(kind, in, creator, next, now), next)
    loop(ins, Vector.empty, lastId)

  /** `next` written over its item; a done item keeps its first done time, any other status clears it. */
  def updated(next: QueueItem, now: Instant): WorkListState =
    this
      .modify(_.items)
      .using(_.map { cur =>
        if cur.id != next.id then cur
        else
          val doneAt =
            if next.status.value == Queue.Done then cur.doneAt.orElse(Some(QueueItem.DoneAt(WorkListState.stamp(now))))
            else None
          next.modify(_.doneAt).using(_ => doneAt)
      })

  def removed(id: QueueItemId): WorkListState = this.modify(_.items).using(_.filterNot(_.id.value == id.value))

  def emptied(kind: Kind): WorkListState = this.modify(_.items).using(_.filterNot(_.list == kind.label))

object WorkListState:

  val empty: WorkListState = WorkListState(Vector.empty, QueueItemId(0))

  private val Stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC)

  /** An instant as the store reads it back: UTC, to the microsecond. */
  def stamp(at: Instant): String = Stamp.format(at.truncatedTo(ChronoUnit.MICROS))

  /** Item `id` of `kind` made from `in` at `now`: open unless it says otherwise, done at once when it is done. */
  def item(kind: Kind, in: QueueAdd, creator: Creator.New, id: QueueItemId, now: Instant): QueueItem =
    val status = in.status.map(_.value.trim.toLowerCase).filter(_.nonEmpty).getOrElse(Queue.Open)
    QueueItem(
      QueueItem.Id(id.value),
      kind.label,
      in.text,
      in.topic,
      QueueItem.Status(status),
      in.reason,
      Some(QueueItem.CreatedAt(stamp(in.createdAt.map(c => Instant.parse(c.value)).getOrElse(now)))),
      Option.when(status == Queue.Done)(QueueItem.DoneAt(stamp(now))),
      in.goal.map(_.value.trim).filter(_.nonEmpty).map(Goal.Slug(_)),
      in.job.map(_.value.trim).filter(_.nonEmpty).map(QueueItem.CreatedBy(_)),
      None,
      Some(
        Jsonb
          .read[Queue.Creator](StoredCreator.write(creator.stored))
          .getOrElse(Queue.Creator(Queue.Creation.Unreadable))
      ),
      Some(if creator.actor == Actor.Agents then Queue.Creation.Agents else Queue.Creation.Person)
    )
