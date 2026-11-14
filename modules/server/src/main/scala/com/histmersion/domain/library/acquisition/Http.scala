package com.histmersion
package domain.library.acquisition
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.Problem

import com.histmersion.domain.HttpPolicy

import com.histmersion.domain.CoreQuota
import com.histmersion.pluginhost.{HttpAnswer, HttpTransport, PolicyHttp}
import com.histmersion.upstreams.landing.RepositoryLanding
import Http.Failure

/** The ONE seam between the acquisition toolset and the network.
  *
  * Every upstream client is written against this trait, so a test supplies a fake that answers from a captured fixture
  * and never opens a socket. It is deliberately tiny — three verbs — because the interesting logic is in the pure
  * parsers, not here.
  *
  * Failures are `Left(Failure)`, never exceptions: an unreachable API is an expected condition for a tool whose whole
  * job is talking to flaky third parties.
  */
trait Http:
  def json(
      url: Http.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] = Map.empty
  ): Either[Failure, Json]
  def text(
      url: Http.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] = Map.empty
  ): Either[Failure, Http.Text]
  def bytes(
      url: Http.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] = Map.empty
  ): Either[Failure, HttpAnswer.Body]

  /** One request, not retried, answered as it came — the status and headers too (CORE's budget reads them). A client
    * that only has bodies (a test's) answers 200 with no headers, or its `Left`.
    */
  def once(
      url: Http.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] = Map.empty
  ): Either[Failure, HttpAnswer] =
    bytes(url, headers).map(HttpAnswer(HttpAnswer.Code(200), Map.empty, _))

object Http:

  /** Why a request gave no answer to use, whichever of the verbs it was: the plugin SPI's problem of a failed request
    * ([[com.histmersion.util.problem.Problem.Upstream]]), with its status, in the server's own layer (the conversions
    * are at the two seams, [[SttpHttp]] and [[HttpPlugin]]).
    */
  enum Failure derives CanEqual:
    /** The upstream answered and the client gave up on the answer: a refusal (401, 403), a missing page (404), a
      * slow-down it left alone, any other error status.
      */
    case Status(code: HttpAnswer.Code, problem: Problem)

    /** No usable answer: the upstream could not be reached, or the answer was not what was asked for. */
    case Transport(problem: Problem)

    /** The sentence for a person. */
    def problem: Problem

  type Url = RepositoryLanding.Candidate.Url
  val Url: RepositoryLanding.Candidate.Url.type = RepositoryLanding.Candidate.Url
  type Text = HttpAnswer.Text
  val Text: HttpAnswer.Text.type = HttpAnswer.Text

  /** A caller's own `User-Agent` (the PMC hosts get the project's public identity) replaces the acquisition one; either
    * goes through [[HttpPolicy.userAgent]], so neither can carry an e-mail.
    */
  val UserAgent = PolicyHttp.UserAgent
