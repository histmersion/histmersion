package com.histmersion
package api
package problem

import util.problem.Problem
import util.problem.Problem.{Stored, Upstream}
import util.problem.Text.word
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** The texts the master versions stored where a problem is kept now (a job's `error`, a step's `error`, a watch's
  * `last_error`, a plan pool's `refusal`, a problem written bare inside a stored record), read back. Problems are
  * stored as their derived entry (`{"group", "case", "data", "text"}`, [[Answer.Entry]]); these texts are older. Only
  * the shapes a master version wrote and the code acts on read as their case; any other text is
  * [[Problem.Stored.Unrecognised]], kept whole. Each shape names the master writer it was read from; a text reads as a
  * case only when that writer, given the case, wrote the text exactly (ReadBackSuite writes each case back as the
  * master writer did).
  *
  * The migration that turned the text columns into entries (`V110__problems_as_entries.sql`) reads the same shapes.
  */
@atBoundary(BoundaryKind.Json, "reads the text a master version stored where a problem is kept back as its case, or keeps it whole")
object StoredShapes:

  /** A run or job a restart cut short: `interrupted` (master `JobRepo.failInterrupted`, `JobState.InterruptedSentence`;
    * `Engine.reconcile` through `Stopped.Cause.Interrupted`).
    */
  val Interrupted = "interrupted"

  /** A recheck the autopilot's drain stopped (master `AcquireRecheckService`, `Stopped.DrainingText`). */
  val Drained = "stopped while the autopilot drains: it resumes once the drain is lifted"

  /** Before why a watch's index was left out of a sweep (master `CandidateSearch`: `not asked this sweep — <why>`). */
  val NotAsked = "not asked this sweep — "

  /** The details the master pauses wrote after `<reason>: ` as literals: a restart's pause (`Engine`: `interrupted`), a
    * pause asked of a running run (`Engine`: `paused on request`) or of a queued one (`Engine.pause`: `paused before it
    * started`), and an upload the restart lost (`IngestQueue.runJob`).
    */
  private[problem] val Details: Vector[(String, Problem)] = Vector(
    Interrupted -> Problem.Run.Interrupted,
    "paused on request" -> Problem.Run.Paused(Problem.Run.PauseReason.Requested, None),
    "paused before it started" -> Problem.Run.Unstarted,
    "the upload is no longer held by this process" -> Problem.Run.UploadUnheld
  )

  /** The problem a stored text stands for: `interrupted`, the drain's stop, `not asked this sweep — <why>`, or a pause
    * `<reason>: <why>` (master `Stopped.of`, the reason as the agents' wire spells it); any other text kept whole.
    */
  def read(stored: String): Problem =
    if stored == Interrupted then Problem.Run.Interrupted
    else if stored == Drained then Problem.Run.Draining(None)
    else if stored.startsWith(NotAsked) then Upstream.Unasked(detail(stored.drop(NotAsked.length)))
    else
      Problem.Run.PauseCause.values
        .find(c => stored.startsWith(s"${c.word}: "))
        .fold(unrecognised(stored))(c => Problem.Run.Halted(c, detail(stored.drop(c.word.length + 2))))

  /** The role a master pause for want of a model names: `<lead>all models for role <role> are …` (master
    * `ModelPicker.pick`, the exhausted chain's sentence, after the step's `<name>: `).
    */
  def exhaustedRole(detail: String): Option[Problem.Run.Role] =
    val at = detail.indexOf(AllModelsFor)
    Option.when(at >= 0)(detail.drop(at + AllModelsFor.length)).flatMap { afterLead =>
      val role = afterLead.takeWhile(_ != ' ')
      Option.when(role.nonEmpty && afterLead.drop(role.length).startsWith(" are "))(Problem.Run.Role(s"$role"))
    }

  private val AllModelsFor = "all models for role "

  private def detail(text: String): Problem =
    Details.collectFirst { case (`text`, problem) => problem }.getOrElse(unrecognised(text))

  @tolerated(LintRule.G60Unrecognised, "pending G60 (only the stored column's reader builds Unrecognised)")
  private def unrecognised(text: String): Problem = Stored.Unrecognised(Stored.Text(s"$text"))
