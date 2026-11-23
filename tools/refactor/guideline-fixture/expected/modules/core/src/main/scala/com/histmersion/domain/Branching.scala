package com.histmersion
package domain

/** G5textBranch: a text operation on the value of the layer Problem, in main code. */
final class Branching(why: Problem):
  def refused: Boolean = why.value.contains("HTTP 403") // assert: GuidelineInventory
  def missing: Boolean = why.value.startsWith("no entry") // assert: GuidelineInventory
  def ended: Boolean = why.value.endsWith(".") // assert: GuidelineInventory
  def numbered: Boolean = why.value.matches("\\d+") // assert: GuidelineInventory
  def offline: Boolean = why.value == "offline" // assert: GuidelineInventory
  def online: Boolean = why.value != "offline" // assert: GuidelineInventory
  def shown: String = s"failed: $why" // assert: GuidelineInventory.G44 (a splice unwraps; no G5textBranch: shown, not branched on)
  def sized: Int = why.value.length // assert: GuidelineInventory.G44
