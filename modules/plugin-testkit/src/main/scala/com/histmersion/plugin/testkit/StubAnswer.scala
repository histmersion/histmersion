package com.histmersion.plugin.testkit

/** A canned answer of [[HttpMode.Stub]]. */
final case class StubAnswer(status: StubAnswer.Status, body: StubAnswer.Body, headers: Map[StubAnswer.HeadersKey, StubAnswer.HeadersValue] = Map.empty)

object StubAnswer:
  type ContentType = ContentType.T
  object ContentType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** A 200 with this body. */
  type OkBody = _root_.com.histmersion.plugin.testkit.StubAnswer.Body
  val OkBody: _root_.com.histmersion.plugin.testkit.StubAnswer.Body.type = _root_.com.histmersion.plugin.testkit.StubAnswer.Body

  type TextContentType = _root_.com.histmersion.plugin.testkit.StubAnswer.ContentType
  val TextContentType: _root_.com.histmersion.plugin.testkit.StubAnswer.ContentType.type = _root_.com.histmersion.plugin.testkit.StubAnswer.ContentType

  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadersKey = HeadersKey.T
  object HeadersKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadersValue = HeadersValue.T
  object HeadersValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def ok(body: com.histmersion.plugin.testkit.StubAnswer.OkBody, contentType: com.histmersion.plugin.testkit.StubAnswer.ContentType = "application/octet-stream"): StubAnswer =
    StubAnswer(200, body, Map("content-type" -> contentType))

  /** A 200 with this text, UTF-8. */
  def text(body: com.histmersion.plugin.testkit.StubAnswer.Text, contentType: com.histmersion.plugin.testkit.StubAnswer.TextContentType = "text/plain; charset=utf-8"): StubAnswer =
    ok(body.getBytes(java.nio.charset.StandardCharsets.UTF_8), contentType)
