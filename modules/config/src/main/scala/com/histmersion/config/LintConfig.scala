package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** `histmersion lint`: how much of each rule's offending set is LISTED (the count is always complete) and how many
  * citekey prefix candidates a dangling edge target is offered.
  */
final case class LintConfig(maxItems: LintConfig.MaxItems, suggestions: LintConfig.Suggestions) derives ConfigReader

object LintConfig:
  type MaxItems = MaxItems.T
  object MaxItems extends Newtype[Int, Pure]
  type Suggestions = Suggestions.T
  object Suggestions extends Newtype[Int, Pure]
