package com.histmersion
package util.database

import com.augustnagro.magnum.{connect as connectTo, transact as transactOn, DbCon}
import com.histmersion.util.database.Transactor.{onConnection, TimeoutMs}
import java.util.concurrent.{CompletableFuture, ExecutionException, Executor}
import javax.sql.DataSource
import scala.annotation.nowarn
import scala.util.Try
import io.github.iltotore.iron.constraint.any.Pure

/** The database's entry points, split by what they may do. A read needs nothing; a write needs a [[WritePermit]], so
  * writing without one does not compile (X4 / G89): the permit comes from the maintenance gate's `guard` (an operation
  * a restore waits for) or from a registered worker's unit (one a restore quiesces and fences off).
  */
trait Transactor:
  /** `work` on a connection of its own, in autocommit mode: reads (and the named exceptions that write a cache or a log
    * line on the way).
    */
  def read[A](work: DbCon ?=> A): A

  /** `work` in one transaction that is rolled back at its end: a consistent snapshot to read (the caller may make it
    * `REPEATABLE READ, READ ONLY`); nothing it does is kept.
    */
  def snapshot[A](work: DbCon ?=> A): A = read(onConnection.rollback(work))

  /** `work` in one transaction, committed at its end, under `permit`. A restore waits for an operation's permit to come
    * back and refuses a worker's while it holds the database ([[WritePermit.Gate]]).
    */
  def write[A](work: DbCon ?=> A)(using permit: WritePermit): A =
    permit.require()
    transact(work)

  /** `work` on a connection in autocommit mode, each statement its own transaction (a `VACUUM`, an import in steps),
    * under `permit`.
    */
  def writeAutocommit[A](work: DbCon ?=> A)(using permit: WritePermit): A =
    permit.require()
    read(onConnection.autocommit(work))

  def readOnlyRollback[A](timeoutMs: TimeoutMs)(work: DbCon ?=> A): A =
    read(onConnection.readOnlyRollback(timeoutMs)(work))

  def readToCompletion[A](work: DbCon ?=> A): A =
    read {
      val con = summon[DbCon]
      val running = CompletableFuture.supplyAsync(() => work(using con), Transactor.OwnThread)
      try running.get()
      catch
        case interrupted: InterruptedException =>
          val _ = Try(running.join())
          throw interrupted
        case failed: ExecutionException => throw failed.getCause
    }

  /** One committed transaction, with no permit asked: only [[write]] reaches it. */
  protected def transact[A](work: DbCon ?=> A): A

object Transactor:
  type TimeoutMs = TimeoutMs.T
  object TimeoutMs extends Newtype[Int, Pure]

  def apply(dataSource: DataSource): Transactor = Implementation(dataSource)

  private val OwnThread: Executor = task =>
    val _ = Thread.ofVirtual().start(task)
  def onConnection(using con: DbCon): Session = new Session(con)

  final class Session private[Transactor] (con: DbCon):
    /** `work` on this connection in autocommit mode, under `permit` (see [[Transactor.writeAutocommit]]). */
    def writeAutocommit[A](work: DbCon ?=> A)(using permit: WritePermit): A =
      permit.require()
      autocommit(work)

    private[Transactor] def autocommit[A](work: DbCon ?=> A): A =
      require(con.connection.getAutoCommit, "Autocommit work cannot borrow an active transaction")
      work(using con)

    /** One committed transaction on this connection, under `permit` (see [[Transactor.write]]). */
    def write[A](work: DbCon ?=> A)(using permit: WritePermit): A =
      permit.require()
      run(true, None)(work)
    def rollback[A](work: DbCon ?=> A): A = run(false, None)(work)
    def readOnlyRollback[A](timeoutMs: TimeoutMs)(work: DbCon ?=> A): A = run(false, Some(timeoutMs))(work)

    private def run[A](commit: Boolean, readOnlyTimeout: Option[TimeoutMs])(work: DbCon ?=> A): A =
      val jdbc = con.connection
      require(jdbc.getAutoCommit, "A scoped transaction cannot borrow an active transaction")
      val wasReadOnly = jdbc.isReadOnly
      var transactionOpen = false
      var primary: Throwable = null
      try
        if readOnlyTimeout.nonEmpty then jdbc.setReadOnly(true)
        transactionOpen = true
        jdbc.setAutoCommit(false)
        readOnlyTimeout.foreach { timeout =>
          scala.util.Using.resource(jdbc.createStatement()) { statement =>
            val _ = statement.execute("SET TRANSACTION READ ONLY")
            val _ = statement.execute(s"SET LOCAL statement_timeout = ${math.max(1, timeout.value)}")
          }
        }
        val result = work(using con)
        if commit then
          jdbc.commit()
          transactionOpen = false
        result
      catch
        // records and rethrows: every Throwable, or a fatal one would be replaced by a cleanup failure below
        case error: (Throwable @nowarn("id=E235")) =>
          primary = error
          throw error
      finally
        var cleanup: Throwable = null
        def attempt(action: => Unit): Boolean =
          try
            action
            true
          catch
            // collected, then rethrown or suppressed on the primary: every Throwable, so each cleanup step still runs
            case error: (Throwable @nowarn("id=E235")) =>
              if cleanup == null then cleanup = error
              else if cleanup ne error then cleanup.addSuppressed(error)
              false
        val rolledBack = !transactionOpen || attempt(jdbc.rollback())
        if rolledBack then
          val _ = attempt(jdbc.setReadOnly(wasReadOnly))
          val _ = attempt(jdbc.setAutoCommit(true))
        if !rolledBack || cleanup != null then
          val _ = attempt(jdbc.close())
        if cleanup != null then
          if primary == null then throw cleanup
          else if primary ne cleanup then primary.addSuppressed(cleanup)

  final private class Implementation(dataSource: DataSource) extends Transactor:
    def read[A](work: DbCon ?=> A): A = connectTo(dataSource)(work)
    protected def transact[A](work: DbCon ?=> A): A = transactOn(dataSource)(work)
