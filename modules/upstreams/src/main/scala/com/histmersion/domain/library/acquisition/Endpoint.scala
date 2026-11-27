package com.histmersion
package domain.library.acquisition
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A keyless upstream's endpoint (`histmersion.acquire.<upstream>.url`). */
final case class Endpoint(url: Endpoint.Url) derives ConfigReader

object Endpoint:
  type Url = Url.T
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  object Url extends Newtype[String, Pure]:
    given ConfigReader[Url] = summon[ConfigReader[String]].emap(Url.either)
