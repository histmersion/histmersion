package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.agents.RunInfo
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class RunMark(
    run: RunInfo.Id,
    before: Option[RunMark.Before] = None,
    after: RunMark.After = RunMark.After(0),
    maxReopened: RunMark.MaxReopened = RunMark.MaxReopened(0),
    escalated: RunMark.Escalated = RunMark.Escalated(0),
    churn: RunMark.Churn = RunMark.Churn.Progress,
    pairsBefore: Option[RunMark.PairsBefore] = None,
    pairsAfter: Option[RunMark.PairsAfter] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object RunMark:
  type Before = Before.T
  object Before extends Newtype[Int, Pure]
  type After = After.T
  object After extends Newtype[Int, Pure]
  type MaxReopened = MaxReopened.T
  object MaxReopened extends Newtype[Int, Pure]
  type Escalated = Escalated.T
  object Escalated extends Newtype[Int, Pure]
  enum Churn derives CanEqual:
    case Churn, Progress
  object Churn extends TwoStates[Churn]:
    extension (churn: Churn) def churn: Boolean = churn == Churn
  type PairsBefore = PairsBefore.T
  object PairsBefore extends Newtype[Int, Pure]
  type PairsAfter = PairsAfter.T
  object PairsAfter extends Newtype[Int, Pure]
