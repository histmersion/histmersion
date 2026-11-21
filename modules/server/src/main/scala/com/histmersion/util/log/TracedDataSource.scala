package com.histmersion
package util.log

import com.augustnagro.magnum.DbCodec
import com.histmersion.api.log.LogEvent
import com.histmersion.util.database.NewtypeColumns.given

import java.io.PrintWriter
import java.sql.Connection
import java.util.logging.Logger
import javax.sql.DataSource
import scala.util.Using

final class TracedDataSource(underlying: DataSource) extends DataSource:
  private val told = java.util.WeakHashMap[AnyRef, (Option[LogEvent.Trace], Option[LogEvent.Label])]()

  def getConnection(): Connection = tell(underlying.getConnection())
  def getConnection(username: String, password: String): Connection = tell(underlying.getConnection(username, password))

  private def tell(c: Connection): Connection =
    val scope = TraceScope.current
    val wanted = (scope.map(_.trace), scope.flatMap(_.label))
    val physical: AnyRef =
      if c.isWrapperFor(classOf[org.postgresql.PGConnection]) then c.unwrap(classOf[org.postgresql.PGConnection])
      else c
    val known = told.synchronized(Option(told.get(physical)))
    if !known.contains(wanted) then
      try
        Using.resource(
          c.prepareStatement(
            "SELECT set_config('histmersion.trace', coalesce(?, ''), false), " +
              "set_config('histmersion.trace_label', coalesce(?, ''), false)"
          )
        ) { s =>
          summon[DbCodec[Option[LogEvent.Trace]]].writeSingle(wanted._1, s, 1)
          summon[DbCodec[Option[LogEvent.Label]]].writeSingle(wanted._2, s, 2)
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
