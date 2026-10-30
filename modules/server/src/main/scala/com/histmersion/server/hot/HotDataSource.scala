package com.histmersion.server.hot

import java.io.PrintWriter
import java.sql.Connection
import java.util.logging.Logger
import javax.sql.DataSource

/** A `DataSource` whose every `getConnection` comes from the pool a [[Hot]] handle holds NOW — the pool of the
  * generation the calling thread is pinned to, else the current one. So the 74 places that take a `DataSource` keep
  * taking one, and a swapped database needs none of them to reload:
  *
  *   - a connection taken before a swap stays on its pool until it is closed, and the old pool is closed only when its
  *     last connection came back (the pool node's `idle` check);
  *   - work pinned to a generation (an HTTP request, a job, an agent run) gets every connection from that generation's
  *     pool, so it finishes on the database it started on;
  *   - unpinned work (a manager's tick, the lease renewal) moves to the new pool at its next step.
  *
  * Taking the connection is itself pinned, so a pool cannot be closed between choosing it and borrowing from it.
  * Considered and not taken: `Hot[DataSource]` in every repo signature (74 files, and every call site wrapped in `use`
  * for no gain — the pin is already per request or per job), and a delegating `Connection` (more surface, and the
  * pool's own active count already says when a retired pool is free).
  */
final class HotDataSource[A](pool: Hot[A])(of: A => DataSource) extends DataSource:
  private def now: DataSource = of(pool.peek)
  def getConnection(): Connection = pool.use(a => of(a).getConnection())
  def getConnection(username: String, password: String): Connection =
    pool.use(a => of(a).getConnection(username, password))

  def getLogWriter: PrintWriter = now.getLogWriter
  def setLogWriter(out: PrintWriter): Unit = now.setLogWriter(out)
  def setLoginTimeout(seconds: Int): Unit = now.setLoginTimeout(seconds)
  def getLoginTimeout: Int = now.getLoginTimeout
  def getParentLogger: Logger = now.getParentLogger
  def unwrap[T](iface: Class[T]): T =
    if iface.isInstance(this) then iface.cast(this) else now.unwrap(iface)
  def isWrapperFor(iface: Class[?]): Boolean = iface.isInstance(this) || now.isWrapperFor(iface)

  override def toString = s"HotDataSource(${pool.name})"
