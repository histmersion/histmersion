package com.histmersion.plugins.ai

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** One HTTP provider's settings, Histmersion's own `agents.providers.<name>`: where it listens, its key (from the
  * server's environment: `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`), whether tool schemas are sent in OpenAI's strict form,
  * and how long one call may take. The key never reaches a log line: [[toString]] redacts it.
  */
final case class ProviderSettings(baseUrl: ProviderSettings.BaseUrl, apiKey: ProviderSettings.ApiKey, strictTools: ProviderSettings.StrictTools, timeoutSeconds: ProviderSettings.TimeoutSeconds)
    derives ConfigReader:
  override def toString: String =
    s"ProviderSettings($baseUrl, apiKey=${if (apiKey).value.isEmpty then "unset" else "<redacted>"}, " +
      s"strictTools=$strictTools, timeoutSeconds=$timeoutSeconds)"

object ProviderSettings:
  type BaseUrl = BaseUrl.T
  object BaseUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApiKey = ApiKey.T
  object ApiKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrictTools = StrictTools.T
  object StrictTools extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
