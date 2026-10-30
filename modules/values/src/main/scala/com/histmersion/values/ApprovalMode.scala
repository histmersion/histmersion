package com.histmersion.values

/** How a kind of proposal is handled: `Manual` waits for a person, `Auto` is applied at once (and still recorded,
  * already approved).
  */
enum ApprovalMode(val wire: String):
  case Manual extends ApprovalMode("manual")
  case Auto extends ApprovalMode("auto")

object ApprovalMode:
  def parse(s: String): Option[ApprovalMode] = values.find(_.wire == s.trim.toLowerCase)
