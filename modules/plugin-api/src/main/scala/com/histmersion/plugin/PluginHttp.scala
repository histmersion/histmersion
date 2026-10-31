package com.histmersion.plugin

import scala.compiletime.deferred

/** The host's HTTP client, as a plugin sees it. Failures are `Left(message)`, never exceptions. */
trait PluginHttp:
  /** One GET under the host's outbound policy (its User-Agent, `Retry-After`, a slow-down asked again, a bot challenge
    * ending the request): the body of a 2xx answer, or why there is none.
    */
  def get(url: String, headers: Map[String, String] = Map.empty): Either[String, Array[Byte]]

  /** One GET with the host's identity, answered as it came — its status and headers, a slow-down or an error answer
    * included — and never asked again: for an upstream whose own budget decides when to ask again (an [[UpstreamQuota]]
    * reads the answer), where a retry would spend what the budget is keeping. `Left` only when the host could not talk
    * to the upstream at all.
    *
    * A host that has only bodies answers through [[get]]: status 200 and no headers, or its `Left`.
    */
  def fetch(url: String, headers: Map[String, String] = Map.empty): Either[String, PluginHttpResponse] =
    get(url, headers).map(PluginHttpResponse(200, Map.empty, _))
