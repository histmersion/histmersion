package com.histmersion.domain.automation.agents

import com.histmersion.util.database.{Param, Sql}

import com.augustnagro.magnum.*

/** The agent lease (V087): one agent server per database, the one that holds the named lease. Every statement runs on
  * the connection it is given (`AgentLease` opens it).
  */
object LeaseRepo:

  type ClaimResult = ClaimResult.T
  object ClaimResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClaimName = ClaimName.T
  object ClaimName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClaimOwner = ClaimOwner.T
  object ClaimOwner extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClaimTtlSeconds = ClaimTtlSeconds.T
  object ClaimTtlSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReleaseName = ReleaseName.T
  object ReleaseName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReleaseOwner = ReleaseOwner.T
  object ReleaseOwner extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HolderResultPart1 = HolderResultPart1.T
  object HolderResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HolderResultPart2 = HolderResultPart2.T
  object HolderResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HolderResultPart3 = HolderResultPart3.T
  object HolderResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HolderName = HolderName.T
  object HolderName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""

  /** Claim (or renew) lease `name` for `owner` for `ttlSeconds`: it is taken when free, expired, or already ours.
    * `true` = we hold it now.
    */
  def claim(name: com.histmersion.domain.automation.agents.LeaseRepo.ClaimName, owner: com.histmersion.domain.automation.agents.LeaseRepo.ClaimOwner, ttlSeconds: com.histmersion.domain.automation.agents.LeaseRepo.ClaimTtlSeconds)(using DbCon): com.histmersion.domain.automation.agents.LeaseRepo.ClaimResult =
    sql"""INSERT INTO agent_leases (name, owner, until) VALUES ($name, $owner, now() + make_interval(secs => $ttlSeconds))
          ON CONFLICT (name) DO UPDATE SET owner = EXCLUDED.owner, until = EXCLUDED.until
          WHERE agent_leases.until < now() OR agent_leases.owner = EXCLUDED.owner
          RETURNING owner""".query[String].run().nonEmpty

  /** Give it up (a clean stop), so the next server need not wait for it to expire. */
  def release(name: com.histmersion.domain.automation.agents.LeaseRepo.ReleaseName, owner: com.histmersion.domain.automation.agents.LeaseRepo.ReleaseOwner)(using DbCon): Unit =
    val _ = sql"DELETE FROM agent_leases WHERE name = $name AND owner = $owner".update.run()

  /** Who holds it, until when, and whether that is in the past. */
  def holder(name: com.histmersion.domain.automation.agents.LeaseRepo.HolderName)(using DbCon): Option[(com.histmersion.domain.automation.agents.LeaseRepo.HolderResultPart1, com.histmersion.domain.automation.agents.LeaseRepo.HolderResultPart2, com.histmersion.domain.automation.agents.LeaseRepo.HolderResultPart3)] =
    Sql
      .of(
        s"SELECT owner, to_char(until AT TIME ZONE 'UTC', $ts), until < now() FROM agent_leases WHERE name = ?",
        Param.S(name)
      )
      .frag
      .query[(String, String, Boolean)]
      .run()
      .headOption
