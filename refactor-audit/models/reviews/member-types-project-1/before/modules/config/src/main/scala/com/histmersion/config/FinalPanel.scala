package com.histmersion.config

/** When a fixing `review-article` run ends with a full panel: only when the re-checks left no MAJOR open (`clear`, the
  * default), after every fix (`always`), or never.
  */
enum FinalPanel(val wire: String):
  case Clear extends FinalPanel("clear")
  case Always extends FinalPanel("always")
  case Never extends FinalPanel("never")

object FinalPanel:
  def parse(s: String): Option[FinalPanel] =
    values.find(_.wire == Option(s).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT))
