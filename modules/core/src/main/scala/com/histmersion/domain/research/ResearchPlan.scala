package com.histmersion
package domain.research

import com.histmersion.api.exchange.Bundle
import com.histmersion.api.research.Article
import com.histmersion.domain.MergePlanner
import com.histmersion.domain.library.Citekey
import com.histmersion.util.data.exchange.{PlanRules, PlanRun}
import com.histmersion.api.text.WikiLinks

/** Research's rules of a merge plan: an article the merge writes links the bundle's sources by the citekeys they have
  * here.
  */
final class ResearchPlan extends PlanRun:
  override def relinked(text: Bundle.Article.Body, citekeys: Map[Citekey, Citekey]): Bundle.Article.Body =
    val retargeted = Article.Links
      .retarget(
        WikiLinks.Body(text.value),
        key => Citekey.option(key.value).flatMap(citekeys.get).map(ck => Article.Link.Target(ck.value))
      )
      ._1
    Bundle.Article.Body(retargeted.value)

object ResearchPlan extends PlanRules:
  def start(in: MergePlanner.Input): PlanRun = ResearchPlan()
