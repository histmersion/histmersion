package com.histmersion.server.acquire

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

/** The host's [[Http]] as the plugin SPI's client: plugins get bytes, and parse what they asked for. */
final class HttpPlugin(http: Http) extends com.histmersion.plugin.PluginHttp:
  def get(url: String, headers: Map[String, String]) = http.bytes(url, headers)
