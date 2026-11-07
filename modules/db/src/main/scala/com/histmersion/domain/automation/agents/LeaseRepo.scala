package com.histmersion.domain.automation.agents

import com.histmersion.util.database.{Param, Sql}

import com.augustnagro.magnum.*

/** The agent lease (V087): one agent server per database, the one that holds the named lease. Every statement runs on
  * the connection it is given (`AgentLease` opens it).
  */
object LeaseRepo:
  type LeaseExpiry = LeaseExpiry.T
  object LeaseExpiry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeaseName = LeaseName.T
  object LeaseName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeaseOwner = LeaseOwner.T
  object LeaseOwner extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TtlSeconds = TtlSeconds.T
  object TtlSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  private val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""

  /** Claim (or renew) lease `name` for `owner` for `ttlSeconds`: it is taken when free, expired, or already ours.
    * `true` = we hold it now.
    */
  def claim(name: com.histmersion.domain.automation.agents.LeaseRepo.LeaseName, owner: com.histmersion.domain.automation.agents.LeaseRepo.LeaseOwner, ttlSeconds: com.histmersion.domain.automation.agents.LeaseRepo.TtlSeconds)(using DbCon): Boolean =
    sql"""INSERT INTO agent_leases (name, owner, until) VALUES ($name, $owner, now() + make_interval(secs => $ttlSeconds))
          ON CONFLICT (name) DO UPDATE SET owner = EXCLUDED.owner, until = EXCLUDED.until
          WHERE agent_leases.until < now() OR agent_leases.owner = EXCLUDED.owner
          RETURNING owner""".query[String].run().nonEmpty

  /** Give it up (a clean stop), so the next server need not wait for it to expire. */
  def release(name: com.histmersion.domain.automation.agents.LeaseRepo.LeaseName, owner: com.histmersion.domain.automation.agents.LeaseRepo.LeaseOwner)(using DbCon): Unit =
    val _ = sql"DELETE FROM agent_leases WHERE name = $name AND owner = $owner".update.run()

  /** Who holds it, until when, and whether that is in the past. */
  def holder(name: com.histmersion.domain.automation.agents.LeaseRepo.LeaseName)(using DbCon): Option[(com.histmersion.domain.automation.agents.LeaseRepo.LeaseOwner, com.histmersion.domain.automation.agents.LeaseRepo.LeaseExpiry, Boolean)] =
    Sql
      .of(
        s"SELECT owner, to_char(until AT TIME ZONE 'UTC', $ts), until < now() FROM agent_leases WHERE name = ?",
        Param.S(name)
      )
      .frag
      .query[(String, String, Boolean)]
      .run()
      .headOption
