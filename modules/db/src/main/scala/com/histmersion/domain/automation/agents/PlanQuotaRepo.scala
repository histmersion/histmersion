package com.histmersion.domain.automation.agents
import com.histmersion.api.automation.PlanQuota.{PoolId, Reading, RefreshInfo, Source}

import com.augustnagro.magnum.*
import com.histmersion.util.database.given

/** The plan pools' readings, settings and refresh log (migrations V063–V065, V069). Stateless. */
object PlanQuotaRepo:

  // Times cross as epoch milliseconds (no timestamp codec in the native image): bound through
  // to_timestamp(ms / 1000.0), read back as (extract(epoch …) * 1000)::bigint.

  /** The newest reading per window replaces the one before (a reading is a snapshot, not a ledger). */
  type SetHoldHeld = SetHoldHeld.T
  object SetHoldHeld extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetFloorsFloors = SetFloorsFloors.T
  object SetFloorsFloors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetRefusalRefusal = SetRefusalRefusal.T
  object SetRefusalRefusal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetRefusalUntil = SetRefusalUntil.T
  object SetRefusalUntil extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetRefusalPlanType = SetRefusalPlanType.T
  object SetRefusalPlanType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FallthroughResultPart1 = FallthroughResultPart1.T
  object FallthroughResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FallthroughResultPart2 = FallthroughResultPart2.T
  object FallthroughResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetFallthroughRole = SetFallthroughRole.T
  object SetFallthroughRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetFallthroughOn = SetFallthroughOn.T
  object SetFallthroughOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogRefreshMode = LogRefreshMode.T
  object LogRefreshMode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogRefreshTrigger = LogRefreshTrigger.T
  object LogRefreshTrigger extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogRefreshOk = LogRefreshOk.T
  object LogRefreshOk extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogRefreshDetail = LogRefreshDetail.T
  object LogRefreshDetail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshesResultPart1 = RefreshesResultPart1.T
  object RefreshesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshesSinceMs = RefreshesSinceMs.T
  object RefreshesSinceMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def record(pool: PoolId, rs: List[Reading])(using DbCon): Unit =
    if rs.nonEmpty then
      val _ =
        rs.foreach { r =>
          val reset = r.resetsAt.map(_.toDouble / 1000)
          val seen = r.observedAt.toDouble / 1000
          val model = r.model.getOrElse("")
          sql"""INSERT INTO plan_quota_readings (pool, limit_id, kind, model, used_pct, resets_at, rejected, observed_at, source)
                VALUES (${pool.wire}, ${r.limit}, ${r.kind}, $model, ${r.usedPct}, to_timestamp($reset::float8), ${r.rejected},
                        to_timestamp($seen::float8), ${r.source.wire})
                ON CONFLICT (pool, limit_id, kind, model) DO UPDATE SET used_pct = EXCLUDED.used_pct,
                  resets_at = EXCLUDED.resets_at, rejected = EXCLUDED.rejected, observed_at = EXCLUDED.observed_at,
                  source = EXCLUDED.source
                WHERE plan_quota_readings.observed_at <= EXCLUDED.observed_at""".update.run()
        }

  def readings(pool: PoolId)(using DbCon): List[Reading] =
    sql"""SELECT limit_id, kind, model, used_pct, (extract(epoch FROM resets_at) * 1000)::bigint, rejected,
                 (extract(epoch FROM observed_at) * 1000)::bigint, source
          FROM plan_quota_readings WHERE pool = ${pool.wire} ORDER BY limit_id, kind"""
      .query[(String, String, String, Double, Option[Long], Boolean, Long, String)]
      .run()
      .toList
      .map { (l, k, m, u, r, rej, o, s) =>
        Reading(l, k, u, r, rej, o, Source.parse(s).getOrElse(Source.Run), Option(m).filter(_.nonEmpty))
      }

  /** What a person set on a pool and what the provider refused. */
  final case class Pool(
      held: Pool.Held,
      floors: Option[Pool.Floors],
      refusal: Option[Pool.Refusal],
      refusalUntil: Option[Pool.RefusalUntil],
      planType: Option[Pool.PlanType]
  )

  object Pool:
    type Held = Held.T
    object Held extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Floors = Floors.T
    object Floors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Refusal = Refusal.T
    object Refusal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefusalUntil = RefusalUntil.T
    object RefusalUntil extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlanType = PlanType.T
    object PlanType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val NoPool: Pool = Pool(false, None, None, None, None)

  def pool(p: PoolId)(using DbCon): Pool =
    sql"""SELECT held, floors, refusal, (extract(epoch FROM refusal_until) * 1000)::bigint, plan_type
          FROM plan_pools WHERE pool = ${p.wire}"""
      .query[(Boolean, Option[String], Option[String], Option[Long], Option[String])]
      .run()
      .headOption
      .map((h, f, r, u, t) => Pool(h, f, r, u, t))
      .getOrElse(NoPool)

  def setHold(p: PoolId, held: com.histmersion.domain.automation.agents.PlanQuotaRepo.SetHoldHeld)(using DbCon): Unit =
    val _ =
      sql"""INSERT INTO plan_pools (pool, held) VALUES (${p.wire}, $held)
            ON CONFLICT (pool) DO UPDATE SET held = EXCLUDED.held, updated_at = now()""".update.run()

  /** Saved floors; None drops them (config's apply again). */
  def setFloors(p: PoolId, floors: Option[com.histmersion.domain.automation.agents.PlanQuotaRepo.SetFloorsFloors])(using DbCon): Unit =
    val _ =
      sql"""INSERT INTO plan_pools (pool, floors) VALUES (${p.wire}, $floors)
            ON CONFLICT (pool) DO UPDATE SET floors = EXCLUDED.floors, updated_at = now()""".update.run()

  /** The provider's own refusal (None lifts it) and its plan type, as last read. */
  def setRefusal(p: PoolId, refusal: Option[com.histmersion.domain.automation.agents.PlanQuotaRepo.SetRefusalRefusal], until: Option[com.histmersion.domain.automation.agents.PlanQuotaRepo.SetRefusalUntil], planType: Option[com.histmersion.domain.automation.agents.PlanQuotaRepo.SetRefusalPlanType])(using DbCon): Unit =
    val u = until.map(_.toDouble / 1000)
    val _ =
      sql"""INSERT INTO plan_pools (pool, refusal, refusal_until, plan_type) VALUES (${p.wire}, $refusal, to_timestamp($u::float8), $planType)
            ON CONFLICT (pool) DO UPDATE SET refusal = EXCLUDED.refusal, refusal_until = EXCLUDED.refusal_until,
              plan_type = COALESCE(EXCLUDED.plan_type, plan_pools.plan_type), updated_at = now()""".update.run()

  def fallthrough(using DbCon): Map[com.histmersion.domain.automation.agents.PlanQuotaRepo.FallthroughResultPart1, com.histmersion.domain.automation.agents.PlanQuotaRepo.FallthroughResultPart2] =
    sql"SELECT role, fallthrough FROM agent_role_fallthrough".query[(String, Boolean)].run().toMap

  def setFallthrough(role: com.histmersion.domain.automation.agents.PlanQuotaRepo.SetFallthroughRole, on: com.histmersion.domain.automation.agents.PlanQuotaRepo.SetFallthroughOn)(using DbCon): Unit =
    val _ =
      sql"""INSERT INTO agent_role_fallthrough (role, fallthrough) VALUES ($role, $on)
            ON CONFLICT (role) DO UPDATE SET fallthrough = EXCLUDED.fallthrough, updated_at = now()""".update.run()

  // ── the refresh log (V069) ──────────────────────────────────────────────────────────────────────

  /** One fresh reading a pool took on purpose (or tried to). */
  def logRefresh(p: PoolId, mode: com.histmersion.domain.automation.agents.PlanQuotaRepo.LogRefreshMode, trigger: com.histmersion.domain.automation.agents.PlanQuotaRepo.LogRefreshTrigger, ok: com.histmersion.domain.automation.agents.PlanQuotaRepo.LogRefreshOk, detail: com.histmersion.domain.automation.agents.PlanQuotaRepo.LogRefreshDetail)(using DbCon): Unit =
    val _ =
      sql"""INSERT INTO plan_quota_refreshes (pool, mode, trigger, ok, detail)
            VALUES (${p.wire}, $mode, $trigger, $ok, ${detail.take(1000)})""".update.run()

  /** How many refreshes `p` took since `sinceMs` (epoch ms), and the newest one. */
  def refreshes(p: PoolId, sinceMs: com.histmersion.domain.automation.agents.PlanQuotaRepo.RefreshesSinceMs)(using DbCon): (com.histmersion.domain.automation.agents.PlanQuotaRepo.RefreshesResultPart1, Option[RefreshInfo]) =
    val since = sinceMs.toDouble / 1000
    val n = sql"""SELECT count(*)::int FROM plan_quota_refreshes
                  WHERE pool = ${p.wire} AND at >= to_timestamp($since::float8)""".query[Int].run().head
    val last = sql"""SELECT to_char(at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'), mode, trigger, ok, detail
                     FROM plan_quota_refreshes WHERE pool = ${p.wire} ORDER BY at DESC, id DESC LIMIT 1"""
      .query[(String, String, String, Boolean, String)]
      .run()
      .headOption
      .map((a, m, t, ok, d) => RefreshInfo(a, m, t, ok, d))
    (n, last)
