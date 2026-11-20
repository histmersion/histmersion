package com.histmersion
package util.log

import java.io.PrintWriter
import java.sql.Connection
import java.util.logging.Logger
import javax.sql.DataSource
import scala.util.Using

/** The server's connections, each told the calling thread's trace and label (`histmersion.trace`,
  * `histmersion.trace_label`) before it is handed out: the database's write triggers record them with every write
  * statement. A physical connection is told only when they differ from what it was told last, so a pool's connection
  * reused by the same trace, or by untraced work, costs no round trip.
  */
final class TracedDataSource(underlying: DataSource) extends DataSource:
  private val told = java.util.WeakHashMap[AnyRef, (String, String)]()

  def getConnection(): Connection = tell(underlying.getConnection())
  def getConnection(username: String, password: String): Connection = tell(underlying.getConnection(username, password))

  private def tell(c: Connection): Connection =
    val scope = Tracing.scope
    val wanted = (scope.fold("")(_.trace.value), scope.flatMap(_.label).fold("")(_.value))
    val physical: AnyRef =
      if c.isWrapperFor(classOf[org.postgresql.PGConnection]) then c.unwrap(classOf[org.postgresql.PGConnection])
      else c
    val known = told.synchronized(Option(told.get(physical)))
    if !known.contains(wanted) then
      try
        Using.resource(
          c.prepareStatement(
            "SELECT set_config('histmersion.trace', ?, false), set_config('histmersion.trace_label', ?, false)"
          )
        ) { s =>
          s.setString(1, wanted._1)
          s.setString(2, wanted._2)
          Using.resource(s.executeQuery())(_ => ())
        }
        val _ = told.synchronized(told.put(physical, wanted))
      catch
        case e: java.sql.SQLException =>
          c.close()
          throw e
    c

  def getLogWriter: PrintWriter = underlying.getLogWriter
  def setLogWriter(out: PrintWriter): Unit = underlying.setLogWriter(out)
  def setLoginTimeout(seconds: Int): Unit = underlying.setLoginTimeout(seconds)
  def getLoginTimeout: Int = underlying.getLoginTimeout
  def getParentLogger: Logger = underlying.getParentLogger
  def unwrap[T](iface: Class[T]): T = if iface.isInstance(this) then iface.cast(this) else underlying.unwrap(iface)
  def isWrapperFor(iface: Class[?]): Boolean = iface.isInstance(this) || underlying.isWrapperFor(iface)
