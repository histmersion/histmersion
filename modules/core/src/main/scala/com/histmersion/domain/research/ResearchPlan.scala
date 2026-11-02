package com.histmersion.domain.research

import com.histmersion.api.ArticleLinks
import com.histmersion.domain.MergePlanner
import com.histmersion.util.data.exchange.{PlanRules, PlanRun}

/** Research's rules of a merge plan: an article the merge writes links the bundle's sources by the citekeys they have
  * here.
  */
final class ResearchPlan extends PlanRun:
  override def relinked(text: String, citekeys: Map[String, String]): String =
    ArticleLinks.retarget(text, citekeys.get)._1

object ResearchPlan extends PlanRules:
  def start(in: MergePlanner.Input): PlanRun = ResearchPlan()
