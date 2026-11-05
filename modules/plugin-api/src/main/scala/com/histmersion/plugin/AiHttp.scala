package com.histmersion.plugin

/** The host's client for model calls: HTTP/1.1, the host's User-Agent, a timeout per call, nothing it sends or receives
  * ever logged (a key travels in a header). Failures to connect are `Left`; an HTTP error status is a response (the
  * provider reads its body).
  */
trait AiHttp:
  def post(
      url: String,
      headers: Map[String, String],
      body: String,
      timeoutSeconds: Int
  ): Either[String, com.histmersion.plugin.AiHttp.Response]
  def get(
      url: String,
      headers: Map[String, String],
      timeoutSeconds: Int
  ): Either[String, com.histmersion.plugin.AiHttp.Response]

object AiHttp:
  /** A client that sends nothing (a test, `--self-check`). */
  val Offline: AiHttp = new AiHttp:
    def post(url: String, headers: Map[String, String], body: String, timeoutSeconds: Int) = Left("offline")
    def get(url: String, headers: Map[String, String], timeoutSeconds: Int) = Left("offline")

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
