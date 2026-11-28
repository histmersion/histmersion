package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Creation
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.agents.ModelOverride
import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.queue.QueueItem
import com.histmersion.api.research.Article
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Filter
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class RunRequest(
    pipeline: RunRequest.Pipeline,
    target: RunRequest.Target,
    model: Option[RunRequest.Model] = None,
    budgetUsd: Option[RunRequest.BudgetUsd] = None,
    budgetTokens: Option[RunRequest.BudgetTokens] = None,
    fix: Option[RunRequest.Fix] = None,
    rounds: Option[RunRequest.Rounds] = None,
    acquireItem: Option[QueueItem.Id] = None,
    title: Option[RunRequest.Title] = None,
    kind: Option[RunRequest.Kind] = None,
    query: Option[RunRequest.Query] = None,
    topics: List[RunRequest.Topics] = Nil,
    tags: List[RunRequest.Tags] = Nil,
    goal: Option[Goal.Slug] = None,
    dryRun: Option[RunRequest.DryRun] = None,
    modelFor: List[ModelOverride] = Nil,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object RunRequest:
  type Pipeline = Pipeline.T
  object Pipeline extends Newtype[String, Pure]
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  type Model = Model.T
  object Model extends Newtype[String, Pure]
  type BudgetUsd = BudgetUsd.T
  object BudgetUsd extends Newtype[Double, Pure]
  type BudgetTokens = BudgetTokens.T
  object BudgetTokens extends Newtype[Long, Pure]
  enum Fix derives CanEqual:
    case Fix, Report
  object Fix extends TwoStates[Fix]:
    extension (fix: Fix) def fix: Boolean = fix == Fix
  type Rounds = Rounds.T
  object Rounds extends Newtype[Int, Pure]
  export Article.Title
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Query = Query.T
  object Query extends Newtype[String, Pure]
  export Filter.Topics
  export Filter.Tags
  enum DryRun derives CanEqual:
    case DryRun, Apply
  object DryRun extends TwoStates[DryRun]:
    extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
