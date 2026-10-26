package com.histmersion.server.acquire

import com.histmersion.config.AcquireConfig
import com.histmersion.domain.HttpPolicy

import com.histmersion.pluginhost.{HttpAnswer, PolicyHttp, SttpTransport}
import io.circe.Json

/** The ONE seam between the acquisition toolset and the network.
  *
  * Every upstream client is written against this trait, so a test supplies a fake that answers from a
  * captured fixture and never opens a socket. It is deliberately tiny — three verbs — because the
  * interesting logic is in the pure parsers, not here.
  *
  * Failures are `Left(message)`, never exceptions: an unreachable API is an expected condition for a
  * tool whose whole job is talking to flaky third parties.
  */
trait Http:
  def json(url: String, headers: Map[String, String] = Map.empty): Either[String, Json]
  def text(url: String, headers: Map[String, String] = Map.empty): Either[String, String]
  def bytes(url: String, headers: Map[String, String] = Map.empty): Either[String, Array[Byte]]

  /** One request, not retried, answered as it came — the status and headers too (CORE's budget reads
    * them). A client that only has bodies (a test's) answers 200 with no headers, or its `Left`. */
  def once(url: String, headers: Map[String, String] = Map.empty): Either[String, HttpAnswer] =
    bytes(url, headers).map(HttpAnswer(200, Map.empty, _))

object Http:
  /** A caller's own `User-Agent` (the PMC hosts get the project's public identity) replaces the
    * acquisition one; either goes through [[HttpPolicy.userAgent]], so neither can carry an e-mail. */
  val UserAgent = PolicyHttp.UserAgent

/** The real client: the host's acquisition client ([[PolicyHttp]] — the one outbound policy, a bot
  * challenge the end of a request) as the three verbs the acquisition clients read. */
final class SttpHttp(policy: PolicyHttp) extends Http:
  def bytes(url: String, headers: Map[String, String]): Either[String, Array[Byte]] = policy.bytes(url, headers)

  override def once(url: String, headers: Map[String, String]): Either[String, HttpAnswer] = policy.once(url, headers)

  def text(url: String, headers: Map[String, String]): Either[String, String] =
    bytes(url, headers).map(new String(_, "UTF-8"))

  def json(url: String, headers: Map[String, String]): Either[String, Json] =
    text(url, headers).flatMap(s =>
      io.circe.parser.parse(s).left.map(e => s"$url returned unparseable JSON: ${e.getMessage}"))

object SttpHttp:
  /** The client over java.net.http (sttp, the backend `cli/Client.scala` uses) with `cfg`'s User-Agent
    * and timeout; `tls` is the trust `acquire.tls` configured, `None` the JDK's roots. */
  def apply(cfg: AcquireConfig, tries: Int = 4, maxRetryAfterSeconds: Int = 60,
      sleep: Long => Unit = ms => Thread.sleep(ms), clock: () => java.time.Instant = () => java.time.Instant.now(),
      tls: Option[javax.net.ssl.SSLContext] = None): SttpHttp =
    new SttpHttp(PolicyHttp(cfg.userAgent, SttpTransport(cfg.timeoutSeconds, tls), tries, maxRetryAfterSeconds, sleep, clock))
