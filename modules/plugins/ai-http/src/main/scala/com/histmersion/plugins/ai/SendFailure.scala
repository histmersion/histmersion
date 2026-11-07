package com.histmersion.plugins.ai

/** A request that got no answer, as the host's client reported it (`<exception>: <message>`), described as these
  * providers have always described it: a connection, timeout or read failure as `<kind>: Exception when sending
  * request: <method> <url>`, anything else (a URL the client refuses) as it came.
  */
final case class SendFailure(reported: SendFailure.Reported):

  /** The exception the host's client named. */
  def exception: com.histmersion.plugins.ai.SendFailure.Problem = _root_.com.histmersion.plugins.ai.SendFailure.Problem((reported).value.takeWhile(_ != ':').trim)

  /** The failure of `method` to `url`. */
  def describe(method: com.histmersion.plugins.ai.SendFailure.Method, url: com.histmersion.plugin.AiHttp.GetUrl): com.histmersion.plugins.ai.SendFailure.Problem =
    SendFailure
      .kind(exception)
      .fold(SendFailure.Problem(reported.value))(k => SendFailure.Problem(s"${k.value}: Exception when sending request: ${method.value} ${url.value}"))

object SendFailure:
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Method = Method.T
  object Method extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


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
  def kind(exception: com.histmersion.plugins.ai.SendFailure.Problem): Option[com.histmersion.plugins.ai.SendFailure.Kind] =
    if Connect((exception).value) then Some(_root_.com.histmersion.plugins.ai.SendFailure.Kind("ConnectException"))
    else if Timeout((exception).value) then Some(_root_.com.histmersion.plugins.ai.SendFailure.Kind("TimeoutException"))
    else if (exception).value.endsWith("Exception") && !NotTransport((exception).value) then Some(_root_.com.histmersion.plugins.ai.SendFailure.Kind("ReadException"))
    else None
