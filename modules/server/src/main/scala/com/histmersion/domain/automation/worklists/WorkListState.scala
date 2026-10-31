package com.histmersion.domain.automation.worklists

import com.histmersion.api.Queue
import com.histmersion.api.Queue.{Kind, QueueAdd, QueueItem}
import com.histmersion.domain.Scheduling

import hearth.kindlings.optics.syntax.*

import scala.annotation.tailrec

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, ZoneOffset}

/** The work lists held in memory: every item in id order, and the last id handed out. The rules are the database's: an
  * item is stamped when it is made (or keeps the instant it was imported with) and when it is first done, its times
  * read as `to_char` writes them (UTC, microseconds).
  */
final case class WorkListState(items: Vector[QueueItem], lastId: Int):

  def listed(kind: Kind, scope: QueueScope): Vector[QueueItem] =
    val ofKind = items.filter(_.list == kind.label)
    scope match
      case QueueScope.Open        => ofKind.filter(_.status == Queue.Open)
      case QueueScope.All         => ofKind
      case QueueScope.ForJob(job) =>
        ofKind.filter(i => i.status != Queue.Note && (i.createdBy.contains(job) || i.closedBy.contains(job)))
      case QueueScope.ForGoal(goal, linked, withDone) =>
        ofKind.filter(i =>
          i.status != Queue.Note && (withDone || i.status == Queue.Open) &&
            (i.goal.contains(goal) || linked.contains(i.id.toString))
        )

  def get(id: QueueItemId): Option[QueueItem] = items.find(_.id == id.value)

  /** The items of `kind` whose text names `doi`, as the database's `strpos` and the DOI rule find them. */
  def naming(kind: Kind, doi: String): Vector[QueueItem] =
    val d = doi.trim.toLowerCase
    if d.isEmpty then Vector.empty
    else
      items.filter(i =>
        i.list == kind.label && i.text.toLowerCase.contains(d) && Scheduling.dois(i.text).contains(d)
      )

  /** `in` added to `kind` at `now`, as the database inserts it. */
  def added(kind: Kind, in: QueueAdd, now: Instant): WorkListState = addedAll(kind, List(in), now)

  /** Each of `ins` added to `kind` at `now`, in order. */
  def addedAll(kind: Kind, ins: List[QueueAdd], now: Instant): WorkListState =
    @tailrec def loop(rest: List[QueueAdd], made: Vector[QueueItem], last: Int): WorkListState = rest match
      case Nil         => WorkListState(items ++ made, last)
      case in :: tail => loop(tail, made :+ WorkListState.item(kind, in, last + 1, now), last + 1)
    loop(ins, Vector.empty, lastId)


  /** `next` written over its item; a done item keeps its first done time, any other status clears it. */
  def updated(next: QueueItem, now: Instant): WorkListState =
    this
      .modify(_.items)
      .using(_.map { cur =>
        if cur.id != next.id then cur
        else
          val doneAt = if next.status == Queue.Done then cur.doneAt.orElse(Some(WorkListState.stamp(now))) else None
          next.modify(_.doneAt).using(_ => doneAt)
      })

  def removed(id: QueueItemId): WorkListState = this.modify(_.items).using(_.filterNot(_.id == id.value))

  def emptied(kind: Kind): WorkListState = this.modify(_.items).using(_.filterNot(_.list == kind.label))

object WorkListState:
  val empty: WorkListState = WorkListState(Vector.empty, 0)

  private val Stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC)

  /** An instant as the store reads it back: UTC, to the microsecond. */
  def stamp(at: Instant): String = Stamp.format(at.truncatedTo(ChronoUnit.MICROS))

  /** Item `id` of `kind` made from `in` at `now`: open unless it says otherwise, done at once when it is done. */
  def item(kind: Kind, in: QueueAdd, id: Int, now: Instant): QueueItem =
    val status = in.status.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(Queue.Open)
    QueueItem(
      id,
      kind.label,
      in.text,
      in.topic,
      status,
      in.reason,
      Some(stamp(in.createdAt.map(Instant.parse).getOrElse(now))),
      Option.when(status == Queue.Done)(stamp(now)),
      in.goal.map(_.trim).filter(_.nonEmpty),
      in.job.map(_.trim).filter(_.nonEmpty),
      None
    )
