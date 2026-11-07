package com.histmersion.domain.automation.agents
import com.histmersion.api.automation.PlanQuota.{PoolId, Reading, RefreshInfo, Source}

import com.augustnagro.magnum.*
import com.histmersion.util.database.given

/** The plan pools' readings, settings and refresh log (migrations V063–V065, V069). Stateless. */
object PlanQuotaRepo:
  type PoolName = PoolName.T
  object PoolName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EpochMillis = EpochMillis.T
  object EpochMillis extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshDetail = RefreshDetail.T
  object RefreshDetail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshMode = RefreshMode.T
  object RefreshMode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshTrigger = RefreshTrigger.T
  object RefreshTrigger extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  // Times cross as epoch milliseconds (no timestamp codec in the native image): bound through
  // to_timestamp(ms / 1000.0), read back as (extract(epoch …) * 1000)::bigint.

  /** The newest reading per window replaces the one before (a reading is a snapshot, not a ledger). */

  def record(pool: PoolId, rs: List[Reading])(using DbCon): Unit =
    if rs.nonEmpty then
      val _ =
        rs.foreach { r =>
          val reset = r.resetsAt.map(_.value.toDouble / 1000)
          val seen = r.observedAt.value.toDouble / 1000
          val model = r.model.map(_.value).getOrElse("")
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
        Reading(
          Reading.Limit(l),
          Reading.Kind(k),
          Reading.UsedPct(u),
          r.map(Reading.ResetsAt(_)),
          Reading.Rejected(rej),
          Reading.ObservedAt(o),
          Source.parse(Source.Wire(s)).getOrElse(Source.Run),
          Option(m).filter(_.nonEmpty).map(Reading.Model(_))
        )
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

  val NoPool: Pool = Pool(com.histmersion.domain.automation.agents.PlanQuotaRepo.Pool.Held(false), None, None, None, None)

  def pool(p: PoolId)(using DbCon): Pool =
    sql"""SELECT held, floors, refusal, (extract(epoch FROM refusal_until) * 1000)::bigint, plan_type
          FROM plan_pools WHERE pool = ${p.wire}"""
      .query[(Boolean, Option[String], Option[String], Option[Long], Option[String])]
      .run()
      .headOption
      .map((h, f, r, u, t) =>
        Pool(Pool.Held(h), f.map(Pool.Floors(_)), r.map(Pool.Refusal(_)), u.map(Pool.RefusalUntil(_)), t.map(Pool.PlanType(_)))
      )
      .getOrElse(NoPool)

  def setHold(p: PoolId, held: com.histmersion.domain.automation.agents.PlanQuotaRepo.Pool.Held)(using DbCon): Unit =
    val _ =
      sql"""INSERT INTO plan_pools (pool, held) VALUES (${p.wire}, $held)
            ON CONFLICT (pool) DO UPDATE SET held = EXCLUDED.held, updated_at = now()""".update.run()

  /** Saved floors; None drops them (config's apply again). */
  def setFloors(p: PoolId, floors: Option[com.histmersion.domain.automation.agents.PlanQuotaRepo.Pool.Floors])(using DbCon): Unit =
    val _ =
      sql"""INSERT INTO plan_pools (pool, floors) VALUES (${p.wire}, $floors)
            ON CONFLICT (pool) DO UPDATE SET floors = EXCLUDED.floors, updated_at = now()""".update.run()

  /** The provider's own refusal (None lifts it) and its plan type, as last read. */
  def setRefusal(p: PoolId, refusal: Option[com.histmersion.domain.automation.agents.PlanQuotaRepo.Pool.Refusal], until: Option[com.histmersion.domain.automation.agents.PlanQuotaRepo.Pool.RefusalUntil], planType: Option[com.histmersion.domain.automation.agents.PlanQuotaRepo.Pool.PlanType])(using DbCon): Unit =
    val u = until.map(_.value.toDouble / 1000)
    val _ =
      sql"""INSERT INTO plan_pools (pool, refusal, refusal_until, plan_type) VALUES (${p.wire}, $refusal, to_timestamp($u::float8), $planType)
            ON CONFLICT (pool) DO UPDATE SET refusal = EXCLUDED.refusal, refusal_until = EXCLUDED.refusal_until,
              plan_type = COALESCE(EXCLUDED.plan_type, plan_pools.plan_type), updated_at = now()""".update.run()

  def fallthrough(using DbCon): Map[com.histmersion.domain.automation.agents.ModelSettingsRepo.ChainRole, Boolean] =
    sql"SELECT role, fallthrough FROM agent_role_fallthrough"
      .query[(String, Boolean)]
      .run()
      .map((role, on) => ModelSettingsRepo.ChainRole(role) -> on)
      .toMap

  def setFallthrough(role: com.histmersion.domain.automation.agents.ModelSettingsRepo.ChainRole, on: Boolean)(using DbCon): Unit =
    val _ =
      sql"""INSERT INTO agent_role_fallthrough (role, fallthrough) VALUES ($role, $on)
            ON CONFLICT (role) DO UPDATE SET fallthrough = EXCLUDED.fallthrough, updated_at = now()""".update.run()

  // ── the refresh log (V069) ──────────────────────────────────────────────────────────────────────

  /** One fresh reading a pool took on purpose (or tried to). */
  def logRefresh(p: PoolId, mode: com.histmersion.domain.automation.agents.PlanQuotaRepo.RefreshMode, trigger: com.histmersion.domain.automation.agents.PlanQuotaRepo.RefreshTrigger, ok: Boolean, detail: com.histmersion.domain.automation.agents.PlanQuotaRepo.RefreshDetail)(using DbCon): Unit =
    val _ =
      sql"""INSERT INTO plan_quota_refreshes (pool, mode, trigger, ok, detail)
            VALUES (${p.wire}, $mode, $trigger, $ok, ${detail.value.take(1000)})""".update.run()

  /** How many refreshes `p` took since `sinceMs` (epoch ms), and the newest one. */
  def refreshes(p: PoolId, sinceMs: com.histmersion.domain.automation.agents.PlanQuotaRepo.EpochMillis)(using DbCon): (com.histmersion.RowCount, Option[RefreshInfo]) =
    val since = sinceMs.value.toDouble / 1000
    val n = sql"""SELECT count(*)::int FROM plan_quota_refreshes
                  WHERE pool = ${p.wire} AND at >= to_timestamp($since::float8)""".query[Int].run().head
    val last = sql"""SELECT to_char(at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'), mode, trigger, ok, detail
                     FROM plan_quota_refreshes WHERE pool = ${p.wire} ORDER BY at DESC, id DESC LIMIT 1"""
      .query[(String, String, String, Boolean, String)]
      .run()
      .headOption
      .map((a, m, t, ok, d) =>
        RefreshInfo(RefreshInfo.At(a), RefreshInfo.Mode(m), RefreshInfo.Trigger(t), RefreshInfo.Ok(ok), RefreshInfo.Detail(d))
      )
    (com.histmersion.RowCount(n), last)
