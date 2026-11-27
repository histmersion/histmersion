package com.histmersion
package domain.library.acquisition
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** CORE's endpoint and its plain key (`histmersion.acquire.core.url`, `.api-key`); the budget's limits are the host's.
  */
final case class CoreEndpoint(url: CoreEndpoint.Url, apiKey: CoreEndpoint.ApiKey) derives ConfigReader:
  /** The key, when one is configured. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def key: Option[CoreEndpoint.ApiKey] =
    (Option(apiKey).map((_).value.trim).filter(_.nonEmpty)).map(refactorValue => CoreEndpoint.ApiKey(refactorValue))

object CoreEndpoint:

  type Url = Url.T
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  object Url extends Newtype[String, Pure]:
    given ConfigReader[Url] = summon[ConfigReader[String]].emap(Url.either)
  type ApiKey = ApiKey.T
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  object ApiKey extends Newtype[String, Pure]:
    given ConfigReader[ApiKey] = summon[ConfigReader[String]].emap(ApiKey.either)
