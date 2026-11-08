package com.histmersion
package plugin.testkit

import com.histmersion.domain.CoreQuota
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One request an AI provider sent through a [[ScriptedAiHttp]]. Header values whose names suggest a credential
  * (`authorization`, `*key*`, `*token*`, `*secret*`) are shown as `***`.
  */
final case class AiHttpRequest(method: AiHttpRequest.Method, url: AiHttpRequest.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue], body: Option[AiHttpRequest.Body])

object AiHttpRequest:
  type Method = Method.T
  object Method extends RefinedType[String, Pure]
  type Url = Url.T
  object Url extends RefinedType[String, Pure]
  type Body = Body.T
  object Body extends RefinedType[String, Pure]
