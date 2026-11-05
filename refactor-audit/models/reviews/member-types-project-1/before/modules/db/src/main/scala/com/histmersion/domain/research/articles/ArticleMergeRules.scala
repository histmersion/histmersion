package com.histmersion.domain.research.articles

import com.histmersion.api.exchange.Bundle

import com.histmersion.domain.research.ResearchPlan
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.exchange.{MergeRules, PlanRules}

/** How a merge treats articles: other lines point at them by uid, the ledger names one by its slug, and an article the
  * merge writes links the bundle's sources by the citekeys they have here.
  */
object ArticleMergeRules extends MergeRules:
  val owner: DataOwner = DataOwner("research")
  val kind: Bundle.Kind = Bundle.Kind.Article
  override val ledgerLabel: Option[String] = Some("SELECT slug FROM articles WHERE uid = l.local_uid")
  override val referable: Boolean = true
  override val planning: Option[PlanRules] = Some(ResearchPlan)
