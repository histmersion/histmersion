package com.histmersion
package pluginhost

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.plugin.AiHttp.Failure
import com.histmersion.testsupport.ComparedByValue.given

/** What a failed send was, read from the type of the exception. */
class AiSendFailureKindsSuite extends munit.FunSuite:

  private val http = new HostAiHttp
  private def kindOf(e: Throwable): String = http.failureOf(e) match
    case Failure.Connect(_)  => "Connect"
    case Failure.TimedOut(_) => "TimedOut"
    case Failure.Read(_)     => "Read"
    case Failure.Refused(_)  => "Refused"

  test("a connection that could not be made is Connect"):
    assertEquals(kindOf(new java.net.ConnectException("refused")), "Connect")
    assertEquals(kindOf(new java.net.UnknownHostException("x")), "Connect")
    assertEquals(kindOf(new java.net.NoRouteToHostException("x")), "Connect")
    assertEquals(kindOf(new java.net.MalformedURLException("x")), "Connect")
    assertEquals(kindOf(new java.net.URISyntaxException("x", "y")), "Connect")

  test("a send that took too long is TimedOut"):
    assertEquals(kindOf(new java.net.http.HttpConnectTimeoutException("HTTP connect timed out")), "TimedOut")
    assertEquals(kindOf(new java.net.http.HttpTimeoutException("request timed out")), "TimedOut")
    assertEquals(kindOf(new java.net.SocketTimeoutException("x")), "TimedOut")
    assertEquals(kindOf(new java.util.concurrent.TimeoutException("x")), "TimedOut")

  test("any other failure of the transport is Read; a request that could not be made is Refused"):
    assertEquals(kindOf(new java.io.EOFException("EOF reached while reading")), "Read")
    assertEquals(kindOf(new java.io.IOException("x")), "Read")
    assertEquals(kindOf(new IllegalArgumentException("URI with undefined scheme")), "Refused")
    assertEquals(kindOf(new IllegalStateException("x")), "Refused")
    assertEquals(kindOf(new NullPointerException()), "Refused")
    assertEquals(kindOf(new AssertionError("x")), "Refused")

  test("the reported text is the exception and its message"):
    assertEquals(
      http.failureOf(new IllegalArgumentException("URI with undefined scheme")).reported,
      Problem.Unclassified(Legacy.Text("IllegalArgumentException: URI with undefined scheme"))
    )
    assertEquals(
      http.failureOf(new java.net.ConnectException()).reported,
      Problem.Unclassified(Legacy.Text("ConnectException: "))
    )
