package com.histmersion
package domain.automation.agents
import com.histmersion.api.json.NewtypeCodecs.given

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence

import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.ModelChains.{LimitSpec, Spend}

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.Jsonb
import com.histmersion.util.database.given
import io.github.iltotore.iron.constraint.any.Pure

/** The model chains and allowances saved over config (migration V060), and the spends an allowance counts — summed from
  * `agent_steps`, the one place tokens and cost are written. Stateless.
  */
private[agents] object ModelSettingsRepo:
  type ChainRole = ChainRole.T
  object ChainRole extends Newtype[String, Pure]
  type WindowSeconds = WindowSeconds.T
  object WindowSeconds extends Newtype[Long, Pure]

  /** A saved chain: the role and its models (`provider:model`, first choice first). */

  def chains(using DbCon): Map[ChainRole, List[Agents.ModelRef.Wire]] =
    sql"SELECT role, models FROM agent_role_chains"
      .query[(String, List[String])]
      .run()
      .map((r, ms) => ChainRole(r) -> ms.map(m => Agents.ModelRef.Wire(m)))
      .toMap

  def setChain(role: ChainRole, models: List[Agents.ModelRef.Wire])(using DbCon): Unit =
    val _ =
      sql"""INSERT INTO agent_role_chains (role, models) VALUES ($role, $models::text[])
            ON CONFLICT (role) DO UPDATE SET models = EXCLUDED.models, updated_at = now()""".update.run()

  def removeChain(role: ChainRole)(using DbCon): Boolean =
    (sql"DELETE FROM agent_role_chains WHERE role = $role".update.run() > 0)

  /** A saved allowance: target, limits, reserve %. Limits that no longer decode are a `Left` the caller reports and
    * treats as BLOCKING — never silently as unlimited.
    */
  final case class Saved(
      target: Saved.Target,
      limits: Either[Problem, List[LimitSpec]],
      reservePct: Saved.ReservePct
  )

  object Saved:
    type Target = Target.T
    object Target extends Newtype[String, Pure]
    type ReservePct = ReservePct.T
    object ReservePct extends Newtype[Int, Pure]

  def allowances(using DbCon): List[Saved] =
    sql"SELECT target, limits::text, reserve_pct FROM agent_allowances ORDER BY target"
      .query[(String, String, Int)]
      .run()
      .toList
      .map { (t, json, r) =>
        Saved(
          Saved.Target(t),
          Jsonb
            .decode[List[LimitSpec]](Jsonb.JsonText(json))
            .left
            .map(e => Problem.Unclassified(Legacy.Text(s"saved limits of $t do not decode: ${e.sentence}"))),
          Saved.ReservePct(r)
        )
      }

  def setAllowance(target: Saved.Target, limits: List[LimitSpec], reservePct: Saved.ReservePct)(using DbCon): Unit =
    val json = Jsonb.write(limits)
    val _ =
      sql"""INSERT INTO agent_allowances (target, limits, reserve_pct) VALUES ($target, $json::jsonb, $reservePct)
            ON CONFLICT (target) DO UPDATE SET limits = EXCLUDED.limits, reserve_pct = EXCLUDED.reserve_pct,
              updated_at = now()""".update.run()

  def removeAllowance(target: Saved.Target)(using DbCon): Boolean =
    (sql"DELETE FROM agent_allowances WHERE target = $target".update.run() > 0)

  /** Every LLM attempt of `models` (exact `provider:model` references) or of a provider (`prefix`, e.g. `claude-code:`)
    * that finished in the last `seconds`: when (epoch ms), tokens (input + output), USD. A failed attempt counts — it
    * was paid for.
    */
  def spends(models: List[Agents.ModelRef.Wire], prefixes: List[String], seconds: WindowSeconds)(using
      DbCon
  ): Vector[Spend] =
    if models.isEmpty && prefixes.isEmpty then Vector.empty
    else
      val likes = prefixes.map(_.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%")
      sql"""SELECT (extract(epoch FROM finished_at) * 1000)::bigint, (tokens_in + tokens_out)::bigint, cost_usd
            FROM agent_steps
            WHERE kind = 'llm' AND finished_at > now() - make_interval(secs => ${seconds.value.toDouble})
              AND (model = ANY($models::text[]) OR model LIKE ANY($likes::text[]))
            ORDER BY finished_at"""
        .query[(Long, Long, BigDecimal)]
        .run()
        .map((at, t, c) => Spend(Spend.At(at), Spend.Tokens(t), c))
