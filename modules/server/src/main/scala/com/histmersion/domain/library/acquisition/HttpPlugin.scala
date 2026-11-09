package com.histmersion
package domain.library.acquisition

import com.histmersion.plugin.Header

import com.histmersion.plugin.Problem

import com.histmersion.domain.CoreQuota
import io.scalaland.chimney.dsl.*

/** The host's [[Http]] as the plugin SPI's client: plugins get bytes, and parse what they asked for; a metered call is
  * sent [[Http.once]], answered with its status and headers.
  */
import com.histmersion.plugin.Plugin
final class HttpPlugin(http: Http) extends Plugin.Http:
  private def sent(headers: Map[Header.Name, Header.Value]) =
    headers.map((k, v) => CoreQuota.HeaderName(k.value) -> CoreQuota.HeaderValue(v.value))
  def get(url: Plugin.Http.Url, headers: Map[Header.Name, Header.Value]) =
    http
      .bytes(Http.Url(url.value), sent(headers))
      .left
      .map(failure)
      .map(b => Plugin.HttpResponse.Body(b.value))
  override def fetch(
      url: Plugin.Http.Url,
      headers: Map[Header.Name, Header.Value]
  ) =
    http
      .once(Http.Url(url.value), sent(headers))
      .left
      .map(failure)
      .map(a =>
        Plugin.HttpResponse(
          Plugin.HttpResponse.Status(a.code.value),
          a.headers.map((k, v) => Header.Name(k.value) -> Header.Value(v.value)),
          Plugin.HttpResponse.Body(a.body.value)
        )
      )

  private def failure(f: Http.Failure): Plugin.Http.Failure = f match
    case Http.Failure.Status(code, why) => Plugin.Http.Failure.Status(code.status, why.transformInto[Problem])
    case Http.Failure.Transport(why)    => Plugin.Http.Failure.Transport(why.transformInto[Problem])
