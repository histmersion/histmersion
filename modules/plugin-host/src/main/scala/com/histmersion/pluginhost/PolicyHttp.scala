package com.histmersion
package pluginhost

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.plugin.Header

import com.histmersion.config.AppConfig
import com.histmersion.domain.CoreQuota
import com.histmersion.domain.HttpPolicy
import sttp.client4.*

import scala.concurrent.duration.*
import com.histmersion.plugin.Plugin
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The acquisition client: every request a source fetcher (a plugin's through [[Plugin.Http]], or a built-in one) and
  * the metadata lookups make, over one [[HttpTransport]].
  *
  * Every request follows [[HttpPolicy]], the one outbound policy: the product's `User-Agent` (no e-mail — Crossref and
  * OpenAlex take the contact as their `mailto` parameter), a slow-down answer (429, 502/503/504) asked again after its
  * `Retry-After` or 1, 2, 4 … s, a host that asks for more than `maxRetryAfterSeconds` left alone. An unreachable host
  * is asked again on the same schedule. A bot challenge ends the request ([[Challenge]]). `tries` counts the first
  * request; `sleep` and `clock` are parameters so a test does not wait.
  */
@tolerated(LintRule.Global, "pending G15 (no globals; everything from config)")
@tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
@atBoundary(BoundaryKind.Http, "sends acquisition requests under the outbound policy: User-Agent, retries, Retry-After")
final class PolicyHttp(
    userAgent: HttpPolicy.UserAgent,
    transport: HttpTransport,
    tries: PolicyHttp.Tries = PolicyHttp.Tries(4),
    maxRetryAfterSeconds: HttpPolicy.MaxRetryAfterSeconds = HttpPolicy.MaxRetryAfterSeconds(60),
    sleep: HttpPolicy.Step.Wait.Ms => Unit = ms => Thread.sleep(ms.value),
    clock: () => java.time.Instant = () => java.time.Instant.now(),
    log: HostLog = HostLog.scribe
):

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private val maxRetries = HttpPolicy.MaxRetries((tries.value - 1).max(0))

  /** The headers as sent: a caller's own `User-Agent` replaces the configured one; either goes through
    * [[HttpPolicy.userAgent]], so neither can carry an e-mail.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def identified(
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]
  ): Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] =
    val ua = headers.collectFirst {
      case (k, v) if k.value.equalsIgnoreCase(PolicyHttp.UserAgent.value) => HttpPolicy.UserAgent(v.value)
    }
    headers.filterNot(_._1.value.equalsIgnoreCase(PolicyHttp.UserAgent.value)) +
      (PolicyHttp.UserAgent -> CoreQuota.HeaderValue(HttpPolicy.userAgent(ua.getOrElse(userAgent)).value))

  /** One GET: the body of a 2xx answer, or why there is none. A caller's own `User-Agent` replaces the configured one;
    * either goes through [[HttpPolicy.userAgent]], so neither can carry an e-mail.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def bytes(
      url: HttpTransport.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] = Map.empty
  ): Either[Problem, HttpAnswer.Body] =
    val sent = identified(headers)
    def again(
        n: HttpPolicy.Attempt,
        ms: HttpPolicy.Step.Wait.Ms,
        why: String
    ): Either[Problem, HttpAnswer.Body] =
      log.warn(HostLog.Line(s"acquire: $why — retrying $url in ${ms}ms"))
      sleep(ms)
      attempt(HttpPolicy.Attempt(n.value + 1))
    def attempt(n: HttpPolicy.Attempt): Either[Problem, HttpAnswer.Body] =
      transport.get(url, sent) match
        case Left(unreachable) =>
          if n.value < maxRetries.value then again(n, HttpPolicy.backoffMs(n), unreachable.sentence)
          else Left(unreachable)
        case Right(answer) =>
          val code = HttpPolicy.StatusCode(answer.code.value)
          if Challenge.isChallenge(code, answer.headers, Challenge.Body(answer.body.value)) then
            Left(Problem.Upstream.Challenged.here(url.upstream, url.address, answer.code.status))
          else
            HttpPolicy.decide(
              code,
              answer.header(CoreQuota.HeaderName("Retry-After")).map(v => HttpPolicy.RetryAfter(v.value)),
              n,
              maxRetries,
              maxRetryAfterSeconds,
              clock()
            ) match
              case HttpPolicy.Step.Accept        => Right(answer.body)
              case HttpPolicy.Step.Wait(ms, why) => again(n, ms, why.said)
              case HttpPolicy.Step.GiveUp(why)   =>
                if HttpPolicy.Throttled(code) then log.warn(HostLog.Line(s"acquire: $url — ${why.said}; giving up"))
                Left(url.answered(answer.code.status, answer.body.value))
    attempt(HttpPolicy.Attempt(0))

  /** One GET with the policy's identity and NO retry: the answer as it came, a slow-down or an error included, with its
    * headers — for an upstream whose own budget decides when to ask again (CORE's rate-limit headers), where a retry
    * would spend what the budget is keeping.
    */
  def once(
      url: HttpTransport.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] = Map.empty
  ): Either[Problem, HttpAnswer] =
    transport.get(url, identified(headers))

  /** The client as the plugin SPI lends it: plugins get bytes, and parse what they asked for; a call an upstream's
    * budget meters is sent [[once]], answered with its status and headers.
    */
  def plugin: Plugin.Http = new Plugin.Http:
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    private def sent(headers: Map[Header.Name, Header.Value]) =
      headers.map((k, v) => CoreQuota.HeaderName(k.value) -> CoreQuota.HeaderValue(v.value))
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def get(url: Plugin.Http.Url, headers: Map[Header.Name, Header.Value]) =
      bytes(HttpTransport.Url(url.value), sent(headers)).map(b => Plugin.HttpResponse.Body(b.value))
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    override def fetch(
        url: Plugin.Http.Url,
        headers: Map[Header.Name, Header.Value]
    ) =
      once(HttpTransport.Url(url.value), sent(headers))
        .map(a =>
          Plugin.HttpResponse(
            a.code.status,
            a.headers
              .map((k, v) => Header.Name(k.value) -> Header.Value(v.value)),
            Plugin.HttpResponse.Body(a.body.value)
          )
        )

object PolicyHttp:
  type Tries = Tries.T
  object Tries extends Newtype[Int, Pure]

  val UserAgent: CoreQuota.HeaderName = CoreQuota.HeaderName("User-Agent")

  /** The acquisition client of a configuration — the ONE client every plugin's `ctx.http`, every built-in fetcher and
    * every metadata lookup of a generation share: `acquire.user-agent`, and the media politeness's patience with
    * `Retry-After`.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def acquisition(
      cfg: AppConfig,
      transport: HttpTransport,
      sleep: HttpPolicy.Step.Wait.Ms => Unit = ms => Thread.sleep(ms.value),
      log: HostLog = HostLog.scribe
  ): PolicyHttp =
    PolicyHttp(
      HttpPolicy.UserAgent(cfg.acquire.userAgent.value),
      transport,
      maxRetryAfterSeconds = HttpPolicy.MaxRetryAfterSeconds(cfg.media.maxRetryAfterSeconds.value),
      sleep = sleep,
      log = log
    )

  /** The transport the acquisition client sends on: `acquire.timeout-seconds`, `acquire.tls`'s trust. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def acquisitionTransport(cfg: AppConfig): HttpTransport =
    SttpTransport(
      SttpTransport.TimeoutSeconds(cfg.acquire.timeoutSeconds.value),
      OutboundTls.configure(cfg.acquire.tls).map(_._1)
    )
