package com.histmersion.domain.automation.agents

import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.*
import io.netty.util.ReferenceCountUtil

import java.nio.charset.StandardCharsets

/** The netty gate in front of the agent MCP endpoint, on every request whatever the method: no live run token → 401; a
  * token for another agent's path → 403; the bare `/mcp/agent` is routed to the token's own agent
  * (`/mcp/agent/<agent>`, whose MCP server holds only that agent's tools). A refused request's body chunks are dropped,
  * as in [[com.histmersion.server.AuthHandler]].
  */
final class RunTokenGate(tokens: RunTokens) extends ChannelInboundHandlerAdapter:
  // `var`: this connection is between a refused request's head and its last chunk
  private var discarding = false

  private def refuse(ctx: ChannelHandlerContext, req: HttpRequest, status: HttpResponseStatus, msg: com.histmersion.domain.automation.agents.RunTokenGate.Problem): Unit =
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
        val uri = req.uri()
        val q = uri.indexOf('?')
        val path = if q < 0 then uri else uri.substring(0, q)
        if !RunTokens.isAgentPath(path) then super.channelRead(ctx, msg)
        else
          tokens.bearer(req.headers().get(HttpHeaderNames.AUTHORIZATION)) match
            case None =>
              scribe.warn(s"agent MCP: refused a request to $path without a live run token")
              refuse(ctx, req, HttpResponseStatus.UNAUTHORIZED, "a live run token is required")
            case Some(g) if path != RunTokens.Path && path != s"${RunTokens.Path}/${g.agent}" =>
              scribe.warn(s"agent MCP: a token of ${g.agent} (run ${g.run.getOrElse("-")}) asked for $path — refused")
              refuse(ctx, req, HttpResponseStatus.FORBIDDEN, "this run token is not for that agent")
            case Some(g) =>
              if path == RunTokens.Path then
                req.setUri(s"${RunTokens.Path}/${g.agent}" + (if q < 0 then "" else uri.substring(q)))
              super.channelRead(ctx, msg)
      case c: HttpContent if discarding =>
        if c.isInstanceOf[LastHttpContent] then discarding = false
        val _ = ReferenceCountUtil.release(c)
      case _ => super.channelRead(ctx, msg)

object RunTokenGate:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
