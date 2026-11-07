package com.histmersion.pluginhost

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale
import scala.jdk.CollectionConverters.*

/** One HTTP answer: the status, the headers (names in lower case, first value) and the body. */
final case class HttpAnswer(code: HttpAnswer.Code, headers: Map[com.histmersion.domain.CoreQuota.HeaderName, com.histmersion.domain.CoreQuota.HeaderValue], body: HttpAnswer.Body):
  def header(name: com.histmersion.domain.CoreQuota.HeaderName): Option[com.histmersion.domain.CoreQuota.HeaderValue] =
    headers.get(com.histmersion.domain.CoreQuota.HeaderName(name.value.toLowerCase(Locale.ROOT)))
  def text: com.histmersion.pluginhost.HttpAnswer.Text = HttpAnswer.Text(new String(body.value, UTF_8))

object HttpAnswer:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Code = Code.T
  object Code extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
