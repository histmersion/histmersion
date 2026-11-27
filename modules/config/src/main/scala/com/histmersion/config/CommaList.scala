package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@movesTo(FeaturePlace.Values)
type CommaList = CommaList.T
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object CommaList extends Newtype[String, Pure]:
  type Entry = Entry.T
  object Entry extends Newtype[String, Pure]

  given ConfigReader[CommaList] = summon[ConfigReader[String]].emap(CommaList.either)

  extension (list: CommaList)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def entries: List[Entry] =
      list.value.split(",").toList.map(_.trim.toLowerCase).filter(_.nonEmpty).map(Entry(_))
