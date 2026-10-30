package com.histmersion.values

/** The person's switch: `paused` starts nothing new (in-flight runs go on unless paused with them), `stopped` cancelled
  * its in-flight runs and items.
  */
enum ManagerState(val wire: String):
  case Active extends ManagerState("active")
  case Paused extends ManagerState("paused")
  case Stopped extends ManagerState("stopped")

object ManagerState:
  def parse(s: String): Option[ManagerState] = values.find(_.wire == s.trim.toLowerCase)
