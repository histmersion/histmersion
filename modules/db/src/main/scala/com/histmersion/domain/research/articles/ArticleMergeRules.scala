package com.histmersion.domain.research.articles

import com.histmersion.api.exchange.Bundle

import com.histmersion.domain.research.ResearchPlan
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.exchange.{MergeRules, PlanRules}

/** How a merge treats articles: other lines point at them by uid, the ledger names one by its slug, and an article the
  * merge writes links the bundle's sources by the citekeys they have here.
  */
object ArticleMergeRules extends MergeRules:
  type LedgerLabel = LedgerLabel.T
  object LedgerLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Referable = Referable.T
  object Referable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val owner: DataOwner = DataOwner("research")
  val kind: Bundle.Kind = Bundle.Kind.Article
  override val ledgerLabel: Option[com.histmersion.domain.research.articles.ArticleMergeRules.LedgerLabel] = Some("SELECT slug FROM articles WHERE uid = l.local_uid")
  override val referable: com.histmersion.domain.research.articles.ArticleMergeRules.Referable = true
  override val planning: Option[PlanRules] = Some(ResearchPlan)
