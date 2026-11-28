package com.histmersion
package domain.schedulerpolicy

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.automation.ManagersMarkdown
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.schedulerpolicy.Resources
import com.histmersion.domain.schedulerpolicy.Candidate
import com.histmersion.Newtype

final case class Block(why: Block.Why):
  def code: Block.Code = why.code

object Block:
  type Code = Code.T
  object Code extends Newtype[String, Pure]
  type Usd = Usd.T
  object Usd extends Newtype[BigDecimal, Pure]:
    extension (u: Usd) def money: String = ManagersMarkdown.usd(u.value.toDouble)
  type Chain = Chain.T
  object Chain extends Newtype[String, Pure]
  type Detail = Detail.T
  object Detail extends Newtype[String, Pure]
  type Since = Since.T
  object Since extends Newtype[String, Pure]
  type By = By.T
  object By extends Newtype[String, Pure]
  type Because = Because.T
  object Because extends Newtype[String, Pure]

  /** Which spend cap was reached. */
  enum SpendCap:
    /** `agents.budgets.daily-usd`: all agents' spend today. */
    case Daily(limit: Usd)

    /** `agents.budgets.monthly-usd`: all agents' spend this month. */
    case Monthly(limit: Usd)

    /** A manager's own daily cap, against its metered spend today. */
    case Manager(spent: Usd, cap: Usd)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def render: String = this match
      case Daily(l)      => s"today's agent spend reached agents.budgets.daily-usd ($l)"
      case Monthly(l)    => s"this month's agent spend reached agents.budgets.monthly-usd ($l)"
      case Manager(s, c) =>
        s"its spend today (${s.money} USD, metered calls only) reached its daily-usd (${c.money}); " +
          "it starts again tomorrow (UTC)"

  /** What another item must finish first. */
  enum Precondition:
    case Plan, Filter

  /** Why `start` did not start an item. */
  enum Refusal:
    case TargetWritten, NotStartable, Settled, Gone

    def render: String = this match
      case TargetWritten => "another run is writing its target"
      case NotStartable  => "it could not start now (its manager's events say why)"
      case Settled       => "settled without a run"
      case Gone          => "the item is gone"

  /** The one account of why an item waits or nothing starts, closed: each case holds what its sentence is made of. The
    * sentence ([[render]]) and the [[code]] it is filed under come from the case alone.
    */
  enum Why derives CanEqual:
    /** A spend cap is reached. */
    case Budget(cap: SpendCap)

    /** The plan pools the item's role chains draw on are below their floors (`chain` says which and when it frees).
      */
    case Starved(chain: Chain)

    /** The agents are draining: nothing new starts until a person lifts it. */
    case Draining(since: Since, by: Option[By])

    /** An item of the same goal must finish first. */
    case WaitsFor(first: Precondition)

    /** The goal's articles are in progress; `detail` counts them. */
    case ArticlesInProgress(detail: Detail)

    /** Too many planned goals are unfinished; `detail` counts them. */
    case UnfinishedGoals(detail: Detail)

    /** `runs` runs draw on the plan pool already. */
    case PoolFull(pool: Candidate.Pools, runs: Resources.PoolCapValue)

    /** The item's manager has its own `maxRuns` in flight. */
    case ManagerFull(manager: Candidate.ManagerId, maxRuns: Resources.ManagerCapValue)

    /** All places are taken; `because` says which items are ahead. */
    case NoPlaceFree(maxRuns: Resources.MaxRuns, occupied: Resources.Occupied, because: Option[Because])

    /** The dispatch refused to start it. */
    case Busy(refusal: Refusal)

    /** The code this reason is filed under. */
    def code: Code = this match
      case Budget(_)             => Code("budget")
      case Starved(_)            => Code("quota")
      case Draining(_, _)        => Code("drain")
      case WaitsFor(_)           => Code("waits-for")
      case ArticlesInProgress(_) => Code("articles-in-progress")
      case UnfinishedGoals(_)    => Code("unfinished-goals")
      case PoolFull(_, _)        => Code("pool")
      case ManagerFull(_, _)     => Code("manager-cap")
      case NoPlaceFree(_, _, _)  => Code("capacity")
      case Busy(_)               => Code("busy")

    /** The sentence. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def render: String = this match
      case Budget(c)      => s"budget: ${c.render}"
      case Starved(c)     => s"starved: $c"
      case Draining(s, b) =>
        s"draining since $s${b.fold("")(x => s" (by $x)")}: nothing new starts until it is lifted " +
          "(histmersion agent autopilot undrain)"
      case WaitsFor(Precondition.Plan)   => "its goal's plan comes first (the plan-goal item is open)"
      case WaitsFor(Precondition.Filter) =>
        "its article's coverage filter is inferred first (the infer-filter item is open)"
      case ArticlesInProgress(d) => s"$d"
      case UnfinishedGoals(d)    => s"$d"
      case PoolFull(p, n)        =>
        s"plan pool $p has $n run(s) drawing on it already (its max-calls-in-flight × agents.scheduler.pool-overcommit)"
      case ManagerFull(m, n)      => s"its manager $m has its own maxRuns $n in flight"
      case NoPlaceFree(m, o, why) =>
        s"no place free: $m run(s) at once (agents.max-concurrent-runs), $o occupied${why.fold("")(w => s"; $w")}"
      case Busy(r) => r.render

    def isStarved: Boolean = this match
      case Starved(_) => true
      case _          => false

    def isBudget: Boolean = this match
      case Budget(_) => true
      case _         => false
