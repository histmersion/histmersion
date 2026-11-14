package com.histmersion
package pluginhost

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.testsupport.ComparedByValue.given

/** What a failed send was, read from the type of the exception: no answer in time, or no answer at all. */
class AiSendFailureKindsSuite extends munit.FunSuite:

  private val http = new HostAiHttp
  private val url = "https://api.example/v1/messages"
  private def failed(e: Throwable): Problem = http.failureOf(e, url, 30)
  private val (name, at) =
    (Problem.Upstream.Name("api.example"), Problem.Upstream.Url("https://api.example/v1/messages"))

  test("a send that took too long is a timeout of the call's own limit"):
    List(
      new java.net.http.HttpConnectTimeoutException("HTTP connect timed out"),
      new java.net.http.HttpTimeoutException("request timed out"),
      new java.net.SocketTimeoutException("x"),
      new java.util.concurrent.TimeoutException("x")
    ).foreach(e => assertEquals(failed(e), Problem.Upstream.Timeout.here(name, at, Problem.Millis(30000L)), e.toString))

  test("anything else is an unreachable upstream, with the exception's own words and never the request"):
    List(
      new java.net.ConnectException("refused"),
      new java.net.UnknownHostException("x"),
      new java.io.EOFException("EOF reached while reading"),
      new IllegalArgumentException("URI with undefined scheme"),
      new NullPointerException()
    ).foreach(e =>
      assertEquals(failed(e), Problem.Upstream.Unreachable.here(name, at, External.Reported.of(e)), e.toString)
    )
    assertEquals(
      failed(new java.net.ConnectException("refused")).toString.contains("java.net.ConnectException: refused"),
      true
    )
