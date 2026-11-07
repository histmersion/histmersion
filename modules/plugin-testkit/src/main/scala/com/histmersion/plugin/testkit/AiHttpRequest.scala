package com.histmersion.plugin.testkit

import com.histmersion.domain.CoreQuota

/** One request an AI provider sent through a [[ScriptedAiHttp]]. Header values whose names suggest a credential
  * (`authorization`, `*key*`, `*token*`, `*secret*`) are shown as `***`.
  */
final case class AiHttpRequest(method: AiHttpRequest.Method, url: AiHttpRequest.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue], body: Option[AiHttpRequest.Body])

object AiHttpRequest:
  type Method = Method.T
  object Method extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
