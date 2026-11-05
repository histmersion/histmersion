package com.histmersion.domain.automation.agents

import io.netty.handler.codec.http.*

import java.util.concurrent.ConcurrentHashMap

/** Per-step run tokens (docs/research-agent-runtimes.md §2.4): each CLI step attempt gets a fresh random token bound to
  * its agent definition, and the agent MCP endpoint serves ONLY that agent's tools to it — the layer no runtime can
  * talk its way around. In memory on purpose: a token lives as long as one process, and a restarted server has no
  * running step.
  */
final class RunTokens(using wallClock: java.time.Clock):
  private val grants = ConcurrentHashMap[String, RunGrant]()
  private val random = java.security.SecureRandom()

  def issue(
      agent: AgentDef,
      scope: Option[RunScope],
      ttlMillis: com.histmersion.domain.automation.agents.RunTokens.IssueTtlMillis,
      now: com.histmersion.domain.automation.agents.RunTokens.IssueNow = wallClock.millis()
  ): RunGrant =
    val bytes = new Array[Byte](32)
    random.nextBytes(bytes)
    val token = "hmrt_" + bytes.map(b => f"$b%02x").mkString
    val g =
      RunGrant(token, agent.name, agent.tools, scope.map(_.run), scope.map(_.step), now + math.max(1000L, ttlMillis))
    val _ = grants.put(token, g)
    prune(now)
    g

  def revoke(token: com.histmersion.domain.automation.agents.RunTokens.RevokeToken): Unit = { val _ = grants.remove(token) }

  /** The grant of a live token; an expired one is dropped. */
  def check(token: com.histmersion.domain.automation.agents.RunTokens.CheckToken, now: com.histmersion.domain.automation.agents.RunTokens.CheckNow = wallClock.millis()): Option[RunGrant] =
    Option(grants.get(token)).flatMap { g =>
      if g.expiresAt > now then Some(g)
      else
        revoke(token)
        None
    }

  /** The grant a `Bearer <token>` header carries. */
  def bearer(header: com.histmersion.domain.automation.agents.RunTokens.BearerHeader, now: com.histmersion.domain.automation.agents.RunTokens.BearerNow = wallClock.millis()): Option[RunGrant] =
    Option(header).map(_.trim).filter(_.startsWith("Bearer ")).map(_.stripPrefix("Bearer ").trim).flatMap(check(_, now))

  private def prune(now: com.histmersion.domain.automation.agents.RunTokens.PruneNow): Unit = grants.values().removeIf(_.expiresAt <= now)

object RunTokens:
  /** The agent MCP endpoint: `/mcp/agent` (the run token picks the agent) and `/mcp/agent/<agent>`. */
  type IssueTtlMillis = IssueTtlMillis.T
  object IssueTtlMillis extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IssueNow = IssueNow.T
  object IssueNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RevokeToken = RevokeToken.T
  object RevokeToken extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckToken = CheckToken.T
  object CheckToken extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckNow = CheckNow.T
  object CheckNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BearerHeader = BearerHeader.T
  object BearerHeader extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BearerNow = BearerNow.T
  object BearerNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PruneNow = PruneNow.T
  object PruneNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsAgentPathResult = IsAgentPathResult.T
  object IsAgentPathResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsAgentPathPath = IsAgentPathPath.T
  object IsAgentPathPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Path = "/mcp/agent"

  /** Where the quota-guard mod reports. */
  val QuotaPath = "/api/agents/quota/claude"

  def isAgentPath(path: com.histmersion.domain.automation.agents.RunTokens.IsAgentPathPath): com.histmersion.domain.automation.agents.RunTokens.IsAgentPathResult = path == Path || path.startsWith(Path + "/")
