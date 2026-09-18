package histdb.server

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.*
import io.netty.buffer.Unpooled
import java.nio.charset.StandardCharsets

/** Netty pipeline handler that gates write methods (POST/PUT/PATCH/DELETE) behind a Bearer token.
  * Inserted between the HTTP codec and tapir's request handler; GET/HEAD/OPTIONS pass through
  * unconditionally so the atlas and all read APIs remain open. */
final class AuthHandler(apiKey: String) extends ChannelInboundHandlerAdapter:
  private val expected = s"Bearer $apiKey"
  private val body401  = """{"message":"unauthorized"}""".getBytes(StandardCharsets.UTF_8)

  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit =
    (msg: @unchecked) match
      case req: FullHttpRequest if isWrite(req.method()) =>
        val auth = req.headers().get(HttpHeaderNames.AUTHORIZATION)
        if auth != expected then
          val buf  = Unpooled.wrappedBuffer(body401)
          val resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED, buf)
          resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json")
          resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body401.length)
          resp.headers().set("WWW-Authenticate", "Bearer")
          ctx.writeAndFlush(resp)
          req.release()
        else super.channelRead(ctx, msg)
      case _ => super.channelRead(ctx, msg)

  private def isWrite(m: HttpMethod): Boolean =
    m == HttpMethod.POST || m == HttpMethod.PUT || m == HttpMethod.PATCH || m == HttpMethod.DELETE
