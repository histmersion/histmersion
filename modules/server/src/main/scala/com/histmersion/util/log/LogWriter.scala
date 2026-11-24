package com.histmersion
package util.log

import com.histmersion.api.log.LogEvent
import com.histmersion.api.log.LogEvent.{Count, Subject, What}
import com.histmersion.util.database.{Transactor, WritePermit}
import com.histmersion.util.jobs.Quiescent

import java.time.Clock
import java.util.concurrent.{ArrayBlockingQueue, ConcurrentHashMap, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}
import java.util.concurrent.ThreadLocalRandom
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

import LogWriter.{subjectsOf, writesCounted, written, StagedEveryMillis}
import com.histmersion.domain.Creator

final class LogWriter(
    settings: () => LogConfig,
    files: LogFiles,
    transactor: Transactor,
    gate: WritePermit.Gate,
    clock: Clock
) extends LogSink:
  private val queue = ArrayBlockingQueue[LogEvent](settings().queueSize.capacity)
  private val lostToQueue = AtomicReference(Count.zero)
  private val perMinute = ConcurrentHashMap[LogEvent.Operation, (Count, Count)]()
  private val running = AtomicBoolean(false)
  private val thread = AtomicReference[Option[Thread]](None)
  private val backlog = AtomicReference(Vector.empty[LogEvent])
  private val countedSince = AtomicLong(clock.millis())
  private val prunedAt = AtomicLong(0L)
  private val retention = LogRetention(files, transactor, gate)
  private val resting = AtomicBoolean(false)
  private val writing = AtomicBoolean(false)

  val quiescent: Quiescent = new Quiescent:
    val name: Quiescent.Name = Quiescent.Name("the structured log's writer")
    def quiesce(): Unit = resting.set(true)
    def busy: Vector[Quiescent.Busy] =
      if writing.get() then Vector(Quiescent.Busy("a batch of events into the table")) else Vector.empty
    def restored()(using WritePermit)(using Creator.New): Unit = ()
    def resume(): Unit = resting.set(false)

  def mintTrace(): LogEvent.Trace =
    val r = ThreadLocalRandom.current()
    LogEvent.Trace.v7(clock.millis(), r.nextLong(), r.nextLong())

  def record(scope: TraceScope, subjects: Vector[Subject], what: What): Unit =
    offer(LogEvent(mintId(), now(), scope.trace, scope.label, scope.actor, subjectsOf(subjects, what), what))

  def recordServer(what: What): Unit = offer(server(what))

  def count(operation: LogEvent.Operation, failed: Boolean): Unit =
    val _ = perMinute.merge(
      operation,
      (Count.one, if failed then Count.one else Count.zero),
      (a, b) => (a._1 + b._1, a._2 + b._2)
    )

  def start(): Unit =
    if running.compareAndSet(false, true) then
      thread.set(Some(Thread.ofPlatform().daemon().name("histmersion-log").start(() => loop())))

  def close(): Unit =
    if running.compareAndSet(true, false) then
      thread.get().foreach { t =>
        t.interrupt()
        t.join(10000L)
      }
      flush()

  def flush(): Unit = round(Vector.empty, stagedDue = true)

  private def round(first: Vector[LogEvent], stagedDue: Boolean): Unit = synchronized {
    val batch = Vector.newBuilder[LogEvent] ++= first
    @annotation.tailrec
    def drain(n: Int): Unit =
      if n > 0 then
        val e = queue.poll()
        if e != null then
          batch += e
          drain(n - 1)
    drain(settings().batchSize.after(first.size))
    write(batch.result() ++ housekeeping(), stagedDue)
  }

  private def loop(): Unit =
    var stagedAt = 0L
    while running.get() do
      try
        val first = queue.poll(settings().flushMillis.millis, TimeUnit.MILLISECONDS)
        val due = clock.millis() - stagedAt >= StagedEveryMillis
        if due then stagedAt = clock.millis()
        round(Option(first).toVector, due)
      catch
        case _: InterruptedException => ()
        case NonFatal(e)             => scribe.warn(s"log writer: ${e.getClass.getSimpleName}")

  private def housekeeping(): Vector[LogEvent] =
    val lost = lostToQueue.getAndSet(Count.zero)
    val lostEvent = Option.when(lost.positive)(server(What.Lost(lost, LogEvent.Loss.QueueFull)))
    val minute = 60000L
    val countedEvents =
      if clock.millis() - countedSince.get() < minute then Vector.empty
      else
        countedSince.set(clock.millis())
        val ops = perMinute.keySet().asScala.toVector
        ops.flatMap { op =>
          Option(perMinute.remove(op)).map((n, failed) => server(What.RequestsCounted(op, n, failed)))
        }
    lostEvent.toVector ++ countedEvents

  private def write(batch: Vector[LogEvent], stagedDue: Boolean): Unit =
    val cfg = settings()
    val _ = files.append(batch)
    val pending = backlog.get() ++ batch
    val staged =
      if gate.active || resting.get() then None
      else if pending.isEmpty && !stagedDue then Some((Vector.empty, Some(0L)))
      else
        writing.set(true)
        try
          gate.unit {
            gate.permit {
              transactor.write {
                val writes = LogRepo.takeStaged(cfg.batchSize.rows).map(written) ++
                  LogRepo.takeCounted.map(c => writesCounted(c, mintId(), mintTrace()))
                (writes, LogRepo.insert(pending ++ writes))
              }
            }.toOption
          }
        catch case NonFatal(_) => None
        finally writing.set(false)
    staged match
      case Some((writes, added)) =>
        scribe.debug(s"log writer: ${added.fold("some")(n => s"$n")} new event(s) in the table")
        backlog.set(Vector.empty)
        val _ = files.append(writes)
      case None =>
        val (kept, over) = cfg.backlog.keep(pending)
        backlog.set(kept)
        if over.nonEmpty then
          val _ = files.append(Vector(server(What.Lost(Count.of(over), LogEvent.Loss.DatabaseBacklog))))
    prune(cfg)

  private def prune(cfg: LogConfig): Unit =
    if cfg.pruneEveryMinutes.due(prunedAt.get(), clock.millis()) && !gate.active then
      prunedAt.set(clock.millis())
      retention.run(cfg, clock.instant())

  private def offer(e: LogEvent): Unit =
    if !queue.offer(e) then
      val _ = lostToQueue.updateAndGet(_ + Count.one)

  private def server(what: What): LogEvent =
    LogEvent(mintId(), now(), mintTrace(), None, LogEvent.Actor.Server, Vector.empty, what)

  private def now(): LogEvent.At = LogEvent.At.of(clock.instant())

  private def mintId(): LogEvent.Id =
    val r = ThreadLocalRandom.current()
    LogEvent.Id.v7(clock.millis(), r.nextLong(), r.nextLong())

object LogWriter:
  val StagedEveryMillis: Long = 1000L

  def subjectsOf(named: Vector[Subject], what: What): Vector[Subject] = what match
    case What.ProblemRaised(Some(id), _) => (named :+ id.subject).distinct
    case _                               => named

  val Jobs: LogEvent.Table = LogEvent.Table("jobs")

  def change(op: String): LogEvent.Change = op match
    case "insert" => LogEvent.Change.Insert
    case "update" => LogEvent.Change.Update
    case "delete" => LogEvent.Change.Delete
    case _        => LogEvent.Change.Truncate

  private[log] def written(s: LogRepo.Staged): LogEvent =
    val jobs = if s.table == Jobs then s.keys.map(Subject.Job(_)).toVector else Vector.empty
    LogEvent(
      s.id,
      s.at,
      s.trace,
      s.label,
      LogEvent.Actor.Database,
      s.citekeys.map(Subject.Source(_)).toVector ++ jobs,
      What.Written(s.table, change(s.op), s.rows, s.keys.toVector)
    )

  private[log] def writesCounted(c: LogRepo.Counted, id: LogEvent.Id, trace: LogEvent.Trace): LogEvent =
    LogEvent(
      id,
      c.at,
      trace,
      None,
      LogEvent.Actor.Database,
      Vector.empty,
      What.WritesCounted(c.table, change(c.op), c.count)
    )
