package com.histmersion
package util.jobs

import com.histmersion.util.database.WritePermit
import com.histmersion.util.problem.{Problem, ProblemLog}

import java.util.concurrent.{ConcurrentHashMap, CopyOnWriteArrayList}
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.Deadline
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** Every long-running worker of one server, as a restore holds them ([[Quiescent]]): the pools and loops register
  * themselves when the composition builds them, and work that runs once on a thread of its own is started through
  * [[detached]], which tracks it. One per server (its [[WritePermit.Gate]]'s), never a global.
  *
  * A worker's unit of work (a job, a run, a tick) runs through [[unit]], stamped with the gate's epoch: a restore that
  * happens meanwhile fences it off from the database.
  */
final class Workers(gate: WritePermit.Gate):
  private val registered = CopyOnWriteArrayList[Quiescent]()
  private val running = ConcurrentHashMap[Workers.Ticket, Quiescent.Busy]()
  private val tickets = AtomicLong(0L)

  /** The work started through [[detached]] that is still running: a restore waits for it like for any worker. */
  private val oneShots: Quiescent = new Quiescent:
    val name: Quiescent.Name = Quiescent.Name("work started once")
    def quiesce(): Unit = ()
    def busy: Vector[Quiescent.Busy] = running.values.asScala.toVector.sorted
    def restored()(using WritePermit)(using Creator.New): Unit = ()
    def resume(): Unit = ()

  registered.add(oneShots)

  /** Add `worker` to what a restore holds. */
  def register(worker: Quiescent): Unit = registered.add(worker)

  /** The registered workers' names, in the order they registered. */
  def names: List[Quiescent.Name] = registered.asScala.toList.map(_.name)

  /** `work` as one unit of a worker's work: fenced off from the database by a restore that happens while it runs. */
  def unit[A](work: WritePermit ?=> A): A = gate.unit(work)

  /** Run `work` once, on a daemon thread of its own named `histmersion-<name>`, as one unit: a restore waits for it
    * (bounded) and fences it off when it is still running then. `work` holds the unit's permit.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def detached(name: Quiescent.Name)(work: () => WritePermit ?=> Unit): Unit =
    val ticket = Workers.Ticket(tickets.incrementAndGet())
    val _ = running.put(ticket, Quiescent.Busy(s"$name"))
    val _ = Thread
      .ofPlatform()
      .daemon(true)
      .name(s"histmersion-$name")
      .start { () =>
        try gate.unit(work())
        finally { val _ = running.remove(ticket) }
      }

  /** A restore's first step: every worker stops taking work and brings what it does to rest. */
  def quiesceAll(): Unit = each(Problem.Where("quiescing"))(_.quiesce())

  /** The work still in flight, by worker. */
  def busy: Vector[(Quiescent.Name, Quiescent.Busy)] =
    registered.asScala.toVector.flatMap(w => w.busy.map(w.name -> _))

  /** Wait until every worker is at rest, or `deadline`: what is still at work then (nothing when all came to rest). */
  def awaitRest(deadline: Deadline): Vector[(Quiescent.Name, Quiescent.Busy)] =
    while busy.nonEmpty && deadline.hasTimeLeft() do Thread.sleep(Workers.RestPollMs)
    busy

  /** After a restore: every worker takes up the restored rows. A worker that fails to is logged and named in the
    * answer; the others go on.
    */
  def reconcileAll()(using WritePermit)(using Creator.New): List[Problem] =
    each(Problem.Where("reconciling with the restored database"))(_.restored())

  /** The restore has ended: every worker takes work again. */
  def resumeAll(): Unit = each(Problem.Where("resuming"))(_.resume())

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def each(step: Problem.Where)(f: Quiescent => Unit): List[Problem] =
    registered.asScala.toList.flatMap { w =>
      try
        f(w)
        None
      catch case NonFatal(e) => Some(ProblemLog.unexpected(e, Problem.Where(s"$step ${w.name}")))
    }

object Workers:
  /** One piece of work started through [[Workers.detached]]. */
  type Ticket = Ticket.T
  object Ticket extends Newtype[Long, Pure]

  /** How often a restore looks whether the workers came to rest. */
  private val RestPollMs = 20L
