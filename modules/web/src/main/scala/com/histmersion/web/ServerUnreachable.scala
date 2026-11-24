package com.histmersion
package web

import com.histmersion.api.Api
import com.histmersion.util.problem.{External, Problem}
import com.histmersion.lint.{LintRule, tolerated}

/** A request that got no answer at all: the browser could not reach the server (it is down or restarting, the network
  * is gone). Not an answer of the server, so no [[ApiFailure]]: a view keeps what it showed and says the server could
  * not be reached ([[Loaded.Unreachable]]).
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
final case class ServerUnreachable(reported: External.Reported) extends Exception(s"$reported")

object ServerUnreachable:

  /** What a failed fetch threw, as the transport failure it is. */
  def of(thrown: Throwable): ServerUnreachable = ServerUnreachable(External.Reported.of(thrown))

  /** A failure the page itself threw (a callback, a decoder): a bug, kept with its class and message. Its id is the
    * page's own: no server log holds it.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def unexpected(thrown: Throwable): Api.Error =
    Api.Error(
      Problem.Internal.Unexpected(
        Problem.Internal.Id(f"page-${scala.util.Random.nextInt(0x1000000)}%06x"),
        Problem.Where("the page"),
        Problem.Internal.ExceptionName.of(thrown),
        External.Reported.of(thrown)
      )
    )
