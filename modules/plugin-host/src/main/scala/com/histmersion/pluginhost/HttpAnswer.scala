package com.histmersion.pluginhost

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale
import scala.jdk.CollectionConverters.*

/** One HTTP answer: the status, the headers (names in lower case, first value) and the body. */
final case class HttpAnswer(code: HttpAnswer.Code, headers: Map[HttpAnswer.HeadersKey, HttpAnswer.HeadersValue], body: HttpAnswer.Body):
  def header(name: com.histmersion.domain.CoreQuota.HeaderName): Option[com.histmersion.pluginhost.HttpAnswer.HeaderResult] = headers.get(name.toLowerCase(Locale.ROOT))
  def text: com.histmersion.pluginhost.HttpAnswer.Text = new String(body, UTF_8)

object HttpAnswer:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeaderResult = HeaderResult.T
  object HeaderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Code = Code.T
  object Code extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadersKey = HeadersKey.T
  object HeadersKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadersValue = HeadersValue.T
  object HeadersValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
