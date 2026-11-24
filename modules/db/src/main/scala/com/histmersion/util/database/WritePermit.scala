package com.histmersion
package util.database

import com.histmersion.util.database.WritePermit.{Gate, Kind}
import com.histmersion.util.problem.{Problem, Raised}

import java.io.PrintWriter
import java.sql.Connection
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Logger
import javax.sql.DataSource
import scala.concurrent.duration.Deadline
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** The right to write to the database for the extent of one block. Only a [[WritePermit.Gate]] issues one: every write
  * transaction ([[Transactor.write]], [[Transactor.writeAutocommit]]) takes one, so a write cannot be opened without
  * one (a compile error), and so it runs either
  *   - inside an operation a restore waits for (a [[WritePermit.Kind.Operation]] permit: [[WritePermit.Gate.permit]],
  *     the maintenance gate's `guard`), or
  *   - inside a unit of a registered worker (a [[WritePermit.Kind.Worker]] permit: [[WritePermit.Gate.unit]]), which a
  *     restore quiesces, waits for (bounded) and fences off: each write through it is refused once the restore holds
  *     the database, and for good when the unit began before it.
  *
  * A permit lives as long as the block it was issued for. Kept past it (captured by a thread that outlives the block),
  * it no longer writes: [[Transactor.write]] refuses a permit whose block has ended.
  */
final class WritePermit private (gate: Gate, val kind: Kind):
  @volatile private var open = true

  /** Whether the block it was issued for still runs. */
  def live: Boolean = open

  /** Its use: refused once the block it was issued for has ended; a unit's, also while a restore holds the database
    * (thrown as [[Raised]] with the refusal's problem) and once the unit is stale.
    */
  private[database] def require(): Unit =
    if !open then throw IllegalStateException("a write permit was used after the block it was issued for ended")
    if kind == Kind.Worker then gate.admit()

  private[WritePermit] def end(): Unit = open = false

object WritePermit:

  /** What a permit stands for: what a restore does about the writes made under it. */
  enum Kind derives CanEqual:
    /** An operation a restore waits for: counted while its block runs, refused while a restore drains. */
    case Operation

    /** A unit of a registered worker: not counted (the restore quiesces the worker and waits for it to rest), checked
      * at each write instead.
      */
    case Worker

    /** A test's: from a gate of its own, never drained ([[standing]]). */
    case Standing

  /** A permit for a test's writes that no restore ever drains: only test code of this package takes it (the test
    * sources' `TestPermits`). Main code never calls it (counted by the G89unguarded rule).
    */
  private[database] def standing(): WritePermit = new WritePermit(Gate(), Kind.Standing)

  /** Why the gate turned a permit or a connection down. */
  enum Refusal derives CanEqual:
    /** A restore holds the gate: it replaces the whole database. */
    case Restoring

    /** The work asking began before a restore replaced the database: what it would write belongs to a database that is
      * gone.
      */
    case Stale

    def problem: Problem = this match
      case Restoring => Problem.Conflict.Busy(Problem.Conflict.Activity.Restore)
      case Stale     => Problem.Run.Restored

  /** How far a restore has taken the gate. */
  enum Stage derives CanEqual:
    /** Permits and connections are handed out. */
    case Open

    /** A restore waits for the permits out: new ones are refused, connections are still handed out (the workers record
      * where they stopped).
      */
    case Draining

    /** The restore runs: no permit and no connection but the restore's own. */
    case Closed

  /** How many restores the gate has let through: work stamped with an older one is stale. */
  type Epoch = Epoch.T
  object Epoch extends Newtype[Long, Pure]:
    extension (e: Epoch)
      def before(other: Epoch): Boolean = e.value < other.value
      def next: Epoch = Epoch(e.value + 1)

  /** How many permits are out. */
  type Outstanding = Outstanding.T
  object Outstanding extends Newtype[Int, Pure]:
    extension (o: Outstanding) def none: Boolean = o.value == 0

  /** The one gate of a server's database: permits for the writes, admission for every connection, and the epoch that
    * fences off work begun before a restore. One per server, never a global: two servers in one JVM (a test run) do not
    * hold each other's writes.
    *
    * A restore takes it in three steps: [[drain]] (new permits refused), [[awaitPermits]] (the permits out come back),
    * [[close]] (every connection but the restore's own refused, the epoch moved on), then [[awaitIdle]] for the
    * connections lent before; [[reopen]] ends it. A thread that holds a permit gets the same one again inside its block
    * (a guarded write calling another), so a nested write never waits on the restore that waits on it.
    */
  final class Gate:
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    @volatile private var stage: Stage = Stage.Open
    @volatile private var holder: Option[Thread] = None
    @volatile private var epoch: Epoch = Epoch(0L)
    private var outstanding = 0 // the permits out; guarded by `lock`
    private val current = ThreadLocal.withInitial[Option[WritePermit]](() => None)
    private val stamped = new InheritableThreadLocal[Option[Epoch]]:
      override def initialValue(): Option[Epoch] = None
    private val gated = java.util.concurrent.CopyOnWriteArrayList[DataSource]()

    /** Whether a restore holds the gate. */
    def active: Boolean = stage != Stage.Open

    def stageNow: Stage = stage

    def epochNow: Epoch = epoch

    /** Run `work` holding a permit: the calling thread's own, when its block is still running, else a new one, given
      * back when `work` returns. Refused while a restore holds the gate (its own thread included: a restore writes
      * nothing through a permit), and for work stamped before the last restore.
      */
    def permit[A](work: WritePermit ?=> A): Either[Refusal, A] =
      val outer = current.get()
      outer.filter(_.live) match
        case Some(_) if stale => Left(Refusal.Stale)
        case Some(own)        => Right(work(using own))
        case None             =>
          enter() match
            case Left(refused) => Left(refused)
            case Right(p)      =>
              current.set(Some(p))
              try Right(work(using p))
              finally
                current.set(outer)
                leave(p)

    private def enter(): Either[Refusal, WritePermit] =
      lock.lock()
      try
        if stale then Left(Refusal.Stale)
        else if stage != Stage.Open then Left(Refusal.Restoring)
        else
          outstanding += 1
          Right(new WritePermit(this, Kind.Operation))
      finally lock.unlock()

    /** [[permit]] for work that answers no `Either`: a refusal is thrown as [[Raised]] with its problem (as the gate's
      * data sources refuse a connection).
      */
    def writing[A](work: WritePermit ?=> A): A =
      permit(work).fold(refused => throw Raised(refused.problem), identity)

    private def leave(p: WritePermit): Unit =
      p.end()
      lock.lock()
      try
        outstanding -= 1
        changed.signalAll()
      finally lock.unlock()

    /** `work` on the thread of the restore that holds this gate, with a [[Kind.Worker]] permit and no stamp (the
      * threads it starts are not stale after the restore): the workers' quiescing and reconciling write through it.
      * Thrown at any other thread. It is not the thread's permit for [[permit]] to hand out again: an operation the
      * restore's thread asks for is refused like any other while the restore holds the gate.
      */
    def holding[A](work: WritePermit ?=> A): A =
      if !holder.contains(Thread.currentThread()) then
        throw IllegalStateException("only the thread of the restore holding the gate writes through its permit")
      val p = new WritePermit(this, Kind.Worker)
      try work(using p)
      finally p.end()

    /** Run `work` as one unit of a worker's work, stamped with the epoch now: a restore that happens while it runs
      * makes it stale, and from then on it gets no permit and no connection. A unit inside another keeps the outer
      * stamp; threads the unit starts inherit it.
      *
      * `work` holds a [[Kind.Worker]] permit (or the permit the calling thread holds already): not counted, since the
      * restore quiesces the worker and waits for it to come to rest; each write through it is refused while the restore
      * holds the database, and for good once the unit is stale. It is the worker's to record where it stopped while a
      * restore drains.
      */
    def unit[A](work: WritePermit ?=> A): A =
      val outer = current.get()
      def run(): A = outer.filter(_.live) match
        case Some(own) => work(using own)
        case None      =>
          val p = new WritePermit(this, Kind.Worker)
          current.set(Some(p))
          try work(using p)
          finally
            current.set(outer)
            p.end()
      if stamped.get().isDefined then run()
      else
        stamped.set(Some(epoch))
        try run()
        finally stamped.remove()

    /** Whether the calling thread's unit began before the last restore. */
    def stale: Boolean = stamped.get().exists(_.before(epoch)) && !holding

    private def holding: Boolean = holder.contains(Thread.currentThread())

    /** May the calling thread have a connection now? Throws [[Raised]] with the refusal's problem when not: a restore
      * holds the gate (its own thread is let through), or the calling unit is stale.
      */
    def admit(): Unit =
      if stage == Stage.Closed && !holding then throw Raised(Refusal.Restoring.problem)
      if stale then throw Raised(Refusal.Stale.problem)

    /** `ds` behind this gate: each connection is asked for through [[admit]]. */
    def dataSource(ds: DataSource): DataSource =
      val g = Gated(ds)
      val _ = gated.add(g)
      g

    /** How many connections the data sources behind this gate have lent out now. */
    def lent: Db.ActiveConnections =
      import scala.jdk.CollectionConverters.*
      Db.ActiveConnections.total(gated.asScala.map(Db.activeConnections))

    /** The first step of a restore, on the restore's own thread: new permits are refused from here on. `false` when a
      * restore holds the gate already.
      */
    def drain(): Boolean =
      lock.lock()
      try
        if stage != Stage.Open then false
        else
          stage = Stage.Draining
          holder = Some(Thread.currentThread())
          true
      finally lock.unlock()

    /** Wait until every permit out has come back, or `deadline`: the permits still out (none when it returns in time).
      * The restore's own thread does not count its own.
      */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def awaitPermits(deadline: Deadline): Outstanding =
      val own = if current.get().exists(p => p.live && p.kind == Kind.Operation) then 1 else 0
      lock.lock()
      try
        while outstanding > own && deadline.hasTimeLeft() do
          val _ = changed.awaitNanos(math.max(1L, deadline.timeLeft.toNanos))
        Outstanding(outstanding - own)
      finally lock.unlock()

    /** No connection is handed out any more but to the restore's own thread. */
    def close(): Unit =
      lock.lock()
      try stage = Stage.Closed
      finally lock.unlock()

    /** The restore runs now: every unit of work begun before is stale from here on, after the restore too. Called only
      * once nothing is lent out, so that a restore given up before it ran fences nothing off.
      */
    def fence(): Unit =
      lock.lock()
      try epoch = epoch.next
      finally lock.unlock()

    /** Wait until the connections lent before [[close]] have come back, or `deadline`: how many are still out. */
    def awaitIdle(deadline: Deadline): Db.ActiveConnections =
      while !lent.none && deadline.hasTimeLeft() do Thread.sleep(IdlePollMs)
      lent

    /** The restore has ended (done, failed or given up): permits and connections are handed out again. */
    def reopen(): Unit =
      lock.lock()
      try
        stage = Stage.Open
        holder = None
        changed.signalAll()
      finally lock.unlock()

    /** A data source whose every connection is admitted by the gate first. */
    final private class Gated(ds: DataSource) extends DataSource:
      def getConnection(): Connection =
        admit()
        ds.getConnection()
      def getConnection(username: String, password: String): Connection =
        admit()
        ds.getConnection(username, password)
      def getLogWriter: PrintWriter = ds.getLogWriter
      def setLogWriter(out: PrintWriter): Unit = ds.setLogWriter(out)
      def setLoginTimeout(seconds: Int): Unit = ds.setLoginTimeout(seconds)
      def getLoginTimeout: Int = ds.getLoginTimeout
      def getParentLogger: Logger = ds.getParentLogger
      def unwrap[T](iface: Class[T]): T = if iface.isInstance(this) then iface.cast(this) else ds.unwrap(iface)
      def isWrapperFor(iface: Class[?]): Boolean = iface.isInstance(this) || ds.isWrapperFor(iface)
      override def toString = s"Gated($ds)"

  /** How often [[Gate.awaitIdle]] looks at the lent connections. */
  private val IdlePollMs = 20L
