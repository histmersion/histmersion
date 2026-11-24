package com.histmersion
package plugins.ai

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** One HTTP provider's settings, Histmersion's own `agents.providers.<name>`: where it listens, its key (from the
  * server's environment: `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`), whether tool schemas are sent in OpenAI's strict form,
  * and how long one call may take. The key never reaches a log line: [[toString]] redacts it.
  */
final case class ProviderSettings(
    baseUrl: ProviderSettings.BaseUrl,
    apiKey: ProviderSettings.ApiKey,
    strictTools: ProviderSettings.StrictTools,
    timeoutSeconds: ProviderSettings.TimeoutSeconds
) derives ConfigReader:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def toString: String =
    s"ProviderSettings($baseUrl, apiKey=${if (apiKey).value.isEmpty then "unset" else "<redacted>"}, " +
      s"strictTools=$strictTools, timeoutSeconds=$timeoutSeconds)"

object ProviderSettings:
  type BaseUrl = BaseUrl.T
  object BaseUrl extends Newtype[String, Pure]
  type ApiKey = ApiKey.T
  object ApiKey extends Newtype[String, Pure]
  type StrictTools = StrictTools.T
  object StrictTools extends Newtype[Boolean, Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends Newtype[Int, Pure]
