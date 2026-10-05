package com.histmersion.db

import com.histmersion.core.PlanQuota.{PoolId, Reading, Source}

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The plan pools' readings and settings (migrations V063–V065). Stateless. */
object PlanQuotaRepo:

  // Times cross as epoch milliseconds (no timestamp codec in the native image): bound through
  // to_timestamp(ms / 1000.0), read back as (extract(epoch …) * 1000)::bigint.

  /** The newest reading per window replaces the one before (a reading is a snapshot, not a ledger). */
  def record(ds: DataSource, pool: PoolId, rs: List[Reading]): Unit =
    if rs.nonEmpty then
      val _ = transact(ds):
        rs.foreach { r =>
          val reset = r.resetsAt.map(_.toDouble / 1000)
          val seen  = r.observedAt.toDouble / 1000
          val model = r.model.getOrElse("")
          sql"""INSERT INTO plan_quota_readings (pool, limit_id, kind, model, used_pct, resets_at, rejected, observed_at, source)
                VALUES (${pool.wire}, ${r.limit}, ${r.kind}, $model, ${r.usedPct}, to_timestamp($reset::float8), ${r.rejected},
                        to_timestamp($seen::float8), ${r.source.wire})
                ON CONFLICT (pool, limit_id, kind, model) DO UPDATE SET used_pct = EXCLUDED.used_pct,
                  resets_at = EXCLUDED.resets_at, rejected = EXCLUDED.rejected, observed_at = EXCLUDED.observed_at,
                  source = EXCLUDED.source
                WHERE plan_quota_readings.observed_at <= EXCLUDED.observed_at""".update.run()
        }

  def readings(ds: DataSource, pool: PoolId): List[Reading] =
    connect(ds):
      sql"""SELECT limit_id, kind, model, used_pct, (extract(epoch FROM resets_at) * 1000)::bigint, rejected,
                   (extract(epoch FROM observed_at) * 1000)::bigint, source
            FROM plan_quota_readings WHERE pool = ${pool.wire} ORDER BY limit_id, kind"""
        .query[(String, String, String, Double, Option[Long], Boolean, Long, String)].run().toList
        .map { (l, k, m, u, r, rej, o, s) =>
          Reading(l, k, u, r, rej, o, Source.parse(s).getOrElse(Source.Run), Option(m).filter(_.nonEmpty))
        }

  /** What a person set on a pool and what the provider refused. */
  final case class Pool(held: Boolean, floors: Option[String], refusal: Option[String], refusalUntil: Option[Long],
      planType: Option[String])

  val NoPool: Pool = Pool(false, None, None, None, None)

  def pool(ds: DataSource, p: PoolId): Pool =
    connect(ds):
      sql"""SELECT held, floors, refusal, (extract(epoch FROM refusal_until) * 1000)::bigint, plan_type
            FROM plan_pools WHERE pool = ${p.wire}"""
        .query[(Boolean, Option[String], Option[String], Option[Long], Option[String])].run().headOption
        .map((h, f, r, u, t) => Pool(h, f, r, u, t)).getOrElse(NoPool)

  def setHold(ds: DataSource, p: PoolId, held: Boolean): Unit =
    val _ = transact(ds):
      sql"""INSERT INTO plan_pools (pool, held) VALUES (${p.wire}, $held)
            ON CONFLICT (pool) DO UPDATE SET held = EXCLUDED.held, updated_at = now()""".update.run()

  /** Saved floors; None drops them (config's apply again). */
  def setFloors(ds: DataSource, p: PoolId, floors: Option[String]): Unit =
    val _ = transact(ds):
      sql"""INSERT INTO plan_pools (pool, floors) VALUES (${p.wire}, $floors)
            ON CONFLICT (pool) DO UPDATE SET floors = EXCLUDED.floors, updated_at = now()""".update.run()

  /** The provider's own refusal (None lifts it) and its plan type, as last read. */
  def setRefusal(ds: DataSource, p: PoolId, refusal: Option[String], until: Option[Long], planType: Option[String]): Unit =
    val u = until.map(_.toDouble / 1000)
    val _ = transact(ds):
      sql"""INSERT INTO plan_pools (pool, refusal, refusal_until, plan_type) VALUES (${p.wire}, $refusal, to_timestamp($u::float8), $planType)
            ON CONFLICT (pool) DO UPDATE SET refusal = EXCLUDED.refusal, refusal_until = EXCLUDED.refusal_until,
              plan_type = COALESCE(EXCLUDED.plan_type, plan_pools.plan_type), updated_at = now()""".update.run()

  def fallthrough(ds: DataSource): Map[String, Boolean] =
    connect(ds):
      sql"SELECT role, fallthrough FROM agent_role_fallthrough".query[(String, Boolean)].run().toMap

  def setFallthrough(ds: DataSource, role: String, on: Boolean): Unit =
    val _ = transact(ds):
      sql"""INSERT INTO agent_role_fallthrough (role, fallthrough) VALUES ($role, $on)
            ON CONFLICT (role) DO UPDATE SET fallthrough = EXCLUDED.fallthrough, updated_at = now()""".update.run()
