package com.histmersion.domain.library.acquisition

import com.histmersion.config.AcquireConfig

import com.histmersion.pluginhost.{HttpAnswer, PolicyHttp, SttpTransport}
import io.circe.Json

/** The real client: the host's acquisition client ([[PolicyHttp]] — the one outbound policy, a bot challenge the end of
  * a request) as the three verbs the acquisition clients read.
  */
final class SttpHttp(policy: PolicyHttp) extends Http:
  def bytes(url: com.histmersion.domain.library.acquisition.SttpHttp.Url, headers: Map[com.histmersion.domain.library.acquisition.SttpHttp.HeaderName, com.histmersion.domain.library.acquisition.SttpHttp.BytesHeadersPart2]): Either[com.histmersion.domain.library.acquisition.SttpHttp.Problem, com.histmersion.domain.library.acquisition.SttpHttp.Bytes] = policy.bytes(url, headers)

  override def once(url: String, headers: Map[String, String]): Either[String, HttpAnswer] = policy.once(url, headers)

  def text(url: com.histmersion.domain.library.acquisition.SttpHttp.Url, headers: Map[com.histmersion.domain.library.acquisition.SttpHttp.HeaderName, com.histmersion.domain.library.acquisition.SttpHttp.TextHeadersPart2]): Either[com.histmersion.domain.library.acquisition.SttpHttp.Problem, com.histmersion.domain.library.acquisition.SttpHttp.Text] =
    bytes(url, headers).map(new String(_, "UTF-8"))

  def json(url: com.histmersion.domain.library.acquisition.SttpHttp.Url, headers: Map[com.histmersion.domain.library.acquisition.SttpHttp.HeaderName, com.histmersion.domain.library.acquisition.SttpHttp.JsonHeadersPart2]): Either[com.histmersion.domain.library.acquisition.SttpHttp.Problem, Json] =
    text(url, headers).flatMap(s =>
      io.circe.parser.parse(s).left.map(e => s"$url returned unparseable JSON: ${e.getMessage}")
    )

object SttpHttp:
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeaderName = HeaderName.T
  object HeaderName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxRetryAfterSeconds = MaxRetryAfterSeconds.T
  object MaxRetryAfterSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SleepMillis = SleepMillis.T
  object SleepMillis extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tries = Tries.T
  object Tries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** The client over java.net.http (sttp, the backend `cli/Client.scala` uses) with `cfg`'s User-Agent and timeout;
    * `tls` is the trust `acquire.tls` configured, `None` the JDK's roots.
    */
  type BytesHeadersPart2 = BytesHeadersPart2.T
  object BytesHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextHeadersPart2 = TextHeadersPart2.T
  object TextHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JsonHeadersPart2 = JsonHeadersPart2.T
  object JsonHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(
      cfg: AcquireConfig,
      tries: com.histmersion.domain.library.acquisition.SttpHttp.Tries = 4,
      maxRetryAfterSeconds: com.histmersion.domain.library.acquisition.SttpHttp.MaxRetryAfterSeconds = 60,
      sleep: com.histmersion.domain.library.acquisition.SttpHttp.SleepMillis => Unit = ms => Thread.sleep(ms),
      clock: () => java.time.Instant,
      tls: Option[javax.net.ssl.SSLContext] = None
  ): SttpHttp =
    new SttpHttp(
      PolicyHttp(cfg.userAgent, SttpTransport(cfg.timeoutSeconds, tls), tries, maxRetryAfterSeconds, sleep, clock)
    )
