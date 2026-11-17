package com.histmersion
package api.automation
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.api.problem.StoredShapes
import com.histmersion.api.problem.Answer.entry

import com.histmersion.api.automation.Agents.PauseReason
import hearth.kindlings.jsoniterjson.Json

/** How a job stopped, as the problems its `jobs.error` keeps say it: a pause for a reason ([[Problem.Run.Halted]], with
  * the problem it stopped on), a restart's cut ([[Problem.Run.Interrupted]]), the autopilot's drain
  * ([[Problem.Run.Draining]]), or a failure (any other problems). A row a master version wrote as a sentence reads into
  * the same cases ([[StoredShapes]]); [[problem]] is the problem a stop is stored as, [[Stopped.of]] reads it back.
  */
enum Stopped:

  /** A run that stopped for `reason` and waits for a person, an allowance or a clock: `why`, what it stopped on. */
  case Paused(reason: PauseReason, why: Problem)

  /** A job a restart cut short. */
  case Interrupted

  /** A job the autopilot's drain stopped; a later pass takes it up. */
  case Draining

  /** Any other problems: a failure, each problem. */
  case Failed(problems: NonEmptyVector[Problem])

  /** Is this a pause for `reason`? */
  def pausedOn(reason: PauseReason): Boolean = this match
    case Paused(r, _) => r == reason
    case _            => false

  /** The reason a run paused for, when its row says it did: its pause, or the restart that cut it short. */
  def pauseReason: Option[PauseReason] = this match
    case Paused(r, _) => Some(r)
    case Interrupted  => Some(PauseReason.Interrupted)
    case _            => None

  /** Does a later job take up where this one stopped? Only one a restart or the drain stopped. */
  def resumable: Boolean = this match
    case Interrupted | Draining => true
    case _                      => false

  /** The problem the stop is stored as: one problem, or a failure's every problem in one ([[Problem.Run.Failed]]). */
  def problem: Problem = this match
    case Paused(reason, why) => Problem.Run.Halted(causeOf(reason), why)
    case Interrupted         => Problem.Run.Interrupted
    case Draining            => Problem.Run.Draining(None)
    case Failed(problems)    => Problems.asOne(problems)

  /** The problem's word for a pause reason. */
  private def causeOf(reason: PauseReason): Problem.Run.PauseCause = reason match
    case PauseReason.Budget      => Problem.Run.PauseCause.Budget
    case PauseReason.Interrupted => Problem.Run.PauseCause.Interrupted
    case PauseReason.Requested   => Problem.Run.PauseCause.Requested
    case PauseReason.Provider    => Problem.Run.PauseCause.Provider
    case PauseReason.Allowance   => Problem.Run.PauseCause.Allowance
    case PauseReason.PlanQuota   => Problem.Run.PauseCause.PlanQuota
    case PauseReason.Stalled     => Problem.Run.PauseCause.Stalled

object Stopped:

  /** What one stored problem says. */
  def of(problem: Problem): Stopped = problem match
    case Problem.Run.Halted(c, why) => Paused(reasonOf(c), why)
    case Problem.Run.Interrupted    => Interrupted
    case Problem.Run.Draining(_)    => Draining
    case other                      => Failed(NonEmptyVector.of(other))

  /** What a job's stored problems say: a pause, a restart's cut or a drain is the one problem the row keeps; several
    * problems are a failure.
    */
  def of(problems: NonEmptyVector[Problem]): Stopped = NonEmptyVector.toVector(problems) match
    case Vector(one) => of(one)
    case _           => Failed(problems)

  /** Why a paused run waits, from the problem its row keeps: a run paused with none was paused by a person. */
  def pausedFor(stored: Option[Problem]): Option[PauseReason] =
    stored.fold(Some(PauseReason.Requested))(of(_).pauseReason)

  /** The role a pause for want of a model names, so a paused run can be asked about just that role again: the chain
    * whose models are all spent, found in what the run stopped on (inside the step it stopped at, too), or read from a
    * master version's sentence.
    */
  def exhaustedRole(why: Problem): Option[AgentRole] =
    exhaustedIn(why).flatMap(role => AgentRole.parse(AgentRole.Wire(s"$role")))

  private def exhaustedIn(why: Problem): Option[Problem.Run.Role] = why match
    case Problem.Run.Exhausted(role, _, _, _) => Some(role)
    case Problem.Run.AtStep(_, problems)      =>
      NonEmptyVector.toVector(problems).iterator.flatMap(exhaustedIn).nextOption()
    case Problem.Stored.Unrecognised(text) => StoredShapes.exhaustedRole(s"$text")
    case _                                 => None

  /** What a job's stored `error` contains when it is a pause for `reason`, as the entry writes it: the SQL `error @>`
    * operand that tells such a row by its case and its cause, whatever it stopped on.
    */
  def pausedMatch(reason: PauseReason): Json =
    val written = Paused(reason, Problem.Run.Interrupted).problem.entry
    val cause = written.data.asObject.flatMap(_("cause")).getOrElse(Json.Null)
    Json.arr(
      Json.obj(
        "group" -> Json.fromString(s"${written.group}"),
        "case" -> Json.fromString(s"${written.`case`}"),
        "data" -> Json.obj("cause" -> cause)
      )
    )

  /** The pause reason a problem's word stands for. */
  private def reasonOf(cause: Problem.Run.PauseCause): PauseReason = cause match
    case Problem.Run.PauseCause.Budget      => PauseReason.Budget
    case Problem.Run.PauseCause.Interrupted => PauseReason.Interrupted
    case Problem.Run.PauseCause.Requested   => PauseReason.Requested
    case Problem.Run.PauseCause.Provider    => PauseReason.Provider
    case Problem.Run.PauseCause.Allowance   => PauseReason.Allowance
    case Problem.Run.PauseCause.PlanQuota   => PauseReason.PlanQuota
    case Problem.Run.PauseCause.Stalled     => PauseReason.Stalled
