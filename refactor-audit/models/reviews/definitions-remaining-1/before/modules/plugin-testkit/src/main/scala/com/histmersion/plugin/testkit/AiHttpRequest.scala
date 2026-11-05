package com.histmersion.plugin.testkit

/** One request an AI provider sent through a [[ScriptedAiHttp]]. Header values whose names suggest a credential
  * (`authorization`, `*key*`, `*token*`, `*secret*`) are shown as `***`.
  */
final case class AiHttpRequest(method: String, url: String, headers: Map[String, String], body: Option[String])
