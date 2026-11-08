package com.histmersion
package pluginhost

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale
import scala.jdk.CollectionConverters.*
import com.histmersion.domain.CoreQuota
import io.github.iltotore.iron.constraint.any.Pure

/** One HTTP answer: the status, the headers (names in lower case, first value) and the body. */
final case class HttpAnswer(
    code: HttpAnswer.Code,
    headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue],
    body: HttpAnswer.Body
):
  def header(name: CoreQuota.HeaderName): Option[CoreQuota.HeaderValue] =
    headers.get(CoreQuota.HeaderName(name.value.toLowerCase(Locale.ROOT)))
  def text: HttpAnswer.Text = HttpAnswer.Text(new String(body.value, UTF_8))

object HttpAnswer:
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  type Body = Body.T
  object Body extends Newtype[Array[Byte], Pure]

  type Code = Code.T
  object Code extends Newtype[Int, Pure]
