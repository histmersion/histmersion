package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.CoreQuota

/** The host's [[Http]] as the plugin SPI's client: plugins get bytes, and parse what they asked for; a metered call is
  * sent [[Http.once]], answered with its status and headers.
  */
import com.histmersion.plugin.Plugin
final class HttpPlugin(http: Http) extends Plugin.Http:
  private def sent(headers: Map[Plugin.Http.GetHeadersPart1, Plugin.Http.GetHeadersPart2]) =
    headers.map((k, v) => CoreQuota.HeaderName(k.value) -> CoreQuota.HeaderValue(v.value))
  def get(url: Plugin.Http.GetUrl, headers: Map[Plugin.Http.GetHeadersPart1, Plugin.Http.GetHeadersPart2]) =
    http.bytes(Http.Url(url.value), sent(headers)).left
      .map(e => Plugin.Http.GetResultPart1(e.value))
      .map(b => Plugin.HttpResponse.Body(b.value))
  override def fetch(url: Plugin.Http.FetchUrl, headers: Map[Plugin.Http.FetchHeadersPart1, Plugin.Http.FetchHeadersPart2]) =
    http.once(Http.Url(url.value), sent(headers)).left
      .map(e => Plugin.Http.FetchResultPart1(e.value))
      .map(a =>
        Plugin.HttpResponse(
          Plugin.HttpResponse.Status(a.code.value),
          a.headers.map((k, v) => Plugin.HttpResponse.HeadersKey(k.value) -> Plugin.HttpResponse.HeadersValue(v.value)),
          Plugin.HttpResponse.Body(a.body.value)
        )
      )
