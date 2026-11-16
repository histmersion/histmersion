package com.histmersion
package domain.library.acquisition
import com.histmersion.util.json.RawJson
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.problem.{External, Problem}
import io.scalaland.chimney.dsl.*
import com.histmersion.config.AcquireConfig

import com.histmersion.domain.{CoreQuota, HttpPolicy}
import com.histmersion.plugin.Plugin
import com.histmersion.pluginhost.{HttpAnswer, HttpTransport, PolicyHttp, SttpTransport}

/** The real client: the host's acquisition client ([[PolicyHttp]] — the one outbound policy, a bot challenge the end of
  * a request) as the three verbs the acquisition clients read.
  */
final class SttpHttp(policy: PolicyHttp) extends Http:
  def bytes(
      url: Http.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]
  ): Either[Problem, HttpAnswer.Body] =
    policy.bytes(transport(url), headers)

  override def once(
      url: Http.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]
  ): Either[Problem, HttpAnswer] =
    policy.once(transport(url), headers)

  /** The URL as the host's client takes it. */
  private def transport(url: Http.Url): HttpTransport.Url = HttpTransport.Url(url.value)

  def text(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[Problem, Http.Text] =
    bytes(url, headers).map(b => Http.Text(new String(b.value, "UTF-8")))

  def json(url: Http.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[Problem, Json] =
    text(url, headers).flatMap(s =>
      RawJson
        .parse(s.value)
        .left
        .map(e =>
          val asked = transport(url)
          Problem.Upstream.BadReply
            .here(asked.upstream, asked.address, Problem.Upstream.Shape.Json, External.Reported.of(s"$e"))
        )
    )

object SttpHttp:

  /** The client over java.net.http (sttp, the backend `cli/Client.scala` uses) with `cfg`'s User-Agent and timeout;
    * `tls` is the trust `acquire.tls` configured, `None` the JDK's roots.
    */
  def apply(
      cfg: AcquireConfig,
      tries: PolicyHttp.Tries = PolicyHttp.Tries(4),
      maxRetryAfterSeconds: HttpPolicy.MaxRetryAfterSeconds = HttpPolicy.MaxRetryAfterSeconds(60),
      sleep: HttpPolicy.Step.Wait.Ms => Unit = ms => Thread.sleep(ms.value),
      clock: () => java.time.Instant = () => java.time.Instant.now(),
      tls: Option[javax.net.ssl.SSLContext] = None
  ): SttpHttp =
    new SttpHttp(
      new PolicyHttp(
        HttpPolicy.UserAgent(cfg.userAgent.value),
        SttpTransport(SttpTransport.TimeoutSeconds(cfg.timeoutSeconds.value), tls),
        tries,
        maxRetryAfterSeconds,
        sleep,
        clock
      )
    )
