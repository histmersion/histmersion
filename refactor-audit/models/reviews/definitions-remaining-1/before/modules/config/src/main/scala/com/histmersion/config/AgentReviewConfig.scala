package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

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
    maxRounds: Int,
    roundsCap: Int,
    articleChars: Int,
    fixerMaxTokens: Int,
    fixerTurnsPerFinding: Int,
    fixerTurnsCap: Int,
    recheckTurns: Int,
    ledgerClosedLines: Int,
    finalPanel: String,
    spotChecks: Int,
    critiquePairs: Int,
    critiqueReasoned: Int
) derives ConfigReader:
  def finalPanelMode: FinalPanel = FinalPanel.parse(finalPanel).getOrElse(FinalPanel.Clear)
