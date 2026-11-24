package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@movesTo(FeaturePlace.LibraryAcquisition)
type IndexName = IndexName.T
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object IndexName extends Newtype[String, Pure]:
  given ConfigReader[IndexName] = summon[ConfigReader[String]].emap(IndexName.either)
