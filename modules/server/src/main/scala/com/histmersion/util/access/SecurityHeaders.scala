package com.histmersion
package util.access

import io.netty.channel.{ChannelHandlerContext, ChannelOutboundHandlerAdapter, ChannelPromise}
import io.netty.handler.codec.http.HttpResponse

/** The netty handler that gives every response this server writes the browser's safety headers: a page (the web UI, a
  * source's or an article's page, an error page, a refusal) runs no script but this server's own files, cannot be
  * framed by another site, and no answer is read as another type than the one it declares. It sits directly after the
  * HTTP codec, so the access gate's refusals pass it too. A header a route set itself is kept.
  */
final class SecurityHeaders extends ChannelOutboundHandlerAdapter:
  import SecurityHeaders.All

  override def write(ctx: ChannelHandlerContext, msg: AnyRef, promise: ChannelPromise): Unit =
    msg match
      case r: HttpResponse =>
        val headers = r.headers()
        All.foreach((name, value) => if !headers.contains(name) then { val _ = headers.set(name, value) })
      case _ => ()
    super.write(ctx, msg, promise)

object SecurityHeaders:
  /** What a page of this server may load. Scripts, styles, fonts and frames: this server only (MapLibre's workers start
    * from `blob:` addresses; styles may be inline, for the values a view sets as CSS custom properties). Images and
    * connections: any web address, because base-map tiles, picture and figure hosts and the credits' sources are
    * settings and upstream data, not a fixed list. No plugin objects, no `<base>` elsewhere, no form sent elsewhere,
    * and no site may frame a page.
    */
  val ContentSecurityPolicy: String = List(
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: blob: https: http:",
    "font-src 'self' data:",
    "connect-src 'self' https: http:",
    "media-src 'self' https: http:",
    "worker-src 'self' blob:",
    "child-src 'self' blob:",
    "frame-src 'none'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'"
  ).mkString("; ")

  val All: List[(String, String)] = List(
    "Content-Security-Policy" -> ContentSecurityPolicy,
    "X-Content-Type-Options" -> "nosniff",
    "X-Frame-Options" -> "DENY"
  )
