package com.histmersion.pluginhost

import com.histmersion.config.AppConfig
import com.histmersion.domain.CoreQuota
import com.histmersion.domain.HttpPolicy
import sttp.client4.*

import scala.concurrent.duration.*
import com.histmersion.plugin.Plugin

/** The acquisition client: every request a source fetcher (a plugin's through [[Plugin.Http]], or a built-in one) and
  * the metadata lookups make, over one [[HttpTransport]].
  *
  * Every request follows [[HttpPolicy]], the one outbound policy: the product's `User-Agent` (no e-mail — Crossref and
  * OpenAlex take the contact as their `mailto` parameter), a slow-down answer (429, 502/503/504) asked again after its
  * `Retry-After` or 1, 2, 4 … s, a host that asks for more than `maxRetryAfterSeconds` left alone. An unreachable host
  * is asked again on the same schedule. A bot challenge ends the request ([[Challenge]]). `tries` counts the first
  * request; `sleep` and `clock` are parameters so a test does not wait.
  */
final class PolicyHttp(
    userAgent: HttpPolicy.UserAgent,
    transport: HttpTransport,
    tries: PolicyHttp.Tries = PolicyHttp.Tries(4),
    maxRetryAfterSeconds: HttpPolicy.MaxRetryAfterSeconds = HttpPolicy.MaxRetryAfterSeconds(60),
    sleep: HttpPolicy.Step.Wait.Ms => Unit = ms => Thread.sleep(ms.value),
    clock: () => java.time.Instant = () => java.time.Instant.now(),
    log: HostLog = HostLog.scribe
):

  private val maxRetries = HttpPolicy.MaxRetries((tries.value - 1).max(0))

  /** The headers as sent: a caller's own `User-Agent` replaces the configured one; either goes through
    * [[HttpPolicy.userAgent]], so neither can carry an e-mail.
    */
  private def identified(headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] =
    val ua = headers.collectFirst {
      case (k, v) if k.value.equalsIgnoreCase(PolicyHttp.UserAgent.value) => HttpPolicy.UserAgent(v.value)
    }
    headers.filterNot(_._1.value.equalsIgnoreCase(PolicyHttp.UserAgent.value)) +
      (PolicyHttp.UserAgent -> CoreQuota.HeaderValue(HttpPolicy.userAgent(ua.getOrElse(userAgent)).value))

  /** One GET: the body of a 2xx answer, or why there is none. A caller's own `User-Agent` replaces the configured one;
    * either goes through [[HttpPolicy.userAgent]], so neither can carry an e-mail.
    */
  def bytes(url: HttpTransport.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] = Map.empty): Either[HttpTransport.Problem, HttpAnswer.Body] =
    val sent = identified(headers)
    def again(n: HttpPolicy.Attempt, ms: HttpPolicy.Step.Wait.Ms, why: String): Either[HttpTransport.Problem, HttpAnswer.Body] =
      log.warn(HostLog.Line(s"acquire: $why — retrying $url in ${ms}ms"))
      sleep(ms)
      attempt(HttpPolicy.Attempt(n.value + 1))
    def attempt(n: HttpPolicy.Attempt): Either[HttpTransport.Problem, HttpAnswer.Body] =
      transport.get(url, sent) match
        case Left(unreachable) =>
          if n.value < maxRetries.value then again(n, HttpPolicy.backoffMs(n), unreachable.value) else Left(unreachable)
        case Right(answer) =>
          val code = HttpPolicy.StatusCode(answer.code.value)
          if Challenge.isChallenge(code, answer.headers, answer.body) then
            Left(HttpTransport.Problem(s"$url: HTTP $code bot-protection challenge — treated as no copy"))
          else
            HttpPolicy.decide(code, answer.header(CoreQuota.HeaderName("Retry-After")).map(v => HttpPolicy.RetryAfter(v.value)), n, maxRetries, maxRetryAfterSeconds, clock()) match
              case HttpPolicy.Step.Accept                                    => Right(answer.body)
              case HttpPolicy.Step.Wait(ms, why)                             => again(n, ms, why.value)
              case HttpPolicy.Step.GiveUp(why) if HttpPolicy.Throttled(code) => Left(HttpTransport.Problem(s"$url failed: $why"))
              case HttpPolicy.Step.GiveUp(_) =>
                Left(HttpTransport.Problem(s"$url: HTTP $code ${new String(answer.body.value.take(200), "UTF-8")}"))
    attempt(HttpPolicy.Attempt(0))

  /** One GET with the policy's identity and NO retry: the answer as it came, a slow-down or an error included, with its
    * headers — for an upstream whose own budget decides when to ask again (CORE's rate-limit headers), where a retry
    * would spend what the budget is keeping.
    */
  def once(url: HttpTransport.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] = Map.empty): Either[HttpTransport.Problem, HttpAnswer] =
    transport.get(url, identified(headers))

  /** The client as the plugin SPI lends it: plugins get bytes, and parse what they asked for; a call an upstream's
    * budget meters is sent [[once]], answered with its status and headers.
    */
  def plugin: Plugin.Http = new Plugin.Http:
    private def sent(headers: Map[Plugin.Http.GetHeadersPart1, Plugin.Http.GetHeadersPart2]) =
      headers.map((k, v) => CoreQuota.HeaderName(k.value) -> CoreQuota.HeaderValue(v.value))
    def get(url: Plugin.Http.GetUrl, headers: Map[Plugin.Http.GetHeadersPart1, Plugin.Http.GetHeadersPart2]) =
      bytes(HttpTransport.Url(url.value), sent(headers)).left
        .map(e => Plugin.Http.GetResultPart1(e.value))
        .map(b => Plugin.HttpResponse.Body(b.value))
    override def fetch(url: Plugin.Http.FetchUrl, headers: Map[Plugin.Http.FetchHeadersPart1, Plugin.Http.FetchHeadersPart2]) =
      once(HttpTransport.Url(url.value), sent(headers)).left
        .map(e => Plugin.Http.FetchResultPart1(e.value))
        .map(a =>
          Plugin.HttpResponse(
            Plugin.HttpResponse.Status(a.code.value),
            a.headers.map((k, v) => Plugin.HttpResponse.HeadersKey(k.value) -> Plugin.HttpResponse.HeadersValue(v.value)),
            Plugin.HttpResponse.Body(a.body.value)
          )
        )

object PolicyHttp:
  type Tries = Tries.T
  object Tries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val UserAgent: CoreQuota.HeaderName = CoreQuota.HeaderName("User-Agent")

  /** The acquisition client of a configuration — the ONE client every plugin's `ctx.http`, every built-in fetcher and
    * every metadata lookup of a generation share: `acquire.user-agent`, and the media politeness's patience with
    * `Retry-After`.
    */
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
  def acquisitionTransport(cfg: AppConfig): HttpTransport =
    SttpTransport(SttpTransport.TimeoutSeconds(cfg.acquire.timeoutSeconds.value), OutboundTls.configure(cfg.acquire.tls).map(_._1))
