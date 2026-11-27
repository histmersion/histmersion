package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@movesTo(FeaturePlace.LibraryAcquisition)
type CitationCount = CitationCount.T
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object CitationCount extends Newtype[Int, Pure]:
  given ConfigReader[CitationCount] = summon[ConfigReader[Int]].emap(CitationCount.either)
