package com.histmersion
package plugin

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.constraint.any.Pure
import AiHttp.Failure

/** The host's client for model calls: HTTP/1.1, the host's User-Agent, a timeout per call, nothing it sends or receives
  * ever logged (a key travels in a header). Failures to connect are `Left`; an HTTP error status is a response (the
  * provider reads its body).
  */
trait AiHttp:
  def post(
      url: Plugin.Http.Url,
      headers: Map[Header.Name, Header.Value],
      body: AiHttp.PostBody,
      timeoutSeconds: AiHttp.TimeoutSeconds
  ): Either[Failure, AiHttp.Response]
  def get(
      url: Plugin.Http.Url,
      headers: Map[Header.Name, Header.Value],
      timeoutSeconds: AiHttp.TimeoutSeconds
  ): Either[Failure, AiHttp.Response]

object AiHttp:

  /** A request that got no answer: what kind of failure the host's client met, with the text it reported (`<exception>:
    * <message>`, never the request).
    */
  enum Failure derives CanEqual:
    /** No connection could be made (refused, unknown host, no route, a malformed URL). */
    case Connect(reported: Problem)

    /** The connection or the answer took longer than the time limit. */
    case TimedOut(reported: Problem)

    /** Any other failure of the transport while sending or reading. */
    case Read(reported: Problem)

    /** The request could not be made at all (a URL the client refuses), or the client sends nothing. */
    case Refused(reported: Problem)

    def reported: Problem

  /** A client that sends nothing (a test, `--self-check`). */
  type PostBody = PostBody.T
  object PostBody extends Newtype[String, Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends Newtype[Int, Pure]

  val Offline: AiHttp = new AiHttp:
    def post(
        url: Plugin.Http.Url,
        headers: Map[Header.Name, Header.Value],
        body: PostBody,
        timeoutSeconds: TimeoutSeconds
    ) = Left(Failure.Refused(Problem.Config.Unavailable(Problem.Config.Capability.Network)))
    def get(url: Plugin.Http.Url, headers: Map[Header.Name, Header.Value], timeoutSeconds: TimeoutSeconds) = Left(
      Failure.Refused(Problem.Config.Unavailable(Problem.Config.Capability.Network))
    )

  /** One HTTP answer. */
  final case class Response(
      status: Response.Status,
      body: Response.Body,
      headers: Map[Header.Name, Header.Value] = Map.empty
  )

  object Response:
    type Status = Status.T
    object Status extends Newtype[Int, Pure]
    type Body = Body.T
    object Body extends Newtype[String, Pure]
