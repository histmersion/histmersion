package com.histmersion.pluginhost

import com.histmersion.config.AppConfig
import com.histmersion.domain.HttpPolicy
import com.histmersion.plugin.{PluginHttp, PluginHttpResponse}
import sttp.client4.*

import scala.concurrent.duration.*

/** The acquisition client: every request a source fetcher (a plugin's through [[PluginHttp]], or a built-in one) and
  * the metadata lookups make, over one [[HttpTransport]].
  *
  * Every request follows [[HttpPolicy]], the one outbound policy: the product's `User-Agent` (no e-mail — Crossref and
  * OpenAlex take the contact as their `mailto` parameter), a slow-down answer (429, 502/503/504) asked again after its
  * `Retry-After` or 1, 2, 4 … s, a host that asks for more than `maxRetryAfterSeconds` left alone. An unreachable host
  * is asked again on the same schedule. A bot challenge ends the request ([[Challenge]]). `tries` counts the first
  * request; `sleep` and `clock` are parameters so a test does not wait.
  */
final class PolicyHttp(
    userAgent: String,
    transport: HttpTransport,
    tries: Int = 4,
    maxRetryAfterSeconds: Int = 60,
    sleep: Long => Unit = ms => Thread.sleep(ms),
    clock: () => java.time.Instant = () => java.time.Instant.now(),
    log: HostLog = HostLog.scribe
):

  private val maxRetries = (tries - 1).max(0)

  /** One GET: the body of a 2xx answer, or why there is none. A caller's own `User-Agent` replaces the configured one;
    * either goes through [[HttpPolicy.userAgent]], so neither can carry an e-mail.
    */
  def bytes(url: String, headers: Map[String, String] = Map.empty): Either[String, Array[Byte]] =
    val ua = headers.collectFirst { case (k, v) if k.equalsIgnoreCase(PolicyHttp.UserAgent) => v }
    val sent = headers.filterNot(_._1.equalsIgnoreCase(PolicyHttp.UserAgent)) +
      (PolicyHttp.UserAgent -> HttpPolicy.userAgent(ua.getOrElse(userAgent)))
    def again(n: Int, ms: Long, why: String): Either[String, Array[Byte]] =
      log.warn(s"acquire: $why — retrying $url in ${ms}ms")
      sleep(ms)
      attempt(n + 1)
    def attempt(n: Int): Either[String, Array[Byte]] =
      transport.get(url, sent) match
        case Left(unreachable) =>
          if n < maxRetries then again(n, HttpPolicy.backoffMs(n), unreachable) else Left(unreachable)
        case Right(answer) =>
          val code = answer.code
          if Challenge.isChallenge(code, answer.headers, answer.body) then
            Left(s"$url: HTTP $code bot-protection challenge — treated as no copy")
          else
            HttpPolicy.decide(code, answer.header("Retry-After"), n, maxRetries, maxRetryAfterSeconds, clock()) match
              case HttpPolicy.Step.Accept                                    => Right(answer.body)
              case HttpPolicy.Step.Wait(ms, why)                             => again(n, ms, why)
              case HttpPolicy.Step.GiveUp(why) if HttpPolicy.Throttled(code) => Left(s"$url failed: $why")
              case HttpPolicy.Step.GiveUp(_) => Left(s"$url: HTTP $code ${new String(answer.body.take(200), "UTF-8")}")
    attempt(0)

  /** One GET with the policy's identity and NO retry: the answer as it came, a slow-down or an error included, with its
    * headers — for an upstream whose own budget decides when to ask again (CORE's rate-limit headers), where a retry
    * would spend what the budget is keeping.
    */
  def once(url: String, headers: Map[String, String] = Map.empty): Either[String, HttpAnswer] =
    val ua = headers.collectFirst { case (k, v) if k.equalsIgnoreCase(PolicyHttp.UserAgent) => v }
    transport.get(
      url,
      headers.filterNot(_._1.equalsIgnoreCase(PolicyHttp.UserAgent)) +
        (PolicyHttp.UserAgent -> HttpPolicy.userAgent(ua.getOrElse(userAgent)))
    )

  /** The client as the plugin SPI lends it: plugins get bytes, and parse what they asked for; a call an upstream's
    * budget meters is sent [[once]], answered with its status and headers.
    */
  def plugin: PluginHttp = new PluginHttp:
    def get(url: String, headers: Map[String, String]) = bytes(url, headers)
    override def fetch(url: String, headers: Map[String, String]) =
      once(url, headers).map(a => PluginHttpResponse(a.code, a.headers, a.body))

object PolicyHttp:
  val UserAgent = "User-Agent"

  /** The acquisition client of a configuration — the ONE client every plugin's `ctx.http`, every built-in fetcher and
    * every metadata lookup of a generation share: `acquire.user-agent`, and the media politeness's patience with
    * `Retry-After`.
    */
  def acquisition(
      cfg: AppConfig,
      transport: HttpTransport,
      sleep: Long => Unit = ms => Thread.sleep(ms),
      log: HostLog = HostLog.scribe
  ): PolicyHttp =
    PolicyHttp(
      cfg.acquire.userAgent,
      transport,
      maxRetryAfterSeconds = cfg.media.maxRetryAfterSeconds,
      sleep = sleep,
      log = log
    )

  /** The transport the acquisition client sends on: `acquire.timeout-seconds`, `acquire.tls`'s trust. */
  def acquisitionTransport(cfg: AppConfig): HttpTransport =
    SttpTransport(cfg.acquire.timeoutSeconds, OutboundTls.configure(cfg.acquire.tls).map(_._1))
