package com.histmersion.domain.library.acquisition

/** The host's [[Http]] as the plugin SPI's client: plugins get bytes, and parse what they asked for; a metered call is
  * sent [[Http.once]], answered with its status and headers.
  */
import com.histmersion.plugin.Plugin
final class HttpPlugin(http: Http) extends Plugin.Http:
  def get(url: String, headers: Map[String, String]) = http.bytes(url, headers)
  override def fetch(url: String, headers: Map[String, String]) =
    http.once(url, headers).map(a => Plugin.HttpResponse(a.code, a.headers, a.body))
