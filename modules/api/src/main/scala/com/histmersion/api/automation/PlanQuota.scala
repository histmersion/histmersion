package com.histmersion
package api.automation

import com.histmersion.api.automation.modelchains.ChainEntry
import com.histmersion.api.automation.planquota.RefreshInfo
import com.histmersion.api.automation.planquota.Rules
import com.histmersion.api.automation.agents.ModelRef
import com.histmersion.api.automation.planquota.Logins
import com.histmersion.api.automation.planquota.PoolId
import com.histmersion.api.automation.planquota.Reading
import com.histmersion.api.automation.planquota.PoolStatus
import com.histmersion.api.automation.planquota.Verdict
import com.histmersion.api.automation.planquota.WindowState
import com.histmersion.api.automation.planquota.RefreshNeed

import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Problems.traverseAll
import com.histmersion.util.problem.Text.sentence

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.Source
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

/** A plan is spent by every client of the account, and the provider says how much is left only as a percentage of each
  * window plus its reset, so a plan is governed by floors rather than budgets.
  */
object PlanQuota:

  /** Why a plan's pool is blocked, a window is not usable or a reading is not taken: the narration of a decision (a
    * provider's own refusal among them), not a failure of the request.
    */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Now = Now.T
  object Now extends Newtype[Long, Pure]
  type PoolIdText = PoolIdText.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object PoolIdText extends Newtype[String, Pure]
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type WindowStateNow = Now
  val WindowStateNow: Now.type = Now
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type VerdictNow = Now
  val VerdictNow: Now.type = Now
  export ChainEntry.Model as VerdictModel
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type RefreshNeedNow = Now
  val RefreshNeedNow: Now.type = Now
  export ChainEntry.Model as RefreshNeedModel
  export RefreshInfo.LastAttempt as RefreshDecisionLastAttempt
  export Rules.MinGap as RefreshDecisionMinGapMs
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type RefreshDecisionNow = Now
  val RefreshDecisionNow: Now.type = Now

  import Agents.ProviderKind

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def poolOf(m: ModelRef, logins: Logins): Option[PoolId] = m.provider match
    case ProviderKind.ClaudeCode if !((logins.claudeCodeApiKey) == Logins.ClaudeCodeApiKey.ApiKey) =>
      Some(PoolId.ClaudePlan)
    case ProviderKind.Codex if !((logins.codexApiKey) == Logins.CodexApiKey.ApiKey) => Some(PoolId.ChatgptPlan)
    case ProviderKind.Opencode
        if ((logins.opencodeChatgpt) == Logins.OpencodeChatgpt.Chatgpt) && m.model.value.startsWith("openai/") =>
      Some(PoolId.ChatgptPlan)
    case _ => None

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def paid(m: ModelRef, logins: Logins): Boolean =
    (m.provider != ProviderKind.Local && poolOf(m, logins).isEmpty)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def current(readings: List[Reading], now: Now, model: Option[ChainEntry.Model] = None): List[Reading] =
    readings
      .filter(r => r.resetsAt.forall(_.value > now.value))
      .filter(r => r.model.isEmpty || model.isEmpty || r.model == model)
      .groupBy(r => (r.limit, r.kind))
      .values
      .map(_.maxBy(_.observedAt))
      .toList
      .sortBy(r => (r.limit, r.kind))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def verdict(
      readings: List[Reading],
      held: PoolStatus.Held,
      refusal: Option[(Rationale, Option[Verdict.Blocked.Until])],
      rules: Rules,
      now: VerdictNow,
      model: Option[VerdictModel] = None
  ): (Verdict, List[WindowState]) =
    val states = current(readings, now, model).map(_.windowState(rules, now))
    val blocking = states.filterNot((refactorCallback => ((refactorCallback.usable) == WindowState.Usable.Usable)))
    val earliest = blocking.flatMap(_.reading.resetsAt).minOption
    val v =
      if (held).held then Verdict.Blocked(Rationale("held: a person paused all agent use of this plan"), None)
      else
        refusal.filter(_._2.forall(_.value > now.value)) match
          case Some((why, until))        => Verdict.Blocked(why, until)
          case None if blocking.nonEmpty =>
            Verdict.Blocked(
              Rationale(blocking.flatMap(_.why).mkString("; ")),
              earliest
            )
          case None if states.isEmpty =>
            Verdict.Usable(Some(Verdict.Usable.Note("no reading yet: unknown until a run reads it")))
          case None if states.exists((refactorCallback => ((refactorCallback.stale) == WindowState.Stale.Stale))) =>
            Verdict.Usable(Some(Verdict.Usable.Note("the last reading is stale")))
          case None => Verdict.Usable(None)
    (v, states)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def refreshNeed(
      readings: List[Reading],
      held: PoolStatus.Held,
      refusal: Option[(Rationale, Option[Verdict.Blocked.Until])],
      rules: Rules,
      now: RefreshNeedNow,
      model: Option[RefreshNeedModel] = None
  ): Option[RefreshNeed] =
    if ((held) == PoolStatus.Held.Held) || refusal.exists(_._2.forall(_.value > now.value)) then None
    else
      val states = current(readings, now, model).map(_.windowState(rules, now))
      if states.isEmpty then Some(RefreshNeed.NoReading)
      else
        val blocking = states.filterNot((refactorCallback => ((refactorCallback.usable) == WindowState.Usable.Usable)))
        Option.when(
          blocking.nonEmpty && blocking.forall(s =>
            ((s.stale) == WindowState.Stale.Stale) && !((s.reading.rejected) == Reading.Rejected.Rejected) && s.leftPct.value > s.floor.value
          )
        )(
          RefreshNeed.StaleOnly
        )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def throttled(lastAttempt: Option[RefreshInfo.LastAttempt], minGapMs: Rules.MinGap, now: Now): Boolean =
    lastAttempt.exists(t => now.value - t.value < minGapMs.value)

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def refreshDecision(
      need: Option[RefreshNeed],
      login: api.automation.planquota.Login,
      enabled: Boolean,
      lastAttempt: Option[RefreshDecisionLastAttempt],
      minGapMs: RefreshDecisionMinGapMs,
      now: RefreshDecisionNow,
      force: Boolean = (false)
  ): Either[Rationale, Option[RefreshNeed]] =
    login match
      case _ if !(enabled)                                          => Left(Rationale("refresh is off for this pool"))
      case api.automation.planquota.Login.Missing(why)              => Left(Rationale(s"no login: $why"))
      case _ if (force)                                             => Right(need)
      case _ if need.isEmpty                                        => Left(Rationale("the reading decides by itself"))
      case _ if ((throttled(lastAttempt, minGapMs, now)) == (true)) =>
        Left(Rationale("a fresh reading was taken (or tried) less than stale-minutes ago"))
      case _ => Right(need)
