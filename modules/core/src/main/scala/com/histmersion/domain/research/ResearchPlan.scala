package com.histmersion
package domain.research

import com.histmersion.api.exchange.Bundle
import com.histmersion.api.research.Article
import com.histmersion.domain.MergePlanner
import com.histmersion.domain.library.Citekey
import com.histmersion.util.data.exchange.{PlanRules, PlanRun}
import com.histmersion.api.text.WikiLinks
import com.histmersion.lint.{LintRule, tolerated}

/** Research's rules of a merge plan: an article the merge writes links the bundle's sources by the citekeys they have
  * here.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class ResearchPlan extends PlanRun:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
