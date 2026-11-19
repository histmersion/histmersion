package com.histmersion
package domain.library.acquisition
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.Problem

import com.histmersion.domain.HttpPolicy

import com.histmersion.domain.CoreQuota
import com.histmersion.pluginhost.{HttpAnswer, HttpTransport, PolicyHttp}
import com.histmersion.upstreams.landing.RepositoryLanding

/** The ONE seam between the acquisition toolset and the network.
  *
  * Every upstream client is written against this trait, so a test supplies a fake that answers from a captured fixture
  * and never opens a socket. It is deliberately tiny — three verbs — because the interesting logic is in the pure
  * parsers, not here.
  *
  * Failures are `Left` with the upstream's problem ([[Problem.Upstream]]: its URL, the status it answered), never
  * exceptions: an unreachable API is an expected condition for a tool whose whole job is talking to flaky third
  * parties.
  */
trait Http:
  def json(
      url: Http.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] = Map.empty
  ): Either[Problem, Json]
  def text(
      url: Http.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] = Map.empty
  ): Either[Problem, Http.Text]
  def bytes(
      url: Http.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] = Map.empty
  ): Either[Problem, HttpAnswer.Body]

  /** One request, not retried, answered as it came — the status and headers too (CORE's budget reads them). A client
    * that only has bodies (a test's) answers 200 with no headers, or its `Left`.
    */
  def once(
      url: Http.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] = Map.empty
  ): Either[Problem, HttpAnswer] =
    bytes(url, headers).map(HttpAnswer(HttpAnswer.Code(200), Map.empty, _))

object Http:

  export RepositoryLanding.Candidate.Url
  export HttpAnswer.Text

  /** A caller's own `User-Agent` (the PMC hosts get the project's public identity) replaces the acquisition one; either
    * goes through [[HttpPolicy.userAgent]], so neither can carry an e-mail.
    */
  val UserAgent = PolicyHttp.UserAgent
