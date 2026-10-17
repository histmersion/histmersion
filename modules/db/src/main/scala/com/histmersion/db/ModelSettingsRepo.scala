package com.histmersion.db

import com.histmersion.core.ModelChains.{LimitSpec, Spend}

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The model chains and allowances saved over config (migration V060), and the spends an allowance
  * counts — summed from `agent_steps`, the one place tokens and cost are written. Stateless. */
object ModelSettingsRepo:

  /** A saved chain: the role and its models (`provider:model`, first choice first). */
  def chains(ds: DataSource): Map[String, List[String]] =
    connect(ds):
      sql"SELECT role, models FROM agent_role_chains".query[(String, List[String])].run().toMap

  def setChain(ds: DataSource, role: String, models: List[String]): Unit =
    val _ = transact(ds):
      sql"""INSERT INTO agent_role_chains (role, models) VALUES ($role, $models::text[])
            ON CONFLICT (role) DO UPDATE SET models = EXCLUDED.models, updated_at = now()""".update.run()

  def removeChain(ds: DataSource, role: String): Boolean =
    transact(ds)(sql"DELETE FROM agent_role_chains WHERE role = $role".update.run() > 0)

  /** A saved allowance: target, limits, reserve %. Limits that no longer decode are a `Left` the caller
    * reports and treats as BLOCKING — never silently as unlimited. */
  final case class Saved(target: String, limits: Either[String, List[LimitSpec]], reservePct: Int)

  def allowances(ds: DataSource): List[Saved] =
    connect(ds):
      sql"SELECT target, limits::text, reserve_pct FROM agent_allowances ORDER BY target"
        .query[(String, String, Int)].run().toList.map { (t, json, r) =>
          Saved(t, Jsonb.decode[List[LimitSpec]](json).left.map(e => s"saved limits of $t do not decode: $e"), r)
        }

  def setAllowance(ds: DataSource, target: String, limits: List[LimitSpec], reservePct: Int): Unit =
    val json = Jsonb.write(limits)
    val _ = transact(ds):
      sql"""INSERT INTO agent_allowances (target, limits, reserve_pct) VALUES ($target, $json::jsonb, $reservePct)
            ON CONFLICT (target) DO UPDATE SET limits = EXCLUDED.limits, reserve_pct = EXCLUDED.reserve_pct,
              updated_at = now()""".update.run()

  def removeAllowance(ds: DataSource, target: String): Boolean =
    transact(ds)(sql"DELETE FROM agent_allowances WHERE target = $target".update.run() > 0)

  /** Every LLM attempt of `models` (exact `provider:model` references) or of a provider (`prefix`,
    * e.g. `claude-code:`) that finished in the last `seconds`: when (epoch ms), tokens (input +
    * output), USD. A failed attempt counts — it was paid for. */
  def spends(ds: DataSource, models: List[String], prefixes: List[String], seconds: Long): Vector[Spend] =
    if models.isEmpty && prefixes.isEmpty then Vector.empty
    else
      val likes = prefixes.map(_.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%")
      connect(ds):
        sql"""SELECT (extract(epoch FROM finished_at) * 1000)::bigint, (tokens_in + tokens_out)::bigint, cost_usd
              FROM agent_steps
              WHERE kind = 'llm' AND finished_at > now() - make_interval(secs => ${seconds.toDouble})
                AND (model = ANY($models::text[]) OR model LIKE ANY($likes::text[]))
              ORDER BY finished_at""".query[(Long, Long, BigDecimal)].run().map((at, t, c) => Spend(at, t, c))
