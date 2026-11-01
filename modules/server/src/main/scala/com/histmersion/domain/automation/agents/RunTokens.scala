package com.histmersion.domain.automation.agents

import io.netty.handler.codec.http.*

import java.util.concurrent.ConcurrentHashMap

/** Per-step run tokens (docs/research-agent-runtimes.md §2.4): each CLI step attempt gets a fresh random token bound to
  * its agent definition, and the agent MCP endpoint serves ONLY that agent's tools to it — the layer no runtime can
  * talk its way around. In memory on purpose: a token lives as long as one process, and a restarted server has no
  * running step.
  */
final class RunTokens:
  private val grants = ConcurrentHashMap[String, RunGrant]()
  private val random = java.security.SecureRandom()

  def issue(
      agent: AgentDef,
      scope: Option[RunScope],
      ttlMillis: Long,
      now: Long = System.currentTimeMillis()
  ): RunGrant =
    val bytes = new Array[Byte](32)
    random.nextBytes(bytes)
    val token = "hmrt_" + bytes.map(b => f"$b%02x").mkString
    val g =
      RunGrant(token, agent.name, agent.tools, scope.map(_.run), scope.map(_.step), now + math.max(1000L, ttlMillis))
    val _ = grants.put(token, g)
    prune(now)
    g

  def revoke(token: String): Unit = { val _ = grants.remove(token) }

  /** The grant of a live token; an expired one is dropped. */
  def check(token: String, now: Long = System.currentTimeMillis()): Option[RunGrant] =
    Option(grants.get(token)).flatMap { g =>
      if g.expiresAt > now then Some(g)
      else
        revoke(token)
        None
    }

  /** The grant a `Bearer <token>` header carries. */
  def bearer(header: String, now: Long = System.currentTimeMillis()): Option[RunGrant] =
    Option(header).map(_.trim).filter(_.startsWith("Bearer ")).map(_.stripPrefix("Bearer ").trim).flatMap(check(_, now))

  private def prune(now: Long): Unit = grants.values().removeIf(_.expiresAt <= now)

object RunTokens:
  /** The agent MCP endpoint: `/mcp/agent` (the run token picks the agent) and `/mcp/agent/<agent>`. */
  val Path = "/mcp/agent"

  /** Where the quota-guard mod reports. */
  val QuotaPath = "/api/agents/quota/claude"

  def isAgentPath(path: String): Boolean = path == Path || path.startsWith(Path + "/")
