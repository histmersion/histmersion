package com.histmersion.plugins.ai

/** A request that got no answer, as the host's client reported it (`<exception>: <message>`), described as these
  * providers have always described it: a connection, timeout or read failure as `<kind>: Exception when sending
  * request: <method> <url>`, anything else (a URL the client refuses) as it came.
  */
final case class SendFailure(reported: SendFailure.Reported):

  /** The exception the host's client named. */
  def exception: String = reported.takeWhile(_ != ':').trim

  /** The failure of `method` to `url`. */
  def describe(method: String, url: String): String =
    SendFailure.kind(exception).fold(reported)(k => s"$k: Exception when sending request: $method $url")

object SendFailure:

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
  def kind(exception: String): Option[String] =
    if Connect(exception) then Some("ConnectException")
    else if Timeout(exception) then Some("TimeoutException")
    else if exception.endsWith("Exception") && !NotTransport(exception) then Some("ReadException")
    else None
