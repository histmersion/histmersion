package com.histmersion.server

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.*
import io.netty.buffer.Unpooled
import io.netty.util.ReferenceCountUtil
import java.nio.charset.StandardCharsets

/** Netty pipeline handler that gates write methods (POST/PUT/PATCH/DELETE) behind a Bearer token. GET/HEAD/OPTIONS pass
  * through unconditionally so the web UI and all read APIs remain open.
  *
  * It sits DIRECTLY after the `HttpServerCodec`, where a request is an [[HttpRequest]] head followed by its
  * [[HttpContent]] chunks. It used to sit before tapir's handler and match only `FullHttpRequest` — but the streams
  * handler in between turns every request WITH A BODY into a streamed request, so a PUT/PATCH/POST carrying JSON or an
  * upload walked straight past the gate (only a body-less DELETE was ever refused). A refused request's chunks are
  * dropped here, up to its `LastHttpContent`, so the connection stays usable for the next request.
  *
  * One instance per connection (`initPipeline` runs per channel): `discarding` is that connection's state, never
  * shared.
  */
final class AuthHandler(
    apiKey: () => AuthHandler.ApiKeyResult,
    tokens: com.histmersion.domain.automation.agents.RunTokens
) extends ChannelInboundHandlerAdapter:
  /** The key is read per request (a config reload may set, change or clear it); none = no gate. */
  private def expected: Option[com.histmersion.server.AuthHandler.ExpectedResult] = Option(apiKey()).filter(_.nonEmpty).map(k => s"Bearer $k")
  private val body401 = """{"message":"unauthorized"}""".getBytes(StandardCharsets.UTF_8)
  // `var`: this connection is between a refused request's head and its last chunk
  private var discarding = false

  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit =
    msg match
      case req: HttpRequest
          if AuthHandler.isWrite(req.method()) &&
            expected.exists(_ != req.headers().get(HttpHeaderNames.AUTHORIZATION)) && !byRunToken(req) && !AuthHandler
              .open(req) =>
        discarding = !req.isInstanceOf[LastHttpContent]
        val keepAlive = HttpUtil.isKeepAlive(req)
        val _ = ReferenceCountUtil.release(req)
        val resp = new DefaultFullHttpResponse(
          HttpVersion.HTTP_1_1,
          HttpResponseStatus.UNAUTHORIZED,
          Unpooled.wrappedBuffer(body401)
        )
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json")
        resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body401.length)
        resp.headers().set(HttpHeaderNames.WWW_AUTHENTICATE, "Bearer")
        HttpUtil.setKeepAlive(resp, keepAlive)
        val _ = ctx.writeAndFlush(resp)
      case c: HttpContent if discarding =>
        if c.isInstanceOf[LastHttpContent] then discarding = false
        val _ = ReferenceCountUtil.release(c)
      case _ => super.channelRead(ctx, msg)

  /** The agent MCP endpoint is gated by run tokens instead ([[com.histmersion.domain.automation.agents.RunTokenGate]],
    * in front of this handler); the quota-guard mod of a headless run reports with its run token.
    */
  private def byRunToken(req: HttpRequest): com.histmersion.server.AuthHandler.ByRunTokenResult =
    val path = req.uri().takeWhile(_ != '?')
    import com.histmersion.domain.automation.agents.RunTokens
    RunTokens.isAgentPath(path) ||
    (path == RunTokens.QuotaPath && tokens.bearer(req.headers().get(HttpHeaderNames.AUTHORIZATION)).isDefined)

object AuthHandler:
  type ApiKeyResult = ApiKeyResult.T
  object ApiKeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ExpectedResult = ExpectedResult.T
  object ExpectedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByRunTokenResult = ByRunTokenResult.T
  object ByRunTokenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsWriteResult = IsWriteResult.T
  object IsWriteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenResult = OpenResult.T
  object OpenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsOpenPathResult = IsOpenPathResult.T
  object IsOpenPathResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsOpenPathPath = IsOpenPathPath.T
  object IsOpenPathPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def isWrite(m: HttpMethod): com.histmersion.server.AuthHandler.IsWriteResult =
    m == HttpMethod.POST || m == HttpMethod.PUT || m == HttpMethod.PATCH || m == HttpMethod.DELETE

  /** The one write that needs no key: the web UI marking a window as read (`POST /api/ui/seen/{board}`,
    * [[com.histmersion.api.instance.UiApi.markSeen]]). It changes nothing but that mark, and a reader without the key
    * must still be able to clear what they have read.
    */
  def open(req: HttpRequest): com.histmersion.server.AuthHandler.OpenResult =
    req.method() == HttpMethod.POST && isOpenPath(req.uri().takeWhile(_ != '?'))

  /** Pure: is this path one the gate lets by without a key? Exactly one segment after the prefix. */
  def isOpenPath(path: com.histmersion.server.AuthHandler.IsOpenPathPath): com.histmersion.server.AuthHandler.IsOpenPathResult =
    path.startsWith(com.histmersion.api.instance.UiApi.SeenPathPrefix) &&
      {
        val rest = path.drop(com.histmersion.api.instance.UiApi.SeenPathPrefix.length);
        rest.nonEmpty && !rest.contains('/')
      }
