package com.histmersion
package util.database

import com.histmersion.util.problem.{External, Problem}

import com.histmersion.config.DbConfig

import java.sql.Connection
import scala.util.control.NonFatal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** One server per database: a session-level advisory lock, held on a connection of its own (never a pooled one, which
  * the pool may close and so release it) for as long as the server runs on the database. A second server finds it held
  * and refuses to start, before it migrates, reconciles or starts any worker; a server that ends, or whose connection
  * dies, gives it up with its session.
  *
  * Advisory locks belong to one database, so servers on other databases of the same cluster never meet here; the key is
  * a pair of 32-bit numbers, a space apart from the one-number keys the transaction locks take.
  */
final class InstanceLock private (connection: Connection):
  import InstanceLock.ValidSeconds

  /** Whether the session that holds the lock is still there (a dead connection has given it up). */
  def alive: Boolean =
    try connection.isValid(ValidSeconds)
    catch case NonFatal(_) => false

  /** Give it up: the session ends, and the lock with it. */
  def release(): Unit =
    try connection.close()
    catch case NonFatal(e) => scribe.warn(s"instance lock: closing its session failed: ${e.getMessage}")

object InstanceLock:

  /** Who holds the lock, as its session names itself in `pg_stat_activity` (`histmersion-server host:pid`). */
  type Holder = Holder.T
  object Holder extends Newtype[String, Pure]:
    /** This process: its host and process id. Two servers in one JVM (a test) have the same name; they are still two
      * holders ([[Holds]]), and the second is refused.
      */
    def thisProcess(): Holder =
      val host =
        try java.net.InetAddress.getLocalHost.getHostName
        catch case NonFatal(_) => "localhost"
      Holder(s"histmersion-server $host:${ProcessHandle.current().pid()}")

    extension (h: Holder) def application: Db.ApplicationName = Db.ApplicationName(h.value)

  /** The advisory lock's key: Histmersion's (the letters HSTM), and the server's within it. */
  private val Space = 0x4853544d
  private val Server = 1

  private val ValidSeconds = 5

  /** Take the lock on `db`'s database as `holder`: `Left` with [[Problem.Conflict.ServerHeld]] naming the session that
    * holds it, or with the database's own error when it cannot be reached. Changes nothing in the database either way.
    */
  def take(db: DbConfig, holder: Holder): Either[Problem, InstanceLock] =
    val opened =
      try Right(Db.session(db, holder.application))
      catch case NonFatal(e) => Left(Problem.Io.Database(Problem.Io.DbOp.Connect, External.Reported.of(e)))
    opened.flatMap { c =>
      val taken =
        try
          if tryLock(c) then Right(new InstanceLock(c))
          else Left(Problem.Conflict.ServerHeld(holderOf(c)))
        catch case NonFatal(e) => Left(Problem.Io.Database(Problem.Io.DbOp.Query, External.Reported.of(e)))
      taken.left.foreach(_ =>
        try c.close()
        catch case NonFatal(_) => ()
      )
      taken
    }

  private def tryLock(c: Connection): Boolean =
    val st = c.prepareStatement("SELECT pg_try_advisory_lock(?, ?)")
    try
      st.setInt(1, Space)
      st.setInt(2, Server)
      val rs = st.executeQuery()
      rs.next() && rs.getBoolean(1)
    finally st.close()

  /** The session that holds the lock on `c`'s database: its name, address, process and connection time. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def holderOf(c: Connection): Problem.Raw =
    val st = c.prepareStatement(
      """SELECT coalesce(nullif(a.application_name, ''), 'a session with no name') || ', Postgres session ' || a.pid ||
                ' from ' || coalesce(host(a.client_addr), 'a local socket') || ', connected since ' ||
                to_char(a.backend_start AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
           FROM pg_locks l JOIN pg_stat_activity a ON a.pid = l.pid
          WHERE l.locktype = 'advisory' AND l.granted AND l.objsubid = 2
            AND l.classid::bigint = ? AND l.objid::bigint = ?
            AND l.database = (SELECT oid FROM pg_database WHERE datname = current_database())"""
    )
    try
      st.setLong(1, Space.toLong)
      st.setLong(2, Server.toLong)
      val rs = st.executeQuery()
      Problem.Raw(if rs.next() then rs.getString(1) else "a session that has ended since")
    finally st.close()

  /** The locks one server holds, one per database, shared by everything in that server that asks for the same database
    * (the bundled runtime's start and the database node; a config reload that rebuilds the node on the same database):
    * the lock is taken by the first [[hold]] and given up when the last hold is released. One per server, never a
    * global: two servers in one JVM (a test) are two holders, and the second is refused.
    */
  final class Holds(val holder: Holder):
    private val held = scala.collection.mutable.Map.empty[DbConfig.Url, (InstanceLock, Int)]

    /** A hold on `db`'s database: the lock this server has there already, or newly taken. */
    def hold(db: DbConfig): Either[Problem, Hold] = synchronized {
      held.get(db.url) match
        case Some((lock, n)) =>
          held.update(db.url, (lock, n + 1))
          Right(new Hold(this, db.url))
        case None =>
          take(db, holder).map { lock =>
            held.update(db.url, (lock, 1))
            new Hold(this, db.url)
          }
    }

    /** Whether this server holds the lock on `db`'s database, on a live session. */
    def holds(db: DbConfig): Boolean = synchronized(held.get(db.url).exists(_._1.alive))

    /** Take the lock on `db`'s database again when its session died (the database restarted, the network dropped):
      * `Left` when another server took it meanwhile. Nothing when the session is alive or no hold asks for it.
      */
    def renew(db: DbConfig): Either[Problem, Unit] = synchronized {
      held.get(db.url) match
        case Some((lock, n)) if !lock.alive =>
          lock.release()
          take(db, holder).map(fresh => held.update(db.url, (fresh, n)))
        case _ => Right(())
    }

    private[InstanceLock] def let(url: DbConfig.Url): Unit = synchronized {
      held.get(url).foreach { (lock, n) =>
        if n > 1 then held.update(url, (lock, n - 1))
        else
          held.remove(url)
          lock.release()
      }
    }

  /** One hold on a database's lock; [[release]] once (a second release does nothing). */
  final class Hold private[InstanceLock] (holds: Holds, url: DbConfig.Url):
    private val released = java.util.concurrent.atomic.AtomicBoolean(false)
    def release(): Unit = if released.compareAndSet(false, true) then holds.let(url)
