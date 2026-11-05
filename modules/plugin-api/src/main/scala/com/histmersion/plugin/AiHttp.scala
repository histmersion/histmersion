package com.histmersion.plugin

/** The host's client for model calls: HTTP/1.1, the host's User-Agent, a timeout per call, nothing it sends or receives
  * ever logged (a key travels in a header). Failures to connect are `Left`; an HTTP error status is a response (the
  * provider reads its body).
  */
trait AiHttp:
  def post(
      url: com.histmersion.plugin.AiHttp.PostUrl,
      headers: Map[com.histmersion.plugin.AiHttp.PostHeadersPart1, com.histmersion.plugin.AiHttp.PostHeadersPart2],
      body: com.histmersion.plugin.AiHttp.PostBody,
      timeoutSeconds: com.histmersion.plugin.AiHttp.PostTimeoutSeconds
  ): Either[com.histmersion.plugin.AiHttp.PostResultPart1, com.histmersion.plugin.AiHttp.Response]
  def get(
      url: com.histmersion.plugin.AiHttp.GetUrl,
      headers: Map[com.histmersion.plugin.AiHttp.GetHeadersPart1, com.histmersion.plugin.AiHttp.GetHeadersPart2],
      timeoutSeconds: com.histmersion.plugin.AiHttp.GetTimeoutSeconds
  ): Either[com.histmersion.plugin.AiHttp.GetResultPart1, com.histmersion.plugin.AiHttp.Response]

object AiHttp:
  /** A client that sends nothing (a test, `--self-check`). */
  type PostResultPart1 = PostResultPart1.T
  object PostResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PostUrl = PostUrl.T
  object PostUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PostHeadersPart1 = PostHeadersPart1.T
  object PostHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PostHeadersPart2 = PostHeadersPart2.T
  object PostHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PostBody = PostBody.T
  object PostBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PostTimeoutSeconds = PostTimeoutSeconds.T
  object PostTimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetResultPart1 = GetResultPart1.T
  object GetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetUrl = GetUrl.T
  object GetUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetHeadersPart1 = GetHeadersPart1.T
  object GetHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetHeadersPart2 = GetHeadersPart2.T
  object GetHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetTimeoutSeconds = GetTimeoutSeconds.T
  object GetTimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Offline: AiHttp = new AiHttp:
    def post(url: com.histmersion.plugin.AiHttp.PostUrl, headers: Map[com.histmersion.plugin.AiHttp.PostHeadersPart1, com.histmersion.plugin.AiHttp.PostHeadersPart2], body: com.histmersion.plugin.AiHttp.PostBody, timeoutSeconds: com.histmersion.plugin.AiHttp.PostTimeoutSeconds) = Left(_root_.com.histmersion.plugin.AiHttp.PostResultPart1("offline"))
    def get(url: com.histmersion.plugin.AiHttp.GetUrl, headers: Map[com.histmersion.plugin.AiHttp.GetHeadersPart1, com.histmersion.plugin.AiHttp.GetHeadersPart2], timeoutSeconds: com.histmersion.plugin.AiHttp.GetTimeoutSeconds) = Left(_root_.com.histmersion.plugin.AiHttp.GetResultPart1("offline"))

  /** One HTTP answer. */
  final case class Response(status: Response.Status, body: Response.Body, headers: Map[Response.HeadersKey, Response.HeadersValue] = Map.empty)

  object Response:
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Body = Body.T
    object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeadersKey = HeadersKey.T
    object HeadersKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeadersValue = HeadersValue.T
    object HeadersValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
