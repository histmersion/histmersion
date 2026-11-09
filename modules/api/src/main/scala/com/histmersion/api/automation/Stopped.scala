package com.histmersion
package api.automation

import com.histmersion.api.Problem as ApiProblem
import com.histmersion.api.automation.Agents.PauseReason
import com.histmersion.api.jobs.JobState
import io.github.iltotore.iron.constraint.any.Pure

/** How a job stopped, as the sentence the `jobs.error` column keeps it. The sentences code writes are the
  * [[Stopped.Cause]]s; [[Stopped.cause]] reads any stored sentence back (what no writer produces is
  * [[Stopped.Cause.Failed]]) and [[Stopped.of]] writes a cause as the same sentence, so a row written by an earlier
  * version reads as it was and one written now reads in an earlier version.
  */
type Stopped = Stopped.T
object Stopped extends Newtype[String, Pure]:

  enum Cause derives CanEqual:

    /** A run that stopped for `reason` and waits for a person, an allowance or a clock: stored `<reason>: <detail>`.
      */
    case Paused(reason: PauseReason, detail: ApiProblem)

    /** A pause for want of a model, stored `<reason>: <lead>all models for role <role> are <rest>`: the role is the one
      * to ask again before the pause's end. `lead` is what the pipeline's step put in front (`enrich: `, or nothing).
      */
    case Exhausted(reason: PauseReason, lead: Lead, role: AgentRole, rest: Rest)

    /** A job a restart cut short: stored `interrupted`. */
    case Interrupted

    /** A job the autopilot's drain stopped; a later pass takes it up. */
    case Draining

    /** Any other sentence, as written (a failure's message, `cancelled`). */
    case Failed(text: ApiProblem)

    /** Is this a pause for `reason`? */
    def pausedOn(reason: PauseReason): Boolean = this match
      case Paused(r, _)          => r == reason
      case Exhausted(r, _, _, _) => r == reason
      case _                     => false

    /** The reason a run paused for, when the sentence says it did: its pause, or the restart that cut it short. */
    def pauseReason: Option[PauseReason] = this match
      case Paused(r, _)          => Some(r)
      case Exhausted(r, _, _, _) => Some(r)
      case Interrupted           => Some(PauseReason.Interrupted)
      case _                     => None

    /** Does a later job take up where this one stopped? Only one a restart or the drain stopped. */
    def resumable: Boolean = this match
      case Interrupted | Draining => true
      case _                      => false

  /** What the step put in front of a pause for want of a model. */
  type Lead = Lead.T
  object Lead extends Newtype[String, Pure]:
    def read(text: String): Lead = Lead(text)

  /** What follows `are` in a pause for want of a model: why no model can run and when one may. */
  type Rest = Rest.T
  object Rest extends Newtype[String, Pure]:
    def read(text: String): Rest = Rest(text)

  /** The words a pause for want of a model starts with (after any lead), up to the role. */
  val AllModelsFor = "all models for role "

  /** The words between the role and what follows. */
  val Are = " are "

  /** The sentence of a job the drain stopped. */
  val DrainingText = "stopped while the autopilot drains: it resumes once the drain is lifted"

  private def prefix(reason: PauseReason): String = s"${reason.wire}: "

  /** The sentence a cause is stored as. */
  def of(c: Cause): Stopped = c match
    case Cause.Paused(reason, detail)              => Stopped(s"${prefix(reason)}$detail")
    case Cause.Exhausted(reason, lead, role, rest) =>
      Stopped(s"${prefix(reason)}$lead$AllModelsFor${role.wire}$Are$rest")
    case Cause.Interrupted  => Stopped(s"${JobState.Interrupted}")
    case Cause.Draining     => Stopped(s"$DrainingText")
    case Cause.Failed(text) => Stopped(s"$text")

  /** The stored sentence of a column read as a problem. */
  def from(stored: ApiProblem): Stopped = Stopped(s"$stored")

  /** The SQL `LIKE` pattern of the stored sentences that read as a pause for `reason`: the prefix [[of]] writes, then
    * anything. A column filtered by it and a column read by [[cause]] agree on which rows are a pause for `reason`.
    */
  def pausedPattern(reason: PauseReason): String = s"${prefix(reason)}%"

  /** Why a paused run waits, from the sentence its row keeps: a run paused with no sentence was paused by a person. */
  def pausedFor(stored: Option[ApiProblem]): Option[PauseReason] =
    stored.fold(Some(PauseReason.Requested))(from(_).cause.pauseReason)

  /** The sentence of a cause, to store or show. */
  def write(c: Cause): ApiProblem = of(c).problem

  private def exhausted(reason: PauseReason, sentence: String): Option[Cause] =
    val at = sentence.indexOf(AllModelsFor)
    Option
      .when(at >= 0) {
        val afterLead = sentence.drop(at + AllModelsFor.length)
        val name = afterLead.takeWhile(_ != ' ')
        val rest = afterLead.drop(name.length)
        AgentRole
          .parse(AgentRole.Wire.read(name))
          .filter(_ => rest.startsWith(Are))
          .map(role => Cause.Exhausted(reason, Lead.read(sentence.take(at)), role, Rest.read(rest.drop(Are.length))))
      }
      .flatten

  extension (s: Stopped)
    def problem: ApiProblem = ApiProblem(s"$s")

    /** What the sentence says. A sentence is only read as a pause, an interruption or a drain when writing that cause
      * gives the sentence back exactly, so [[of]] of [[cause]] is always the stored sentence.
      */
    def cause: Cause =
      val text: String = s.value
      val read: Cause = text match
        case JobState.Interrupted => Cause.Interrupted
        case DrainingText         => Cause.Draining
        case _                    =>
          PauseReason.values
            .find(r => text.startsWith(prefix(r)))
            .fold[Cause](Cause.Failed(ApiProblem(s"$s"))) { r =>
              val detail = text.drop(prefix(r).length)
              exhausted(r, detail).getOrElse(Cause.Paused(r, ApiProblem(s"$detail")))
            }
      if of(read).value == text then read else Cause.Failed(ApiProblem(s"$s"))
