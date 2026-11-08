package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** The host's client for model calls: HTTP/1.1, the host's User-Agent, a timeout per call, nothing it sends or receives
  * ever logged (a key travels in a header). Failures to connect are `Left`; an HTTP error status is a response (the
  * provider reads its body).
  */
trait AiHttp:
  def post(
      url: AiHttp.PostUrl,
      headers: Map[AiHttp.PostHeadersPart1, AiHttp.PostHeadersPart2],
      body: AiHttp.PostBody,
      timeoutSeconds: AiHttp.PostTimeoutSeconds
  ): Either[AiHttp.PostResultPart1, AiHttp.Response]
  def get(
      url: AiHttp.GetUrl,
      headers: Map[AiHttp.GetHeadersPart1, AiHttp.GetHeadersPart2],
      timeoutSeconds: AiHttp.GetTimeoutSeconds
  ): Either[AiHttp.GetResultPart1, AiHttp.Response]

object AiHttp:
  /** A client that sends nothing (a test, `--self-check`). */
  type PostResultPart1 = PostResultPart1.T
  object PostResultPart1 extends Newtype[String, Pure]
  type PostUrl = PostUrl.T
  object PostUrl extends Newtype[String, Pure]
  type PostHeadersPart1 = PostHeadersPart1.T
  object PostHeadersPart1 extends Newtype[String, Pure]
  type PostHeadersPart2 = PostHeadersPart2.T
  object PostHeadersPart2 extends Newtype[String, Pure]
  type PostBody = PostBody.T
  object PostBody extends Newtype[String, Pure]
  type PostTimeoutSeconds = PostTimeoutSeconds.T
  object PostTimeoutSeconds extends Newtype[Int, Pure]
  type GetResultPart1 = GetResultPart1.T
  object GetResultPart1 extends Newtype[String, Pure]
  type GetUrl = GetUrl.T
  object GetUrl extends Newtype[String, Pure]
  type GetHeadersPart1 = GetHeadersPart1.T
  object GetHeadersPart1 extends Newtype[String, Pure]
  type GetHeadersPart2 = GetHeadersPart2.T
  object GetHeadersPart2 extends Newtype[String, Pure]
  type GetTimeoutSeconds = GetTimeoutSeconds.T
  object GetTimeoutSeconds extends Newtype[Int, Pure]

  val Offline: AiHttp = new AiHttp:
    def post(url: PostUrl, headers: Map[PostHeadersPart1, PostHeadersPart2], body: PostBody, timeoutSeconds: PostTimeoutSeconds) = Left(PostResultPart1("offline"))
    def get(url: GetUrl, headers: Map[GetHeadersPart1, GetHeadersPart2], timeoutSeconds: GetTimeoutSeconds) = Left(GetResultPart1("offline"))

  /** One HTTP answer. */
  final case class Response(status: Response.Status, body: Response.Body, headers: Map[Response.HeadersKey, Response.HeadersValue] = Map.empty)

  object Response:
    type Status = Status.T
    object Status extends Newtype[Int, Pure]
    type Body = Body.T
    object Body extends Newtype[String, Pure]
    type HeadersKey = HeadersKey.T
    object HeadersKey extends Newtype[String, Pure]
    type HeadersValue = HeadersValue.T
    object HeadersValue extends Newtype[String, Pure]
