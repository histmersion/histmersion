package com.histmersion
package pluginhost

import com.histmersion.plugin.Plugin
import com.histmersion.util.problem.{External, Problem}
import scala.jdk.CollectionConverters.*
import com.histmersion.domain.CoreQuota
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** One GET on the wire: the seam between the host's HTTP policies ([[Polite]], [[PolicyHttp]]) and the network. A test
  * supplies one that answers from fixtures or recordings and never opens a socket. Failures to talk at all are `Left`;
  * any HTTP answer, a 429 included, is a `Right` — the policy decides what it means.
  */
trait HttpTransport:
  def get(
      url: HttpTransport.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]
  ): Either[Problem, HttpAnswer]

object HttpTransport:
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends Newtype[Int, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]:
    extension (url: Url)
      /** The upstream a failed request names: the URL's host (the URL itself when it has none). */
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def upstream: Problem.Upstream.Name =
        Problem.Upstream.Name(
          s"${scala.util.Try(java.net.URI.create(s"$url").getHost).toOption.flatMap(Option(_)).getOrElse(s"$url")}"
        )

      /** The URL as a failed request names it. */
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def address: Problem.Upstream.Url = Problem.Upstream.Url(s"$url")

      /** `status` was the answer at this URL, `body` its start. */
      def answered(status: Problem.Upstream.Status, body: Array[Byte]): Problem =
        Problem.Upstream.Http.here(
          url.upstream,
          url.address,
          status,
          External.ReplyExcerpt.head(new String(body, "UTF-8"))
        )
