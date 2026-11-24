package com.histmersion
package server

final case class MergeBase(commit: String, ref: String):
  def describe: String = s"the merge base ${commit.take(9)} of HEAD and $ref"

object MergeBase:
  val Variable: String = "HISTMERSION_MERGE_BASE"
  val Defaults: List[String] = List("contracts3-pilot", "origin/contracts3-pilot")
