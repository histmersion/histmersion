package com.histmersion
package plugins.ai

import com.histmersion.plugin.{Plugin, Problem}

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.plugin.AiHttp

/** A request without an answer, described as the providers always described it. */
class SendFailureSuite extends munit.FunSuite:

  private val url = Plugin.Http.Url("http://127.0.0.1:1/v1/messages")

  test("connection, timeout and read failures name their kind, the method and the URL"):
    assertEquals(
      SendFailure(AiHttp.Failure.Connect(Problem("ConnectException: "))).describe(SendFailure.Method("POST"), url),
      s"ConnectException: Exception when sending request: POST $url"
    )
    assertEquals(
      SendFailure(AiHttp.Failure.TimedOut(Problem("HttpConnectTimeoutException: HTTP connect timed out")))
        .describe(SendFailure.Method("POST"), url),
      s"TimeoutException: Exception when sending request: POST $url"
    )
    assertEquals(
      SendFailure(AiHttp.Failure.TimedOut(Problem("HttpTimeoutException: request timed out")))
        .describe(SendFailure.Method("POST"), url),
      s"TimeoutException: Exception when sending request: POST $url"
    )
    assertEquals(
      SendFailure(AiHttp.Failure.Read(Problem("EOFException: EOF reached while reading")))
        .describe(SendFailure.Method("POST"), url),
      s"ReadException: Exception when sending request: POST $url"
    )
    assertEquals(
      SendFailure(AiHttp.Failure.Connect(Problem("UnknownHostException: x")))
        .describe(SendFailure.Method("GET"), url)
        .value
        .take(16),
      "ConnectException"
    )

  test("a request the client refuses to make, or a client that sends nothing, is described as it came"):
    assertEquals(
      SendFailure(AiHttp.Failure.Refused(Problem("IllegalArgumentException: URI with undefined scheme")))
        .describe(SendFailure.Method("POST"), Plugin.Http.Url("x")),
      "IllegalArgumentException: URI with undefined scheme"
    )
    assertEquals(
      SendFailure(AiHttp.Failure.Refused(Problem("offline"))).describe(SendFailure.Method("POST"), url),
      "offline"
    )
    assertEquals(
      SendFailure(AiHttp.Failure.Refused(Problem("no scripted answer"))).describe(SendFailure.Method("POST"), url),
      "no scripted answer"
    )
