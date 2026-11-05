package com.histmersion.plugin.testkit

/** A canned answer of [[HttpMode.Stub]]. */
final case class StubAnswer(status: Int, body: Array[Byte], headers: Map[String, String] = Map.empty)

object StubAnswer:
  /** A 200 with this body. */
  def ok(body: Array[Byte], contentType: String = "application/octet-stream"): StubAnswer =
    StubAnswer(200, body, Map("content-type" -> contentType))

  /** A 200 with this text, UTF-8. */
  def text(body: String, contentType: String = "text/plain; charset=utf-8"): StubAnswer =
    ok(body.getBytes(java.nio.charset.StandardCharsets.UTF_8), contentType)
