package com.histmersion.domain.automation.agents
import com.histmersion.api.automation.ModelChains.{LimitSpec, Spend}

import com.augustnagro.magnum.*
import com.histmersion.util.database.Jsonb
import com.histmersion.util.database.given

/** The model chains and allowances saved over config (migration V060), and the spends an allowance counts — summed from
  * `agent_steps`, the one place tokens and cost are written. Stateless.
  */
private[agents] object ModelSettingsRepo:
  type ChainRole = ChainRole.T
  object ChainRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WindowSeconds = WindowSeconds.T
  object WindowSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A saved chain: the role and its models (`provider:model`, first choice first). */

  def chains(using DbCon): Map[com.histmersion.domain.automation.agents.ModelSettingsRepo.ChainRole, List[com.histmersion.domain.automation.agents.AgentRepo.RunRow.Model]] =
    sql"SELECT role, models FROM agent_role_chains".query[(String, List[String])].run().toMap

  def setChain(role: com.histmersion.domain.automation.agents.ModelSettingsRepo.ChainRole, models: List[com.histmersion.domain.automation.agents.AgentRepo.RunRow.Model])(using DbCon): Unit =
    val _ =
      sql"""INSERT INTO agent_role_chains (role, models) VALUES ($role, $models::text[])
            ON CONFLICT (role) DO UPDATE SET models = EXCLUDED.models, updated_at = now()""".update.run()

  def removeChain(role: com.histmersion.domain.automation.agents.ModelSettingsRepo.ChainRole)(using DbCon): Boolean =
    (sql"DELETE FROM agent_role_chains WHERE role = $role".update.run() > 0)

  /** A saved allowance: target, limits, reserve %. Limits that no longer decode are a `Left` the caller reports and
    * treats as BLOCKING — never silently as unlimited.
    */
  final case class Saved(target: Saved.Target, limits: Either[Saved.LimitsPart1, List[LimitSpec]], reservePct: Saved.ReservePct)

  object Saved:
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LimitsPart1 = LimitsPart1.T
    object LimitsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReservePct = ReservePct.T
    object ReservePct extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def allowances(using DbCon): List[Saved] =
    sql"SELECT target, limits::text, reserve_pct FROM agent_allowances ORDER BY target"
      .query[(String, String, Int)]
      .run()
      .toList
      .map { (t, json, r) =>
        Saved(t, Jsonb.decode[List[LimitSpec]](json).left.map(e => s"saved limits of $t do not decode: $e"), r)
      }

  def setAllowance(target: com.histmersion.domain.automation.agents.ModelSettingsRepo.Saved.Target, limits: List[LimitSpec], reservePct: com.histmersion.domain.automation.agents.ModelSettingsRepo.Saved.ReservePct)(using DbCon): Unit =
    val json = Jsonb.write(limits)
    val _ =
      sql"""INSERT INTO agent_allowances (target, limits, reserve_pct) VALUES ($target, $json::jsonb, $reservePct)
            ON CONFLICT (target) DO UPDATE SET limits = EXCLUDED.limits, reserve_pct = EXCLUDED.reserve_pct,
              updated_at = now()""".update.run()

  def removeAllowance(target: com.histmersion.domain.automation.agents.ModelSettingsRepo.Saved.Target)(using DbCon): Boolean =
    (sql"DELETE FROM agent_allowances WHERE target = $target".update.run() > 0)

  /** Every LLM attempt of `models` (exact `provider:model` references) or of a provider (`prefix`, e.g. `claude-code:`)
    * that finished in the last `seconds`: when (epoch ms), tokens (input + output), USD. A failed attempt counts — it
    * was paid for.
    */
  def spends(models: List[com.histmersion.domain.automation.agents.AgentRepo.RunRow.Model], prefixes: List[String], seconds: com.histmersion.domain.automation.agents.ModelSettingsRepo.WindowSeconds)(using DbCon): Vector[Spend] =
    if models.isEmpty && prefixes.isEmpty then Vector.empty
    else
      val likes = prefixes.map(_.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%")
      sql"""SELECT (extract(epoch FROM finished_at) * 1000)::bigint, (tokens_in + tokens_out)::bigint, cost_usd
            FROM agent_steps
            WHERE kind = 'llm' AND finished_at > now() - make_interval(secs => ${seconds.toDouble})
              AND (model = ANY($models::text[]) OR model LIKE ANY($likes::text[]))
            ORDER BY finished_at""".query[(Long, Long, BigDecimal)].run().map((at, t, c) => Spend(at, t, c))
