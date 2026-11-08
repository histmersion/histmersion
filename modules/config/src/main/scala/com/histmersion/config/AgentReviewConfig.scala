package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** `review-article`: at most `maxRounds` panel rounds per run (a run may ask for fewer, never more than `roundsCap`),
  * the article body is sent up to `articleChars` characters (a longer one is reviewed cut, and never revised), and the
  * fixer — whose answer is a list of edits — may write up to `fixerMaxTokens` output tokens per call. The fixer's
  * tool-loop turns are its agent's `max-turns` plus `fixerTurnsPerFinding` for each major or factual minor finding, at
  * most `fixerTurnsCap`. A verification pass's re-check of one reviewer gets `recheckTurns` plus one per finding it
  * checks, never more than its agent's `max-turns`; the ledger's closed findings reach a prompt as at most
  * `ledgerClosedLines` lines; `finalPanel` says when a fixing run ends with a full panel
  * (docs/research-review-convergence.md §6.2). A full pass's rules reviewer must report `spotChecks` cited claims, each
  * resting on a get_source/passages call the step made (fewer when the article cites fewer sources; 0 = not enforced,
  * #1840). The skeptic of a full pass is shown at most `critiquePairs` open pairs of the critique checklist in full
  * (the rest by name) and at most `critiqueReasoned` of its reasoned ones (§6.8).
  */
final case class AgentReviewConfig(
    maxRounds: AgentReviewConfig.MaxRounds,
    roundsCap: AgentReviewConfig.RoundsCap,
    articleChars: AgentReviewConfig.ArticleChars,
    fixerMaxTokens: AgentReviewConfig.FixerMaxTokens,
    fixerTurnsPerFinding: AgentReviewConfig.FixerTurnsPerFinding,
    fixerTurnsCap: AgentReviewConfig.FixerTurnsCap,
    recheckTurns: AgentReviewConfig.RecheckTurns,
    ledgerClosedLines: AgentReviewConfig.LedgerClosedLines,
    finalPanel: AgentReviewConfig.FinalPanel,
    spotChecks: AgentReviewConfig.SpotChecks,
    critiquePairs: AgentReviewConfig.CritiquePairs,
    critiqueReasoned: AgentReviewConfig.CritiqueReasoned
) derives ConfigReader:
  def finalPanelMode: FinalPanel = FinalPanel.parse(finalPanel).getOrElse(FinalPanel.Clear)

object AgentReviewConfig:
  type MaxRounds = MaxRounds.T
  object MaxRounds extends RefinedType[Int, Pure]
  type RoundsCap = RoundsCap.T
  object RoundsCap extends RefinedType[Int, Pure]
  type ArticleChars = ArticleChars.T
  object ArticleChars extends RefinedType[Int, Pure]
  type FixerMaxTokens = FixerMaxTokens.T
  object FixerMaxTokens extends RefinedType[Int, Pure]
  type FixerTurnsPerFinding = FixerTurnsPerFinding.T
  object FixerTurnsPerFinding extends RefinedType[Int, Pure]
  type FixerTurnsCap = FixerTurnsCap.T
  object FixerTurnsCap extends RefinedType[Int, Pure]
  type RecheckTurns = RecheckTurns.T
  object RecheckTurns extends RefinedType[Int, Pure]
  type LedgerClosedLines = LedgerClosedLines.T
  object LedgerClosedLines extends RefinedType[Int, Pure]
  type FinalPanel = FinalPanel.T
  object FinalPanel extends RefinedType[String, Pure]
  type SpotChecks = SpotChecks.T
  object SpotChecks extends RefinedType[Int, Pure]
  type CritiquePairs = CritiquePairs.T
  object CritiquePairs extends RefinedType[Int, Pure]
  type CritiqueReasoned = CritiqueReasoned.T
  object CritiqueReasoned extends RefinedType[Int, Pure]
