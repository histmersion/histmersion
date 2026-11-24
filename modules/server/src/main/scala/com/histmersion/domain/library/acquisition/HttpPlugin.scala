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
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
@atBoundary(BoundaryKind.Spi, "offers the host's HTTP client as the plugin API's HTTP client")
final class HttpPlugin(http: Http) extends Plugin.Http:
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def sent(headers: Map[Header.Name, Header.Value]) =
    headers.map((k, v) => CoreQuota.HeaderName(k.value) -> CoreQuota.HeaderValue(v.value))
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def get(url: Plugin.Http.Url, headers: Map[Header.Name, Header.Value]) =
    http
      .bytes(Http.Url(url.value), sent(headers))
      .map(b => Plugin.HttpResponse.Body(b.value))
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  override def fetch(
      url: Plugin.Http.Url,
      headers: Map[Header.Name, Header.Value]
  ) =
    http
      .once(Http.Url(url.value), sent(headers))
      .map(a =>
        Plugin.HttpResponse(
          Problem.Upstream.Status(a.code.value),
          a.headers.map((k, v) => Header.Name(k.value) -> Header.Value(v.value)),
          Plugin.HttpResponse.Body(a.body.value)
        )
      )
