package com.histmersion.plugins.ai

/** A request without an answer, described as the providers always described it. */
class SendFailureSuite extends munit.FunSuite:

  private val url = "http://127.0.0.1:1/v1/messages"

  test("connection, timeout and read failures name their kind, the method and the URL"):
    assertEquals(
      SendFailure("ConnectException: ").describe("POST", url),
      s"ConnectException: Exception when sending request: POST $url"
    )
    assertEquals(
      SendFailure("HttpConnectTimeoutException: HTTP connect timed out").describe("POST", url),
      s"TimeoutException: Exception when sending request: POST $url"
    )
    assertEquals(
      SendFailure("HttpTimeoutException: request timed out").describe("POST", url),
      s"TimeoutException: Exception when sending request: POST $url"
    )
    assertEquals(
      SendFailure("EOFException: EOF reached while reading").describe("POST", url),
      s"ReadException: Exception when sending request: POST $url"
    )
    assertEquals(SendFailure("UnknownHostException: x").describe("GET", url).take(16), "ConnectException")

  test("a request the client refuses to make, or a report that names no exception, is described as it came"):
    assertEquals(
      SendFailure("IllegalArgumentException: URI with undefined scheme").describe("POST", "x"),
      "IllegalArgumentException: URI with undefined scheme"
    )
    assertEquals(SendFailure("offline").describe("POST", url), "offline")
    assertEquals(SendFailure("no scripted answer").describe("POST", url), "no scripted answer")
