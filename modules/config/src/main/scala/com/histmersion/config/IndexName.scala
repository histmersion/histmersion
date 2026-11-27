package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@movesTo(FeaturePlace.LibraryAcquisition)
type IndexName = IndexName.T
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object IndexName extends Newtype[String, Pure]:
  given ConfigReader[IndexName] = summon[ConfigReader[String]].emap(IndexName.either)
