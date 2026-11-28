package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.agents.RunRequest
import com.histmersion.api.automation.agents.ModelOverride
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates
import com.histmersion.Newtype

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class RunParams(
    fix: Option[RunRequest.Fix] = None,
    rounds: Option[RunRequest.Rounds] = None,
    title: Option[RunRequest.Title] = None,
    kind: Option[RunRequest.Kind] = None,
    query: Option[RunRequest.Query] = None,
    topics: Option[List[RunRequest.Topics]] = None,
    tags: Option[List[RunRequest.Tags]] = None,
    modelFor: Option[List[ModelOverride]] = None,
    notes: Option[List[RunParams.Notes]] = None,
    chainReview: Option[RunParams.ChainReview] = None,
    minSources: Option[RunParams.MinSources] = None,
    maxArticles: Option[RunParams.MaxArticles] = None,
    autoIngest: Option[RunParams.AutoIngest] = None,
    hint: Option[RunParams.Hint] = None,
    subGoals: Option[RunParams.SubGoals] = None,
    oaBacklog: Option[RunParams.OaBacklog] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  def fixes: Boolean = (fix.contains(RunRequest.Fix.Fix))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def noteList: List[RunParams.Notes] = (notes
    .getOrElse(Nil)
    .map((_).value.trim)
    .filter(_.nonEmpty))
    .map(refactorValue4 => RunParams.Notes(refactorValue4))
  def overrides: List[ModelOverride] = modelFor.getOrElse(Nil)

object RunParams:

  type Notes = Notes.T
  object Notes extends Newtype[String, Pure]
  enum ChainReview derives CanEqual:
    case ChainReview, Alone
  object ChainReview extends TwoStates[ChainReview]:
    extension (chainReview: ChainReview) def chainReview: Boolean = chainReview == ChainReview
  type MinSources = MinSources.T
  object MinSources extends Newtype[Int, Pure]
  type MaxArticles = MaxArticles.T
  object MaxArticles extends Newtype[Int, Pure]
  type AutoIngest = AutoIngest.T
  object AutoIngest extends Newtype[Int, Pure]
  type Hint = Hint.T
  object Hint extends Newtype[Long, Pure]
  type SubGoals = SubGoals.T
  object SubGoals extends Newtype[Int, Pure]
  enum OaBacklog derives CanEqual:
    case Backlog, Regular
  object OaBacklog extends TwoStates[OaBacklog]:
    extension (oaBacklog: OaBacklog) def oaBacklog: Boolean = oaBacklog == Backlog
