package com.histmersion.server.agents

import com.histmersion.db.ManagerRepo

import javax.sql.DataSource

/** One agent server per database (docs/research-process-managers.md §2): the server that holds the
  * lease `agents` runs the engine, the scheduler and the managers; another one pointed at the same
  * database runs none of them, and takes over when the holder's lease runs out (it renews it every
  * `ttl / 3`; the ttl is 3 × the managers' tick). `owner` names this PROCESS (host and pid): two
  * servers in one JVM (a test suite) share it; two processes never do. */
final class AgentLease(ds: DataSource, ttlSeconds: Int, val owner: String = AgentLease.ownerId()):

  @volatile private var held = false
  @volatile private var thread: Option[Thread] = None
  @volatile private var stopping = false

  def isHeld: Boolean = held

  /** Has [[start]] run (agents on)? Only then is the lease kept — and claimed again on a swapped
    * database. */
  def started: Boolean = thread.isDefined

  /** A swapped database (docs/research-plugins.md §3.6): claim the lease on `target` — the new pool,
    * before the swap is published — when this server keeps it at all. `true` = held there. The renewal
    * thread goes on renewing it through the server's DataSource, which then is the new pool. */
  def claimOn(target: DataSource): Boolean =
    if !started then false
    else
      val ok =
        try ManagerRepo.claim(target, AgentLease.Name, owner, ttlSeconds)
        catch
          case e: Exception =>
            scribe.warn(s"agent lease: could not claim it on the new database: ${e.getMessage}")
            false
      held = ok
      ok

  /** Give the lease up on `target` (an old database, when its generation closes) if this server holds
    * it there; nothing otherwise. */
  def releaseOn(target: DataSource): Unit =
    if started then
      try ManagerRepo.release(target, AgentLease.Name, owner)
      catch case e: Exception => scribe.warn(s"agent lease: could not release it on the old database: ${e.getMessage}")

  /** Claim or renew now; `true` = held. A database error is a lost renewal, logged. */
  def claim(): Boolean =
    val ok =
      try ManagerRepo.claim(ds, AgentLease.Name, owner, ttlSeconds)
      catch
        case e: Exception =>
          scribe.warn(s"agent lease: could not claim or renew: ${e.getMessage}")
          false
    if held && !ok then scribe.error(s"agent lease: LOST to ${holderText} — another server may now run agents on this database")
    held = ok
    ok

  def holderText: String =
    try ManagerRepo.holder(ds, AgentLease.Name).fold("nobody")((o, until, _) => s"$o until $until")
    catch case _: Exception => "unknown"

  /** Keep the lease: claim now and every ttl/3. `onAcquired` runs (once) on the renewing thread the
    * first time the lease is held — at once when the first claim wins. */
  def start(onAcquired: () => Unit): Boolean =
    val first = claim()
    if first then onAcquired()
    else scribe.warn(s"agent lease held by $holderText: this server runs no agents until it is free")
    if thread.isEmpty then
      stopping = false
      val every = math.max(1000L, ttlSeconds.toLong * 1000L / 3)
      val t = Thread.ofPlatform().daemon(true).name("histmersion-agent-lease").unstarted { () =>
        var started = first // the once-only flag of onAcquired; confined to this thread after start
        try
          while !stopping do
            Thread.sleep(every)
            if claim() && !started then
              started = true
              scribe.info(s"agent lease acquired by $owner: starting the agents")
              onAcquired()
        catch case _: InterruptedException => ()
      }
      thread = Some(t)
      t.start()
    first

  def stop(): Unit =
    stopping = true
    thread.foreach(_.interrupt())
    thread = None
    if held then
      try ManagerRepo.release(ds, AgentLease.Name, owner) catch case _: Exception => ()
      held = false

  /** For About and the doctor: (holder, until, expired, held by this server). */
  def status: Option[(String, String, Boolean, Boolean)] =
    try ManagerRepo.holder(ds, AgentLease.Name).map((o, u, expired) => (o, u, expired, o == owner))
    catch case _: Exception => None

object AgentLease:
  val Name = "agents"

  def ownerId(): String =
    val host = try java.net.InetAddress.getLocalHost.getHostName catch case _: Exception => "localhost"
    s"$host:${ProcessHandle.current().pid()}"
