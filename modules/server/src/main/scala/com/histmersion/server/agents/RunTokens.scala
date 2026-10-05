package com.histmersion.server.agents

import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.*
import io.netty.util.ReferenceCountUtil

import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/** What one run token allows: the agent definition a step runs as, and so exactly its tools; the run
  * and step it belongs to; when it expires (epoch ms). */
final case class RunGrant(token: String, agent: String, tools: List[ToolName], run: Option[String], step: Option[Int],
    expiresAt: Long)

/** Per-step run tokens (docs/research-agent-runtimes.md §2.4): each CLI step attempt gets a fresh
  * random token bound to its agent definition, and the agent MCP endpoint serves ONLY that agent's
  * tools to it — the layer no runtime can talk its way around. In memory on purpose: a token lives as
  * long as one process, and a restarted server has no running step. */
final class RunTokens:
  private val grants = ConcurrentHashMap[String, RunGrant]()
  private val random = java.security.SecureRandom()

  def issue(agent: AgentDef, scope: Option[RunScope], ttlMillis: Long, now: Long = System.currentTimeMillis()): RunGrant =
    val bytes = new Array[Byte](32)
    random.nextBytes(bytes)
    val token = "hmrt_" + bytes.map(b => f"$b%02x").mkString
    val g = RunGrant(token, agent.name, agent.tools, scope.map(_.run), scope.map(_.step), now + math.max(1000L, ttlMillis))
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

/** The netty gate in front of the agent MCP endpoint, on every request whatever the method: no live
  * run token → 401; a token for another agent's path → 403; the bare `/mcp/agent` is routed to the
  * token's own agent (`/mcp/agent/<agent>`, whose MCP server holds only that agent's tools). A refused
  * request's body chunks are dropped, as in [[com.histmersion.server.AuthHandler]]. */
final class RunTokenGate(tokens: RunTokens) extends ChannelInboundHandlerAdapter:
  // `var`: this connection is between a refused request's head and its last chunk
  private var discarding = false

  private def refuse(ctx: ChannelHandlerContext, req: HttpRequest, status: HttpResponseStatus, msg: String): Unit =
    discarding = !req.isInstanceOf[LastHttpContent]
    val keepAlive = HttpUtil.isKeepAlive(req)
    val _ = ReferenceCountUtil.release(req)
    val body = s"""{"message":"$msg"}""".getBytes(StandardCharsets.UTF_8)
    val resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(body))
    resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json")
    resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.length)
    HttpUtil.setKeepAlive(resp, keepAlive)
    val _ = ctx.writeAndFlush(resp)

  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit =
    msg match
      case req: HttpRequest =>
        val uri  = req.uri()
        val q    = uri.indexOf('?')
        val path = if q < 0 then uri else uri.substring(0, q)
        if !RunTokens.isAgentPath(path) then super.channelRead(ctx, msg)
        else tokens.bearer(req.headers().get(HttpHeaderNames.AUTHORIZATION)) match
          case None =>
            scribe.warn(s"agent MCP: refused a request to $path without a live run token")
            refuse(ctx, req, HttpResponseStatus.UNAUTHORIZED, "a live run token is required")
          case Some(g) if path != RunTokens.Path && path != s"${RunTokens.Path}/${g.agent}" =>
            scribe.warn(s"agent MCP: a token of ${g.agent} (run ${g.run.getOrElse("-")}) asked for $path — refused")
            refuse(ctx, req, HttpResponseStatus.FORBIDDEN, "this run token is not for that agent")
          case Some(g) =>
            if path == RunTokens.Path then req.setUri(s"${RunTokens.Path}/${g.agent}" + (if q < 0 then "" else uri.substring(q)))
            super.channelRead(ctx, msg)
      case c: HttpContent if discarding =>
        if c.isInstanceOf[LastHttpContent] then discarding = false
        val _ = ReferenceCountUtil.release(c)
      case _ => super.channelRead(ctx, msg)
