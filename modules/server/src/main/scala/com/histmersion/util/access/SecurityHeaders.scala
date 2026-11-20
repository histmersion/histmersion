package com.histmersion
package util.access

import com.histmersion.api.basemap.BaseMapSource
import io.netty.channel.{ChannelHandlerContext, ChannelOutboundHandlerAdapter, ChannelPromise}
import io.netty.handler.codec.http.{HttpResponse, HttpStatusClass}

/** The netty handler that gives every response this server writes the browser's safety headers: a page (the web UI, a
  * source's or an article's page, an error page, a refusal) runs no script but this server's own files, cannot be
  * framed by another site, connects to no host but this server and the configured tile servers, and no answer is read
  * as another type than the one it declares. It sits directly after the HTTP codec, so the access gate's refusals pass
  * it too. A header a route set itself is kept.
  *
  * `headers` gives the headers of the config in force: they are built ([[SecurityHeaders.of]]) from the tile templates
  * the settings name when the config is read, and again when a reload changes those settings, and read here at each
  * response.
  */
final class SecurityHeaders(headers: () => List[(String, String)]) extends ChannelOutboundHandlerAdapter:

  override def write(ctx: ChannelHandlerContext, msg: AnyRef, promise: ChannelPromise): Unit =
    msg match
      // an interim answer (`100 Continue`) is a bare status line a client reads before sending the body: no headers
      case r: HttpResponse if !r.status().codeClass().equals(HttpStatusClass.INFORMATIONAL) =>
        val hs = r.headers()
        headers().foreach((name, value) => if !hs.contains(name) then { val _ = hs.set(name, value) })
      case _ => ()
    super.write(ctx, msg, promise)

object SecurityHeaders:
  val Policy = "Content-Security-Policy"

  /** The headers when the page's online base maps fetch from the tile templates `tiles` (none: the page asks no other
    * host for a map).
    */
  def of(tiles: List[String]): List[(String, String)] =
    List(
      Policy -> contentSecurityPolicy(tiles.flatMap(origins).distinct),
      "X-Content-Type-Options" -> "nosniff",
      "X-Frame-Options" -> "DENY"
    )

  /** What a page of this server may load. Scripts, styles, fonts and frames: this server only (MapLibre's workers start
    * from `blob:` addresses; styles may be inline, for the values a view sets as CSS custom properties). Connections:
    * this server and `tiles`, the tile servers the online base maps fetch from (MapLibre fetches raster tiles; the
    * vector maps, their glyphs and the relief come from this server). No plugin objects, no `<base>` elsewhere, no form
    * sent elsewhere, and no site may frame a page.
    *
    * Images stay open to any web address, the one directive the settings cannot list: a media provider's thumbnail and
    * a picture's provider page come from the provider's own hosts (Commons, Europeana, museum IIIF servers, whatever a
    * media plugin returns), and a stored summary, note or full text may show an image from any host. `data:` there is
    * the favicon and the inline raster pictures `Href.image` lets through.
    */
  def contentSecurityPolicy(tiles: List[String]): String = List(
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: blob: https: http:",
    "font-src 'self' data:",
    ("connect-src 'self'" :: tiles).mkString(" "),
    "media-src 'self' https: http:",
    "worker-src 'self' blob:",
    "child-src 'self' blob:",
    "frame-src 'none'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'"
  ).mkString("; ")

  private val Absolute = """(?i)^(https?)://([^/?#]*)""".r
  private val HostPort =
    """(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)*[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?::[0-9]{1,5})?""".r

  /** The origins (`scheme://host[:port]`, lower-case) a tile template fetches from, `{s}` expanded as the page expands
    * it. None for an address on this server (relative), another scheme, or an authority that is no plain host name and
    * port (user info, another placeholder): a policy source must not carry it, and the page cannot load such tiles.
    */
  def origins(template: String): List[String] =
    Absolute.findPrefixMatchOf(template.trim) match
      case None    => Nil
      case Some(m) =>
        val scheme = m.group(1).toLowerCase(java.util.Locale.ROOT)
        val authority = m.group(2).toLowerCase(java.util.Locale.ROOT)
        val hosts =
          if authority.contains("{s}") then BaseMapSource.Subdomains.map(s => authority.replace("{s}", s))
          else List(authority)
        hosts.filter(HostPort.matches).map(h => s"$scheme://$h")
