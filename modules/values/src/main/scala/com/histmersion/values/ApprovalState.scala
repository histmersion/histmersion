package com.histmersion.values

/** `Failed` = a person approved it but applying it failed (the source changed or went away); the reason is kept in
  * `result`.
  */
enum ApprovalState(val wire: String):
  case Open extends ApprovalState("open")
  case Approved extends ApprovalState("approved")
  case Rejected extends ApprovalState("rejected")
  case Failed extends ApprovalState("failed")

object ApprovalState:
  def parse(s: String): Option[ApprovalState] = values.find(_.wire == s.trim.toLowerCase)
