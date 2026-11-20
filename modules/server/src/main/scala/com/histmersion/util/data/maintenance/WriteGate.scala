package com.histmersion
package util.data.maintenance

import com.histmersion.api.backup.Backup
import com.histmersion.api.Api
import com.histmersion.util.database.WritePermit
import com.histmersion.util.jobs.{Quiescent, Workers}
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.Problem

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.FiniteDuration
import io.github.iltotore.iron.constraint.any.Pure

/** The maintenance lock. A restore drops and recreates every table, so nothing else may touch the database while it
  * runs, and nothing begun before it may write after it:
  *
  *   - a write runs through [[guard]], which holds a [[WritePermit]] for its whole block (the permit `Transactor.write`
  *     takes); while a restore holds the gate a write is answered 503;
  *   - [[exclusively]] stops new permits, has every registered worker ([[workers]]) come to rest, waits for the permits
  *     out and then for every connection lent out, and only then runs the restore, alone: the server's data sources
  *     refuse every other thread a connection until it ends;
  *   - work a worker began before the restore and still runs after it is stale: it gets no permit and no connection.
  *
  * One instance per server rather than a global, so two servers in one JVM (which is what a test run is) do not freeze
  * each other's writes. One server per database is enforced at start (`InstanceLock`), so this gate covers every writer
  * of the database.
  */
final class WriteGate(val permits: WritePermit.Gate = WritePermit.Gate()):
  import WriteGate.{refusal, undrained, OperationName, Quiet}

  /** The server's long-running workers, as a restore holds them. */
  val workers: Workers = Workers(permits)

  def active: Boolean = permits.active

  /** Run `f` holding a write permit, unless a restore holds the gate (answered 503) or `f` belongs to work begun before
    * the last restore. A restore waits for `f` to finish before it starts.
    */
  def guard[A](op: OperationName)(f: WritePermit ?=> Either[Api.Error, A]): Either[Api.Error, A] =
    permits.permit(f).fold(r => Left(refusal(op, r)), identity)

  /** Run `f` holding the whole database: a second concurrent attempt is refused rather than queued. Once no new write
    * gets a permit, `refused` may still turn the operation down (work it must not run beside, which can no longer start
    * now). Before `f` runs, every worker is asked to come to rest and the writes and connections in flight are waited
    * for, up to `drain` in all; when writes or connections are still out then, nothing runs and the answer names them
    * ([[Problem.Conflict.Undrained]]). A worker still at work then is fenced off and named in what `f` is given. After
    * `f`, every worker reconciles with what is in the database now (once: `f` may do it first, through
    * [[WriteGate.Quiet.reconcile]], to report it), and takes work again.
    */
  def exclusively[A](
      op: OperationName,
      drain: FiniteDuration = FiniteDuration(0L, java.util.concurrent.TimeUnit.SECONDS),
      refused: => Option[Api.Error] = None
  )(f: Quiet ?=> Either[Api.Error, A]): Either[Api.Error, A] =
    if !permits.drain() then Left(refusal(op, WritePermit.Refusal.Restoring))
    else
      try
        refused match
          case Some(no) => Left(no)
          case None     =>
            workers.quiesceAll()
            val deadline = drain.fromNow
            val restless = workers.awaitRest(deadline)
            val out = permits.awaitPermits(deadline)
            if !out.none then Left(undrained(Problem.Raw(s"$out guarded write(s) in flight"), restless, drain))
            else
              permits.close()
              val lent = permits.awaitIdle(deadline)
              if !lent.none then Left(undrained(Problem.Raw(s"$lent database connection(s) in use"), restless, drain))
              else
                permits.fence()
                val ran = new Quiet(restless, workers)
                try f(using ran)
                finally { val _ = ran.reconcile() }
      finally
        permits.reopen()
        workers.resumeAll()

object WriteGate:
  type OperationName = OperationName.T
  object OperationName extends Newtype[String, Pure]

  /** What a restore holding the gate finds: the workers still at work when the wait ran out (fenced off: they write
    * nothing from now on), and the reconciliation the gate runs after it.
    */
  final class Quiet private[WriteGate] (val restless: Vector[(Quiescent.Name, Quiescent.Busy)], workers: Workers):
    private val reconciled = AtomicBoolean(false)

    /** Every worker reconciles with the database as it is now; once (later calls answer nothing). The workers that
      * failed to, as problems (each logged).
      */
    def reconcile(): List[Problem] =
      if reconciled.compareAndSet(false, true) then workers.reconcileAll() else Nil

  private def refusal(op: OperationName, why: WritePermit.Refusal): Api.Error = why match
    case WritePermit.Refusal.Restoring => Backup.maintenance(s"$op")
    case WritePermit.Refusal.Stale     => Api.Error(why.problem, Some(Api.Error.Code(Backup.CodeMaintenance)))

  private def undrained(
      first: Problem.Raw,
      restless: Vector[(Quiescent.Name, Quiescent.Busy)],
      drain: FiniteDuration
  ): Api.Error =
    Api.Error(
      Problem.Conflict.Undrained(
        NonEmptyVector.of(first, restless.map((name, busy) => Problem.Raw(s"$name: $busy"))*),
        Problem.Count(drain.toSeconds)
      ),
      Some(Api.Error.Code(Backup.CodeMaintenance))
    )
