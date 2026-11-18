package com.histmersion
package domain.automation.agents

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure

/** The agent lease (V087): one agent server per database, the one that holds the named lease. Every statement runs on
  * the connection it is given (`AgentLease` opens it).
  */
object LeaseRepo:
  type LeaseExpiry = LeaseExpiry.T
  object LeaseExpiry extends Newtype[String, Pure]
  type LeaseName = LeaseName.T
  object LeaseName extends Newtype[String, Pure]
  type LeaseOwner = LeaseOwner.T
  object LeaseOwner extends Newtype[String, Pure]
  type TtlSeconds = TtlSeconds.T
  object TtlSeconds extends Newtype[Int, Pure]

  private val ts = sql"""'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""

  /** Claim (or renew) lease `name` for `owner` for `ttlSeconds`: it is taken when free, expired, or already ours.
    * `true` = we hold it now.
    */
  def claim(name: LeaseName, owner: LeaseOwner, ttlSeconds: TtlSeconds)(using DbCon): Boolean =
    sql"""INSERT INTO agent_leases (name, owner, until) VALUES ($name, $owner, now() + make_interval(secs => $ttlSeconds))
          ON CONFLICT (name) DO UPDATE SET owner = EXCLUDED.owner, until = EXCLUDED.until
          WHERE agent_leases.until < now() OR agent_leases.owner = EXCLUDED.owner
          RETURNING owner""".query[String].run().nonEmpty

  /** Give it up (a clean stop), so the next server need not wait for it to expire. */
  def release(name: LeaseName, owner: LeaseOwner)(using DbCon): Unit =
    val _ = sql"DELETE FROM agent_leases WHERE name = $name AND owner = $owner".update.run()

  /** Who holds it, until when, and whether that is in the past. */
  def holder(name: LeaseName)(using DbCon): Option[(LeaseOwner, LeaseExpiry, Boolean)] =
    sql"SELECT owner, to_char(until AT TIME ZONE 'UTC', $ts), until < now() FROM agent_leases WHERE name = $name"
      .query[(String, String, Boolean)]
      .run()
      .headOption
      .map((owner, until, expired) => (LeaseOwner(owner), LeaseExpiry(until), expired))
