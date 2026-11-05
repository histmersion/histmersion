package com.histmersion.domain.library.acquisition

/** The host's [[Http]] as the plugin SPI's client: plugins get bytes, and parse what they asked for; a metered call is
  * sent [[Http.once]], answered with its status and headers.
  */
import com.histmersion.plugin.Plugin
final class HttpPlugin(http: Http) extends Plugin.Http:
  def get(url: com.histmersion.domain.library.acquisition.HttpPlugin.GetUrl, headers: Map[com.histmersion.domain.library.acquisition.HttpPlugin.GetHeadersPart1, com.histmersion.domain.library.acquisition.HttpPlugin.GetHeadersPart2]) = http.bytes(url, headers)
  override def fetch(url: String, headers: Map[String, String]) =
    http.once(url, headers).map(a => Plugin.HttpResponse(a.code, a.headers, a.body))

object HttpPlugin:
  type GetUrl = GetUrl.T
  object GetUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetHeadersPart1 = GetHeadersPart1.T
  object GetHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetHeadersPart2 = GetHeadersPart2.T
  object GetHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
