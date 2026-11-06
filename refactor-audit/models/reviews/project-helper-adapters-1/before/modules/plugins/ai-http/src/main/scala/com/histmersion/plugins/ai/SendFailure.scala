package com.histmersion.plugins.ai

/** A request that got no answer, as the host's client reported it (`<exception>: <message>`), described as these
  * providers have always described it: a connection, timeout or read failure as `<kind>: Exception when sending
  * request: <method> <url>`, anything else (a URL the client refuses) as it came.
  */
final case class SendFailure(reported: SendFailure.Reported):

  /** The exception the host's client named. */
  def exception: com.histmersion.plugins.ai.SendFailure.ExceptionResult = _root_.com.histmersion.plugins.ai.SendFailure.ExceptionResult((reported).value.takeWhile(_ != ':').trim)

  /** The failure of `method` to `url`. */
  def describe(method: com.histmersion.plugins.ai.SendFailure.DescribeMethod, url: com.histmersion.plugins.ai.SendFailure.DescribeUrl): com.histmersion.plugins.ai.SendFailure.DescribeResult =
    SendFailure.kind(exception).fold(reported)(k => s"$k: Exception when sending request: $method $url")

object SendFailure:

  type ExceptionResult = ExceptionResult.T
  object ExceptionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DescribeResult = DescribeResult.T
  object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DescribeMethod = DescribeMethod.T
  object DescribeMethod extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DescribeUrl = DescribeUrl.T
  object DescribeUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindResult = KindResult.T
  object KindResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindException = KindException.T
  object KindException extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Reported = Reported.T
  object Reported extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Connect = Set(
    "ConnectException",
    "UnknownHostException",
    "MalformedURLException",
    "NoRouteToHostException",
    "PortUnreachableException",
    "ProtocolException",
    "URISyntaxException"
  )

  private val Timeout =
    Set("TimeoutException", "HttpTimeoutException", "HttpConnectTimeoutException", "SocketTimeoutException")

  /** Exceptions that are not the transport's (the request could not be made at all): reported as they came, as is any
    * report that names no exception (an offline client's).
    */
  private val NotTransport = Set(
    "IllegalArgumentException",
    "IllegalStateException",
    "SecurityException",
    "UnsupportedOperationException",
    "NullPointerException"
  )

  /** The kind of transport failure an exception of the host's client is, if it is one. */
  def kind(exception: com.histmersion.plugins.ai.SendFailure.KindException): Option[com.histmersion.plugins.ai.SendFailure.KindResult] =
    if Connect((exception).value) then Some(_root_.com.histmersion.plugins.ai.SendFailure.KindResult("ConnectException"))
    else if Timeout((exception).value) then Some(_root_.com.histmersion.plugins.ai.SendFailure.KindResult("TimeoutException"))
    else if (exception).value.endsWith("Exception") && !NotTransport((exception).value) then Some(_root_.com.histmersion.plugins.ai.SendFailure.KindResult("ReadException"))
    else None
