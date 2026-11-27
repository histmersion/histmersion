package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

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
@movesTo(FeaturePlace.ResearchWorkflows)
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
  object MaxRounds extends Newtype[Int, Pure]
  type RoundsCap = RoundsCap.T
  object RoundsCap extends Newtype[Int, Pure]
  type ArticleChars = ArticleChars.T
  object ArticleChars extends Newtype[Int, Pure]
  type FixerMaxTokens = FixerMaxTokens.T
  object FixerMaxTokens extends Newtype[Int, Pure]
  type FixerTurnsPerFinding = FixerTurnsPerFinding.T
  object FixerTurnsPerFinding extends Newtype[Int, Pure]
  type FixerTurnsCap = FixerTurnsCap.T
  object FixerTurnsCap extends Newtype[Int, Pure]
  type RecheckTurns = RecheckTurns.T
  object RecheckTurns extends Newtype[Int, Pure]
  type LedgerClosedLines = LedgerClosedLines.T
  object LedgerClosedLines extends Newtype[Int, Pure]
  type FinalPanel = FinalPanel.T
  object FinalPanel extends Newtype[String, Pure]
  type SpotChecks = SpotChecks.T
  object SpotChecks extends Newtype[Int, Pure]
  type CritiquePairs = CritiquePairs.T
  object CritiquePairs extends Newtype[Int, Pure]
  type CritiqueReasoned = CritiqueReasoned.T
  object CritiqueReasoned extends Newtype[Int, Pure]
