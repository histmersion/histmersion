package com.histmersion
package plugins.ai

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.plugin.AiHttp

/** A request without an answer, described as the providers always described it. */
class SendFailureSuite extends munit.FunSuite:

  private val url = AiHttp.GetUrl("http://127.0.0.1:1/v1/messages")

  test("connection, timeout and read failures name their kind, the method and the URL"):
    assertEquals(
      SendFailure(SendFailure.Reported("ConnectException: ")).describe(SendFailure.Method("POST"), url),
      s"ConnectException: Exception when sending request: POST $url"
    )
    assertEquals(
      SendFailure(SendFailure.Reported("HttpConnectTimeoutException: HTTP connect timed out"))
        .describe(SendFailure.Method("POST"), url),
      s"TimeoutException: Exception when sending request: POST $url"
    )
    assertEquals(
      SendFailure(SendFailure.Reported("HttpTimeoutException: request timed out"))
        .describe(SendFailure.Method("POST"), url),
      s"TimeoutException: Exception when sending request: POST $url"
    )
    assertEquals(
      SendFailure(SendFailure.Reported("EOFException: EOF reached while reading"))
        .describe(SendFailure.Method("POST"), url),
      s"ReadException: Exception when sending request: POST $url"
    )
    assertEquals(
      SendFailure(SendFailure.Reported("UnknownHostException: x"))
        .describe(SendFailure.Method("GET"), url)
        .value
        .take(16),
      "ConnectException"
    )

  test("a request the client refuses to make, or a report that names no exception, is described as it came"):
    assertEquals(
      SendFailure(SendFailure.Reported("IllegalArgumentException: URI with undefined scheme"))
        .describe(SendFailure.Method("POST"), AiHttp.GetUrl("x")),
      "IllegalArgumentException: URI with undefined scheme"
    )
    assertEquals(SendFailure(SendFailure.Reported("offline")).describe(SendFailure.Method("POST"), url), "offline")
    assertEquals(
      SendFailure(SendFailure.Reported("no scripted answer")).describe(SendFailure.Method("POST"), url),
      "no scripted answer"
    )
