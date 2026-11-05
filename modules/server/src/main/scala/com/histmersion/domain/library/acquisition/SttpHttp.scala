package com.histmersion.domain.library.acquisition

import com.histmersion.config.AcquireConfig

import com.histmersion.pluginhost.{HttpAnswer, PolicyHttp, SttpTransport}
import io.circe.Json

/** The real client: the host's acquisition client ([[PolicyHttp]] — the one outbound policy, a bot challenge the end of
  * a request) as the three verbs the acquisition clients read.
  */
final class SttpHttp(policy: PolicyHttp) extends Http:
  def bytes(url: com.histmersion.domain.library.acquisition.SttpHttp.BytesUrl, headers: Map[com.histmersion.domain.library.acquisition.SttpHttp.BytesHeadersPart1, com.histmersion.domain.library.acquisition.SttpHttp.BytesHeadersPart2]): Either[com.histmersion.domain.library.acquisition.SttpHttp.BytesResultPart1, com.histmersion.domain.library.acquisition.SttpHttp.BytesResultPart2] = policy.bytes(url, headers)

  override def once(url: String, headers: Map[String, String]): Either[String, HttpAnswer] = policy.once(url, headers)

  def text(url: com.histmersion.domain.library.acquisition.SttpHttp.TextUrl, headers: Map[com.histmersion.domain.library.acquisition.SttpHttp.TextHeadersPart1, com.histmersion.domain.library.acquisition.SttpHttp.TextHeadersPart2]): Either[com.histmersion.domain.library.acquisition.SttpHttp.TextResultPart1, com.histmersion.domain.library.acquisition.SttpHttp.TextResultPart2] =
    bytes(url, headers).map(new String(_, "UTF-8"))

  def json(url: com.histmersion.domain.library.acquisition.SttpHttp.JsonUrl, headers: Map[com.histmersion.domain.library.acquisition.SttpHttp.JsonHeadersPart1, com.histmersion.domain.library.acquisition.SttpHttp.JsonHeadersPart2]): Either[com.histmersion.domain.library.acquisition.SttpHttp.JsonResultPart1, Json] =
    text(url, headers).flatMap(s =>
      io.circe.parser.parse(s).left.map(e => s"$url returned unparseable JSON: ${e.getMessage}")
    )

object SttpHttp:
  /** The client over java.net.http (sttp, the backend `cli/Client.scala` uses) with `cfg`'s User-Agent and timeout;
    * `tls` is the trust `acquire.tls` configured, `None` the JDK's roots.
    */
  type BytesResultPart1 = BytesResultPart1.T
  object BytesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesResultPart2 = BytesResultPart2.T
  object BytesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesUrl = BytesUrl.T
  object BytesUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesHeadersPart1 = BytesHeadersPart1.T
  object BytesHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesHeadersPart2 = BytesHeadersPart2.T
  object BytesHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextResultPart1 = TextResultPart1.T
  object TextResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextResultPart2 = TextResultPart2.T
  object TextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextUrl = TextUrl.T
  object TextUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextHeadersPart1 = TextHeadersPart1.T
  object TextHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextHeadersPart2 = TextHeadersPart2.T
  object TextHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JsonResultPart1 = JsonResultPart1.T
  object JsonResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JsonUrl = JsonUrl.T
  object JsonUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JsonHeadersPart1 = JsonHeadersPart1.T
  object JsonHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JsonHeadersPart2 = JsonHeadersPart2.T
  object JsonHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyTries = ApplyTries.T
  object ApplyTries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyMaxRetryAfterSeconds = ApplyMaxRetryAfterSeconds.T
  object ApplyMaxRetryAfterSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplySleepArg1 = ApplySleepArg1.T
  object ApplySleepArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(
      cfg: AcquireConfig,
      tries: com.histmersion.domain.library.acquisition.SttpHttp.ApplyTries = 4,
      maxRetryAfterSeconds: com.histmersion.domain.library.acquisition.SttpHttp.ApplyMaxRetryAfterSeconds = 60,
      sleep: com.histmersion.domain.library.acquisition.SttpHttp.ApplySleepArg1 => Unit = ms => Thread.sleep(ms),
      clock: () => java.time.Instant,
      tls: Option[javax.net.ssl.SSLContext] = None
  ): SttpHttp =
    new SttpHttp(
      PolicyHttp(cfg.userAgent, SttpTransport(cfg.timeoutSeconds, tls), tries, maxRetryAfterSeconds, sleep, clock)
    )
