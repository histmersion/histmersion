package com.histmersion
package plugins.ai

import com.histmersion.plugin.Problem
import com.histmersion.plugin.{AiHttp, Plugin}
import io.github.iltotore.iron.constraint.any.Pure

/** A request that got no answer, as the host's client reported it, described as these providers have always described
  * it: a connection, timeout or read failure as `<kind>: Exception when sending request: <method> <url>`, anything else
  * (a URL the client refuses) as it came.
  */
final case class SendFailure(failure: AiHttp.Failure):

  /** The failure of `method` to `url`. */
  def describe(method: SendFailure.Method, url: Plugin.Http.Url): Problem =
    val kind = failure match
      case AiHttp.Failure.Connect(_)  => Some("ConnectException")
      case AiHttp.Failure.TimedOut(_) => Some("TimeoutException")
      case AiHttp.Failure.Read(_)     => Some("ReadException")
      case AiHttp.Failure.Refused(_)  => None
    kind.fold(failure.reported)(k => Problem(s"$k: Exception when sending request: $method $url"))

object SendFailure:
  type Method = Method.T
  object Method extends Newtype[String, Pure]
