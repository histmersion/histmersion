package com.histmersion.server.acquire

import com.histmersion.plugin.{PluginHttp, PluginHttpResponse}

/** The host's [[Http]] as the plugin SPI's client: plugins get bytes, and parse what they asked for; a metered call is
  * sent [[Http.once]], answered with its status and headers.
  */
final class HttpPlugin(http: Http) extends PluginHttp:
  def get(url: String, headers: Map[String, String]) = http.bytes(url, headers)
  override def fetch(url: String, headers: Map[String, String]) =
    http.once(url, headers).map(a => PluginHttpResponse(a.code, a.headers, a.body))
