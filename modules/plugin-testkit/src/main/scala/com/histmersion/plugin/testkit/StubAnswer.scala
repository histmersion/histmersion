package com.histmersion
package plugin.testkit

import com.histmersion.domain.CoreQuota
import com.histmersion.pluginhost.HttpAnswer
import com.histmersion.lint.{LintRule, tolerated}

/** A canned answer of [[HttpMode.Stub]]. */
final case class StubAnswer(
    status: HttpAnswer.Code,
    body: HttpAnswer.Body,
    headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] = Map.empty
)

object StubAnswer:
  /** A 200 with this body. */
  def ok(
      body: HttpAnswer.Body,
      contentType: CoreQuota.HeaderValue = CoreQuota.HeaderValue("application/octet-stream")
  ): StubAnswer =
    StubAnswer(HttpAnswer.Code(200), body, Map(CoreQuota.HeaderName("content-type") -> contentType))

  /** A 200 with this text, UTF-8. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def text(
      body: HttpAnswer.Text,
      contentType: CoreQuota.HeaderValue = CoreQuota.HeaderValue("text/plain; charset=utf-8")
  ): StubAnswer =
    ok(HttpAnswer.Body(body.value.getBytes(java.nio.charset.StandardCharsets.UTF_8)), contentType)
