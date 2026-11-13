package com.histmersion
package domain

import util.problem.Problem
import util.problem.Problem.legacyText
import util.problem.Text
import util.problem.Text.sentence

/** G5textBranch on the closed Problem's words: its sentence, its temporary legacy text, the api's renderer's output. */
final class WordChecks(p: Problem, ps: List[Problem]):
  def sentenceContains: Boolean = p.sentence.contains("HTTP 403") // assert: GuidelineInventory
  def sentencePrefix: Boolean = p.sentence.startsWith("no entry") // assert: GuidelineInventory
  def sentenceCompared: Boolean = p.sentence == "offline" // assert: GuidelineInventory
  def legacyContains: Boolean = p.legacyText.contains("timed out") // assert: GuidelineInventory
  def legacyFlipped: Boolean = "offline" != p.legacyText // assert: GuidelineInventory
  def headlineEnds: Boolean = Text.sentence(p).endsWith(".") // assert: GuidelineInventory
  def anyQuota: Boolean = ps.exists(_.sentence.contains("quota")) // assert: GuidelineInventory
  def sentenceCase: Boolean = p.sentence match // assert: GuidelineInventory
    case "interrupted" => true
    case _             => false

  // no finding: shown, not branched on
  def shown: String = s"failed: ${p.sentence}"
  def same: Boolean = p.sentence == ps.head.sentence
