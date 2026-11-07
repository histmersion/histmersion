package com.histmersion.util.database

import com.augustnagro.magnum.{connect as connectTo, transact as transactOn, DbCon}
import javax.sql.DataSource

trait Transactor:
  def read[A](work: DbCon ?=> A): A
  def transact[A](work: DbCon ?=> A): A
  def autocommit[A](work: DbCon ?=> A): A = read(Transactor.onConnection.autocommit(work))
  def readOnlyRollback[A](timeoutMs: com.histmersion.util.database.Transactor.TimeoutMs)(work: DbCon ?=> A): A =
    read(Transactor.onConnection.readOnlyRollback(timeoutMs)(work))

object Transactor:
  type TimeoutMs = TimeoutMs.T
  object TimeoutMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(dataSource: DataSource): Transactor = Implementation(dataSource)
  def onConnection(using con: DbCon): Session = new Session(con)

  final class Session private[Transactor] (con: DbCon):
    def autocommit[A](work: DbCon ?=> A): A =
      require(con.connection.getAutoCommit, "Autocommit work cannot borrow an active transaction")
      work(using con)

    def transact[A](work: DbCon ?=> A): A = run(true, None)(work)
    def rollback[A](work: DbCon ?=> A): A = run(false, None)(work)
    def readOnlyRollback[A](timeoutMs: com.histmersion.util.database.Transactor.TimeoutMs)(work: DbCon ?=> A): A = run(false, Some(timeoutMs))(work)

    private def run[A](commit: Boolean, readOnlyTimeout: Option[com.histmersion.util.database.Transactor.TimeoutMs])(work: DbCon ?=> A): A =
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
        case error: Throwable =>
          primary = error
          throw error
      finally
        var cleanup: Throwable = null
        def attempt(action: => Unit): Boolean =
          try
            action
            true
          catch
            case error: Throwable =>
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
    def transact[A](work: DbCon ?=> A): A = transactOn(dataSource)(work)