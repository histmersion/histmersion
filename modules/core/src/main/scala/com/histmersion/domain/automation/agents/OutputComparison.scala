package com.histmersion.domain.automation.agents

import com.histmersion.domain.CompareRuns

/** What a workflow's own outputs come to, run by run, in `agent compare` (#992): its owner reads its runs' checkpoints
  * (the library's enrich-source: findings, quotes, places, dates; research's review-article: verdicts, findings per
  * reviewer and kind). Every member defaults to nothing of that kind.
  */
trait OutputComparison:

  /** enrich-source's outputs of each run, in the runs' order. */
  def enrich(runs: List[CompareRuns.Run]): List[CompareRuns.EnrichSide] = Nil

  /** review-article's outputs of each run, in the runs' order. */
  def review(runs: List[CompareRuns.Run]): List[CompareRuns.ReviewSide] = Nil

  /** The major findings each pair of runs shares, the pairs in the runs' order. */
  def overlaps(runs: List[CompareRuns.Run]): List[CompareRuns.MajorOverlap] = Nil

object OutputComparison:

  /** A workflow whose outputs are not compared. */
  val none: OutputComparison = new OutputComparison {}
