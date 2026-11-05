package com.histmersion.domain.library.acquisition

import com.histmersion.config.AcquireConfig

import com.histmersion.pluginhost.{HttpAnswer, PolicyHttp, SttpTransport}
import io.circe.Json

/** The real client: the host's acquisition client ([[PolicyHttp]] — the one outbound policy, a bot challenge the end of
  * a request) as the three verbs the acquisition clients read.
  */
final class SttpHttp(policy: PolicyHttp) extends Http:
  def bytes(url: String, headers: Map[String, String]): Either[String, Array[Byte]] = policy.bytes(url, headers)

  override def once(url: String, headers: Map[String, String]): Either[String, HttpAnswer] = policy.once(url, headers)

  def text(url: String, headers: Map[String, String]): Either[String, String] =
    bytes(url, headers).map(new String(_, "UTF-8"))

  def json(url: String, headers: Map[String, String]): Either[String, Json] =
    text(url, headers).flatMap(s =>
      io.circe.parser.parse(s).left.map(e => s"$url returned unparseable JSON: ${e.getMessage}")
    )

object SttpHttp:
  /** The client over java.net.http (sttp, the backend `cli/Client.scala` uses) with `cfg`'s User-Agent and timeout;
    * `tls` is the trust `acquire.tls` configured, `None` the JDK's roots.
    */
  def apply(
      cfg: AcquireConfig,
      tries: Int = 4,
      maxRetryAfterSeconds: Int = 60,
      sleep: Long => Unit = ms => Thread.sleep(ms),
      clock: () => java.time.Instant,
      tls: Option[javax.net.ssl.SSLContext] = None
  ): SttpHttp =
    new SttpHttp(
      PolicyHttp(cfg.userAgent, SttpTransport(cfg.timeoutSeconds, tls), tries, maxRetryAfterSeconds, sleep, clock)
    )
