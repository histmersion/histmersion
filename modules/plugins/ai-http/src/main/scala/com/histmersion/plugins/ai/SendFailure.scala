package com.histmersion
package plugins.ai

import com.histmersion.plugin.AiHttp
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A request that got no answer, as the host's client reported it (`<exception>: <message>`), described as these
  * providers have always described it: a connection, timeout or read failure as `<kind>: Exception when sending
  * request: <method> <url>`, anything else (a URL the client refuses) as it came.
  */
final case class SendFailure(reported: SendFailure.Reported):

  /** The exception the host's client named. */
  def exception: SendFailure.Problem = SendFailure.Problem((reported).value.takeWhile(_ != ':').trim)

  /** The failure of `method` to `url`. */
  def describe(method: SendFailure.Method, url: AiHttp.GetUrl): SendFailure.Problem =
    SendFailure
      .kind(exception)
      .fold(SendFailure.Problem(reported.value))(k => SendFailure.Problem(s"${k.value}: Exception when sending request: ${method.value} ${url.value}"))

object SendFailure:
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type Method = Method.T
  object Method extends RefinedType[String, Pure]
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]


  type Reported = Reported.T
  object Reported extends RefinedType[String, Pure]

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
  def kind(exception: Problem): Option[Kind] =
    if Connect((exception).value) then Some(Kind("ConnectException"))
    else if Timeout((exception).value) then Some(Kind("TimeoutException"))
    else if (exception).value.endsWith("Exception") && !NotTransport((exception).value) then Some(Kind("ReadException"))
    else None
