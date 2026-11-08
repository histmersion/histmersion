package com.histmersion
package domain.automation.goals

import com.histmersion.domain.automation.Goal
import com.histmersion.domain.research.Slug

/** How a domain measures progress towards the goals (the engine's objectives) in its own terms: the items it links to
  * each goal that exist, and which of them count as finished — research's articles, finished once they passed their
  * review. The autopilot moves a goal's stage, the scheduler and the focus rank goals, from these.
  */
trait ObjectiveProgress:
  /** Every goal's linked items that exist, by goal slug, each list in order. */
  def linked(): Map[Goal.Slug, List[Slug]]

  /** The linked items that count as finished. */
  def finished(): Set[Slug]
