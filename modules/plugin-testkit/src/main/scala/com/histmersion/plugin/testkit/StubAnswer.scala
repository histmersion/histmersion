package com.histmersion.plugin.testkit

/** A canned answer of [[HttpMode.Stub]]. */
final case class StubAnswer(status: StubAnswer.Status, body: StubAnswer.Body, headers: Map[StubAnswer.HeadersKey, StubAnswer.HeadersValue] = Map.empty)

object StubAnswer:
  /** A 200 with this body. */
  type OkBody = OkBody.T
  object OkBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type OkContentType = OkContentType.T
  object OkContentType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextBody = TextBody.T
  object TextBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextContentType = TextContentType.T
  object TextContentType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadersKey = HeadersKey.T
  object HeadersKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadersValue = HeadersValue.T
  object HeadersValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def ok(body: com.histmersion.plugin.testkit.StubAnswer.OkBody, contentType: com.histmersion.plugin.testkit.StubAnswer.OkContentType = "application/octet-stream"): StubAnswer =
    StubAnswer(200, body, Map("content-type" -> contentType))

  /** A 200 with this text, UTF-8. */
  def text(body: com.histmersion.plugin.testkit.StubAnswer.TextBody, contentType: com.histmersion.plugin.testkit.StubAnswer.TextContentType = "text/plain; charset=utf-8"): StubAnswer =
    ok(body.getBytes(java.nio.charset.StandardCharsets.UTF_8), contentType)
