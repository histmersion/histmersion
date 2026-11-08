package com.histmersion
package domain.automation.agents

import io.netty.handler.codec.http.*

import java.util.concurrent.ConcurrentHashMap
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Per-step run tokens (docs/research-agent-runtimes.md §2.4): each CLI step attempt gets a fresh random token bound to
  * its agent definition, and the agent MCP endpoint serves ONLY that agent's tools to it — the layer no runtime can
  * talk its way around. In memory on purpose: a token lives as long as one process, and a restarted server has no
  * running step.
  */
final class RunTokens(using wallClock: java.time.Clock):
  private val grants = ConcurrentHashMap[RunGrant.Token, RunGrant]()
  private val random = java.security.SecureRandom()

  def issue(
      agent: AgentDef,
      scope: Option[RunScope],
      ttlMillis: RunTokens.TtlMillis,
      now: PlanQuotaRepo.EpochMillis = PlanQuotaRepo.EpochMillis(wallClock.millis())
  ): RunGrant =
    val bytes = new Array[Byte](32)
    random.nextBytes(bytes)
    val token = RunGrant.Token("hmrt_" + bytes.map(b => f"$b%02x").mkString)
    val g =
      RunGrant(
        token,
        RunGrant.Agent(agent.name.value),
        agent.tools,
        scope.map(s => RunGrant.Run(s.run.value)),
        scope.map(s => RunGrant.Step(s.step.value)),
        RunGrant.ExpiresAt(now.value + math.max(1000L, ttlMillis.value))
      )
    val _ = grants.put(token, g)
    prune(now)
    g

  def revoke(token: RunTokens.Token): Unit = { val _ = grants.remove(token) }

  /** The grant of a live token; an expired one is dropped. */
  def check(token: RunTokens.Token, now: PlanQuotaRepo.EpochMillis = PlanQuotaRepo.EpochMillis(wallClock.millis())): Option[RunGrant] =
    Option(grants.get(token)).flatMap { g =>
      if g.expiresAt.value > now.value then Some(g)
      else
        revoke(token)
        None
    }

  /** The grant a `Bearer <token>` header carries. */
  def bearer(header: String, now: PlanQuotaRepo.EpochMillis = PlanQuotaRepo.EpochMillis(wallClock.millis())): Option[RunGrant] =
    Option(header).map(_.trim).filter(_.startsWith("Bearer ")).map(_.stripPrefix("Bearer ").trim).flatMap(t => check(RunGrant.Token(t), now))

  private def prune(now: PlanQuotaRepo.EpochMillis): Unit = grants.values().removeIf(_.expiresAt.value <= now.value)

object RunTokens:
  type Token = RunGrant.Token
  val Token: RunGrant.Token.type = RunGrant.Token
  type TtlMillis = TtlMillis.T
  object TtlMillis extends RefinedType[Long, Pure]
  /** The agent MCP endpoint: `/mcp/agent` (the run token picks the agent) and `/mcp/agent/<agent>`. */

  val Path = "/mcp/agent"

  /** Where the quota-guard mod reports. */
  val QuotaPath = "/api/agents/quota/claude"

  def isAgentPath(path: String): Boolean = path == Path || path.startsWith(Path + "/")
