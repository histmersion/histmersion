package com.histmersion
package pluginhost

import scala.jdk.CollectionConverters.*
import com.histmersion.domain.CoreQuota
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One GET on the wire: the seam between the host's HTTP policies ([[Polite]], [[PolicyHttp]]) and the network. A test
  * supplies one that answers from fixtures or recordings and never opens a socket. Failures to talk at all are `Left`;
  * any HTTP answer, a 429 included, is a `Right` — the policy decides what it means.
  */
trait HttpTransport:
  def get(url: HttpTransport.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[HttpTransport.Problem, HttpAnswer]

object HttpTransport:
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends RefinedType[Int, Pure]
  type Url = Url.T
  object Url extends RefinedType[String, Pure]
