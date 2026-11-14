package com.histmersion
package domain.library.acquisition

import com.histmersion.util.problem.Problem
import com.histmersion.plugin.Header

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
          Problem.Upstream.Status(a.code.value),
          a.headers.map((k, v) => Header.Name(k.value) -> Header.Value(v.value)),
          Plugin.HttpResponse.Body(a.body.value)
        )
      )

  /** The problem the plugin sees: it carries the status itself when the host's answer had one. */
  private def failure(f: Http.Failure): Problem = f match
    case Http.Failure.Status(_, why) => why
    case Http.Failure.Transport(why) => why
