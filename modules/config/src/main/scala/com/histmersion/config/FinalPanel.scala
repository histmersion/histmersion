package com.histmersion
package config

import io.github.iltotore.iron.constraint.any.Pure

/** When a fixing `review-article` run ends with a full panel: only when the re-checks left no MAJOR open (`clear`, the
  * default), after every fix (`always`), or never.
  */
enum FinalPanel(val wire: FinalPanel.Wire):
  case Clear extends FinalPanel(FinalPanel.Wire("clear"))
  case Always extends FinalPanel(FinalPanel.Wire("always"))
  case Never extends FinalPanel(FinalPanel.Wire("never"))

object FinalPanel:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  def parse(s: AgentReviewConfig.FinalPanel): Option[FinalPanel] =
    values.find(_.wire.value == Option(s).map(_.value).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT))
