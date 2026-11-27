package com.histmersion
package domain.automation.agents
import com.histmersion.util.access.Refusal
import com.histmersion.util.problem.Problem

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.*
import io.netty.util.ReferenceCountUtil
import com.histmersion.lint.{tolerated, LintRule}

/** The netty gate in front of the agent MCP endpoint, on every request whatever the method: no live run token → 401; a
  * token for another agent's path → 403; the bare `/mcp/agent` is routed to the token's own agent
  * (`/mcp/agent/<agent>`, whose MCP server holds only that agent's tools). A refusal is answered as every endpoint
  * answers a problem ([[Refusal]]), and a refused request's body chunks are dropped, as in
  * [[com.histmersion.util.access.AccessGate]], which every request passes first.
  */
final class RunTokenGate(tokens: RunTokens) extends ChannelInboundHandlerAdapter:
  // `var`: this connection is between a refused request's head and its last chunk
  private var discarding = false

  private def refuse(ctx: ChannelHandlerContext, req: HttpRequest, problem: Problem): Unit =
    discarding = !req.isInstanceOf[LastHttpContent]
    Refusal(problem).answer(ctx, req)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit =
    msg match
      case req: HttpRequest =>
        val uri = req.uri()
        val q = uri.indexOf('?')
        val path = if q < 0 then uri else uri.substring(0, q)
        if !RunTokens.isAgentPath(path) then super.channelRead(ctx, msg)
        else
          tokens.bearer(req.headers().get(HttpHeaderNames.AUTHORIZATION)) match
            case None =>
              scribe.warn(s"agent MCP: refused a request to $path without a live run token")
              refuse(ctx, req, Problem.Refused.Unauthenticated(Problem.Refused.Credential.RunToken))
            case Some(g) if path != RunTokens.Path && path != s"${RunTokens.Path}/${g.agent}" =>
              scribe.warn(s"agent MCP: a token of ${g.agent} (run ${g.run.getOrElse("-")}) asked for $path — refused")
              refuse(ctx, req, Problem.Refused.Policy(Problem.Refused.Rule.TokenOtherAgent))
            case Some(g) =>
              if path == RunTokens.Path then
                req.setUri(s"${RunTokens.Path}/${g.agent}" + (if q < 0 then "" else uri.substring(q)))
              super.channelRead(ctx, msg)
      case c: HttpContent if discarding =>
        if c.isInstanceOf[LastHttpContent] then discarding = false
        val _ = ReferenceCountUtil.release(c)
      case _ => super.channelRead(ctx, msg)
