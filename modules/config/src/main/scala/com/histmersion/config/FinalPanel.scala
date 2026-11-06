package com.histmersion.config

/** When a fixing `review-article` run ends with a full panel: only when the re-checks left no MAJOR open (`clear`, the
  * default), after every fix (`always`), or never.
  */
enum FinalPanel(val wire: FinalPanel.Wire):
  case Clear extends FinalPanel("clear")
  case Always extends FinalPanel("always")
  case Never extends FinalPanel("never")

object FinalPanel:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def parse(s: com.histmersion.config.AgentReviewConfig.FinalPanel): Option[FinalPanel] =
    values.find(_.wire == Option(s).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT))
