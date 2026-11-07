package com.histmersion.plugins.ai

import com.histmersion.testsupport.ComparedByValue.given

/** A request without an answer, described as the providers always described it. */
class SendFailureSuite extends munit.FunSuite:

  private val url = com.histmersion.plugin.AiHttp.GetUrl("http://127.0.0.1:1/v1/messages")

  test("connection, timeout and read failures name their kind, the method and the URL"):
    assertEquals(
      SendFailure(com.histmersion.plugins.ai.SendFailure.Reported("ConnectException: ")).describe(com.histmersion.plugins.ai.SendFailure.Method("POST"), url),
      s"ConnectException: Exception when sending request: POST $url"
    )
    assertEquals(
      SendFailure(com.histmersion.plugins.ai.SendFailure.Reported("HttpConnectTimeoutException: HTTP connect timed out")).describe(com.histmersion.plugins.ai.SendFailure.Method("POST"), url),
      s"TimeoutException: Exception when sending request: POST $url"
    )
    assertEquals(
      SendFailure(com.histmersion.plugins.ai.SendFailure.Reported("HttpTimeoutException: request timed out")).describe(com.histmersion.plugins.ai.SendFailure.Method("POST"), url),
      s"TimeoutException: Exception when sending request: POST $url"
    )
    assertEquals(
      SendFailure(com.histmersion.plugins.ai.SendFailure.Reported("EOFException: EOF reached while reading")).describe(com.histmersion.plugins.ai.SendFailure.Method("POST"), url),
      s"ReadException: Exception when sending request: POST $url"
    )
    assertEquals(SendFailure(com.histmersion.plugins.ai.SendFailure.Reported("UnknownHostException: x")).describe(com.histmersion.plugins.ai.SendFailure.Method("GET"), url).value.take(16), "ConnectException")

  test("a request the client refuses to make, or a report that names no exception, is described as it came"):
    assertEquals(
      SendFailure(com.histmersion.plugins.ai.SendFailure.Reported("IllegalArgumentException: URI with undefined scheme")).describe(com.histmersion.plugins.ai.SendFailure.Method("POST"), com.histmersion.plugin.AiHttp.GetUrl("x")),
      "IllegalArgumentException: URI with undefined scheme"
    )
    assertEquals(SendFailure(com.histmersion.plugins.ai.SendFailure.Reported("offline")).describe(com.histmersion.plugins.ai.SendFailure.Method("POST"), url), "offline")
    assertEquals(SendFailure(com.histmersion.plugins.ai.SendFailure.Reported("no scripted answer")).describe(com.histmersion.plugins.ai.SendFailure.Method("POST"), url), "no scripted answer")
