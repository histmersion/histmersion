package com.histmersion
package pluginhost

import com.histmersion.util.problem.Problem
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale
import scala.jdk.CollectionConverters.*
import com.histmersion.domain.CoreQuota
import com.histmersion.plugin.Plugin
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** One HTTP answer: the status, the headers (names in lower case, first value) and the body. */
final case class HttpAnswer(
    code: HttpAnswer.Code,
    headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue],
    body: HttpAnswer.Body
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def header(name: CoreQuota.HeaderName): Option[CoreQuota.HeaderValue] =
    headers.get(CoreQuota.HeaderName(name.value.toLowerCase(Locale.ROOT)))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def text: HttpAnswer.Text = HttpAnswer.Text(new String(body.value, UTF_8))

object HttpAnswer:
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  export Challenge.Body

  type Code = Code.T
  object Code extends Newtype[Int, Pure]:
    extension (c: Code)
      /** The status as the plugin SPI states it. */
      def status: Problem.Upstream.Status = Problem.Upstream.Status(c.value)
