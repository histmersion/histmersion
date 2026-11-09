package com.histmersion
package plugins.ai

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.plugin.Plugin

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.plugin.AiHttp

/** A request without an answer, described as the providers always described it. */
class SendFailureSuite extends munit.FunSuite:

  private val url = Plugin.Http.Url("http://127.0.0.1:1/v1/messages")

  test("connection, timeout and read failures name their kind, the method and the URL"):
    assertEquals(
      (SendFailure(AiHttp.Failure.Connect(Problem.Unclassified(Legacy.Text("ConnectException: "))))
        .describe(SendFailure.Method("POST"), url))
        .legacyText,
      s"ConnectException: Exception when sending request: POST $url"
    )
    assertEquals(
      (SendFailure(
        AiHttp.Failure.TimedOut(
          Problem.Unclassified(Legacy.Text("HttpConnectTimeoutException: HTTP connect timed out"))
        )
      )
        .describe(SendFailure.Method("POST"), url))
        .legacyText,
      s"TimeoutException: Exception when sending request: POST $url"
    )
    assertEquals(
      (SendFailure(
        AiHttp.Failure.TimedOut(Problem.Unclassified(Legacy.Text("HttpTimeoutException: request timed out")))
      )
        .describe(SendFailure.Method("POST"), url))
        .legacyText,
      s"TimeoutException: Exception when sending request: POST $url"
    )
    assertEquals(
      (SendFailure(AiHttp.Failure.Read(Problem.Unclassified(Legacy.Text("EOFException: EOF reached while reading"))))
        .describe(SendFailure.Method("POST"), url))
        .legacyText,
      s"ReadException: Exception when sending request: POST $url"
    )
    assertEquals(
      SendFailure(AiHttp.Failure.Connect(Problem.Unclassified(Legacy.Text("UnknownHostException: x"))))
        .describe(SendFailure.Method("GET"), url)
        .legacyText
        .take(16),
      "ConnectException"
    )

  test("a request the client refuses to make, or a client that sends nothing, is described as it came"):
    assertEquals(
      (SendFailure(
        AiHttp.Failure.Refused(Problem.Unclassified(Legacy.Text("IllegalArgumentException: URI with undefined scheme")))
      )
        .describe(SendFailure.Method("POST"), Plugin.Http.Url("x")))
        .legacyText,
      "IllegalArgumentException: URI with undefined scheme"
    )
    assertEquals(
      (SendFailure(AiHttp.Failure.Refused(Problem.Unclassified(Legacy.Text("offline"))))
        .describe(SendFailure.Method("POST"), url))
        .legacyText,
      "offline"
    )
    assertEquals(
      (SendFailure(AiHttp.Failure.Refused(Problem.Unclassified(Legacy.Text("no scripted answer"))))
        .describe(SendFailure.Method("POST"), url))
        .legacyText,
      "no scripted answer"
    )
