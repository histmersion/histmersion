package com.histmersion
package config

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** When a fixing `review-article` run ends with a full panel: only when the re-checks left no MAJOR open (`clear`, the
  * default), after every fix (`always`), or never.
  */
@movesTo(FeaturePlace.ResearchWorkflows)
enum FinalPanel(val wire: FinalPanel.Wire):
  case Clear extends FinalPanel(FinalPanel.Wire("clear"))
  case Always extends FinalPanel(FinalPanel.Wire("always"))
  case Never extends FinalPanel(FinalPanel.Wire("never"))

object FinalPanel:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: AgentReviewConfig.FinalPanel): Option[FinalPanel] =
    values.find(_.wire.value == Option(s).map(_.value).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT))
