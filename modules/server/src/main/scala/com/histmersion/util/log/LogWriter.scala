package com.histmersion
package util.log

import com.histmersion.api.log.LogEvent
import com.histmersion.api.log.LogEvent.{Count, Subject, What}
import com.histmersion.config.LogConfig
import com.histmersion.util.database.{Transactor, WritePermit}
import com.histmersion.util.jobs.Quiescent

import java.time.Clock
import java.util.concurrent.{ArrayBlockingQueue, ConcurrentHashMap, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}
import java.util.concurrent.ThreadLocalRandom
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** The structured log's one writer, and the only way an event reaches the table or the files. Recording never blocks:
  * an event goes into a bounded queue (full: it is counted, and one `Lost` event says how many), and this writer's own
  * thread takes it from there in batches. Each batch is appended to the day's file first, then written to the table
  * together with the write statements the triggers staged; while the database cannot take it, the batch waits in a
  * bounded backlog (the file has it already), and what overflows that is counted as kept in the file only. The
  * retention runs on the same thread.
  */
final class LogWriter(
    settings: () => LogConfig,
    files: LogFiles,
    transactor: Transactor,
    gate: WritePermit.Gate,
    clock: Clock
) extends LogSink:
  private val queue = ArrayBlockingQueue[LogEvent](math.max(1, settings().queueSize.value))
  private val lostToQueue = AtomicLong(0L)
  private val counted = ConcurrentHashMap[LogEvent.Operation, (Long, Long)]()
  private val running = AtomicBoolean(false)
  private val thread = AtomicReference[Option[Thread]](None)
  private val backlog = AtomicReference(Vector.empty[LogEvent])
  private val countedSince = AtomicLong(clock.millis())
  private val prunedAt = AtomicLong(0L)
  private val resting = AtomicBoolean(false)
  private val writing = AtomicBoolean(false)

  /** The writer as a restore holds it: at rest, it keeps appending to the files and leaves the database alone (the
    * events wait in the backlog, and reach the restored database's table when it resumes).
    */
  val quiescent: Quiescent = new Quiescent:
    val name: Quiescent.Name = Quiescent.Name("the structured log's writer")
    def quiesce(): Unit = resting.set(true)
    def busy: Vector[Quiescent.Busy] =
      if writing.get() then Vector(Quiescent.Busy("a batch of events into the table")) else Vector.empty
    def restored(): Unit = ()
    def resume(): Unit = resting.set(false)

  /** A new trace, as the server mints one for a request, a start-up or its own work. */
  def mintTrace(): LogEvent.Trace = LogEvent.Trace(mintId())

  def record(scope: TraceScope, subjects: Vector[Subject], what: What): Unit =
    offer(
      LogEvent(
        LogEvent.Id(mintId()),
        now(),
        scope.trace,
        scope.label,
        scope.actor,
        LogWriter.subjects(subjects, what),
        what
      )
    )

  /** `what`, the server's own step outside any request or job: a trace of its own. */
  def recordServer(what: What): Unit =
    offer(LogEvent(LogEvent.Id(mintId()), now(), mintTrace(), None, LogEvent.Actor.Server, Vector.empty, what))

  /** One request on a counted path (map tiles, static files, the health check): summed per minute. */
  def count(operation: LogEvent.Operation, failed: Boolean): Unit =
    val _ = counted.merge(operation, (1L, if failed then 1L else 0L), (a, b) => (a._1 + b._1, a._2 + b._2))

  /** Start the writer's thread (once). */
  def start(): Unit =
    if running.compareAndSet(false, true) then
      thread.set(Some(Thread.ofPlatform().daemon().name("histmersion-log").start(() => loop())))

  /** Stop the thread, after it wrote what was recorded until now. */
  def close(): Unit =
    if running.compareAndSet(true, false) then
      thread.get().foreach { t =>
        t.interrupt()
        t.join(10000L)
      }
      flush()

  /** One round of the writer's work, on the calling thread, with the database's staged writes taken at once: what the
    * thread does each time (tests call it).
    */
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
    drain(math.max(1, settings().batchSize.value) - first.size)
    write(batch.result() ++ housekeeping(), stagedDue)
  }

  private def loop(): Unit =
    var stagedAt = 0L
    while running.get() do
      try
        val first = queue.poll(math.max(1, settings().flushMillis.value).toLong, TimeUnit.MILLISECONDS)
        val due = clock.millis() - stagedAt >= LogWriter.StagedEveryMillis
        if due then stagedAt = clock.millis()
        round(Option(first).toVector, due)
      catch
        case _: InterruptedException => ()
        case NonFatal(e)             => scribe.warn(s"log writer: ${e.getClass.getSimpleName}")

  private def housekeeping(): Vector[LogEvent] =
    val lost = lostToQueue.getAndSet(0L)
    val lostEvent = Option.when(lost > 0)(server(What.Lost(Count(lost), LogEvent.Loss.QueueFull)))
    val minute = 60000L
    val countedEvents =
      if clock.millis() - countedSince.get() < minute then Vector.empty
      else
        countedSince.set(clock.millis())
        val ops = counted.keySet().asScala.toVector
        ops.flatMap { op =>
          Option(counted.remove(op)).map((n, failed) => server(What.RequestsCounted(op, Count(n), Count(failed))))
        }
    lostEvent.toVector ++ countedEvents

  private def write(batch: Vector[LogEvent], stagedDue: Boolean): Unit =
    val cfg = settings()
    val _ = files.append(batch)
    val pending = backlog.get() ++ batch
    val staged =
      if gate.active || resting.get() then None
      else if pending.isEmpty && !stagedDue then Some(Vector.empty)
      else
        writing.set(true)
        try
          gate.unit {
            gate.permit {
              transactor.write {
                val writes = LogRepo.takeStaged(cfg.batchSize.value).map(LogWriter.written)
                LogRepo.insert(pending ++ writes)
                writes
              }
            }.toOption
          }
        catch case NonFatal(_) => None
        finally writing.set(false)
    staged match
      case Some(writes) =>
        backlog.set(Vector.empty)
        val _ = files.append(writes)
      case None =>
        val keep = math.max(0, cfg.backlog.value)
        val over = pending.size - keep
        backlog.set(pending.takeRight(keep))
        if over > 0 then
          val _ = files.append(Vector(server(What.Lost(Count(over.toLong), LogEvent.Loss.DatabaseBacklog))))
    prune(cfg)

  private def prune(cfg: LogConfig): Unit =
    val every = math.max(1, cfg.pruneEveryMinutes.value).toLong * 60000L
    if clock.millis() - prunedAt.get() >= every && !gate.active then
      prunedAt.set(clock.millis())
      LogRetention.run(cfg, files, transactor, gate, clock.instant())

  private def offer(e: LogEvent): Unit =
    if !queue.offer(e) then
      val _ = lostToQueue.incrementAndGet()

  private def server(what: What): LogEvent =
    LogEvent(LogEvent.Id(mintId()), now(), mintTrace(), None, LogEvent.Actor.Server, Vector.empty, what)

  private def now(): LogEvent.At = LogEvent.At(LogStamp.iso(clock.instant()))

  private def mintId(): String =
    val r = ThreadLocalRandom.current()
    LogStamp.uuidV7(clock.millis(), r.nextLong(), r.nextLong())

object LogWriter:
  /** How often the writer takes the database's staged write statements when it has no event of its own to write. */
  val StagedEveryMillis: Long = 1000L

  /** `named` and what the step itself names: a problem with an id is a subject, so it can be looked up by that id. */
  def subjects(named: Vector[Subject], what: What): Vector[Subject] = what match
    case LogEvent.What.ProblemRaised(Some(id), _) => (named :+ Subject.Problem(LogEvent.Key(id.value))).distinct
    case _                                        => named

  /** A staged write statement as its event: the id, time, trace and label the trigger gave it. */
  private[log] def written(s: LogRepo.Staged): LogEvent =
    val change = s.op match
      case "insert" => LogEvent.Change.Insert
      case "update" => LogEvent.Change.Update
      case "delete" => LogEvent.Change.Delete
      case _        => LogEvent.Change.Truncate
    val subjects = s.citekeys.map(c => Subject.Source(LogEvent.Key(c))).toVector ++ (s.table match
      case "jobs" => s.keys.map(k => Subject.Job(LogEvent.Key(k))).toVector
      case _      => Vector.empty)
    LogEvent(
      LogEvent.Id(s.id),
      LogEvent.At(s.at),
      LogEvent.Trace(s.trace),
      s.label.map(LogEvent.Label(_)),
      LogEvent.Actor.Database,
      subjects,
      What.Written(LogEvent.Table(s.table), change, s.rows.map(Count(_)), s.keys.map(LogEvent.Key(_)).toVector)
    )
